package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.ai.engine.SealedDeckGenerator
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.gameserver.config.AiProperties
import com.wingedsheep.gameserver.config.GameProperties
import com.wingedsheep.gameserver.persistence.dto.PersistentLobbyPlayer
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.gameserver.session.SessionRegistry
import com.wingedsheep.gameserver.tournament.llm.LlmCostTracker
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json

class AiControllerSeatLifecycleTest : FunSpec({
    test("explicit provider profile survives identity creation and repeated game wiring") {
        val provider = LifecycleProvider()
        val sessions = SessionRegistry()
        val manager = lifecycleManager(provider, sessions)
        val spec = AiControllerSpec(mode = "SEARCH-TEACHER", profileId = "strict-v1")

        val identity = manager.createAiIdentity(controllerSpec = spec)

        identity.aiControllerSpec shouldBe spec
        provider.contexts.map { it.profileId } shouldBe listOf("strict-v1")
        provider.contexts.single().gameSessionId shouldBe null

        val firstGame = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "game-1"
        }
        manager.wireAiForGame(
            gameSession = firstGame,
            aiPlayerId = identity.playerId,
            deckList = mapOf("Mountain" to 40),
            onActionReady = { _, _, _ -> },
            onMulliganKeep = { _ -> },
            onMulliganTake = { _ -> },
            onBottomCards = { _, _ -> },
        )

        val rematch = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "game-2"
        }
        manager.wireAiForGame(
            gameSession = rematch,
            aiPlayerId = identity.playerId,
            deckList = mapOf("Island" to 40),
            onActionReady = { _, _, _ -> },
            onMulliganKeep = { _ -> },
            onMulliganTake = { _ -> },
            onBottomCards = { _, _ -> },
        )

        provider.contexts.map { it.gameSessionId } shouldBe listOf(null, "game-1", "game-2")
        provider.contexts.map { it.profileId } shouldBe listOf("strict-v1", "strict-v1", "strict-v1")
        sessions.destroy()
    }

    test("two AI seats in one game keep independent provider profiles") {
        val provider = LifecycleProvider()
        val sessions = SessionRegistry()
        val manager = lifecycleManager(provider, sessions)

        val strict = manager.createAiIdentity(
            controllerSpec = AiControllerSpec(provider.mode, "strict-v1")
        )
        val loose = manager.createAiIdentity(
            controllerSpec = AiControllerSpec(provider.mode, "loose-v1")
        )
        provider.contexts.clear()

        val sharedGame = mockk<GameSession>(relaxed = true) {
            every { sessionId } returns "shared-game"
        }
        manager.wireAiForGame(
            gameSession = sharedGame,
            aiPlayerId = strict.playerId,
            deckList = mapOf("Mountain" to 40),
            onActionReady = { _, _, _ -> },
            onMulliganKeep = { _ -> },
            onMulliganTake = { _ -> },
            onBottomCards = { _, _ -> },
        )
        manager.wireAiForGame(
            gameSession = sharedGame,
            aiPlayerId = loose.playerId,
            deckList = mapOf("Island" to 40),
            onActionReady = { _, _, _ -> },
            onMulliganKeep = { _ -> },
            onMulliganTake = { _ -> },
            onBottomCards = { _, _ -> },
        )

        provider.contexts.map { it.gameSessionId } shouldBe listOf("shared-game", "shared-game")
        provider.contexts.associate { it.playerId to it.profileId } shouldBe mapOf(
            strict.playerId to "strict-v1",
            loose.playerId to "loose-v1",
        )
        sessions.destroy()
    }

    test("explicit unknown provider profile fails before an AI identity is created") {
        val provider = LifecycleProvider()
        val sessions = SessionRegistry()
        val manager = lifecycleManager(provider, sessions)

        shouldThrow<IllegalArgumentException> {
            manager.createAiIdentity(
                controllerSpec = AiControllerSpec(mode = provider.mode, profileId = "missing")
            )
        }.message shouldBe "Unknown AI controller profile 'missing' for mode 'search-teacher'"
        sessions.getAllIdentities().isEmpty() shouldBe true
        sessions.destroy()
    }

    test("rehydration restores an explicit provider profile instead of using server defaults") {
        val provider = LifecycleProvider()
        val sessions = SessionRegistry()
        val manager = lifecycleManager(provider, sessions)
        val spec = AiControllerSpec(provider.mode, "strict-v1")
        val identity = PlayerIdentity(
            token = "restored-profile",
            playerId = EntityId.of("restored-profile"),
            playerName = "Restored Profile",
            isAi = true,
            aiControllerSpec = spec,
        )

        manager.rehydrateAiIdentity(identity)

        provider.contexts.single().profileId shouldBe "strict-v1"
        provider.contexts.single().gameSessionId shouldBe null
        identity.aiControllerSpec shouldBe spec
        sessions.destroy()
    }

    test("lobby persistence serializes the per-seat controller spec") {
        val spec = AiControllerSpec("search-teacher", "strict-v1")
        val persisted = PersistentLobbyPlayer(
            playerId = "ai-1",
            playerName = "AI",
            token = "token",
            cardPoolNames = emptyList(),
            submittedDeck = null,
            isAi = true,
            aiControllerSpec = spec,
        )

        val json = Json.encodeToString(PersistentLobbyPlayer.serializer(), persisted)
        val restored = Json.decodeFromString(PersistentLobbyPlayer.serializer(), json)

        restored.aiControllerSpec shouldBe spec
    }
})

