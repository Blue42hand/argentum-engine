package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mbs.cards.BlackSunsZenith
import com.wingedsheep.mtg.sets.definitions.ori.cards.HangarbackWalker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class HangarbackWalkerScenarioTest : FunSpec({
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(HangarbackWalker, BlackSunsZenith))
        it.initMirrorMatch(Deck.of("Swamp" to 40), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun counters(game: GameTestDriver, walker: EntityId) =
        game.state.getEntity(walker)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun thopters(game: GameTestDriver, player: EntityId) =
        game.state.getZone(player, Zone.BATTLEFIELD).filter { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.typeLine?.subtypes
                ?.any { it.value == "Thopter" } == true
        }

    test("two equal X symbols cost four mana and enter with two counters") {
        val game = driver()
        val you = game.player1
        val walker = game.putCardInHand(you, "Hangarback Walker")
        game.giveColorlessMana(you, 4)

        game.submit(CastSpell(you, walker, xValue = 2)).error shouldBe null
        game.bothPass()

        counters(game, walker) shouldBe 2
        game.findPermanent(you, "Hangarback Walker") shouldBe walker
    }

    test("two equal X symbols cannot be cast for three mana") {
        val game = driver()
        val you = game.player1
        val walker = game.putCardInHand(you, "Hangarback Walker")
        game.giveColorlessMana(you, 3)

        game.submit(CastSpell(you, walker, xValue = 2)).error shouldNotBe null
        game.findPermanent(you, "Hangarback Walker") shouldBe null
    }

    test("dies trigger creates one flying artifact Thopter per last-known plus-one counter") {
        val game = driver()
        val you = game.player1
        val walker = game.putCardInHand(you, "Hangarback Walker")
        game.giveColorlessMana(you, 4)
        game.submit(CastSpell(you, walker, xValue = 2)).error shouldBe null
        game.bothPass()

        val murder = game.putCardInHand(you, "Murder")
        game.giveMana(you, Color.BLACK, 3)
        game.castSpell(you, murder, listOf(walker)).error shouldBe null
        game.bothPass() // Murder resolves; the death trigger goes on the stack.
        game.bothPass() // The death trigger creates tokens using last-known counters.

        game.findPermanent(you, "Hangarback Walker") shouldBe null
        val tokens = thopters(game, you)
        tokens.size shouldBe 2
        tokens.forEach { token ->
            val card = game.state.getEntity(token)!!.get<CardComponent>()!!
            card.typeLine.isArtifact shouldBe true
            game.state.projectedState.hasKeyword(token, Keyword.FLYING) shouldBe true
            game.state.projectedState.getPower(token) shouldBe 1
            game.state.projectedState.getToughness(token) shouldBe 1
            game.state.projectedState.getColors(token) shouldBe emptySet()
        }
    }

    test("tap ability adds a counter after summoning sickness wears off") {
        val game = driver()
        val you = game.player1
        val walker = game.putCardInHand(you, "Hangarback Walker")
        game.giveColorlessMana(you, 2)
        game.submit(CastSpell(you, walker, xValue = 1)).error shouldBe null
        game.bothPass()
        counters(game, walker) shouldBe 1

        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)

        game.giveColorlessMana(you, 1)
        game.submit(ActivateAbility(you, walker, HangarbackWalker.activatedAbilities.single().id)).error shouldBe null
        game.bothPass()
        counters(game, walker) shouldBe 2
    }

    test("simultaneous minus-one counters preserve the last-known plus-one count for Thopters") {
        val game = driver()
        val you = game.player1
        val walker = game.putCardInHand(you, "Hangarback Walker")
        game.giveColorlessMana(you, 6)
        game.submit(CastSpell(you, walker, xValue = 3)).error shouldBe null
        game.bothPass()
        counters(game, walker) shouldBe 3

        val zenith = game.putCardInHand(you, "Black Sun's Zenith")
        game.giveMana(you, Color.BLACK, 6)
        game.submit(CastSpell(you, zenith, xValue = 4)).error shouldBe null
        game.bothPass() // Four -1/-1 counters are placed; state-based actions destroy Walker.
        game.bothPass() // Resolve Walker's death trigger.

        game.findPermanent(you, "Hangarback Walker") shouldBe null
        thopters(game, you).size shouldBe 3
    }
})
