package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.CantSearchLibrariesComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.rav.cards.ClingingDarkness
import com.wingedsheep.mtg.sets.definitions.rav.cards.ShadowOfDoubt
import com.wingedsheep.mtg.sets.definitions.rav.cards.ThreeDreams
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Shadow of Doubt — {U/B}{U/B} Instant (Ravnica: City of Guilds #253)
 *
 * "Players can't search libraries this turn. Draw a card."
 *
 * Three Dreams is the probe search: a gather marked `search = true`. Under Shadow of Doubt the
 * search finds nothing even with Auras in the library; the restriction ends with the turn.
 */
class ShadowOfDoubtScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + ShadowOfDoubt + ThreeDreams + ClingingDarkness)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.castShadow() {
        val shadow = putCardInHand(player1, "Shadow of Doubt")
        giveMana(player1, Color.BLUE, 1)
        giveMana(player1, Color.BLACK, 1)
        castSpell(player1, shadow).error shouldBe null
        bothPass()
    }

    fun GameTestDriver.castThreeDreams(playerId: com.wingedsheep.sdk.model.EntityId) {
        val dreams = putCardInHand(playerId, "Three Dreams")
        giveMana(playerId, Color.WHITE, 1)
        giveColorlessMana(playerId, 4)
        castSpell(playerId, dreams).error shouldBe null
        bothPass()
    }

    test("draws a card, and a search this turn finds nothing") {
        val d = driver()
        val aura = d.putCardOnTopOfLibrary(d.player1, "Clinging Darkness")
        val handBefore = d.getHandSize(d.player1)

        d.castShadow()
        withClue("Shadow of Doubt draws a card — the Aura that was on top") {
            d.getHandSize(d.player1) shouldBe handBefore + 1
        }
        // Put a fresh Aura in the library for the probe search.
        val aura2 = d.putCardOnTopOfLibrary(d.player1, "Clinging Darkness")
        (aura in d.state.getHand(d.player1)) shouldBe true

        val handBeforeSearch = d.getHandSize(d.player1)
        d.castThreeDreams(d.player1)

        withClue("no search choice is offered — the search can't find anything") {
            d.state.pendingDecision shouldBe null
        }
        withClue("the Aura stays in the library") {
            (aura2 in d.state.getLibrary(d.player1)) shouldBe true
            d.getHandSize(d.player1) shouldBe handBeforeSearch
        }
    }

    test("the restriction applies to every player") {
        val d = driver()
        d.castShadow()
        d.state.getEntity(d.player1)?.get<CantSearchLibrariesComponent>() shouldNotBe null
        d.state.getEntity(d.player2)?.get<CantSearchLibrariesComponent>() shouldNotBe null
    }

    test("searching works again on the next turn") {
        val d = driver()
        d.castShadow()

        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        withClue("it is now the opponent's turn") {
            d.state.activePlayerId shouldBe d.player2
        }
        d.state.getEntity(d.player2)?.get<CantSearchLibrariesComponent>() shouldBe null

        val aura = d.putCardOnTopOfLibrary(d.player2, "Clinging Darkness")
        d.castThreeDreams(d.player2)
        d.submitCardSelection(d.player2, listOf(aura)).error shouldBe null
        withClue("the Aura was found and put into hand") {
            d.state.getHand(d.player2).mapNotNull { d.state.getEntity(it)?.get<CardComponent>()?.name }
                .contains("Clinging Darkness") shouldBe true
        }
    }
})
