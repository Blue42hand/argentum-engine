package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.view.*
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.web.socket.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class QaCallbackSessionTest : FunSpec({
    val seat = EntityId.of("qa-seat")
    val pass = PassPriority(seat)
    val actions = listOf(LegalActionInfo("PassPriority", "Pass", pass))
    val state = ClientGameState(viewingPlayerId=seat, cards=emptyMap(), zones=emptyList(), players=emptyList(),
        currentPhase=Phase.PRECOMBAT_MAIN, currentStep=Step.PRECOMBAT_MAIN, activePlayerId=seat,
        priorityPlayerId=seat, turnNumber=1, isGameOver=false, winnerId=null, combat=null)
    val json = Json { classDiscriminator="type";encodeDefaults=true;serializersModule=engineSerializersModule }
    fun socket(gate: QaCallbackAdmission, controller: AiPlayerController) = AiWebSocketSession(
        aiPlayerId=seat,controller=controller,thinkingDelayMs=0,onActionReady={_,_,_->},
        onMulliganKeep={},onMulliganTake={},onBottomCards={_,_->},allowActionsOnlyFallback=false,
        qaCallbackAdmission=gate)
    fun evidence() = mockk<AiDecisionEvidence>().also { every { it.correlationId } returns "qa-callback" }

    test("actual async socket pauses before evidence and drains through application and disposition hooks") {
        val gate=QaCallbackAdmission(true);gate.pause()
        val controller=mockk<RecordedAiCallbackController>()
        every { controller.chooseRecordedCallback(any(),any(),any(),any(),any(),any()) } returns
            RecordedAiChoice(ActionResponse.SubmitAction(pass),0)
        val socket=socket(gate,controller)
        val issued=AtomicInteger();val application=CountDownLatch(1);val finishApplication=CountDownLatch(1)
        val disposition=CountDownLatch(1);val finishDisposition=CountDownLatch(1)
        socket.onCallbackEvidence={_,_,_,_,_,_->issued.incrementAndGet();evidence()}
        socket.onRecordedCallbackReady={_,action,_,_,_,_,_->
            action shouldBe pass;application.countDown();check(finishApplication.await(5,TimeUnit.SECONDS))
            PaymentCorrectionOutcome.ACCEPTED }
        socket.onCallbackDisposition={_,_,_->disposition.countDown();check(finishDisposition.await(5,TimeUnit.SECONDS))}
        try {
            val message: ServerMessage=ServerMessage.StateUpdate(state,emptyList(),actions,interactionEpoch="current")
            socket.sendMessage(TextMessage(json.encodeToString(message)))
            eventually(5.seconds) { gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 1 }
            issued.get() shouldBe 0
            gate.resume(1)
            check(application.await(5,TimeUnit.SECONDS));gate.pause()
            gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe false
            finishApplication.countDown();check(disposition.await(5,TimeUnit.SECONDS))
            gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe false
            finishDisposition.countDown()
            eventually(5.seconds) { gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe true }
            issued.get() shouldBe 1
        } finally { finishApplication.countDown();finishDisposition.countDown();socket.close() }
    }

    test("payment correction is paused before context issuance and cancellation never invokes provider") {
        val gate=QaCallbackAdmission(true);gate.pause()
        val controller=mockk<RecordedAiCallbackController>(relaxed=true)
        val socket=socket(gate,controller);val issued=AtomicInteger()
        socket.onCallbackEvidence={_,_,_,_,_,_->issued.incrementAndGet();evidence()}
        val decision=SelectManaSourcesDecision("qa-payment",seat,"Pay",DecisionContext(),emptyList(),"{1}",emptyList(),true)
        val snapshot=GameSession.AiPaymentRetrySnapshot(state,actions,decision,"current",1)
        try {
            socket.retryRejectedPayment(snapshot,"Rejected payment",{true},"qa-game") shouldBe PaymentRetryAdmission.SCHEDULED
            eventually(5.seconds) { gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 1 }
            issued.get() shouldBe 0
            socket.close()
            eventually(5.seconds) { gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 0 }
            issued.get() shouldBe 0
            verify(exactly=0) { controller.chooseRecordedCallback(any(),any(),any(),any(),any(),any()) }
        } finally { socket.close() }
    }

    test("bounded pre-admission payment failures release retry latch instead of wedging session") {
        coroutineScope {
            val gate=QaCallbackAdmission(true);gate.pause()
            val waiting=(1..64).map { async { gate.admitted { error("Must not admit") } } }
            eventually(5.seconds) { gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 64 }
            val controller=mockk<AiPlayerController>(relaxed=true);val socket=socket(gate,controller)
            val decision=SelectManaSourcesDecision("qa-payment",seat,"Pay",DecisionContext(),emptyList(),"{1}",emptyList(),true)
            val snapshot=GameSession.AiPaymentRetrySnapshot(state,actions,decision,"current",1)
            try {
                socket.retryRejectedPayment(snapshot,"Rejected payment",{true},"qa-game") shouldBe PaymentRetryAdmission.SCHEDULED
                eventually(5.seconds) { gate.snapshot()["qaCallbackAdmissionFailures"] shouldBe 1 }
                delay(20)
                socket.retryRejectedPayment(snapshot,"Rejected payment",{true},"qa-game") shouldBe PaymentRetryAdmission.SCHEDULED
                eventually(5.seconds) { gate.snapshot()["qaCallbackAdmissionFailures"] shouldBe 2 }
                delay(20)
                socket.retryRejectedPayment(snapshot,"Rejected payment",{true},"qa-game") shouldBe PaymentRetryAdmission.EXHAUSTED
                verify(exactly=0) { controller.chooseActionAfterRejectedPayment(any(),any(),any(),any(),any()) }
            } finally { socket.close();waiting.forEach { it.cancelAndJoin() } }
        }
    }
})
