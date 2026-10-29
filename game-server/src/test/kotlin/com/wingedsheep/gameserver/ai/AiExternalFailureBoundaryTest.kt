package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.view.*
import com.wingedsheep.gameserver.protocol.GameOverReason
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.web.socket.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class AiExternalFailureBoundaryTest : FunSpec({
    val seat = EntityId.of("failure-boundary-seat")
    val pass = PassPriority(seat)
    val actions = listOf(LegalActionInfo("PassPriority", "Pass", pass))
    val state = ClientGameState(viewingPlayerId=seat, cards=emptyMap(), zones=emptyList(), players=emptyList(),
        currentPhase=Phase.PRECOMBAT_MAIN, currentStep=Step.PRECOMBAT_MAIN, activePlayerId=seat,
        priorityPlayerId=seat, turnNumber=1, isGameOver=false, winnerId=null, combat=null)
    val decision = SelectManaSourcesDecision("payment", seat, "Pay", DecisionContext(), emptyList(), "{1}", emptyList(), true)
    val snapshot = GameSession.AiPaymentRetrySnapshot(state, actions, decision, "current", 1)
    val json = Json { classDiscriminator="type"; encodeDefaults=true; serializersModule=engineSerializersModule }
    val update: ServerMessage = ServerMessage.StateUpdate(state, emptyList(), actions, interactionEpoch="current")
    fun send(session: AiWebSocketSession, message: ServerMessage = update) =
        session.sendMessage(TextMessage(json.encodeToString(message)))
    fun socket(controller: AiPlayerController, gate: QaCallbackAdmission = QaCallbackAdmission(),
               deliver: () -> Unit = {}) = AiWebSocketSession(
        aiPlayerId=seat, controller=controller, thinkingDelayMs=0, onActionReady={_,_,_->deliver()},
        onMulliganKeep={}, onMulliganTake={}, onBottomCards={_,_->}, allowActionsOnlyFallback=false,
        qaCallbackAdmission=gate)

    for (failure in listOf("provider exception", "declined correction", "fatal application")) {
        test("$failure latches correction and suppresses queued ordinary and correction callbacks") {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val controller = mockk<AiPlayerController>()
            every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) } answers {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                when (failure) {
                    "provider exception" -> throw IllegalStateException("provider failed")
                    "declined correction" -> null
                    else -> ActionResponse.SubmitAction(pass)
                }
            }
            every { controller.chooseAction(any(), any(), any(), any()) } returns ActionResponse.SubmitAction(pass)
            val deliveries = AtomicInteger()
            val failures = AtomicInteger()
            val session = socket(controller) { deliveries.incrementAndGet() }
            session.onControllerFailure = { failures.incrementAndGet() }
            session.onPaymentCorrectionReady = {_,_,_,_,_-> PaymentCorrectionOutcome.FATAL }
            try {
                session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
                check(entered.await(5, TimeUnit.SECONDS))
                repeat(4) { send(session) }
                session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
                delay(100)
                verify(exactly=0) { controller.chooseAction(any(), any(), any(), any()) }
                release.countDown()
                eventually(5.seconds) { failures.get() shouldBe 1 }
                repeat(4) { send(session) }
                session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.OBSOLETE
                delay(100)
                verify(exactly=1) { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) }
                verify(exactly=0) { controller.chooseAction(any(), any(), any(), any()) }
                failures.get() shouldBe 1
                deliveries.get() shouldBe 0
            } finally { release.countDown(); session.close() }
        }
    }

    test("ordinary provider failure suppresses a correction queued behind it") {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val controller = mockk<AiPlayerController>()
        every { controller.chooseAction(any(), any(), any(), any()) } answers {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            throw IllegalStateException("provider failed")
        }
        val session = socket(controller)
        val failures = AtomicInteger()
        session.onControllerFailure = { failures.incrementAndGet() }
        try {
            send(session)
            check(entered.await(5, TimeUnit.SECONDS))
            session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
            release.countDown()
            eventually(5.seconds) { failures.get() shouldBe 1 }
            delay(100)
            verify(exactly=0) { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) }
        } finally { release.countDown(); session.close() }
    }

    test("a rejecting ordinary callback schedules its correction without mutex re-entry") {
        val delivered = CountDownLatch(1)
        val controller = mockk<AiPlayerController>()
        every { controller.chooseAction(any(), any(), any(), any()) } returns ActionResponse.SubmitAction(pass)
        every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) } returns ActionResponse.SubmitAction(pass)
        lateinit var session: AiWebSocketSession
        session = socket(controller) {
            session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
        }
        session.onPaymentCorrectionReady = {_,_,_,_,_-> delivered.countDown(); PaymentCorrectionOutcome.ACCEPTED }
        try {
            send(session)
            check(delivered.await(5, TimeUnit.SECONDS))
            verify(exactly=1) { controller.chooseAction(any(), any(), any(), any()) }
            verify(exactly=1) { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) }
        } finally { session.close() }
    }

    test("fatal recorded ordinary application closes the same failure boundary exactly once") {
        val controller = mockk<RecordedAiCallbackController>()
        every { controller.chooseRecordedCallback(any(), any(), any(), any(), any(), any()) } returns
            RecordedAiChoice(ActionResponse.SubmitAction(pass), 0)
        val evidence = mockk<AiDecisionEvidence> { every { correlationId } returns "ordinary-fatal" }
        val session = socket(controller)
        val failures = AtomicInteger()
        val dispositions = AtomicInteger()
        session.onCallbackEvidence = {_,_,_,_,_,_-> evidence }
        session.onRecordedCallbackReady = {_,_,_,_,_,_,_-> PaymentCorrectionOutcome.FATAL }
        session.onControllerFailure = { failures.incrementAndGet() }
        session.onCallbackDisposition = {_,_,_-> dispositions.incrementAndGet() }
        try {
            send(session)
            eventually(5.seconds) { failures.get() shouldBe 1; dispositions.get() shouldBe 1 }
            send(session)
            session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.OBSOLETE
            delay(100)
            verify(exactly=1) { controller.chooseRecordedCallback(any(), any(), any(), any(), any(), any()) }
        } finally { session.close() }
        dispositions.get() shouldBe 1
    }

    test("shutdown finalizes blocked recorded correction evidence once and never applies its late answer") {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val controller = mockk<RecordedAiCallbackController>()
        every { controller.chooseRecordedCallback(any(), any(), any(), any(), any(), any()) } answers {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            RecordedAiChoice(ActionResponse.SubmitAction(pass), 0)
        }
        val evidence = mockk<AiDecisionEvidence> { every { correlationId } returns "correction-close" }
        val session = socket(controller)
        val dispositions = AtomicInteger()
        val deliveries = AtomicInteger()
        session.onCallbackEvidence = {_,_,_,_,_,_-> evidence }
        session.onCallbackDisposition = {_,_,_-> dispositions.incrementAndGet() }
        session.onRecordedCallbackReady = {_,_,_,_,_,_,_-> deliveries.incrementAndGet(); PaymentCorrectionOutcome.ACCEPTED }
        session.onPaymentCorrectionReady = {_,_,_,_,_-> error("Must use recorded callback") }
        try {
            session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
            check(entered.await(5, TimeUnit.SECONDS))
            send(session, ServerMessage.GameCancelled)
            eventually(5.seconds) { session.isOpen shouldBe false; dispositions.get() shouldBe 1 }
            release.countDown()
            delay(100)
            deliveries.get() shouldBe 0
            dispositions.get() shouldBe 1
        } finally { release.countDown(); session.close() }
        dispositions.get() shouldBe 1
    }

    for (termination in listOf("close", "game over", "game cancelled")) {
        for (blocked in listOf("provider", "QA admission")) {
            test("$termination bypasses $blocked and prevents delivery of blocked correction") {
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val controller = mockk<AiPlayerController>()
                every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any()) } answers {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    ActionResponse.SubmitAction(pass)
                }
                val gate = QaCallbackAdmission(true)
                if (blocked == "QA admission") gate.pause()
                val session = socket(controller, gate)
                val deliveries = AtomicInteger()
                val failures = AtomicInteger()
                session.onPaymentCorrectionReady = {_,_,_,_,_-> deliveries.incrementAndGet(); PaymentCorrectionOutcome.ACCEPTED }
                session.onControllerFailure = { failures.incrementAndGet() }
                try {
                    session.retryRejectedPayment(snapshot, "Rejected", {true}, "game") shouldBe PaymentRetryAdmission.SCHEDULED
                    if (blocked == "provider") {
                        check(entered.await(5, TimeUnit.SECONDS))
                        gate.pause()
                        gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe false
                    }
                    else eventually(5.seconds) { gate.snapshot()["qaCallbackWaitingHandlers"] shouldBe 1 }
                    send(session) // Also leave ordinary work waiting behind the correction.
                    when (termination) {
                        "close" -> session.close()
                        "game over" -> send(session, ServerMessage.GameOver(null, GameOverReason.DRAW))
                        else -> send(session, ServerMessage.GameCancelled)
                    }
                    eventually(5.seconds) { session.isOpen shouldBe false }
                    release.countDown()
                    eventually(5.seconds) { gate.snapshot()["qaCallbackAdmissionDrained"] shouldBe true }
                    deliveries.get() shouldBe 0
                    failures.get() shouldBe 0
                    verify(exactly=0) { controller.chooseAction(any(), any(), any(), any()) }
                    verify(exactly=if (blocked == "provider") 1 else 0) {
                        controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), any())
                    }
                } finally { release.countDown(); session.close() }
            }
        }
    }
})
