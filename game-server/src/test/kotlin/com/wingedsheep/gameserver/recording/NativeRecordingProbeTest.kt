package com.wingedsheep.gameserver.recording

import com.wingedsheep.gameserver.lifecycle.NativeGameAdmission
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import com.wingedsheep.gameserver.session.GameSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class NativeRecordingProbeTest : FunSpec({
    test("zero policy seats still requires connected producers and ready recovery epoch") {
        val root = Files.createTempDirectory("synthetic-native-proof-").toRealPath()
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
        val admission = NativeGameAdmission()
        val repo = InMemoryGameRepository()
        val session = mockk<GameSession>()
        every { session.sessionId } returns "synthetic-only-human"
        every { session.evidenceHealthy() } returns false
        repo.save(session)
        val priorFailures = PrivateGameEvidence.failures.get()
        System.setProperty("game.recording.root", root.toString())
        System.setProperty("game.recording.engine-revision", admission.engineSha)
        PrivateGameEvidence.failures.set(0)
        try {
            val probe = NativeRecordingProbe(repo, admission)
            fun proof() = Json.parseToJsonElement(Files.readString(root.resolve(".native-health.json"))).jsonObject
            probe.publish()
            proof().getValue("recoveryComplete").jsonPrimitive.boolean shouldBe false
            proof().getValue("producerCoverageComplete").jsonPrimitive.boolean shouldBe false
            every { session.evidenceHealthy() } returns true
            probe.ready()
            proof().getValue("producerCoverageComplete").jsonPrimitive.boolean shouldBe true
            proof().getValue("recoveryComplete").jsonPrimitive.boolean shouldBe true
            proof().getValue("bootId").jsonPrimitive.content shouldBe admission.bootId
            proof().containsKey("gameId") shouldBe false
            Files.getPosixFilePermissions(root.resolve(".native-health.json")) shouldBe PosixFilePermissions.fromString("rw-------")
            PrivateGameEvidence.failures.incrementAndGet()
            probe.publish()
            proof().getValue("recordingHealthy").jsonPrimitive.boolean shouldBe false
        } finally {
            PrivateGameEvidence.failures.set(priorFailures)
            System.clearProperty("game.recording.root")
            System.clearProperty("game.recording.engine-revision")
        }
    }
})
