package com.wingedsheep.gameserver.protocol

import com.wingedsheep.gameserver.ai.AiControllerSpec
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
