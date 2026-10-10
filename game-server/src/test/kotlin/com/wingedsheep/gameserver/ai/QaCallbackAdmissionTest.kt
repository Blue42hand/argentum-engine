package com.wingedsheep.gameserver.ai

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.cancelAndJoin
import com.wingedsheep.gameserver.lifecycle.NativeControlSocket
import com.wingedsheep.gameserver.lifecycle.NativeGameAdmission
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import com.wingedsheep.gameserver.repository.InMemoryLobbyRepository
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import kotlinx.serialization.json.*

class QaCallbackAdmissionTest : FunSpec({
    test("disabled gate preserves production pass through and rejects controls") {
        val gate = QaCallbackAdmission()
        gate.admitted { 42 } shouldBe 42
        shouldThrow<IllegalStateException> { gate.pause() }
        gate.snapshot()["qaCallbackActiveHandlers"] shouldBe 0
    }

    test("pause drains admitted handlers before allowing exact generation resume") {
        coroutineScope {
            val gate = QaCallbackAdmission(true)
            val inside = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val current = async { gate.admitted { inside.complete(Unit); finish.await() } }
            inside.await()
            gate.pause()
            gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe false
            val queuedInside = CompletableDeferred<Unit>()
            val queued = async { gate.admitted { queuedInside.complete(Unit) } }
            yield()
            gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 1
            queuedInside.isCompleted shouldBe false
            shouldThrow<IllegalStateException> { gate.resume(1) }
            finish.complete(Unit); current.await()
            gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe true
            shouldThrow<IllegalStateException> { gate.resume(0) }
            gate.resume(1); queued.await()
            queuedInside.isCompleted shouldBe true
            gate.snapshot()["qaCallbackActiveHandlers"] shouldBe 0
        }
    }

    test("cancelled queued work never enters and releases bounded admission") {
        coroutineScope {
            val gate = QaCallbackAdmission(true);gate.pause()
            var invoked = false
            val job = async { gate.admitted { invoked = true } }
            yield();job.cancelAndJoin()
            invoked shouldBe false
            gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 0
            gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe true
        }
    }

    test("private QA controls reject wrong boot identity and production gate") {
        val admission = NativeGameAdmission(true, "a".repeat(40), "b".repeat(64), "e".repeat(40))
        val gate = QaCallbackAdmission(true)
        val control = NativeControlSocket(admission, InMemoryGameRepository(admission), "/unused", "operator",
            InMemoryLobbyRepository(), QuickGameLobbyRepository(), gate)
        fun request(op: String, boot: String = admission.bootId, generation: Long? = null) = buildJsonObject {
            put("protocol", 1);put("op", op);put("bootId", boot)
            generation?.let { put("generation", it) }
        }
        shouldThrow<IllegalArgumentException> { control.dispatch("other",request("qa-callback-pause")) }
        shouldThrow<IllegalArgumentException> { control.dispatch("operator",request("qa-callback-pause","wrong")) }
        control.dispatch("operator",request("qa-callback-pause"))["qaCallbackAdmissionDrained"]!!.jsonPrimitive.boolean shouldBe true
        shouldThrow<IllegalStateException> { control.dispatch("operator",request("qa-callback-resume",generation=0)) }
        control.dispatch("operator",request("qa-callback-resume",generation=1))["qaCallbackPaused"]!!.jsonPrimitive.boolean shouldBe false
        val production = NativeControlSocket(admission, InMemoryGameRepository(admission), "/unused", "operator",
            InMemoryLobbyRepository(), QuickGameLobbyRepository())
        shouldThrow<IllegalArgumentException> { production.dispatch("operator",request("qa-callback-pause")) }
        production.dispatch("operator",buildJsonObject { put("protocol",1);put("op","status") }).keys.any { it.startsWith("qaCallback") } shouldBe false
    }
})
