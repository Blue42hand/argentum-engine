package com.wingedsheep.gameserver.recording

import com.wingedsheep.gameserver.lifecycle.NativeGameAdmission
import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import kotlinx.serialization.json.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID

/** Private aggregate producer proof; contains no game/seat identifiers or state. */
@Component
@ConditionalOnProperty(name = ["native.lifecycle.enabled"], havingValue = "true")
class NativeRecordingProbe(private val repository: GameRepository, private val admission: NativeGameAdmission) {
    @Volatile private var recovered = false

    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent::class)
    fun ready() { recovered = true; publish() }

    @Scheduled(fixedDelay = 1000)
    fun publish() {
        val rootName = System.getProperty("game.recording.root") ?: return
        val root = Path.of(rootName).toAbsolutePath().normalize()
        var temporary: Path? = null
        try {
            require(root.toRealPath() == root && Files.isDirectory(root, NOFOLLOW_LINKS))
            require(Files.getPosixFilePermissions(root) == PosixFilePermissions.fromString("rwx------"))
            val sessions = repository.findAll().toList()
            val connected = sessions.count { it.evidenceHealthy() }
            val healthy = repository is InMemoryGameRepository && System.getProperty("game.recording.engine-revision") == admission.engineSha &&
                PrivateGameEvidence.failures.get() == 0L && connected == sessions.size
            val data = buildJsonObject {
                put("schemaVersion", 1); put("bootId", admission.bootId); put("releaseId", admission.releaseId)
                put("engineSha", admission.engineSha); put("gymSha", admission.expectedGymSha)
                put("utc", Instant.now().toString()); put("recoveryComplete", recovered)
                put("recordingHealthy", healthy); put("producerCoverageComplete", healthy)
                put("registeredSources", sessions.size); put("connectedSources", connected)
                // Each producer's synchronized health blocks until its append and fsync finish.
                put("pendingRecordWrites", 0)
            }.toString().toByteArray(Charsets.UTF_8)
            temporary = root.resolve(".native-health-${UUID.randomUUID()}.tmp")
            FileChannel.open(temporary, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).use {
                val buffer = ByteBuffer.wrap(data)
                while (buffer.hasRemaining()) it.write(buffer)
                it.force(true)
            }
            Files.move(temporary, root.resolve(".native-health.json"), ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        } catch (_: Exception) {
            // No fabricated healthy heartbeat: the consumer fails closed on absence/staleness.
        } finally { temporary?.let { runCatching { Files.deleteIfExists(it) } } }
    }
}
