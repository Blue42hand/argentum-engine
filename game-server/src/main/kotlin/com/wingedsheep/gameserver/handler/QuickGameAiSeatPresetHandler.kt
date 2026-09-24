package com.wingedsheep.gameserver.handler

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gameserver.ai.AiGameManager
import com.wingedsheep.gameserver.deck.DeckValidator
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import com.wingedsheep.gameserver.protocol.ErrorCode
import com.wingedsheep.gameserver.protocol.SetQuickGameAiController
import com.wingedsheep.gameserver.session.SessionRegistry
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.model.Deck
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * Host-only in-lobby controller/profile mutation for Quick Game's single AI seat.
 *
 * Controller/profile resolution and any provider-owned deck validation complete before the lobby is
 * changed. A profile with no associated deck resets the AI seat to Auto, matching the tournament
 * preset service and ensuring a deck owned by a previous profile cannot survive after replacement.
 */
@Component
class QuickGameAiSeatPresetHandler(
    private val sessionRegistry: SessionRegistry,
    private val lobbyRepository: QuickGameLobbyRepository,
    private val aiGameManager: AiGameManager,
    private val deckValidator: DeckValidator,
    private val boosterGenerator: BoosterGenerator,
    private val sender: MessageSender,
    private val quickGameLobbyHandler: QuickGameLobbyHandler,
) {
    fun handle(session: WebSocketSession, message: SetQuickGameAiController) {
        val playerSession = sessionRegistry.getPlayerSession(session.id) ?: run {
            sender.sendError(session, ErrorCode.NOT_CONNECTED, "Not connected")
            return
        }
        val lobby = lobbyRepository.findContainingPlayer(playerSession.playerId) ?: run {
            sender.sendError(session, ErrorCode.GAME_NOT_FOUND, "Not in a lobby")
            return
        }

        var applied = false
        lobbyRepository.withLock(lobby.lobbyId) { current ->
            if (current == null) return@withLock
            val host = current.players.firstOrNull { !it.isAi }
            if (host?.playerId != playerSession.playerId) {
                sender.sendError(session, ErrorCode.INVALID_ACTION, "Only the host can choose an AI controller")
                return@withLock
            }
            if (!current.vsAi || current.players.none { it.isAi }) {
                sender.sendError(session, ErrorCode.INVALID_ACTION, "This lobby has no AI opponent")
                return@withLock
            }

            val resolved = try {
                message.spec?.let(aiGameManager::resolveSeatPreset)
            } catch (error: IllegalArgumentException) {
                sender.sendError(
                    session,
                    ErrorCode.INVALID_ACTION,
                    error.message ?: "AI controller selection is unavailable",
                )
                return@withLock
            }

            if (current.momirBasic && resolved?.deckSpec != null) {
                sender.sendError(
                    session,
                    ErrorCode.INVALID_ACTION,
                    "The selected AI controller profile includes a deck preset and cannot be combined with Momir Basic",
                )
                return@withLock
            }

            resolved?.deckSpec?.let { deckSpec ->
                aiDeckValidationError(deckSpec, current.format)?.let { reason ->
                    sender.sendError(session, ErrorCode.INVALID_ACTION, "AI controller profile deck rejected: $reason")
                    return@withLock
                }
            }

            val controllerSpec = resolved?.controllerSpec
            val deckSpec = resolved?.deckSpec ?: AiDeckSpec.Auto
            if (current.aiControllerSpec == controllerSpec && current.aiDeckSpec == deckSpec) return@withLock

            // Commit both halves only after resolution and validation have succeeded.
            current.aiControllerSpec = controllerSpec
            current.aiDeckSpec = deckSpec
            applied = true
        }

        // A vs-AI Quick Game has one human seat. Reuse the canonical snapshot builder rather than
        // duplicating its deck-label/rules projection here.
        if (applied) {
            quickGameLobbyHandler.handleReconnect(session, playerSession.playerId, lobby.lobbyId)
        }
    }

    private fun aiDeckValidationError(spec: AiDeckSpec, format: DeckFormat?): String? = when (spec) {
        is AiDeckSpec.Fixed -> {
            if (spec.deckList.isEmpty()) {
                "The AI's deck is empty"
            } else {
                val result = if (format?.isCommanderShape == true) {
                    deckValidator.validate(
                        Deck(
                            cards = spec.deckList.flatMap { (name, count) -> List(count) { name } },
                            commander = spec.commander?.takeIf { it.isNotBlank() },
                        ),
                        format,
                    )
                } else {
                    deckValidator.validate(spec.deckList, format)
                }
                if (result.valid) null else result.errors.firstOrNull()?.message ?: "Deck is not legal"
            }
        }

        is AiDeckSpec.Sets -> {
            val unknown = spec.setCodes.filterNot { it in boosterGenerator.availableSets }
            if (unknown.isEmpty()) null
            else "Unknown set${if (unknown.size > 1) "s" else ""}: ${unknown.joinToString(", ")}"
        }

        is AiDeckSpec.Auto -> null
    }
}
