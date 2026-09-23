package com.wingedsheep.gameserver.protocol

import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.lobby.AiDeckSpecView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Host-only update for one tournament/FFA AI seat's controller selection.
 *
 * [spec] is generic game-server configuration: mode selects a built-in or registered controller
 * provider and profileId, when present, remains opaque to Argentum. A null spec clears the explicit
 * selection and returns the seat to the server-wide controller fallback. If the selected provider
 * profile owns a deck preset, the server applies that deck together with the controller selection.
 */
@Serializable
@SerialName("setLobbyAiController")
data class SetLobbyAiController(
    val playerId: String,
    val spec: AiControllerSpec? = null,
) : ClientMessage

/**
 * Ask for the controllers this server can currently offer to an AI seat.
 *
 * Supplying [lobbyId] also asks for the authoritative current selection of every AI seat in that
 * lobby. The requester must already be seated there; this prevents using the catalog endpoint as a
 * lobby-state side channel. Omitting [lobbyId] is the pre-lobby path used by Quick Game.
 */
@Serializable
@SerialName("getAiControllerCatalog")
data class GetAiControllerCatalog(
    val lobbyId: String? = null,
) : ClientMessage

/**
 * One generic controller choice the server can currently satisfy.
 *
 * [spec] is the exact value a client sends back. External [AiControllerSpec.profileId] values are
 * opaque provider identifiers. [deck] is only a summary of a provider-associated preset: an exact
 * deck list is never exposed through the catalog.
 */
@Serializable
data class AiControllerOptionView(
    val spec: AiControllerSpec,
    val displayName: String,
    val description: String? = null,
    val deck: AiDeckSpecView? = null,
)

/** Current explicit selection for one AI seat. Null means the server-wide controller fallback. */
@Serializable
data class AiControllerSeatSelectionView(
    val playerId: String,
    val spec: AiControllerSpec? = null,
)

/**
 * Client-visible controller catalog plus, when requested, authoritative lobby seat selections.
 *
 * The catalog deliberately exposes no provider credentials, prompts, model policy, or deck list.
 * Availability is resolved server-side before an option is advertised, and an unavailable explicit
 * selection remains fail-closed in the ordinary seat mutation/start paths.
 */
@Serializable
@SerialName("aiControllerCatalog")
data class AiControllerCatalog(
    val options: List<AiControllerOptionView>,
    val lobbyId: String? = null,
    val seats: List<AiControllerSeatSelectionView> = emptyList(),
) : ServerMessage
