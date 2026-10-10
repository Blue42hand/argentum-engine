package com.wingedsheep.gameserver.persistence

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.engine.registry.TokenArtRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.ai.QaCallbackAdmission
import com.wingedsheep.gameserver.config.RedisProperties
import com.wingedsheep.gameserver.lifecycle.NativeGameAdmission
import com.wingedsheep.gameserver.lifecycle.NativeRecordingHealth
import com.wingedsheep.gameserver.repository.RedisGameRepository
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.SessionRegistry
import com.wingedsheep.gameserver.replay.ReplayService
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.serialization.json.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class QaRedisPersistenceTest : FunSpec({
    val properties = RedisProperties(true, "127.0.0.1", 26379, 30, "qa:${"a".repeat(32)}:")
    fun policy() = QaRedisPersistence(true, properties, true, true, true)
    fun admission() = NativeGameAdmission(true, "b".repeat(40), "c".repeat(64), "d".repeat(40))
    fun ready(admission: NativeGameAdmission) {
        admission.markApplicationReady()
        admission.reportRecording(admission.bootId, admission.releaseId, NativeRecordingHealth(true,true,true,0,1,1,gymSha="d".repeat(40)))
        admission.resume(admission.bootId,admission.releaseId)
    }
    fun session(): GameSession = GameSession(sessionId="synthetic-qa",cardRegistry=CardRegistry()).also {
        it.manualHumanStart=true
        it.setPlayerPersistenceInfo(EntityId("human"),"Human","fake-human")
        it.setPlayerPersistenceInfo(EntityId("pilot"),"Pilot","fake-pilot",isAi=true,aiControllerSpec=AiControllerSpec("commander-gym","exact-profile"))
        it.restoreFromPersistence(GameState(), emptyMap(),emptyMap(),emptyMap())
    }
    test("QA Redis is explicit and incompatible endpoint or open startup is rejected") {
        QaRedisPersistence().healthy() shouldBe false
        for (props in listOf(properties.copy(host="0.0.0.0"),properties.copy(port=6379),properties.copy(keyPrefix="argentum:"),properties.copy(ttlMinutes=1440))) {
            shouldThrow<IllegalArgumentException> { QaRedisPersistence(true,props,true,true,true) }
        }
        shouldThrow<IllegalArgumentException> { QaRedisPersistence(true,properties,true,true,false) }
    }
    test("initial pause blocks restored callback until exact fresh generation resumes") {
        coroutineScope {
            val gate=QaCallbackAdmission(true,true);var issued=false
            val callback=async { gate.admitted { issued=true } };yield()
            issued shouldBe false
            gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 1
            gate.resume(1);callback.await();issued shouldBe true
        }
        shouldThrow<IllegalArgumentException> { QaCallbackAdmission(false,true) }
    }
    test("player tokens controller preset manual flag and state survive exact synthetic snapshot") {
        val original=session();val persistent=original.toPersistent(null)
        val (restored,identities)=restoreGameSession(persistent,CardRegistry())
        restored.toPersistent(null) shouldBe persistent
        restored.manualHumanStart shouldBe true
        identities.single { it.isAi }.aiControllerSpec shouldBe AiControllerSpec("commander-gym","exact-profile")
        identities.map { it.token }.toSet() shouldBe setOf("fake-human","fake-pilot")
    }
    test("actual stored bytes supply snapshot proof and tampering rejects recovery") {
        val data=mutableMapOf<String,String>()
        val template=mockk<RedisTemplate<String,String>>()
        val values=mockk<ValueOperations<String,String>>()
        every { template.opsForValue() } returns values
        every { values.set(any(),any(),any<Duration>()) } answers { data[firstArg()]=secondArg();Unit }
        every { values.get(any()) } answers { data[firstArg()] }
        every { template.keys(any()) } answers { data.keys.toSet() }
        val factory=mockk<org.springframework.data.redis.connection.RedisConnectionFactory>()
        val connection=mockk<org.springframework.data.redis.connection.RedisConnection>(relaxed=true)
        val strings=mockk<org.springframework.data.redis.connection.RedisStringCommands>()
        every { template.connectionFactory } returns factory
        every { factory.connection } returns connection
        every { connection.stringCommands() } returns strings
        every { strings.get(any()) } answers { data[firstArg<ByteArray>().toString(Charsets.UTF_8)]?.toByteArray(Charsets.UTF_8) }
        val replay=mockk<ReplayService>();every { replay.findStored(any()) } returns null
        val qa=policy();qa.recovered();val gate=admission()
        val repository=RedisGameRepository(template,CardRegistry(),mockk<PrintingRegistry>(),mockk<TokenArtRegistry>(),SessionRegistry(),properties,replay,qa,gate)
        val original=session()
        shouldThrow<com.wingedsheep.gameserver.lifecycle.NativeAdmissionClosed> { repository.save(original) }
        ready(gate);repository.save(original)
        val proof=repository.qaSnapshot(original.sessionId)
        proof["snapshotBytes"]!!.jsonPrimitive.int shouldBe data.values.single().toByteArray().size
        proof["snapshotSha256"]!!.jsonPrimitive.content shouldBe com.wingedsheep.gameserver.recording.PrivateGameEvidence.sha256(data.values.single().toByteArray())
        val restoredRepository=RedisGameRepository(template,CardRegistry(),mockk<PrintingRegistry>(),mockk<TokenArtRegistry>(),SessionRegistry(),properties,replay,qa,admission())
        restoredRepository.loadAllFromRedis().single().first.toPersistent(null) shouldBe original.toPersistent(null)
        restoredRepository.qaSnapshot(original.sessionId) shouldBe proof
        data[data.keys.single()]=data.values.single()+" "
        shouldThrow<IllegalArgumentException> { repository.qaSnapshot(original.sessionId) }
        every { strings.get(any()) } returns byteArrayOf(0xc3.toByte(),0x28)
        shouldThrow<java.nio.charset.CharacterCodingException> { repository.qaSnapshot(original.sessionId) }
        val badPolicy=policy()
        val badRepository=RedisGameRepository(template,CardRegistry(),mockk<PrintingRegistry>(),mockk<TokenArtRegistry>(),SessionRegistry(),properties,replay,badPolicy,admission())
        badRepository.loadAllFromRedis().isEmpty() shouldBe true
        shouldThrow<IllegalStateException> { badPolicy.recovered() }
    }
    test("snapshot RPC is updater-only epoch-bound strict and refuses unpaused gates") {
        val repository=mockk<RedisGameRepository>()
        every { repository.qaSnapshot("synthetic-qa") } returns buildJsonObject { put("synthetic",true) }
        val admission=admission();val callbacks=QaCallbackAdmission(true,true)
        val control=com.wingedsheep.gameserver.lifecycle.NativeControlSocket(admission,repository,"/unused","operator",
            com.wingedsheep.gameserver.repository.InMemoryLobbyRepository(),com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository(),callbacks)
        val request=buildJsonObject {
            put("protocol",1);put("op","qa-redis-snapshot");put("bootId",admission.bootId);put("releaseId",admission.releaseId);put("gameId","synthetic-qa")
        }
        shouldThrow<IllegalArgumentException> { control.dispatch("recorder",request) }
        shouldThrow<IllegalArgumentException> { control.dispatch("operator",JsonObject(request + ("bootId" to JsonPrimitive("wrong")))) }
        shouldThrow<IllegalArgumentException> { control.dispatch("operator",JsonObject(request + ("extra" to JsonPrimitive(true)))) }
        control.dispatch("operator",request)["ok"]!!.jsonPrimitive.boolean shouldBe true
        callbacks.resume(1)
        shouldThrow<IllegalStateException> { control.dispatch("operator",request) }
        verify(exactly=1) { repository.qaSnapshot("synthetic-qa") }
    }
    test("shared recovery key and byte bounds cannot be exceeded") {
        val qa=policy()
        qa.recoveryKeys(63)
        shouldThrow<IllegalArgumentException> { qa.recoveryKeys(2) }
        val bounded=policy()
        bounded.recoveryValue("x".repeat(8*1024*1024))
        bounded.recoveryValue("x".repeat(8*1024*1024))
        shouldThrow<IllegalArgumentException> { bounded.recoveryValue("x") }
    }
    test("missing observed lobby record or oversized tournament fails recovery") {
        for (oversized in listOf(false,true)) {
            val template=mockk<RedisTemplate<String,String>>();val values=mockk<ValueOperations<String,String>>()
            every { template.opsForValue() } returns values
            every { template.keys(any()) } returns setOf("${properties.keyPrefix}lobby:missing")
            every { values.get(any()) } returns if (oversized) "x".repeat(8*1024*1024+1) else null
            val qa=policy()
            val repository=com.wingedsheep.gameserver.repository.RedisLobbyRepository(template,CardRegistry(),properties,mockk(),qa)
            repository.loadAllLobbiesFromRedis().isEmpty() shouldBe true
            shouldThrow<IllegalStateException> { qa.recovered() }
        }
    }
    test("caught Redis write failures cannot become healthy after a recovery flag") {
        val template=mockk<RedisTemplate<String,String>>();val values=mockk<ValueOperations<String,String>>()
        every { template.opsForValue() } returns values
        every { values.set(any(),any(),any<Duration>()) } throws IllegalStateException("synthetic failure")
        val qa=policy();val gate=admission();ready(gate)
        val repository=RedisGameRepository(template,CardRegistry(),mockk(),mockk(),SessionRegistry(),properties,mockk(),qa,gate)
        repository.save(session())
        qa.healthy() shouldBe false
        shouldThrow<IllegalStateException> { qa.recovered() }
    }
})
