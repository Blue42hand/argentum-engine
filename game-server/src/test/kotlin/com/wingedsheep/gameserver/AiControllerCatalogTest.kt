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
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import kotlin.time.Duration.Companion.seconds

@Import(AiControllerCatalogTest.ProfileProviderConfig::class)
class AiControllerCatalogTest : GameServerTestBase() {

    init {
        test("catalog advertises opaque provider profiles without exposing their bound deck list") {
            val client = createClient()
            client.connectAs("Catalog Host")

            client.send(GetAiControllerCatalog())

            eventually(5.seconds) {
                val catalog = client.messages.filterIsInstance<AiControllerCatalog>().lastOrNull()
                val option = catalog?.options?.firstOrNull {
                    it.spec == AiControllerSpec(TEST_MODE, TEST_PROFILE)
                }
                option?.displayName shouldBe "Catalog profile"
                option?.description shouldBe "Opaque external controller profile"
                option?.deck?.kind shouldBe "deck"
                option?.deck?.label shouldBe "Catalog bound deck"
                option?.deck?.cardCount shouldBe 60
            }
        }

        test("catalog returns the authoritative current quick-game AI seat selection") {
            val client = createClient()
            client.connectAs("Selection Host")
            client.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, TEST_PROFILE),
                )
            )

            val lobbyId = eventually(5.seconds) {
                client.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>()
                    .lastOrNull()?.lobbyId
                    .also { (it != null) shouldBe true }
                    ?: error("Quick-game lobby state not received")
            }

            client.send(GetAiControllerCatalog(lobbyId))

            eventually(5.seconds) {
                val catalog = client.messages.filterIsInstance<AiControllerCatalog>()
                    .lastOrNull { it.lobbyId == lobbyId }
                catalog?.seats?.size shouldBe 1
                catalog?.seats?.single()?.spec shouldBe AiControllerSpec(TEST_MODE, TEST_PROFILE)
                catalog?.seats?.single()?.playerId?.startsWith("ai-pending-") shouldBe true
            }
        }

        test("catalog does not expose another lobby's controller selections") {
            val host = createClient()
            host.connectAs("Private Catalog Host")
            host.send(
                ClientMessage.CreateQuickGameLobby(
                    vsAi = true,
                    aiControllerSpec = AiControllerSpec(TEST_MODE, TEST_PROFILE),
                )
            )
            val lobbyId = eventually(5.seconds) {
                host.messages.filterIsInstance<ServerMessage.QuickGameLobbyState>()
                    .lastOrNull()?.lobbyId
                    .also { (it != null) shouldBe true }
                    ?: error("Quick-game lobby state not received")
            }

            val outsider = createClient()
            outsider.connectAs("Catalog Outsider")
            outsider.send(GetAiControllerCatalog(lobbyId))

            eventually(5.seconds) {
                outsider.latestError()?.message shouldBe "Lobby not found or you are not seated in it"
            }
            outsider.messages.filterIsInstance<AiControllerCatalog>()
                .none { it.lobbyId == lobbyId } shouldBe true
        }
    }

    @TestConfiguration
    class ProfileProviderConfig {
        @Bean
        fun catalogProfileProvider(): AiControllerProvider = object : AiControllerProvider {
            override val mode: String = TEST_MODE
            override val profiles: List<AiControllerProfile> = listOf(
                AiControllerProfile(
                    id = TEST_PROFILE,
                    displayName = "Catalog profile",
                    description = "Opaque external controller profile",
                    deckSpec = AiDeckSpec.Fixed(
                        deckList = mapOf("Plains" to 60),
                        label = "Catalog bound deck",
                    ),
                )
            )

            override fun create(context: AiControllerContext): AiPlayerController =
                error("This test qualifies catalog/selection projection, not controller execution")
        }
    }

    companion object {
        private const val TEST_MODE = "catalog-provider-test"
        private const val TEST_PROFILE = "binding/opaque:v1"
    }
}
