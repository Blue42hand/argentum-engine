package com.wingedsheep.gameserver.handler

import com.wingedsheep.gameserver.ai.AiControllerProvider
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.ai.AiGameManager
import com.wingedsheep.gameserver.lobby.AiDeckSpecView
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import com.wingedsheep.gameserver.protocol.AiControllerCatalog
import com.wingedsheep.gameserver.protocol.AiControllerOptionView
import com.wingedsheep.gameserver.protocol.AiControllerSeatSelectionView
import com.wingedsheep.gameserver.protocol.ErrorCode
import com.wingedsheep.gameserver.protocol.GetAiControllerCatalog
import com.wingedsheep.gameserver.repository.LobbyRepository
import com.wingedsheep.gameserver.session.SessionRegistry
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * Read-only client projection of Argentum's generic per-seat AI controller seam.
 *
 * Providers remain authoritative for opaque profile IDs and optional deck presets. This handler
 * exposes only selections that [AiGameManager] says are usable right now, and only a summary of an
 * associated deck. Lobby selection state is returned only to a player already seated in that lobby.
 */
@Component
class AiControllerCatalogHandler(
    private val aiGameManager: AiGameManager,
    private val quickGameLobbyRepository: QuickGameLobbyRepository,
    private val lobbyRepository: LobbyRepository,
    private val sessionRegistry: SessionRegistry,
    private val sender: MessageSender,
    controllerProviders: List<AiControllerProvider> = emptyList(),
) {
    private val controllerProviders = controllerProviders.sortedBy { it.mode.trim().lowercase() }

    fun handle(session: WebSocketSession, message: GetAiControllerCatalog) {
        val requester = sessionRegistry.getPlayerSession(session.id) ?: run {
            sender.sendError(session, ErrorCode.NOT_CONNECTED, "Not connected")
            return
        }

        val seats = message.lobbyId?.let { lobbyId ->
            currentSelections(requester.playerId, lobbyId) ?: run {
                sender.sendError(session, ErrorCode.GAME_NOT_FOUND, "Lobby not found or you are not seated in it")
                return
            }
        }.orEmpty()

        sender.send(
            session,
            AiControllerCatalog(
                options = availableOptions(),
                lobbyId = message.lobbyId,
                seats = seats,
            )
        )
    }

    private fun availableOptions(): List<AiControllerOptionView> = buildList {
        addIfAvailable(
            spec = AiControllerSpec("engine"),
            displayName = "Engine",
            description = "Built-in rules-engine controller",
        )
        addIfAvailable(
            spec = AiControllerSpec("llm"),
            displayName = "LLM",
            description = "Built-in configured LLM controller",
        )

        for (provider in controllerProviders) {
            addIfAvailable(
                spec = AiControllerSpec(provider.mode),
                displayName = "${provider.mode} (default)",
                description = "Provider default",
            )
            for (profile in provider.profiles) {
                addIfAvailable(
                    spec = AiControllerSpec(provider.mode, profile.id),
                    displayName = profile.displayName,
                    description = profile.description,
                )
            }
        }
    }

    private fun MutableList<AiControllerOptionView>.addIfAvailable(
        spec: AiControllerSpec,
        displayName: String,
        description: String?,
    ) {
        val resolved = try {
            aiGameManager.resolveSeatPreset(spec)
        } catch (_: IllegalArgumentException) {
            return
        }
        add(
            AiControllerOptionView(
                spec = resolved.controllerSpec,
                displayName = displayName,
                description = description,
                deck = resolved.deckSpec?.let(AiDeckSpecView::of),
            )
        )
    }

    private fun currentSelections(
        requesterId: com.wingedsheep.sdk.model.EntityId,
        lobbyId: String,
    ): List<AiControllerSeatSelectionView>? {
        quickGameLobbyRepository.findById(lobbyId)?.let { lobby ->
            if (lobby.findPlayer(requesterId) == null) return null
            return lobby.players
                .asSequence()
                .filter { it.isAi }
                .map { aiSeat ->
                    AiControllerSeatSelectionView(
                        playerId = aiSeat.playerId.value,
                        spec = lobby.aiControllerSpec,
                    )
                }
                .toList()
        }

        val lobby = lobbyRepository.findLobbyById(lobbyId) ?: return null
        if (!lobby.players.containsKey(requesterId)) return null
        return lobby.players
            .asSequence()
            .filter { (playerId, _) -> aiGameManager.isAiPlayer(playerId) }
            .map { (playerId, state) ->
                AiControllerSeatSelectionView(
                    playerId = playerId.value,
                    spec = state.identity.aiControllerSpec,
                )
            }
            .toList()
    }
}
