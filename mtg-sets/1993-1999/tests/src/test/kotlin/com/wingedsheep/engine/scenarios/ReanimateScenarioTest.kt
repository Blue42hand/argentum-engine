package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmp.cards.Reanimate
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ReanimateScenarioTest : ScenarioTestBase() {
    init {
        cardRegistry.register(card("Reanimate X Creature") {
            manaCost = "{X}{G}"
            colorIdentity = "G"
            typeLine = "Creature — Hydra"
            power = 1
            toughness = 1
        })

        test("returns an opponent's creature under your control and loses its graveyard mana value") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInGraveyard(2, "Centaur Courser")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val courser = game.state.getGraveyard(game.player2Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Centaur Courser"
            }
            val cast = game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, courser)),
            ))
            withClue("Reanimate should target the opponent's graveyard: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            game.isOnBattlefield("Centaur Courser") shouldBe true
            game.state.getEntity(game.findPermanent("Centaur Courser")!!)
                ?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
            game.getLifeTotal(1) shouldBe 17
            game.state.getGraveyard(game.player2Id).contains(courser) shouldBe false
        }

        test("X in the targeted graveyard card's mana cost counts as zero") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInGraveyard(1, "Reanimate X Creature")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val creature = game.state.getGraveyard(game.player1Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Reanimate X Creature"
            }
            game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, creature)),
            )).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Reanimate X Creature") shouldBe true
            game.getLifeTotal(1) shouldBe 19 // {X}{G} has mana value 1 outside the stack.
        }

        test("you lose life before triggered enters abilities resolve") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Soul Warden")
                .withCardInGraveyard(2, "Centaur Courser")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val courser = game.state.getGraveyard(game.player2Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Centaur Courser"
            }
            game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, courser)),
            )).error shouldBe null

            game.passPriority()
            game.passPriority() // Reanimate resolves; Soul Warden's trigger remains on the stack.
            game.getLifeTotal(1) shouldBe 17
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 18
        }
    }
}

/**
 * Reanimate (TMP #151) — {B} Sorcery.
 *
 *   "Put target creature card from a graveyard onto the battlefield under your control. You lose
 *    life equal to that card's mana value."
 *
 * Proves the two things a plain `Move` gets wrong: a card from an *opponent's* graveyard enters
 * under the caster's control (not its owner's), and the caster loses life equal to the card's mana
 * value.
 */
class ReanimateUpstreamScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + Reanimate)
        initMirrorMatch(deck = Deck.of("Swamp" to 60), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("reanimates an opponent's creature under your control and you lose its mana value") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val force = d.putCardInGraveyard(opp, "Force of Nature") // {3}{G}{G}, mana value 5

        val spell = d.putCardInHand(me, "Reanimate")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpellWithTargets(me, spell, listOf(ChosenTarget.Card(force, opp, Zone.GRAVEYARD))).error shouldBe null
        while (!d.isPaused && d.state.stack.isNotEmpty()) d.bothPass()

        d.getPermanents(me) shouldContain force
        d.state.projectedState.getController(force) shouldBe me
        d.getLifeTotal(me) shouldBe 15
        d.getLifeTotal(opp) shouldBe 20
    }

    test("reanimates from your own graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val courser = d.putCardInGraveyard(me, "Centaur Courser") // {2}{G}, mana value 3

        val spell = d.putCardInHand(me, "Reanimate")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpellWithTargets(me, spell, listOf(ChosenTarget.Card(courser, me, Zone.GRAVEYARD))).error shouldBe null
        while (!d.isPaused && d.state.stack.isNotEmpty()) d.bothPass()

        d.getPermanents(me) shouldContain courser
        d.getLifeTotal(me) shouldBe 17
    }
})
