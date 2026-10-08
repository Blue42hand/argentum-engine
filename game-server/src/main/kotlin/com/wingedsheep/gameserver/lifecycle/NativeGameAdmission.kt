package com.wingedsheep.gameserver.lifecycle

import com.wingedsheep.gameserver.repository.GameRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

class NativeAdmissionClosed : IllegalStateException("New games are paused for maintenance")

data class NativeRecordingHealth(
    val recoveryComplete: Boolean,
    val recordingHealthy: Boolean,
    val producerCoverageComplete: Boolean,
    val pendingRecordWrites: Int,
    val recordingSchemaVersion: Int,
    val durableBytes: Long,
    val drainId: String = "",
    val drainComplete: Boolean = false,
    val gymSha: String = "",
)

/** Internal control metadata only. The recorder remains the source of capture/flush evidence. */
@Component
class NativeGameAdmission @Autowired constructor(
    @Value("\${native.lifecycle.enabled:false}") val enabled: Boolean,
    @Value("\${app.version:dev}") val engineSha: String,
    @Value("\${native.lifecycle.release-id:}") val releaseId: String,
    @Value("\${native.lifecycle.gym-sha:}") val expectedGymSha: String,
    @Value("\${native.lifecycle.recording-schema-version:1}") val expectedRecordingSchemaVersion: Int = 1,
) {
    constructor() : this(false, "dev", "", "")
    internal var nanoTime: () -> Long = System::nanoTime
    val bootId: String = UUID.randomUUID().toString()
    private val lock = Any()
    private var accepting = !enabled
    private var drainAcknowledged = false
    private var drainId = ""
    private var applicationReady = false
    private var recording: NativeRecordingHealth? = null
    private var receivedNanos = 0L
    private var inFlightAdmissions = 0
    private val admittedRequest = ThreadLocal.withInitial { false }
    private var pendingActivities: () -> Int = { 0 }

    init {
        if (enabled) {
            require(engineSha.matches(Regex("[a-f0-9]{40}"))) { "Missing native source identity" }
            require(releaseId.matches(Regex("[a-f0-9]{64}"))) { "Missing native release identity" }
            require(expectedGymSha.matches(Regex("[a-f0-9]{40}"))) { "Missing native recorder source identity" }
            require(expectedRecordingSchemaVersion > 0) { "Missing recording schema identity" }
        }
    }

    fun markApplicationReady() = synchronized(lock) { applicationReady = true }
    internal fun bindPendingActivities(counter: () -> Int) = synchronized(lock) { pendingActivities = counter }

    fun reportRecording(expectedBootId: String, expectedReleaseId: String, health: NativeRecordingHealth) =
        synchronized(lock) {
            require(expectedBootId == bootId && expectedReleaseId == releaseId) { "Wrong recording epoch" }
            require(health.gymSha == expectedGymSha) { "Wrong recorder source identity" }
            require(health.recordingSchemaVersion == expectedRecordingSchemaVersion) { "Wrong recording schema identity" }
            require(health.pendingRecordWrites >= 0 && health.recordingSchemaVersion > 0 && health.durableBytes >= 0)
            if (recording != null && !recordingReady()) accepting = false
            recording = health
            receivedNanos = nanoTime()
            if (!recordingReady()) accepting = false
        }

    private fun recordingFresh(): Boolean = recording != null && nanoTime() - receivedNanos in 0..10_000_000_000L

    private fun recordingReady(): Boolean = applicationReady && recordingFresh() && recording!!.let {
        it.recoveryComplete && it.recordingHealthy && it.producerCoverageComplete
    }

    /** The repository registers new sessions under the same lock that acknowledges drain. */
    fun <T> register(isNew: () -> Boolean, save: () -> T): T {
        if (!enabled) return save()
        return synchronized(lock) {
            if (!recordingReady()) accepting = false
            // New client creation is reserved at ingress; already admitted lobbies
            // must still be able to produce their queued matches while draining.
            if (isNew() && (!recordingReady() ||
                (!accepting && !admittedRequest.get() && pendingActivities() == 0))) throw NativeAdmissionClosed()
            save()
        }
    }

    /** Reserve before a start handler mutates lobby/player state, without holding its locks. */
    fun <T> withNewGameAdmission(work: () -> T): T {
        if (!enabled || admittedRequest.get()) return work()
        synchronized(lock) {
            if (!accepting || !recordingReady()) throw NativeAdmissionClosed()
            inFlightAdmissions++
        }
        admittedRequest.set(true)
        try {
            return work()
        } finally {
            admittedRequest.remove()
            synchronized(lock) { inFlightAdmissions-- }
        }
    }

    fun drain(expectedBootId: String) = synchronized(lock) {
        require(expectedBootId == bootId) { "Wrong native epoch" }
        accepting = false
        if (!drainAcknowledged) drainId = UUID.randomUUID().toString()
        drainAcknowledged = true
    }

    fun resume(expectedBootId: String, expectedReleaseId: String) = synchronized(lock) {
        require(expectedBootId == bootId && expectedReleaseId == releaseId) { "Wrong native epoch" }
        check(recordingReady()) { "Recording is not ready" }
        accepting = true
        drainAcknowledged = false
        drainId = ""
    }

    fun status(repository: GameRepository, activityCounter: () -> Int = pendingActivities): Map<String, Any?> {
        // Never take a session state lock while holding admission: state changes can save sessions.
        val (sessions, metadata) = synchronized(lock) {
            if (enabled && !recordingReady()) accepting = false
            val fresh = recordingFresh()
            repository.findAll().toList() to linkedMapOf<String, Any?>(
                "protocol" to 1, "ok" to true, "engineSha" to engineSha, "releaseId" to releaseId,
                "bootId" to bootId, "observedUnix" to Instant.now().epochSecond,
                "acceptingNewGames" to (accepting && (!enabled || recordingReady())),
                "drainAcknowledged" to drainAcknowledged,
                "drainId" to drainId,
                "recordingDrainComplete" to (fresh && drainAcknowledged && recording?.drainId == drainId &&
                    recording?.drainComplete == true && recording?.pendingRecordWrites == 0 && inFlightAdmissions == 0),
                "recoveryComplete" to (applicationReady && fresh && recording?.recoveryComplete == true),
                "recordingHealthy" to (fresh && recording?.recordingHealthy == true &&
                    recording?.producerCoverageComplete == true),
                "pendingRecordWrites" to if (fresh) recording?.pendingRecordWrites else null,
                "recordingSchemaVersion" to if (fresh) recording?.recordingSchemaVersion else null,
                "durableBytes" to if (fresh) recording?.durableBytes else null,
                "gymSha" to if (fresh) recording?.gymSha else null,
                "inFlightAdmissions" to inFlightAdmissions,
                "pendingActivities" to activityCounter(),
            )
        }
        metadata["activeGames"] = sessions.count { !it.isGameOver() } + (metadata["inFlightAdmissions"] as Int)
        if (metadata["activeGames"] != 0 || metadata["pendingActivities"] != 0) metadata["recordingDrainComplete"] = false
        return metadata
    }
}
