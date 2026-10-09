package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.GameRng
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

class DiabolicIntentDecisionContractTest : ScenarioTestBase() {
    init {
        test("enrichment preserves native noncreature options and opponents receive only status") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Diabolic Intent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInLibrary(1, "Sol Ring")
                .withCardInLibrary(1, "Dark Ritual")
                .withCardInLibrary(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.state = game.state.copy(rng = GameRng.seeded(42))
            game.castSpellWithAdditionalSacrifice(1, "Diabolic Intent", "Grizzly Bears").error.shouldBeNull()
            game.resolveStack()
            val native = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val session = GameSession(cardRegistry = cardRegistry)
            val players = listOf(game.player1Id, game.player2Id).associateWith { player ->
                val socket = mockk<WebSocketSession>(relaxed = true) {
                    every { id } returns player.value
                }
                PlayerSession(socket, player, player.value)
            }
            session.injectStateForTesting(game.state, players)
            val chooser = session.createStateUpdate(game.player1Id, emptyList())
                .shouldBeInstanceOf<ServerMessage.StateUpdate>()
            val enriched = chooser.pendingDecision
                .shouldBeInstanceOf<SelectCardsDecision>()
            enriched.options shouldBe native.options
            enriched.minSelections shouldBe 1
            enriched.maxSelections shouldBe 1
            enriched.cardInfo.shouldNotBeNull().values.map { it.name }.toSet() shouldBe
                setOf("Sol Ring", "Dark Ritual", "Swamp")
            enriched.cardInfo.shouldNotBeNull().values.forEach { it.imageUri.shouldNotBeNull() }
            val opponent = session.createStateUpdate(game.player2Id, emptyList())
                .shouldBeInstanceOf<ServerMessage.StateUpdate>()
            opponent.pendingDecision.shouldBeNull()
            native.options.none { it in opponent.state.cards } shouldBe true
            opponent.state.cards.values.none { it.name == "Sol Ring" || it.name == "Dark Ritual" } shouldBe true
            val status = opponent.opponentDecisionStatus.shouldNotBeNull()
            status.displayText shouldBe "Selecting cards"
            status.sourceName shouldBe "Diabolic Intent"
        }
    }
}
