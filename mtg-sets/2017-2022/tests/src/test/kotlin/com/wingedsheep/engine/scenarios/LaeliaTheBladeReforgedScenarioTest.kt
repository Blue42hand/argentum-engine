package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.mtg.sets.definitions.c21.cards.LaeliaTheBladeReforged
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.matchers.collections.shouldContain
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Laelia, the Blade Reforged (C21 #53) — {2}{R} 2/2 haste.
 *
 *   Whenever Laelia attacks, exile the top card of your library. You may play that card this turn.
 *   Whenever one or more cards are put into exile from your library and/or your graveyard, put a
 *   +1/+1 counter on Laelia.
 *
 * Proves the impulse grant, that her own impulse exile feeds the counter trigger, that the counter
 * trigger is a once-per-batch trigger (ruling 2024-06-07), and that it ignores the opponent's zones.
 */
class LaeliaTheBladeReforgedScenarioTest : ScenarioTestBase() {

    private fun laeliaCounters(game: TestGame): Int {
        val id = game.findPermanent("Laelia, the Blade Reforged")!!
        return game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Laelia, the Blade Reforged") {
            test("attacking exiles your top card, lets you play it, and grows Laelia") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Laelia, the Blade Reforged", summoningSickness = false)
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Laelia, the Blade Reforged" to 2)).error shouldBe null
                game.resolveStack()

                val exiled = game.state.getExile(game.player1Id)
                withClue("the top card of Player1's library is exiled") {
                    exiled.mapNotNull { game.state.getEntity(it)?.get<CardComponent>()?.name } shouldBe listOf("Grizzly Bears")
                }
                withClue("Player1 may play the exiled card this turn") {
                    game.state.mayPlayPermissions.any {
                        it.controllerId == game.player1Id && exiled.first() in it.cardIds
                    } shouldBe true
                }
                withClue("library→exile triggers Laelia's counter ability") {
                    laeliaCounters(game) shouldBe 1
                }
            }

            test("exiling several graveyard cards at once adds exactly one counter") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Laelia, the Blade Reforged", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInHand(1, "Rest in Peace")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Rest in Peace").error shouldBe null
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                withClue("one batch of exiles is one trigger (CR 603.2c)") {
                    laeliaCounters(game) shouldBe 1
                }
            }

            test("exiling several library cards in one batch adds exactly one counter") {
                val exileTwo = card("Laelia Test Exile Two") {
                    manaCost = "{0}"
                    typeLine = "Sorcery"
                    spell { effect = Patterns.Exile.impulse(count = 2) }
                }
                val driver = GameTestDriver().apply {
                    registerCards(TestCards.all + listOf(LaeliaTheBladeReforged, exileTwo))
                    initMirrorMatch(deck = Deck.of("Mountain" to 40), startingLife = 20)
                    passPriorityUntil(Step.PRECOMBAT_MAIN)
                }
                val player = driver.activePlayer!!
                val laelia = driver.putCreatureOnBattlefield(player, "Laelia, the Blade Reforged")
                val first = driver.putCardOnTopOfLibrary(player, "Grizzly Bears")
                val second = driver.putCardOnTopOfLibrary(player, "Hill Giant")
                val spell = driver.putCardInHand(player, "Laelia Test Exile Two")

                driver.castSpell(player, spell).error shouldBe null
                repeat(40) {
                    when {
                        driver.state.pendingDecision != null -> driver.autoResolveDecision()
                        driver.state.stack.isNotEmpty() -> driver.bothPass()
                        else -> return@repeat
                    }
                }
                driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain first
                driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain second
                driver.state.getEntity(laelia)?.get<CountersComponent>()
                    ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            }

            test("exiling a card from the opponent's graveyard does not trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Laelia, the Blade Reforged", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInHand(1, "Coffin Purge")
                    .withCardInGraveyard(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingGraveyardCard(1, "Coffin Purge", 2, "Grizzly Bears").error shouldBe null
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                withClue("only your own library and graveyard count") {
                    laeliaCounters(game) shouldBe 0
                }
            }
        }
    }
}
