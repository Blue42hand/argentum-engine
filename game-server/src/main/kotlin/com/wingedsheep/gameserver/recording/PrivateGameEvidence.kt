package com.wingedsheep.gameserver.recording

import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Private server evidence only. Consumers must never use admin rows as pilot input. */
fun interface GameEvidenceSink {
    fun append(kind: String, payload: JsonElement, seatId: String?)
}

/** Opt-in bounded append-only capture. Never deletes old games or changes existing permissions. */
class PrivateGameEvidence private constructor(
    private val directory: Path,
    private val gameId: String,
    private val engineRevision: String,
    private val maxBytes: Long,
    private val ownership: FileChannel,
) : GameEvidenceSink, AutoCloseable {
    private var sequence = 0L
    private var previous = "0".repeat(64)
    private var bytes = 0L
    private var failed = false
    private var sealed = false
    private val epoch = UUID.randomUUID().toString()

    @Synchronized
    override fun close() {
        if (!sealed) failed = true
        ownership.close() // incomplete source stays an intact prefix without a terminal
    }

    @Synchronized
    override fun append(kind: String, payload: JsonElement, seatId: String?) {
        if (failed || sealed) return
        var boundFailure: JsonObject? = null
        try {
            val body = buildJsonObject {
                put("schemaVersion", 1)
                put("gameId", gameId)
                put("sequence", sequence + 1)
                put("previousSha256", previous)
                put("engineRevision", engineRevision)
                put("clockEpoch", epoch)
                put("utc", Instant.now().toString())
                put("monotonicNanos", System.nanoTime())
                put("kind", kind)
                put("visibility", if (seatId == null) "admin" else "seat")
                seatId?.let { put("seatId", it) }
                put("payload", payload)
            }.toString()
            val digest = sha256(body.toByteArray(Charsets.UTF_8))
            val line = (buildJsonObject { put("body", body); put("sha256", digest) }.toString() + "\n")
                .toByteArray(Charsets.UTF_8)
            if (bytes > maxBytes || line.size.toLong() > maxBytes - bytes) {
                // Safe numeric evidence of this specific guard; never retain the rejected payload.
                boundFailure = buildJsonObject {
                    put("reason", "storage_bound_reached")
                    put("committedBytes", bytes)
                    put("maxBytes", maxBytes)
                    put("attemptedRowBytes", line.size)
                    put("lastCommittedSequence", sequence)
                }
                throw IllegalArgumentException("native evidence storage bound reached")
            }
            val file = directory.resolve("native-000000.ndjson")
            FileChannel.open(file, CREATE, WRITE, APPEND, NOFOLLOW_LINKS).use { channel ->
                val buffer = ByteBuffer.wrap(line)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            sync(directory)
            bytes += line.size
            sequence++
            previous = digest
            if (kind == "terminal" || kind == "session_closed") {
                sealed = true
                ownership.close()
            }
        } catch (error: Exception) {
            // Preserve the prefix and freeze permanently; no later terminal can claim completeness.
            failed = true
            runCatching { ownership.close() }
            failures.incrementAndGet()
            // Exception messages/stacks may contain private transport or payload details.
            val reason = boundFailure?.getValue("reason")?.jsonPrimitive?.content ?: "append_failed"
            logger.error("Private evidence capture failed for game {} ({}, reason={})",
                gameId, error.javaClass.simpleName, reason)
            markGap(directory, gameId, error.javaClass.simpleName, boundFailure)
        }
    }

    @Synchronized
    fun healthy(): Boolean = !failed

    companion object {
        internal val failures = java.util.concurrent.atomic.AtomicLong()

        private val logger = LoggerFactory.getLogger(PrivateGameEvidence::class.java)
        private val privateDirectory = PosixFilePermissions.fromString("rwx------")
        private val privateFile = PosixFilePermissions.fromString("rw-------")
        internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun sync(path: Path) = FileChannel.open(path, READ).use { it.force(true) }

        private fun markGap(directory: Path, gameId: String, code: String, boundFailure: JsonObject? = null) {
            try {
                if (!Files.isDirectory(directory, NOFOLLOW_LINKS) || Files.getPosixFilePermissions(directory) != privateDirectory) return
                if (Files.exists(directory.resolve("manifest.json"), NOFOLLOW_LINKS)) return
                val file = directory.resolve("native-gap-${UUID.randomUUID()}.json")
                val data = buildJsonObject {
                    put("schemaVersion", 1)
                    put("gameId", gameId)
                    put("code", code)
                    put("utc", Instant.now().toString())
                    boundFailure?.forEach { (key, value) -> put(key, value) }
                }.toString().toByteArray(Charsets.UTF_8)
                FileChannel.open(file, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(privateFile)).use {
                    val buffer = ByteBuffer.wrap(data)
                    while (buffer.hasRemaining()) it.write(buffer)
                    it.force(true)
                }
                sync(directory)
            } catch (_: Exception) { /* Disk failure can also prevent a marker; stderr still records the class. */ }
        }

        fun fromSystemProperties(gameId: String): GameEvidenceSink? {
            val root = System.getProperty("game.recording.root") ?: return null
            return try {
                val revision = requireNotNull(System.getProperty("game.recording.engine-revision")) {
                    "enabled private recording requires an exact engine revision"
                }
                require(revision.matches(Regex("[0-9a-f]{40}"))) { "engine revision must be a full commit id" }
                create(Path.of(root), gameId, revision)
            } catch (error: Exception) {
                failures.incrementAndGet()
                // Optional telemetry must not make persisted games unrecoverable. Preserve history.
                logger.error("Private evidence unavailable for game {} ({})", gameId, error.javaClass.simpleName)
                if (gameId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    markGap(Path.of(root).resolve(gameId), gameId, error.javaClass.simpleName)
                }
                null
            }
        }

        internal fun create(root: Path, gameId: String, engineRevision: String,
                            maxBytes: Long = 1024L * 1024 * 1024): PrivateGameEvidence {
            require(gameId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "invalid evidence game id" }
            require(Files.isDirectory(root, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(root) == privateDirectory) {
                "recording root must already be private (0700)"
            }
            val directory = root.resolve(gameId)
            try {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(privateDirectory))
            } catch (_: FileAlreadyExistsException) {
                require(Files.isDirectory(directory, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(directory) == privateDirectory) {
                    "existing recording directory must be private"
                }
            }
            sync(root)
            val ownership = FileChannel.open(directory.resolve(".native-writer.lock"),
                setOf(CREATE, WRITE, NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(privateFile))
            try {
                require(Files.getPosixFilePermissions(directory.resolve(".native-writer.lock")) == privateFile) {
                    "native writer lock must be private"
                }
                require(ownership.tryLock() != null) { "native game source already has a writer" }
                val file = directory.resolve("native-000000.ndjson")
                if (!Files.exists(file, NOFOLLOW_LINKS)) {
                    Files.createFile(file, PosixFilePermissions.asFileAttribute(privateFile))
                    sync(directory)
                }
                require(!Files.isSymbolicLink(file) && Files.getPosixFilePermissions(file) == privateFile) {
                    "existing native source must be private"
                }
                val writer = PrivateGameEvidence(directory, gameId, engineRevision, maxBytes, ownership)
                Files.newBufferedReader(file).use { reader ->
                    var count = 0L
                    while (true) {
                        val line = reader.readLine() ?: break
                        val wrapper = Json.parseToJsonElement(line).jsonObject
                        val exact = wrapper.getValue("body").jsonPrimitive.content
                        val digest = sha256(exact.toByteArray(Charsets.UTF_8))
                        val body = Json.parseToJsonElement(exact).jsonObject
                        require(wrapper.getValue("sha256").jsonPrimitive.content == digest &&
                            body.getValue("schemaVersion").jsonPrimitive.int == 1 &&
                            body.getValue("gameId").jsonPrimitive.content == gameId &&
                            body.getValue("sequence").jsonPrimitive.long == writer.sequence + 1 &&
                            body.getValue("previousSha256").jsonPrimitive.content == writer.previous && !writer.sealed) {
                            "uncertain native source cannot resume"
                        }
                        writer.sequence++
                        writer.previous = digest
                        writer.sealed = body.getValue("kind").jsonPrimitive.content in setOf("terminal", "session_closed")
                        count += line.toByteArray(Charsets.UTF_8).size + 1
                    }
                    require(count == Files.size(file) && !writer.sealed) { "partial or terminal source cannot resume" }
                    writer.bytes = count
                }
                if (writer.sequence > 0) writer.append("resume", buildJsonObject {
                    put("previousSourceSequence", writer.sequence)
                    put("exactReplayVerified", false)
                }, null)
                return writer
            } catch (error: Exception) {
                ownership.close()
                throw error
            }
        }
    }
}
