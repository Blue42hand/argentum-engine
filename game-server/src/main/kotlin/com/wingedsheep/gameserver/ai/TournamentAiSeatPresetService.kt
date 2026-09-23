package com.wingedsheep.gameserver.ai

import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.lobby.LobbyState
import com.wingedsheep.gameserver.lobby.TournamentFormat
import com.wingedsheep.gameserver.lobby.TournamentLobby
import com.wingedsheep.sdk.model.EntityId
import org.springframework.stereotype.Service

/**
 * Applies one controller/profile selection and its optional provider-owned deck to a tournament
 * lobby AI seat as one host-side mutation.
 *
 * Resolution and deck generation happen before any state changes. The old lobby row is restored if
 * the generated deck cannot be submitted, so a failed preset cannot leave the seat with only its
 * controller half or only its deck half. This is deliberately generic: providers own opaque profile
 * IDs and may associate an [AiDeckSpec], while policy semantics remain outside Argentum.
 */
@Service
class TournamentAiSeatPresetService(
    private val aiGameManager: AiGameManager,
    private val randomDeckResolver: RandomDeckResolver,
) {
    sealed interface ApplyResult {
        data class Applied(
            val controllerSpec: AiControllerSpec?,
            val deckSpec: AiDeckSpec,
        ) : ApplyResult

        data class Rejected(val message: String) : ApplyResult
    }

    /**
     * Apply an explicit per-seat controller selection, or clear it when [controllerSpec] is null.
     *
     * Provider-associated decks are authoritative for the selected preset. A controller/profile
     * with no associated deck resets the seat to [AiDeckSpec.Auto]; the host may then choose an
     * independent deck through the existing deck picker. This prevents a deck owned by a previous
     * profile from surviving after that profile is replaced.
     */
    fun applyPremadePreset(
        lobby: TournamentLobby,
        aiPlayerId: EntityId,
        controllerSpec: AiControllerSpec?,
    ): ApplyResult {
        if (lobby.state != LobbyState.WAITING_FOR_PLAYERS) {
            return ApplyResult.Rejected("Can only change an AI controller while waiting for players")
        }
        if (lobby.format != TournamentFormat.PREMADE_DECKS) {
            return ApplyResult.Rejected("AI controller deck presets require a premade-decks lobby")
        }

        val previousState = lobby.players[aiPlayerId]
            ?: return ApplyResult.Rejected("No such player in this lobby")
        if (!aiGameManager.isAiPlayer(aiPlayerId)) {
            return ApplyResult.Rejected("Controller presets can only be applied to AI seats")
        }

        val resolved = try {
            controllerSpec?.let(aiGameManager::resolveSeatPreset)
        } catch (error: IllegalArgumentException) {
            return ApplyResult.Rejected(error.message ?: "AI controller selection is unavailable")
        }

        val deckSpec = resolved?.deckSpec ?: AiDeckSpec.Auto
        val generated = try {
            randomDeckResolver.resolve(
                deckSpec,
                lobby.deckFormat,
                lobby.setCodes,
                lobby.usesCommanderRules,
            )
        } catch (error: RuntimeException) {
            return ApplyResult.Rejected(error.message ?: "Could not build the AI controller preset deck")
        }

        val commander = generated.commander?.takeIf { lobby.usesCommanderRules }
        if (lobby.usesCommanderRules && commander == null) {
            return ApplyResult.Rejected("The selected AI controller preset could not produce a commander deck")
        }
        val submitted = if (commander != null) generated.submissionList else generated.deckList

        // PREMADE_DECKS accepts only one submitted deck at a time. Snapshot the complete row, clear
        // the old deck, and restore that row if validation rejects the replacement. Controller state
        // is not changed until submission has succeeded, so failure is observably all-or-nothing.
        lobby.discardSubmittedDeck(aiPlayerId)
        return when (val result = lobby.submitDeck(aiPlayerId, submitted, commander = commander)) {
            is TournamentLobby.DeckSubmissionResult.Error -> {
                lobby.players[aiPlayerId] = previousState
                ApplyResult.Rejected("AI controller preset deck rejected: ${result.message}")
            }

            is TournamentLobby.DeckSubmissionResult.Success -> {
                val committedState = checkNotNull(lobby.players[aiPlayerId]) {
                    "AI seat disappeared while applying a controller preset"
                }
                committedState.aiDeckSpec = deckSpec
                committedState.identity.aiControllerSpec = resolved?.controllerSpec
                ApplyResult.Applied(
                    controllerSpec = resolved?.controllerSpec,
                    deckSpec = deckSpec,
                )
            }
        }
    }

    /**
     * Return the provider-owned deck for an explicit selection, if any.
     *
     * Callers should use this before allowing an independent deck edit. Resolution intentionally
     * throws for an unknown/disappeared explicit provider or profile so those states fail closed.
     */
    fun providerOwnedDeck(controllerSpec: AiControllerSpec?): AiDeckSpec? =
        controllerSpec?.let(aiGameManager::resolveSeatPreset)?.deckSpec
}
