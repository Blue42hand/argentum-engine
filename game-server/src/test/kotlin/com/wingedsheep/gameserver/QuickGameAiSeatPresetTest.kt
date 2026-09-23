package com.wingedsheep.gameserver

import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.gameserver.ai.AiControllerContext
import com.wingedsheep.gameserver.ai.AiControllerProfile
import com.wingedsheep.gameserver.ai.AiControllerProvider
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.ServerMessage
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import kotlin.time.Duration.Companion.seconds

@Import(QuickGameAiSeatPresetTest.ProfileProviderConfig::class)
class QuickGameAiSeatPresetTest : GameServerTestBase() {

    init {
        test("quick-game profile applies its controller and deck preset as one validated seat update") {
            val client = createClient()
            client.connectAs("Preset Host")

            client.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, TEST_PROFILE),
                )
            )

            eventually(5.seconds) {
                val state = client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().lastOrNull()
                state?.vsAi shouldBe true
                state?.aiDeck?.label shouldBe "Bound profile deck"
            }

            // A provider-owned deck cannot be edited independently from its controller/profile.
            client.send(ClientMessage.SetQuickGameAiDeck(AiDeckSpec.Auto))
            eventually(5.seconds) {
                client.latestError()?.message?.contains("owns this seat's deck", ignoreCase = true) shouldBe true
            }
        }

        test("unknown explicit quick-game profile fails closed before the lobby is created") {
            val client = createClient()
            client.connectAs("Bad Preset Host")

            client.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, "missing"),
                )
            )

            eventually(5.seconds) {
                client.latestError()?.message?.contains("Unknown AI controller profile") shouldBe true
            }
            client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().isEmpty() shouldBe true
        }
    }

    @TestConfiguration
    class ProfileProviderConfig {
        @Bean
        fun quickGamePresetProvider(): AiControllerProvider = object : AiControllerProvider {
            override val mode: String = TEST_MODE
            override val profiles: List<AiControllerProfile> = listOf(
                AiControllerProfile(
                    id = TEST_PROFILE,
                    displayName = "Bound profile",
                    deckSpec = AiDeckSpec.Fixed(
                        deckList = mapOf("Plains" to 60),
                        label = "Bound profile deck",
                    ),
                )
            )

            override fun create(context: AiControllerContext): AiPlayerController =
                error("This test qualifies lobby preset application, not controller execution")
        }
    }

    companion object {
        private const val TEST_MODE = "quick-game-preset-test"
        private const val TEST_PROFILE = "bound"
    }
}
