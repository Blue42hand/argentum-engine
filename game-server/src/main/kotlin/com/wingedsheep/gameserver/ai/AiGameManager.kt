package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.ai.jev.JevAiPlayerController
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.LlmAiPlayerController
import com.wingedsheep.ai.llm.LlmClient
import com.wingedsheep.gameserver.config.GameProperties
import com.wingedsheep.ai.engine.SealedDeckGenerator
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.gameserver.session.SessionRegistry
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.EntityId
import org.slf4j.LoggerFactory
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(AiGameManager::class.java)

/**
 * Manages the lifecycle of AI opponents in games.
 *
 * Supports built-in and externally supplied AI modes:
 * - **engine** (default): Built-in rules-engine AI. No API key needed. Fast, deterministic.
 * - **llm**: LLM-based AI via OpenAI-compatible API. Requires API key.
 * - any unique mode registered by an [AiControllerProvider].
 */
@Service
class AiGameManager(
    private val gameProperties: GameProperties,
    private val sessionRegistry: SessionRegistry,
    private val deckGenerator: SealedDeckGenerator,
    private val cardRegistry: CardRegistry,
    private val llmCostTracker: com.wingedsheep.gameserver.tournament.llm.LlmCostTracker,
    private val aiInsightService: AiInsightService,
    controllerProviders: List<AiControllerProvider> = emptyList(),
) {
    private val controllerProviders = AiControllerProviderRegistry(controllerProviders)
    /**
     * The live AI sessions of each game, keyed game → AI player. A multiplayer pod seats more than
     * one AI (an FFA table, a Two-Headed Giant team), so this is per *seat* and not per game: keyed
     * by game alone, the second AI wired into a pod replaced the first, and [cleanupGame] shut down
     * one of them and leaked the rest.
     */
    private val activeSessions = ConcurrentHashMap<String, ConcurrentHashMap<EntityId, AiWebSocketSession>>()

    /** Register [session] as [aiPlayerId]'s live session in [gameSessionId], shutting down any predecessor. */
    private fun trackSession(gameSessionId: String, aiPlayerId: EntityId, session: AiWebSocketSession) {
        val previous = activeSessions
            .computeIfAbsent(gameSessionId) { ConcurrentHashMap() }
            .put(aiPlayerId, session)
        previous?.shutdown()
    }

    @PostConstruct
    fun logConfig() {
        val ai = gameProperties.ai
        if (!ai.enabled) {
            logger.info("AI opponent: disabled")
            return
        }
        if (ai.isEngineMode) {
            logger.info("AI opponent: enabled | mode=engine (built-in)")
        } else if (ai.isLlmMode) {
            val provider = if (ai.baseUrl.contains("openrouter")) "OpenRouter" else "Local (${ai.baseUrl})"
            logger.info("AI opponent: enabled | mode=llm | provider={} | model={} | deckbuilding-model={}",
                provider, ai.model, ai.effectiveDeckbuildingModel)
        } else {
            requireExternalProvider(ai.mode)
            logger.info("AI opponent: enabled | mode={} (external)", ai.mode)
        }
    }

    companion object {
        private val AI_NAMES = listOf(
            "Cruel Optimus",
            "Thought Harvester",
            "The Stack Tyrant",
            "Mindripper Prime",
            "Soulless Topdeckr",
            "The Unblinkable",
            "Dread Calculus",
            "Synapse Ravager",
            "Neural Butcher",
            "The Iron Oracle",
            "Phyrexian Brainframe",
            "Darksteel Nemesis",
            "Voltaic Mastermind",
            "Myr Overlord",
            "Blightsteel Brain",
        )

        fun randomAiName(): String = "[AI] ${AI_NAMES.random()}"
    }

    val isEnabled: Boolean get() {
        if (!gameProperties.ai.enabled) return false
        val ai = gameProperties.ai
        if (ai.isEngineMode) return true
        if (ai.isLlmMode) return ai.effectiveApiKey.isNotBlank()
        return controllerProviders[ai.mode] != null
    }

    /**
     * Master AI toggle, ignoring the LLM-key gate that [isEnabled] also applies. Use this
     * for code paths that force engine mode (e.g. dev scenarios) and so don't need a key
     * even when the server's global config is LLM.
     */
    val aiEnabledToggle: Boolean get() = gameProperties.ai.enabled

    /**
     * Look up the legacy LLM model override for an AI player by querying its identity.
     * New per-seat controller selection lives in [PlayerIdentity.aiControllerSpec]; the legacy
     * override remains readable so persisted pre-spec lobbies keep working during migration.
     */
    private fun lookupModelOverride(aiPlayerId: EntityId): String? {
        return sessionRegistry.getAllIdentities()
            .firstOrNull { it.playerId == aiPlayerId }
            ?.aiModelOverride
    }

    /**
     * Create the appropriate AI controller from a per-seat selection or the server-wide fallback.
     *
     * An explicit [controllerSpec] is authoritative. Unknown providers or profiles throw instead of
     * falling back to `game.ai.mode`; this is what makes a restored external seat fail closed when
     * its provider disappears. [modelOverride] is the legacy built-in-LLM selector and may only be
     * used when no controller spec exists.
     */
    private fun createController(
        aiPlayerId: EntityId,
        gameSession: GameSession? = null,
        modelOverride: String? = null,
        controllerSpec: AiControllerSpec? = null,
    ): AiPlayerController {
        require(controllerSpec == null || modelOverride == null) {
            "An AI seat cannot select both aiControllerSpec and legacy aiModelOverride"
        }
        val ai = gameProperties.ai
        val explicitMode = controllerSpec?.mode?.trim()

        if (controllerSpec != null && !controllerProviders.isBuiltIn(explicitMode!!)) {
            val provider = requireExternalProvider(explicitMode)
            controllerSpec.profileId?.let { controllerProviders.requireProfile(explicitMode, it) }
            return provider.create(
                AiControllerContext(
                    playerId = aiPlayerId,
                    gameSessionId = gameSession?.sessionId,
                    profileId = controllerSpec.profileId,
                    snapshot = { gameSession?.getAiRuntimeSnapshot() },
                )
            )
        }

        // No per-seat selection: preserve the existing server-wide external-provider behavior.
        if (controllerSpec == null && modelOverride == null && !ai.isEngineMode && !ai.isLlmMode) {
            return requireExternalProvider(ai.mode).create(
                AiControllerContext(
                    playerId = aiPlayerId,
                    gameSessionId = gameSession?.sessionId,
                    snapshot = { gameSession?.getAiRuntimeSnapshot() },
                )
            )
        }

        if (controllerSpec?.profileId != null) {
            throw IllegalArgumentException("Built-in AI controller mode '${controllerSpec.mode}' does not support profiles")
        }

        // A legacy model override explicitly requests LLM. An explicit built-in spec selects its
        // requested mode without changing the server's model/base-url tuning.
        val aiConfig = ai.toAiConfig().let { cfg ->
            when {
                modelOverride != null -> cfg.copy(model = modelOverride, mode = "llm")
                explicitMode != null -> cfg.copy(mode = explicitMode)
                else -> cfg
            }
        }
        // Local testing mode only, and null everywhere else: the LLM controller's engine fallback
        // gets one too, so a fallback decision doesn't silently vanish from the panel.
        val insightSink = gameSession?.sessionId?.let { aiInsightService.sinkFor(it, aiPlayerId) }
        return if (aiConfig.isEngineMode) {
            EngineAiPlayerController(
                cardRegistry = cardRegistry,
                playerId = aiPlayerId,
                gameStateProvider = { gameSession?.getStateSnapshot() },
                insightSink = insightSink,
            )
        } else {
            val engineFallback = EngineAiPlayerController(
                cardRegistry = cardRegistry,
                playerId = aiPlayerId,
                gameStateProvider = { gameSession?.getStateSnapshot() },
                insightSink = insightSink,
            )
            // Attribute in-game LLM token usage + cost to this game session, so the LLM-tournament
            // can report cost per game. No-op when there's no game (e.g. placeholder identities).
            val gameId = gameSession?.sessionId
            val usageSink: ((com.wingedsheep.ai.llm.LlmUsage) -> Unit)? =
                if (gameId != null) { usage -> llmCostTracker.record(gameId, usage) } else null
            val llmClient = LlmClient(aiConfig, usageSink)
            LlmAiPlayerController(aiConfig, llmClient, aiPlayerId, fallback = engineFallback)
        }
    }

    private fun requireExternalProvider(mode: String): AiControllerProvider =
        requireNotNull(controllerProviders[mode]) {
            "Unknown game.ai.mode '$mode'; expected ${controllerProviders.supportedModes().sorted().joinToString()}"
        }

    /** Validate a newly selected seat controller before creating any identity/session state. */
    private fun requireAvailableSelection(controllerSpec: AiControllerSpec?, modelOverride: String?) {
        require(gameProperties.ai.enabled) { "AI is not enabled. Set game.ai.enabled=true." }
        require(controllerSpec == null || modelOverride == null) {
            "An AI seat cannot select both aiControllerSpec and legacy aiModelOverride"
        }
        requireCredentialedOverride(modelOverride)
        if (controllerSpec == null) {
            require(isEnabled) { "AI is not enabled or its configured controller is unavailable." }
            return
        }

        val mode = controllerSpec.mode.trim()
        when {
            mode.equals("engine", ignoreCase = true) -> require(controllerSpec.profileId == null) {
                "Built-in AI controller mode '${controllerSpec.mode}' does not support profiles"
            }
            mode.equals("llm", ignoreCase = true) -> {
                require(controllerSpec.profileId == null) {
                    "Built-in AI controller mode '${controllerSpec.mode}' does not support profiles"
                }
                require(gameProperties.ai.effectiveApiKey.isNotBlank()) {
                    "AI controller mode 'llm' requires an API key"
                }
            }
            else -> {
                requireExternalProvider(mode)
                controllerSpec.profileId?.let { controllerProviders.requireProfile(mode, it) }
            }
        }
    }

    /**
     * Reject a per-player LLM model override that no key can serve.
     */
    private fun requireCredentialedOverride(modelOverride: String?) {
        require(modelOverride == null || gameProperties.ai.effectiveApiKey.isNotBlank()) {
            "A per-player LLM model override requires an API key"
        }
    }

    /**
     * Legacy model overrides may degrade during recovery/wiring because that was their historical
     * behavior. Explicit [AiControllerSpec] selections never use this path and therefore never fall
     * back silently when their controller/profile is unavailable.
     */
    private fun usableModelOverride(modelOverride: String?, context: String): String? {
        if (modelOverride == null || gameProperties.ai.effectiveApiKey.isNotBlank()) return modelOverride
        logger.warn(
            "Ignoring LLM model override '{}' while {}: no API key is configured. " +
                "This AI falls back to game.ai.mode={}.",
            modelOverride, context, gameProperties.ai.mode
        )
        return null
    }

    private fun com.wingedsheep.gameserver.config.AiProperties.toAiConfig() = com.wingedsheep.ai.llm.AiConfig(
        enabled = enabled, mode = mode, baseUrl = baseUrl,
        apiKey = apiKey, openRouterApiKey = openRouterApiKey,
        model = model, deckbuildingModel = deckbuildingModel,
        reasoningEffort = reasoningEffort, maxRetries = maxRetries,
        timeoutMs = timeoutMs, thinkingDelayMs = thinkingDelayMs
    )

    /** The only place an [AiWebSocketSession] is constructed. */
    private fun buildAiSession(
        aiPlayerId: EntityId,
        controller: AiPlayerController,
        gameSession: GameSession?,
        onActionReady: (EntityId, GameAction, String?) -> Unit = { _, _, _ -> },
        onMulliganKeep: (EntityId) -> Unit = { _ -> },
        onMulliganTake: (EntityId) -> Unit = { _ -> },
        onBottomCards: (EntityId, List<EntityId>) -> Unit = { _, _ -> },
    ): AiWebSocketSession = AiWebSocketSession(
        aiPlayerId = aiPlayerId,
        controller = controller,
        thinkingDelayMs = gameProperties.ai.thinkingDelayMs,
        onActionReady = onActionReady,
        onMulliganKeep = onMulliganKeep,
        onMulliganTake = onMulliganTake,
        onBottomCards = onBottomCards,
        allowActionsOnlyFallback = controller is EngineAiPlayerController || controller is LlmAiPlayerController || controller is JevAiPlayerController,
        actionGate = gameSession?.let { aiInsightService.gateFor(it.sessionId) },
    )

    private fun registerAiSession(
        gameSession: GameSession,
        aiPlayerId: EntityId,
        playerName: String,
        controller: AiPlayerController,
        modelOverride: String? = null,
        controllerSpec: AiControllerSpec? = null,
        onActionReady: (EntityId, GameAction, String?) -> Unit,
        onMulliganKeep: (EntityId) -> Unit,
        onMulliganTake: (EntityId) -> Unit,
        onBottomCards: (EntityId, List<EntityId>) -> Unit,
    ): Pair<PlayerSession, PlayerIdentity> {
        val aiSession = buildAiSession(
            aiPlayerId = aiPlayerId,
            controller = controller,
            gameSession = gameSession,
            onActionReady = onActionReady,
            onMulliganKeep = onMulliganKeep,
            onMulliganTake = onMulliganTake,
            onBottomCards = onBottomCards,
        )

        val playerSession = PlayerSession(
            webSocketSession = aiSession,
            playerId = aiPlayerId,
            playerName = playerName
        )

        val identity = PlayerIdentity(
            token = "ai-token-${UUID.randomUUID().toString().take(8)}",
            playerId = aiPlayerId,
            playerName = playerName,
            isAi = true,
            aiModelOverride = modelOverride,
            aiControllerSpec = controllerSpec,
        )
        identity.webSocketSession = aiSession
        identity.currentGameSessionId = gameSession.sessionId
        sessionRegistry.register(identity, aiSession, playerSession)

        trackSession(gameSession.sessionId, aiPlayerId, aiSession)
        aiPlayerIds.add(aiPlayerId)
        return playerSession to identity
    }

    fun createAiOpponent(
        gameSession: GameSession,
        setCode: String? = null,
        onActionReady: (EntityId, GameAction, String?) -> Unit,
        onMulliganKeep: (EntityId) -> Unit,
        onMulliganTake: (EntityId) -> Unit,
        onBottomCards: (EntityId, List<EntityId>) -> Unit,
        deckOverride: Map<String, Int>? = null,
        commanderCardName: String? = null,
        /** Optional per-seat controller selection; null preserves the server-wide fallback. */
        controllerSpec: AiControllerSpec? = null,
    ): PlayerSession {
        requireAvailableSelection(controllerSpec, modelOverride = null)

        val aiPlayerId = EntityId("ai-${UUID.randomUUID().toString().take(8)}")
        val effectiveMode = controllerSpec?.mode ?: gameProperties.ai.mode
        val aiName = randomAiName() + if (effectiveMode.trim().equals("jev", ignoreCase = true)) " (Jev)" else ""

        val controller = createController(aiPlayerId, gameSession, controllerSpec = controllerSpec)

        val (playerSession, identity) = registerAiSession(
            gameSession = gameSession,
            aiPlayerId = aiPlayerId,
            playerName = aiName,
            controller = controller,
            controllerSpec = controllerSpec,
            onActionReady = onActionReady,
            onMulliganKeep = onMulliganKeep,
            onMulliganTake = onMulliganTake,
            onBottomCards = onBottomCards,
        )

        val aiDeck = deckOverride
            ?: if (setCode != null) deckGenerator.generate(setCode) else deckGenerator.generate()
        gameSession.addPlayer(playerSession, aiDeck, commanderCardName = commanderCardName)
        controller.setDeckList(aiDeck)
        gameSession.setPlayerPersistenceInfo(aiPlayerId, aiName, identity.token, isAi = true)

        logger.info("Created AI opponent ({}) for game {} [mode={}, profile={}]",
            aiPlayerId.value, gameSession.sessionId, effectiveMode, controllerSpec?.profileId)
        return playerSession
    }

    fun wireAiForDevScenario(
        gameSession: GameSession,
        aiPlayerId: EntityId,
        playerName: String,
        onActionReady: (EntityId, GameAction, String?) -> Unit,
        onMulliganKeep: (EntityId) -> Unit,
        onMulliganTake: (EntityId) -> Unit,
        onBottomCards: (EntityId, List<EntityId>) -> Unit
    ): PlayerSession {
        require(gameProperties.ai.enabled) { "AI is not enabled. Set game.ai.enabled=true." }

        val controller = EngineAiPlayerController(
            cardRegistry = cardRegistry,
            playerId = aiPlayerId,
            gameStateProvider = { gameSession.getStateSnapshot() },
            insightSink = aiInsightService.sinkFor(gameSession.sessionId, aiPlayerId),
        )

        val (playerSession, identity) = registerAiSession(
            gameSession = gameSession,
            aiPlayerId = aiPlayerId,
            playerName = playerName,
            controller = controller,
            onActionReady = onActionReady,
            onMulliganKeep = onMulliganKeep,
            onMulliganTake = onMulliganTake,
            onBottomCards = onBottomCards,
        )

        gameSession.associatePlayer(playerSession)
        gameSession.setPlayerPersistenceInfo(aiPlayerId, playerName, identity.token, isAi = true)

        logger.info("Wired AI ({}) into dev scenario {} [engine mode forced]",
            aiPlayerId.value, gameSession.sessionId)
        return playerSession
    }

    /**
     * Create an AI PlayerIdentity for use in a tournament/pod lobby.
     * [controllerSpec] is the generic durable seat selection; [modelOverride] remains legacy input.
     */
    fun createAiIdentity(
        modelOverride: String? = null,
        controllerSpec: AiControllerSpec? = null,
    ): PlayerIdentity {
        requireAvailableSelection(controllerSpec, modelOverride)

        val aiPlayerId = EntityId("ai-${UUID.randomUUID().toString().take(8)}")
        val aiProperties = gameProperties.ai

        val controller = createController(
            aiPlayerId = aiPlayerId,
            modelOverride = modelOverride,
            controllerSpec = controllerSpec,
        )
        val aiSession = buildAiSession(aiPlayerId, controller, gameSession = null)

        val effectiveModel = modelOverride ?: if (controllerSpec == null && gameProperties.ai.isLlmMode) gameProperties.ai.model else null
        val modelSuffix = effectiveModel?.substringAfterLast('/')?.let { " ($it)" } ?: ""
        val effectiveMode = controllerSpec?.mode ?: gameProperties.ai.mode
        val suffix = if (effectiveMode.trim().equals("jev", ignoreCase = true) && modelOverride == null) " (Jev)" else modelSuffix
        val aiName = randomAiName() + suffix
        val identity = PlayerIdentity(
            token = "ai-token-${UUID.randomUUID().toString().take(8)}",
            playerId = aiPlayerId,
            playerName = aiName,
            isAi = true,
            aiModelOverride = modelOverride,
            aiControllerSpec = controllerSpec,
        )
        identity.webSocketSession = aiSession

        val playerSession = PlayerSession(
            webSocketSession = aiSession,
            playerId = aiPlayerId,
            playerName = aiName
        )
        sessionRegistry.register(identity, aiSession, playerSession)
        aiPlayerIds.add(aiPlayerId)

        logger.info(
            "Created AI identity: {} ({}) [mode={}, profile={}, legacyModel={}]",
            identity.playerName,
            aiPlayerId.value,
            effectiveMode,
            controllerSpec?.profileId,
            modelOverride ?: aiProperties.model,
        )
        return identity
    }

    /**
     * Re-establish in-memory AI tracking for a [PlayerIdentity] loaded from persistence.
     * Explicit controller specs fail closed if their provider/profile is unavailable; legacy model
     * overrides retain their historical degradation behavior.
     */
    fun rehydrateAiIdentity(identity: PlayerIdentity) {
        require(identity.isAi) { "rehydrateAiIdentity called on non-AI identity ${identity.playerName}" }
        if (!gameProperties.ai.enabled) {
            logger.warn("AI is disabled but recovered AI identity {}; AI players will not act.", identity.playerName)
            return
        }

        val aiPlayerId = identity.playerId
        val controllerSpec = identity.aiControllerSpec
        if (controllerSpec != null) {
            requireAvailableSelection(controllerSpec, modelOverride = null)
        } else if (!isEnabled) {
            logger.warn("Recovered AI identity {} uses server defaults, but the configured controller is unavailable.", identity.playerName)
            return
        }

        val modelOverride = if (controllerSpec == null) {
            usableModelOverride(identity.aiModelOverride, "rehydrating AI identity ${identity.playerName}")
        } else null
        val controller = createController(
            aiPlayerId = aiPlayerId,
            modelOverride = modelOverride,
            controllerSpec = controllerSpec,
        )
        val aiSession = buildAiSession(aiPlayerId, controller, gameSession = null)

        identity.webSocketSession = aiSession
        val playerSession = PlayerSession(
            webSocketSession = aiSession,
            playerId = aiPlayerId,
            playerName = identity.playerName
        )
        sessionRegistry.register(identity, aiSession, playerSession)
        aiPlayerIds.add(aiPlayerId)

        logger.info("Rehydrated AI identity: {} ({}) [mode={}, profile={}]",
            identity.playerName,
            aiPlayerId.value,
            controllerSpec?.mode ?: gameProperties.ai.mode,
            controllerSpec?.profileId)
    }

    /** Wire an AI player's session for a specific tournament/pod match. */
    fun wireAiForGame(
        gameSession: GameSession,
        aiPlayerId: EntityId,
        deckList: Map<String, Int>?,
        onActionReady: (EntityId, GameAction, String?) -> Unit,
        onMulliganKeep: (EntityId) -> Unit,
        onMulliganTake: (EntityId) -> Unit,
        onBottomCards: (EntityId, List<EntityId>) -> Unit
    ) {
        val identity = sessionRegistry.getAllIdentities().find { it.playerId == aiPlayerId }
        val oldSession = identity?.webSocketSession as? AiWebSocketSession
        if (oldSession != null) oldSession.shutdown()

        val controllerSpec = identity?.aiControllerSpec
        if (controllerSpec != null) requireAvailableSelection(controllerSpec, modelOverride = null)
        val modelOverride = if (controllerSpec == null) {
            usableModelOverride(
                lookupModelOverride(aiPlayerId),
                "wiring AI ${aiPlayerId.value} for game ${gameSession.sessionId}"
            )
        } else null
        val controller = createController(
            aiPlayerId = aiPlayerId,
            gameSession = gameSession,
            modelOverride = modelOverride,
            controllerSpec = controllerSpec,
        )

        if (deckList != null) controller.setDeckList(deckList)

        val newSession = buildAiSession(
            aiPlayerId = aiPlayerId,
            controller = controller,
            gameSession = gameSession,
            onActionReady = onActionReady,
            onMulliganKeep = onMulliganKeep,
            onMulliganTake = onMulliganTake,
            onBottomCards = onBottomCards,
        )

        identity?.webSocketSession = newSession
        if (identity != null) {
            val playerSession = PlayerSession(
                webSocketSession = newSession,
                playerId = aiPlayerId,
                playerName = identity.playerName
            )
            sessionRegistry.setPlayerSession(newSession.id, playerSession)
        }

        gameSession.clearLastSentState(aiPlayerId)
        trackSession(gameSession.sessionId, aiPlayerId, newSession)
        logger.info("Wired AI {} for game {} [mode={}, profile={}]",
            aiPlayerId.value,
            gameSession.sessionId,
            controllerSpec?.mode ?: gameProperties.ai.mode,
            controllerSpec?.profileId)
    }

    fun setThinkingDelay(aiPlayerId: EntityId, thinkingDelayMs: Long) {
        val ws = sessionRegistry.getAllIdentities()
            .firstOrNull { it.playerId == aiPlayerId }
            ?.webSocketSession as? AiWebSocketSession
        ws?.thinkingDelayMs = thinkingDelayMs
    }

    private val aiPlayerIds = ConcurrentHashMap.newKeySet<EntityId>()

    fun isAiPlayer(playerId: EntityId): Boolean = playerId in aiPlayerIds

    fun cleanupGame(gameSessionId: String) {
        val sessions = activeSessions.remove(gameSessionId) ?: return
        sessions.values.forEach { it.shutdown() }
        logger.info("Cleaned up {} AI session(s) for game {}", sessions.size, gameSessionId)
    }

    fun hasAiPlayer(gameSessionId: String): Boolean =
        activeSessions[gameSessionId]?.isNotEmpty() == true
}
