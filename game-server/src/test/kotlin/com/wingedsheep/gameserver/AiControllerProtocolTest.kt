package com.wingedsheep.gameserver

import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.SetLobbyAiController
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AiControllerProtocolTest : FunSpec({
    val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
    }

    test("lobby AI controller message round-trips an opaque per-seat profile") {
        val message: ClientMessage = SetLobbyAiController(
            playerId = "ai-seat-2",
            spec = AiControllerSpec(mode = "external-provider", profileId = "profile/opaque:v3"),
        )

        val encoded = json.encodeToString(ClientMessage.serializer(), message)
        val decoded = json.decodeFromString(ClientMessage.serializer(), encoded)

        decoded shouldBe message
    }

    test("lobby AI controller message round-trips clearing an explicit selection") {
        val message: ClientMessage = SetLobbyAiController(
            playerId = "ai-seat-2",
            spec = null,
        )

        val encoded = json.encodeToString(ClientMessage.serializer(), message)
        val decoded = json.decodeFromString(ClientMessage.serializer(), encoded)

        decoded shouldBe message
    }
})
