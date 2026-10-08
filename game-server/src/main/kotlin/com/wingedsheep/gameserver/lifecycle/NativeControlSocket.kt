package com.wingedsheep.gameserver.lifecycle

import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import com.wingedsheep.gameserver.repository.InMemoryLobbyRepository
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import jakarta.annotation.PreDestroy
import jdk.net.ExtendedSocketOptions
import kotlinx.serialization.json.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.concurrent.thread

/** Private, opt-in control plane. No public HTTP route, token or game payload. */
@Component
@ConditionalOnProperty(name = ["native.lifecycle.enabled"], havingValue = "true")
class NativeControlSocket(
    private val admission: NativeGameAdmission,
    private val repository: GameRepository,
    @Value("\${native.lifecycle.socket:/run/argentum-play/lifecycle.sock}") private val socketPath: String,
    @Value("\${native.lifecycle.updater-user:root}") private val updaterUser: String,
    private val lobbies: InMemoryLobbyRepository,
    private val quickLobbies: QuickGameLobbyRepository,
) : AutoCloseable {
    private var server: ServerSocketChannel? = null
    private var worker: Thread? = null
    private var fileKey: Any? = null
    private val recorderUser = System.getProperty("user.name")
    private val path get() = Path.of(socketPath).toAbsolutePath().normalize()

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        check(admission.enabled && repository is InMemoryGameRepository) { "Unsupported native lifecycle repository" }
        check(server == null)
        val parent = path.parent
        check(Files.isDirectory(parent, NOFOLLOW_LINKS) && parent.toRealPath() == parent)
        check(Files.getOwner(parent, NOFOLLOW_LINKS).name == recorderUser)
        check(Files.getPosixFilePermissions(parent, NOFOLLOW_LINKS) == PosixFilePermissions.fromString("rwx------"))
        check(!Files.exists(path, NOFOLLOW_LINKS)) { "Native lifecycle socket already exists" }
        SocketChannel.open(StandardProtocolFamily.UNIX).use {
            check(it.supportedOptions().contains(ExtendedSocketOptions.SO_PEERCRED)) { "Peer credentials unavailable" }
        }
        val listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            listener.bind(UnixDomainSocketAddress.of(path), 8)
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            fileKey = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey()
            server = listener
            admission.bindPendingActivities { lobbies.nativePendingActivities() + quickLobbies.findAll().size }
            admission.markApplicationReady()
            worker = thread(name = "native-lifecycle", isDaemon = true) {
                while (listener.isOpen) {
                    val channel = try { listener.accept() } catch (_: Exception) { break }
                    channel.use {
                        try {
                            val peer = it.getOption(ExtendedSocketOptions.SO_PEERCRED).user().name
                            if (peer != updaterUser && peer != recorderUser) return@use
                            val request = Json.parseToJsonElement(readLine(it)).jsonObject
                            val response = try { dispatch(peer, request) } catch (_: Exception) {
                                buildJsonObject { put("protocol", 1); put("ok", false); put("error", "request_rejected") }
                            }
                            writeLine(it, response.toString())
                        } catch (_: Exception) {
                            // Transport failures and untrusted input never enter application logs.
                        }
                    }
                }
                // A failed control listener must not leave admission open.
                admission.drain(admission.bootId)
            }
        } catch (failure: Exception) {
            listener.close()
            throw failure
        }
    }

    internal fun dispatch(peer: String, request: JsonObject): JsonObject {
        require(request["protocol"]?.jsonPrimitive?.let { !it.isString && it.intOrNull == 1 } == true)
        val op = request["op"]?.jsonPrimitive?.content
        val common = setOf("protocol", "op")
        when (op) {
            "status" -> require(request.keys == common && (peer == updaterUser || peer == recorderUser))
            "drain" -> {
                require(peer == updaterUser && request.keys == common + "bootId")
                admission.drain(request.getValue("bootId").jsonPrimitive.content)
            }
            "resume" -> {
                require(peer == updaterUser && request.keys == common + setOf("bootId", "releaseId"))
                admission.resume(request.getValue("bootId").jsonPrimitive.content, request.getValue("releaseId").jsonPrimitive.content)
            }
            "recording" -> {
                val fields = setOf("bootId", "releaseId", "recoveryComplete", "recordingHealthy",
                    "producerCoverageComplete", "pendingRecordWrites", "recordingSchemaVersion", "durableBytes", "drainId", "drainComplete", "gymSha")
                require(peer == recorderUser && request.keys == common + fields)
                for (key in listOf("pendingRecordWrites", "recordingSchemaVersion", "durableBytes")) {
                    require(!request.getValue(key).jsonPrimitive.isString)
                }
                for (key in listOf("recoveryComplete", "recordingHealthy", "producerCoverageComplete", "drainComplete")) {
                    require(!request.getValue(key).jsonPrimitive.isString)
                }
                admission.reportRecording(request.getValue("bootId").jsonPrimitive.content,
                    request.getValue("releaseId").jsonPrimitive.content,
                    NativeRecordingHealth(
                        request.getValue("recoveryComplete").jsonPrimitive.boolean,
                        request.getValue("recordingHealthy").jsonPrimitive.boolean,
                        request.getValue("producerCoverageComplete").jsonPrimitive.boolean,
                        request.getValue("pendingRecordWrites").jsonPrimitive.int,
                        request.getValue("recordingSchemaVersion").jsonPrimitive.int,
                        request.getValue("durableBytes").jsonPrimitive.long,
                        request.getValue("drainId").jsonPrimitive.content,
                        request.getValue("drainComplete").jsonPrimitive.boolean,
                        request.getValue("gymSha").jsonPrimitive.content,
                    ))
            }
            else -> error("Unknown private control operation")
        }
        return buildJsonObject {
            admission.status(repository).forEach { (key, value) ->
                put(key, when (value) {
                    null -> JsonNull
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                })
            }
        }
    }

    private fun readLine(channel: SocketChannel): String {
        channel.configureBlocking(false)
        val buffer = ByteBuffer.allocate(4096)
        val deadline = System.nanoTime() + 2_000_000_000L
        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_READ)
            while (System.nanoTime() < deadline) {
                val read = channel.read(buffer)
                check(read >= 0 && buffer.hasRemaining()) { "Incomplete or oversized control request" }
                val end = buffer.array().take(buffer.position()).indexOf(10.toByte())
                if (end >= 0) {
                    require(end == buffer.position() - 1) { "Multiple requests are not supported" }
                    return String(buffer.array(), 0, end, Charsets.UTF_8)
                }
                selector.select(100)
                selector.selectedKeys().clear()
            }
        }
        error("Control request timed out")
    }

    private fun writeLine(channel: SocketChannel, line: String) {
        val buffer = ByteBuffer.wrap((line + "\n").toByteArray(Charsets.UTF_8))
        val deadline = System.nanoTime() + 2_000_000_000L
        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_WRITE)
            while (buffer.hasRemaining() && System.nanoTime() < deadline) {
                channel.write(buffer)
                if (buffer.hasRemaining()) selector.select(100)
                selector.selectedKeys().clear()
            }
            check(!buffer.hasRemaining())
        }
    }

    @PreDestroy
    override fun close() {
        admission.drain(admission.bootId)
        server?.close()
        worker?.join(5000)
        if (fileKey != null && Files.exists(path, NOFOLLOW_LINKS) &&
            Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey() == fileKey) {
            Files.delete(path)
        }
    }
}
