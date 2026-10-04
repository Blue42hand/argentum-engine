package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ManaSourcesSelectedResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.gameserver.handler.GamePlayHandler
import com.wingedsheep.gameserver.handler.MessageSender
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ExternalAiPaymentRetryTest : FunSpec({
    val seat = EntityId.of("payment-pilot")
    val arena = EntityId.of("arena")
    val epoch = "payment-epoch"
    val decision = SelectManaSourcesDecision(
        id = "r0", playerId = seat, prompt = "Produce mana for Krenko",
        context = DecisionContext(), availableSources = emptyList(),
        requiredCost = "{2}{R}{R}", autoPaySuggestion = emptyList(), canDecline = true,
    )
    val invalid = SubmitDecision(seat,
        ManaSourcesSelectedResponse(decision.id, selectedSources = listOf(arena)))
    val reason = "Selected mana sources cannot pay this spell's cost"

    fun handler(sender: MessageSender) = GamePlayHandler(
        sessionRegistry = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        lobbyRepository = mockk(relaxed = true),
        sender = sender,
        cardRegistry = mockk(relaxed = true),
        printingRegistry = mockk(relaxed = true),
        tokenArtRegistry = mockk(relaxed = true),
        deckGenerator = mockk(relaxed = true),
        gameProperties = mockk(relaxed = true),
        replayService = mockk(relaxed = true),
        replayCheckpointFlusher = mockk(relaxed = true),
        engineVersion = mockk(relaxed = true),
        aiGameManager = mockk(relaxed = true),
        matchResultSink = mockk(relaxed = true),
        rankedResultSink = mockk(relaxed = true),
        deckProfiler = mockk(relaxed = true),
    )

    test("rejected payment asks the same pilot for a correction and retains its decision origin") {
        val controller = mockk<AiPlayerController>()
        val corrected = PassPriority(seat)
        every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), reason) } returns
            ActionResponse.SubmitAction(corrected)
        val socket = AiWebSocketSession(
            aiPlayerId = seat, controller = controller, thinkingDelayMs = 0,
            onActionReady = { _, _, _ -> error("Correction used ordinary action callback") },
            onMulliganKeep = {}, onMulliganTake = {}, onBottomCards = { _, _ -> },
            allowActionsOnlyFallback = false,
        )
        val session = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "payment-retry"
            every { getPlayerSession(seat) } returns PlayerSession(socket, seat, "Pilot")
            every { executeAiAction(seat, invalid, epoch) } returns GameSession.ActionResult.Failure(reason)
            every { aiPaymentRetrySnapshot(seat, epoch, decision.id) } returns
                GameSession.AiPaymentRetrySnapshot(mockk<ClientGameState>(), emptyList(), decision, epoch)
            every { executeAiPaymentCorrection(seat, corrected, epoch, decision.id) } returns null
        }
        val sender = mockk<MessageSender>(relaxed = true)
        try {
            handler(sender).handleAiAction(session, seat, invalid, epoch)
            verify(timeout = 3_000, exactly = 1) {
                session.executeAiPaymentCorrection(seat, corrected, epoch, decision.id)
            }
            verify(exactly = 0) { session.getLegalActions(any()) }
            verify(exactly = 0) { session.noteAiActionRejected(any(), any()) }
            verify(exactly = 0) { sender.send(any(), any()) }
        } finally { socket.close() }
    }

    test("payment correction stops after two rejections without server fallback") {
        val controller = mockk<AiPlayerController>()
        every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), reason) } returns null
        val socket = AiWebSocketSession(
            aiPlayerId = seat, controller = controller, thinkingDelayMs = 0,
            onActionReady = { _, _, _ -> error("Unexpected action") },
            onMulliganKeep = {}, onMulliganTake = {}, onBottomCards = { _, _ -> },
            allowActionsOnlyFallback = false,
        )
        val session = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "payment-limit"
            every { getPlayerSession(seat) } returns PlayerSession(socket, seat, "Pilot")
            every { executeAiAction(seat, invalid, epoch) } returns GameSession.ActionResult.Failure(reason)
            every { aiPaymentRetrySnapshot(seat, epoch, decision.id) } returns
                GameSession.AiPaymentRetrySnapshot(mockk<ClientGameState>(), emptyList(), decision, epoch)
        }
        val sender = mockk<MessageSender>(relaxed = true)
        try {
            val play = handler(sender)
            repeat(3) { play.handleAiAction(session, seat, invalid, epoch) }
            verify(timeout = 3_000, exactly = 2) {
                controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), reason)
            }
            verify(exactly = 0) { session.getLegalActions(any()) }
            verify(exactly = 0) { session.noteAiActionRejected(any(), any()) }
            verify(exactly = 0) { sender.send(any(), any()) }
        } finally { socket.close() }
    }

    test("provider exception never starts a strategic fallback or submits a correction") {
        val controller = mockk<AiPlayerController>()
        every { controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), reason) } throws
            IllegalStateException("provider unavailable")
        val socket = AiWebSocketSession(
            aiPlayerId = seat, controller = controller, thinkingDelayMs = 0,
            onActionReady = { _, _, _ -> error("Unexpected action") },
            onMulliganKeep = {}, onMulliganTake = {}, onBottomCards = { _, _ -> },
            allowActionsOnlyFallback = false,
        )
        val session = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "payment-provider-failure"
            every { getPlayerSession(seat) } returns PlayerSession(socket, seat, "Pilot")
            every { executeAiAction(seat, invalid, epoch) } returns GameSession.ActionResult.Failure(reason)
            every { aiPaymentRetrySnapshot(seat, epoch, decision.id) } returns
                GameSession.AiPaymentRetrySnapshot(mockk<ClientGameState>(), emptyList(), decision, epoch)
        }
        val sender = mockk<MessageSender>(relaxed = true)
        try {
            handler(sender).handleAiAction(session, seat, invalid, epoch)
            verify(timeout = 3_000, exactly = 1) {
                controller.chooseActionAfterRejectedPayment(any(), any(), any(), any(), reason)
            }
            verify(exactly = 0) { session.executeAiPaymentCorrection(any(), any(), any(), any()) }
            verify(exactly = 0) { session.getLegalActions(any()) }
            verify(exactly = 0) { sender.send(any(), any()) }
        } finally { socket.close() }
    }
})
