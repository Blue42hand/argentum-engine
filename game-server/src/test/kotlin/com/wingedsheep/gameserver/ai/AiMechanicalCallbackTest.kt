package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.engine.view.StateDelta
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureNanoTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AiMechanicalCallbackTest : FunSpec({
    val player = EntityId.of("external-ai")
    val opponent = EntityId.of("opponent")
    val blocker = EntityId.of("blocker")
    val attacker = EntityId.of("attacker")
    val action = DeclareBlockers(player, emptyMap())
    val emptyOffer = LegalActionInfo("DeclareBlockers", "Declare blockers", action,
        validBlockers = emptyList(), validBlockTargets = emptyMap())
    val state = ClientGameState(
        viewingPlayerId = player, cards = emptyMap(), zones = emptyList(), players = emptyList(),
        currentPhase = Phase.COMBAT, currentStep = Step.DECLARE_BLOCKERS,
        activePlayerId = opponent, priorityPlayerId = player, turnNumber = 1,
        isGameOver = false, winnerId = null, combat = null,
    )
    fun update(offers: List<LegalActionInfo> = listOf(emptyOffer), epoch: String? = "origin",
               view: ClientGameState = state, decision: PendingDecision? = null) =
        ServerMessage.StateUpdate(view, emptyList(), offers, pendingDecision = decision,
            interactionEpoch = epoch)

    fun socket(controller: AiPlayerController, submitted: MutableList<Pair<GameAction, String?>>,
               external: Boolean = true, gate: AiActionGate? = null) = AiWebSocketSession(
        aiPlayerId = player, controller = controller, thinkingDelayMs = 500,
        onActionReady = { _, chosen, epoch -> submitted.add(chosen to epoch) },
        onMulliganKeep = {}, onMulliganTake = {}, onBottomCards = { _, _ -> },
        allowActionsOnlyFallback = !external, actionGate = gate,
    )

    test("native empty blockers need zero callbacks and zero artificial milliseconds through full and delta updates") {
        runTest {
            val controller = mockk<AiPlayerController>()
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val socket = socket(controller, submitted)
            try {
                socket.handleServerMessage(update())
                socket.handleServerMessage(ServerMessage.StateDeltaUpdate(
                    delta = StateDelta(players = emptyList(), turnNumber = 2), events = emptyList(),
                    legalActions = listOf(emptyOffer), interactionEpoch = "fresh-delta",
                ))
                currentTime shouldBe 0L
                submitted shouldBe listOf(action to "origin", action to "fresh-delta")
                verify(exactly = 0) { controller.chooseAction(any(), any(), any(), any()) }
            } finally { socket.close() }
        }
    }

    test("synthetic no-provider JSON transport and mechanical dispatch measurement") {
        val json = Json { serializersModule = engineSerializersModule; classDiscriminator = "type"; encodeDefaults = true }
        val submitted = mutableListOf<Pair<GameAction, String?>>()
        val controller = mockk<AiPlayerController>()
        val socket = socket(controller, submitted)
        try {
            val samples = (0 until 120).map { index ->
                measureNanoTime {
                    val wire = json.encodeToString<ServerMessage>(update(epoch = "sample-$index"))
                    socket.handleServerMessage(json.decodeFromString<ServerMessage>(wire))
                }
            }.drop(20).sorted()
            submitted.size shouldBe 120
            verify(exactly = 0) { controller.chooseAction(any(), any(), any(), any()) }
            println("Synthetic empty-blocker JSON encode/decode + dispatch: samples=${samples.size}, " +
                "p50Micros=${samples[49] / 1000}, p95Micros=${samples[94] / 1000}; " +
                "provider calls=0, configured artificial pacing=0ms (previous path=2500ms at delay=500ms)")
        } finally { socket.close() }
    }

    test("real or incomplete blocker menus and absent epochs retain callback and 2500 milliseconds of configured pacing") {
        val cases = listOf(
            update(listOf(emptyOffer.copy(validBlockers = listOf(blocker),
                validBlockTargets = mapOf(blocker to listOf(attacker))))),
            // The native randomized-piles offer is empty blockers with absent pairwise targets.
            update(listOf(emptyOffer.copy(validBlockTargets = null))),
            update(listOf(emptyOffer.copy(validBlockers = null))),
            update(listOf(emptyOffer.copy(validBlockTargets = mapOf(blocker to listOf(attacker))))),
            update(listOf(emptyOffer.copy(mandatoryBlockerAssignments = mapOf(blocker to listOf(attacker))))),
            update(listOf(emptyOffer, LegalActionInfo("PassPriority", "Pass", PassPriority(player)))),
            update(epoch = null),
            update(epoch = ""),
            update(view = state.copy(viewingPlayerId = opponent)),
        )
        for (message in cases) runTest {
            val controller = mockk<AiPlayerController>()
            every { controller.chooseAction(any(), any(), any(), any()) } returns ActionResponse.SubmitAction(action)
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val socket = socket(controller, submitted)
            try {
                socket.handleServerMessage(message)
                currentTime shouldBe 2500L
                verify(exactly = 1) { controller.chooseAction(any(), any(), any(), any()) }
                submitted shouldBe listOf(action to message.interactionEpoch)
            } finally { socket.close() }
        }
    }

    test("pending decisions and built-in controller pacing remain unchanged") {
        runTest {
            val decision = YesNoDecision("question", player, "choice", DecisionContext())
            val controller = mockk<AiPlayerController>()
            every { controller.chooseAction(any(), any(), any(), any()) } returns ActionResponse.SubmitAction(action)
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val external = socket(controller, submitted)
            val builtin = socket(controller, submitted, external = false)
            try {
                external.handleServerMessage(update(decision = decision))
                builtin.handleServerMessage(update())
                currentTime shouldBe 5000L
                verify(exactly = 1) { controller.chooseAction(state, listOf(emptyOffer), decision, emptyList()) }
                verify(exactly = 1) { controller.chooseAction(state, listOf(emptyOffer), null, emptyList()) }
            } finally { external.close(); builtin.close() }
        }
    }

    test("opponent priority is never consumed") {
        runTest {
            val controller = mockk<AiPlayerController>()
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val socket = socket(controller, submitted)
            try {
                socket.handleServerMessage(update(view = state.copy(priorityPlayerId = opponent)))
                currentTime shouldBe 0L
                submitted shouldBe emptyList()
                verify(exactly = 0) { controller.chooseAction(any(), any(), any(), any()) }
            } finally { socket.close() }
        }
    }

    test("a QA hold retains its original epoch even when another update arrives") {
        runTest {
            val release = CompletableDeferred<Unit>()
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val socket = socket(mockk(), submitted, gate = AiActionGate { _, proposed -> release.await(); proposed })
            try {
                val held = launch { socket.handleServerMessage(update()) }
                runCurrent()
                submitted shouldBe emptyList()
                socket.handleServerMessage(update(emptyList(), epoch = "replacement"))
                release.complete(Unit)
                held.join()
                submitted shouldBe listOf(action to "origin")
            } finally { socket.close() }
        }
    }

    test("close and coroutine cancellation during a QA hold cannot submit the mechanical action") {
        for (closeSocket in listOf(false, true)) runTest {
            val release = CompletableDeferred<Unit>()
            val submitted = mutableListOf<Pair<GameAction, String?>>()
            val socket = socket(mockk(), submitted, gate = AiActionGate { _, proposed -> release.await(); proposed })
            try {
                val held = launch { socket.handleServerMessage(update()) }
                runCurrent()
                if (closeSocket) {
                    socket.close()
                    release.complete(Unit)
                    held.join()
                } else held.cancelAndJoin()
                submitted shouldBe emptyList()
            } finally { socket.close() }
        }
    }
})
