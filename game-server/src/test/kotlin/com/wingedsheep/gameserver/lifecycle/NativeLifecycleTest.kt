package com.wingedsheep.gameserver.lifecycle

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import com.wingedsheep.gameserver.repository.InMemoryLobbyRepository
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import com.wingedsheep.gameserver.session.GameSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class NativeLifecycleTest : FunSpec({
    fun health() = NativeRecordingHealth(true, true, true, 0, 1, 10, gymSha = "e".repeat(40))
    fun gate() = NativeGameAdmission(true, "a".repeat(40), "b".repeat(64), "e".repeat(40))
    fun ready(gate: NativeGameAdmission) {
        gate.markApplicationReady()
        gate.reportRecording(gate.bootId, gate.releaseId, health())
        gate.resume(gate.bootId, gate.releaseId)
    }

    test("opt-in starts closed and missing recorder evidence prevents opening") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        shouldThrow<NativeAdmissionClosed> { repo.save(GameSession(cardRegistry = CardRegistry())) }
        shouldThrow<IllegalStateException> { admission.resume(admission.bootId, admission.releaseId) }
        admission.status(repo)["acceptingNewGames"] shouldBe false
        admission.status(repo)["recordingHealthy"] shouldBe false
        admission.status(repo)["pendingRecordWrites"] shouldBe null
    }

    test("drain counts idle pregame sessions and allows existing session saves") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        val session = GameSession(cardRegistry = CardRegistry())
        repo.save(session)
        admission.drain(admission.bootId)
        admission.status(repo)["activeGames"] shouldBe 1
        admission.status(repo)["drainAcknowledged"] shouldBe true
        repo.save(session)
        shouldThrow<NativeAdmissionClosed> { repo.save(GameSession(cardRegistry = CardRegistry())) }
    }

    test("expired recorder health rejects new games and hides stale write count") {
        val admission = gate()
        var now = 100L
        admission.nanoTime = { now }
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        now += 10_000_000_001L
        shouldThrow<NativeAdmissionClosed> { repo.save(GameSession(cardRegistry = CardRegistry())) }
        admission.status(repo)["recordingHealthy"] shouldBe false
        admission.status(repo)["pendingRecordWrites"] shouldBe null
        shouldThrow<IllegalStateException> { admission.resume(admission.bootId, admission.releaseId) }
    }

    test("wrong epochs and incomplete native producer coverage cannot open admission") {
        val admission = gate()
        admission.markApplicationReady()
        shouldThrow<IllegalArgumentException> { admission.reportRecording("other", admission.releaseId, health()) }
        admission.reportRecording(admission.bootId, admission.releaseId, health().copy(producerCoverageComplete = false))
        shouldThrow<IllegalStateException> { admission.resume(admission.bootId, admission.releaseId) }
        shouldThrow<IllegalArgumentException> { admission.drain("other") }
    }

    test("drain acknowledgement waits for admitted registration to finish") {
        val admission = gate()
        ready(admission)
        val inside = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val writer = thread {
            admission.register({ true }) {
                inside.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
            }
        }
        check(inside.await(5, TimeUnit.SECONDS))
        val drainer = thread { admission.drain(admission.bootId); drained.countDown() }
        try {
            drained.await(100, TimeUnit.MILLISECONDS) shouldBe false
        } finally {
            finish.countDown()
            writer.join(5000)
            drainer.join(5000)
        }
        drained.count shouldBe 0
    }

    test("control schema rejects arbitrary fields and non-operator drain") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        val control = NativeControlSocket(admission, repo, "/unused", "native-test-operator",
            InMemoryLobbyRepository(), QuickGameLobbyRepository())
        shouldThrow<IllegalArgumentException> {
            control.dispatch("unauthorized", buildJsonObject { put("protocol", 1); put("op", "status") })
        }
        shouldThrow<IllegalArgumentException> {
            control.dispatch(System.getProperty("user.name"), buildJsonObject {
                put("protocol", 1); put("op", "drain"); put("bootId", admission.bootId)
            })
        }
        shouldThrow<IllegalArgumentException> {
            control.dispatch("native-test-operator", buildJsonObject {
                put("protocol", 1); put("op", "status"); put("unexpected", "payload")
            })
        }
    }

    test("real Unix socket returns only metadata and is private") {
        val directory = Files.createTempDirectory(java.nio.file.Path.of("/tmp").toRealPath(), "an-").toRealPath()
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val path = directory.resolve("control.sock")
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        val control = NativeControlSocket(admission, repo, path.toString(), System.getProperty("user.name"),
            InMemoryLobbyRepository(), QuickGameLobbyRepository())
        try {
            control.start()
            Files.getPosixFilePermissions(path) shouldBe PosixFilePermissions.fromString("rw-------")
            SocketChannel.open(StandardProtocolFamily.UNIX).use { socket ->
                socket.connect(UnixDomainSocketAddress.of(path))
                socket.write(ByteBuffer.wrap("{\"protocol\":1,\"op\":\"status\"}\n".toByteArray()))
                val buffer = ByteBuffer.allocate(4096)
                check(socket.read(buffer) > 0)
                val response = Json.parseToJsonElement(String(buffer.array(), 0, buffer.position())).jsonObject
                response["ok"]!!.jsonPrimitive.boolean shouldBe true
                response["bootId"]!!.jsonPrimitive.content shouldBe admission.bootId
                response["activeGames"]!!.jsonPrimitive.int shouldBe 0
                response.keys.any { it.contains("token", true) || it.contains("player", true) } shouldBe false
            }
        } finally {
            control.close()
            Files.deleteIfExists(path)
            Files.delete(directory)
        }
    }

    test("ordinary server remains opt-out") {
        val admission = NativeGameAdmission()
        val repo = InMemoryGameRepository(admission)
        repo.save(GameSession(cardRegistry = CardRegistry()))
        admission.status(repo)["activeGames"] shouldBe 1
    }

    test("Spring selects configured constructor and enables the private admission gate") {
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("native-test",
                mapOf("native.lifecycle.enabled" to "true", "app.version" to "a".repeat(40),
                    "native.lifecycle.release-id" to "b".repeat(64), "native.lifecycle.gym-sha" to "e".repeat(40))))
            context.register(NativeGameAdmission::class.java)
            context.refresh()
            val admission = context.getBean(NativeGameAdmission::class.java)
            admission.enabled shouldBe true
            admission.expectedGymSha shouldBe "e".repeat(40)
            admission.status(InMemoryGameRepository(admission))["acceptingNewGames"] shouldBe false
        }
    }

    test("an admitted start is counted through drain and registers before its reservation ends") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        val inside = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val writer = thread {
            admission.withNewGameAdmission {
                inside.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                repo.save(GameSession(cardRegistry = CardRegistry()))
            }
        }
        try {
            check(inside.await(5, TimeUnit.SECONDS))
            admission.drain(admission.bootId)
            admission.status(repo)["activeGames"] shouldBe 1
            shouldThrow<NativeAdmissionClosed> { admission.withNewGameAdmission { error("must not run") } }
        } finally {
            finish.countDown()
            writer.join(5000)
        }
        admission.status(repo)["activeGames"] shouldBe 1
        admission.status(repo)["inFlightAdmissions"] shouldBe 0
    }

    test("a new healthy heartbeat cannot silently reopen admission after expiry") {
        val admission = gate()
        var now = 100L
        admission.nanoTime = { now }
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        now += 10_000_000_001L
        admission.reportRecording(admission.bootId, admission.releaseId, health())
        shouldThrow<NativeAdmissionClosed> { repo.save(GameSession(cardRegistry = CardRegistry())) }
        admission.resume(admission.bootId, admission.releaseId)
        repo.save(GameSession(cardRegistry = CardRegistry()))
    }

    test("recording drain acknowledgement is bound to this drain and all activity counts") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        admission.drain(admission.bootId)
        admission.status(repo)["recordingDrainComplete"] shouldBe false
        val drainId = admission.status(repo).getValue("drainId") as String
        admission.reportRecording(admission.bootId, admission.releaseId, health().copy(drainId=drainId, drainComplete=true))
        admission.status(repo)["recordingDrainComplete"] shouldBe true
        admission.status(repo) { 1 }["recordingDrainComplete"] shouldBe false
        admission.resume(admission.bootId, admission.releaseId)
        admission.drain(admission.bootId)
        admission.status(repo)["recordingDrainComplete"] shouldBe false
    }

    test("drain lets an existing lobby produce its match while new client creation stays closed") {
        val admission = gate()
        val repo = InMemoryGameRepository(admission)
        ready(admission)
        var activities = 1
        admission.bindPendingActivities { activities }
        admission.drain(admission.bootId)
        shouldThrow<NativeAdmissionClosed> { admission.withNewGameAdmission { error("must not run") } }
        repo.save(GameSession(cardRegistry = CardRegistry()))
        admission.status(repo)["activeGames"] shouldBe 1
        admission.status(repo)["pendingActivities"] shouldBe 1
        activities = 0
        shouldThrow<NativeAdmissionClosed> { repo.save(GameSession(cardRegistry = CardRegistry())) }
    }
})