private fun lifecycleManager(
    provider: AiControllerProvider,
    sessions: SessionRegistry,
): AiGameManager = AiGameManager(
    gameProperties = GameProperties(ai = AiProperties(enabled = true, mode = provider.mode)),
    sessionRegistry = sessions,
    deckGenerator = mockk<SealedDeckGenerator>(relaxed = true),
    cardRegistry = mockk<CardRegistry>(relaxed = true),
    llmCostTracker = LlmCostTracker(),
    aiInsightService = AiInsightService(GameProperties()),
    controllerProviders = listOf(provider),
)

private class LifecycleProvider : AiControllerProvider {
    override val mode: String = "search-teacher"
    override val profiles: List<AiControllerProfile> = listOf(
        AiControllerProfile(id = "strict-v1", displayName = "Strict"),
        AiControllerProfile(id = "loose-v1", displayName = "Loose"),
    )
    val contexts = mutableListOf<AiControllerContext>()

    override fun create(context: AiControllerContext): AiPlayerController {
        contexts += context
        return LifecycleController
    }
}

private data object LifecycleController : AiPlayerController {
    override fun chooseAction(
        state: ClientGameState,
        legalActions: List<LegalActionInfo>,
        pendingDecision: PendingDecision?,
        recentGameLog: List<String>,
    ): ActionResponse = error("not used")

    override fun decideMulligan(mulliganMessage: MulliganInfo): Boolean = error("not used")
    override fun chooseBottomCards(message: BottomCardsInfo): List<EntityId> = error("not used")
    override fun setDeckList(deckList: Map<String, Int>, archetype: String?) = Unit
    override fun chooseDraftPick(
        pack: List<CardSummary>,
        pickedSoFar: List<CardSummary>,
        packNumber: Int,
        pickNumber: Int,
        picksRequired: Int,
        passDirection: String,
    ): List<String> = error("not used")

    override fun chooseWinstonAction(
        pileCards: List<CardSummary>,
        pileIndex: Int,
        pileSizes: List<Int>,
        pickedSoFar: List<CardSummary>,
    ): Boolean = error("not used")

    override fun chooseGridDraftPick(
        grid: List<CardSummary?>,
        availableSelections: List<String>,
        pickedSoFar: List<CardSummary>,
    ): String = error("not used")
}
