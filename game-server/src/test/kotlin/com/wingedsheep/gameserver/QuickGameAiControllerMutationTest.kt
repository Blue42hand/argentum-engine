package com.wingedsheep.gameserver

import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.gameserver.ai.AiControllerContext
import com.wingedsheep.gameserver.ai.AiControllerProfile
import com.wingedsheep.gameserver.ai.AiControllerProvider
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.protocol.AiControllerCatalog
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.GetAiControllerCatalog
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.protocol.SetQuickGameAiController
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import kotlin.time.Duration.Companion.seconds

@Import(QuickGameAiControllerMutationTest.ProfileProviderConfig::class)
class QuickGameAiControllerMutationTest : GameServerTestBase() {

    init {
        test("quick-game host can atomically replace controller profile and provider-owned deck") {
            val client = createClient()
            client.connectAs("Controller Host")

            client.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, PROFILE_A),
                )
            )

            val lobbyId = eventually(5.seconds) {
                val state = client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().lastOrNull()
                state?.aiDeck?.label shouldBe "Profile A deck"
                checkNotNull(state).lobbyId
            }

            client.send(SetQuickGameAiController(AiControllerSpec(TEST_MODE, PROFILE_B)))

            eventually(5.seconds) {
                val state = client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().last()
                state.aiDeck?.label shouldBe "Profile B deck"
            }

            client.send(GetAiControllerCatalog(lobbyId))
            eventually(5.seconds) {
                val catalog = client.messages.filterIsInstance<AiControllerCatalog>().last()
                catalog.seats.single().spec shouldBe AiControllerSpec(TEST_MODE, PROFILE_B)
            }
        }

        test("unavailable quick-game controller selection fails closed without changing the seat") {
            val client = createClient()
            client.connectAs("Fail Closed Host")

            client.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, PROFILE_A),
                )
            )

            val lobbyId = eventually(5.seconds) {
                checkNotNull(client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().lastOrNull()).lobbyId
            }

            client.send(SetQuickGameAiController(AiControllerSpec(TEST_MODE, "missing")))
            eventually(5.seconds) {
                client.latestError()?.message?.contains("Unknown AI controller profile") shouldBe true
            }

            client.send(GetAiControllerCatalog(lobbyId))
            eventually(5.seconds) {
                val catalog = client.messages.filterIsInstance<AiControllerCatalog>().last()
                catalog.seats.single().spec shouldBe AiControllerSpec(TEST_MODE, PROFILE_A)
            }
            client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>().last().aiDeck?.label shouldBe "Profile A deck"
        }
    }

    @TestConfiguration
    class ProfileProviderConfig {
        @Bean
        fun quickGameMutationProvider(): AiControllerProvider = object : AiControllerProvider {
            override val mode: String = TEST_MODE
            override val profiles: List<AiControllerProfile> = listOf(
                AiControllerProfile(
                    id = PROFILE_A,
                    displayName = "Profile A",
                    deckSpec = AiDeckSpec.Fixed(
                        deckList = mapOf("Plains" to 60),
                        label = "Profile A deck",
                    ),
                ),
                AiControllerProfile(
                    id = PROFILE_B,
                    displayName = "Profile B",
                    deckSpec = AiDeckSpec.Fixed(
                        deckList = mapOf("Island" to 60),
                        label = "Profile B deck",
                    ),
                ),
            )

            override fun create(context: AiControllerContext): AiPlayerController =
                error("This test qualifies lobby selection, not controller execution")
        }
    }

    companion object {
        private const val TEST_MODE = "quick-game-controller-mutation-test"
        private const val PROFILE_A = "alpha"
        private const val PROFILE_B = "beta"
    }
}
