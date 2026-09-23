package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.replay.ReplaySetup
import com.wingedsheep.gameserver.replay.ReplayYieldEntry
import com.wingedsheep.sdk.model.EntityId

/**
 * One lock-consistent view of the live inputs needed by a trusted server-side AI controller.
 *
 * [state] is the *unmasked* authoritative state — both players' hands and libraries — the same view
 * [com.wingedsheep.ai.engine.EngineAiPlayerController] reads, and strictly more than the masked
 * `ClientGameState` an [AiPlayerController] is handed at decision time. Only trusted in-process
 * providers get one.
 *
 * [replayHistory] says whether compact inputs reproducing that state exist at all, and if so whether
 * they are complete or only the prefix retained after replay recording reached its cap. Keeping that
 * distinction in the type prevents an external controller from treating a plausible but incomplete
 * action list as the history of the live position.
 */
data class AiRuntimeSnapshot(
    val state: GameState,
    val replayHistory: AiReplayHistory,
)

/**
 * Replay inputs sampled atomically with an [AiRuntimeSnapshot]'s state.
 *
 * Persistent yield changes are inputs too: the engine can consume them without a [GameAction], so
 * setup plus actions alone does not necessarily reconstruct the position an AI is looking at.
 */
sealed interface AiReplayHistory {
    /**
     * The session records no replay inputs at all, so the snapshot's state cannot be reached from
     * any input stream. Injected sessions — dev scenarios, hotseat — are built this way. The live
     * state is still authoritative and still worth reading; only its history is missing.
     */
    data object Unavailable : AiReplayHistory

    /** Replay inputs that were recorded for this session. */
    sealed interface Recorded : AiReplayHistory {
        val setup: ReplaySetup
        val actions: List<GameAction>
        val yields: List<ReplayYieldEntry>
    }

    /** These inputs reproduce the snapshot's live state. */
    data class Complete(
        override val setup: ReplaySetup,
        override val actions: List<GameAction>,
        override val yields: List<ReplayYieldEntry>,
    ) : Recorded

    /**
     * Recording stopped before the snapshot's live state. These inputs remain an honest replay
     * prefix, but must not be used to reconstruct or explain the current state.
     */
    data class TruncatedPrefix(
        override val setup: ReplaySetup,
        override val actions: List<GameAction>,
        override val yields: List<ReplayYieldEntry>,
    ) : Recorded
}

/**
 * One provider-advertised controller preset selectable for an AI seat.
 *
 * [id] is opaque to game-server: it is persisted and handed back to the owning provider unchanged.
 * [deckSpec] is optional generic seat configuration, not controller policy. A caller that applies a
 * profile with a deck preset must apply the controller/profile and deck selection as one seat update
 * so a failed validation cannot leave the two out of sync.
 */
data class AiControllerProfile(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val deckSpec: AiDeckSpec? = null,
)

/** Inputs supplied to an AI implementation hosted outside the game-server build. */
data class AiControllerContext(
    val playerId: EntityId,
    /** Null while a persisted or tournament AI identity is not yet attached to a game. */
    val gameSessionId: String?,
    /** Opaque provider profile selected for this seat, or null for the provider's default behavior. */
    val profileId: String? = null,
    /** Null until the attached game has started and has a live state. */
    val snapshot: () -> AiRuntimeSnapshot?,
)

/**
 * Extension point for a server-side AI implementation supplied by another build.
 *
 * Providers own controller policy only. Game lifecycle, callbacks, and the authoritative runtime
 * snapshot remain game-server responsibilities.
 */
interface AiControllerProvider {
    /** Case-insensitive configuration value used by `game.ai.mode`. */
    val mode: String

    /** Selectable presets for this provider. IDs are opaque and case-sensitive. */
    val profiles: List<AiControllerProfile> get() = emptyList()

    fun create(context: AiControllerContext): AiPlayerController
}

/** Single authority for validating and resolving external mode/profile names. */
internal class AiControllerProviderRegistry(providers: List<AiControllerProvider>) {
    private val providersByMode: Map<String, AiControllerProvider>

    init {
        val indexed = linkedMapOf<String, AiControllerProvider>()
        for (provider in providers) {
            val mode = normalize(provider.mode)
            require(mode.isNotEmpty()) { "AI controller provider mode must not be blank" }
            require(mode !in BUILT_IN_MODES) {
                "AI controller provider mode '${provider.mode}' conflicts with a built-in mode"
            }
            require(indexed.put(mode, provider) == null) {
                "Multiple AI controller providers registered for mode '${provider.mode}'"
            }

            val profileIds = mutableSetOf<String>()
            for (profile in provider.profiles) {
                require(profile.id.isNotBlank()) {
                    "AI controller provider '${provider.mode}' has a blank profile id"
                }
                require(profile.displayName.isNotBlank()) {
                    "AI controller provider '${provider.mode}' profile '${profile.id}' has a blank display name"
                }
                require(profileIds.add(profile.id)) {
                    "AI controller provider '${provider.mode}' has duplicate profile id '${profile.id}'"
                }
            }
        }
        providersByMode = indexed
    }

    operator fun get(mode: String): AiControllerProvider? = providersByMode[normalize(mode)]

    /** Provider-advertised profiles for [mode], preserving each opaque profile id verbatim. */
    fun profiles(mode: String): List<AiControllerProfile> = providersByMode[normalize(mode)]?.profiles.orEmpty()

    /**
     * Resolve one explicit external profile or fail closed. An explicit selection never falls back
     * to another provider, another profile, or the provider default.
     */
    fun requireProfile(mode: String, profileId: String): AiControllerProfile {
        val provider = requireNotNull(providersByMode[normalize(mode)]) {
            "Unknown AI controller mode '$mode'; expected ${supportedModes().sorted().joinToString()}"
        }
        return requireNotNull(provider.profiles.firstOrNull { it.id == profileId }) {
            "Unknown AI controller profile '$profileId' for mode '${provider.mode}'"
        }
    }

    fun supportedModes(): Set<String> = BUILT_IN_MODES + providersByMode.keys

    private fun normalize(mode: String): String = mode.trim().lowercase()

    private companion object {
        val BUILT_IN_MODES = setOf("engine", "llm")
    }
}
