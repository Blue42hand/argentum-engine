package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.mtg.sets.definitions.scg.cards.CarrionFeeder
import com.wingedsheep.mtg.sets.definitions.big.cards.FomoriVault
import com.wingedsheep.mtg.sets.definitions.eld.cards.ThrillOfPossibility
import com.wingedsheep.mtg.sets.definitions.m21.cards.VillageRites
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

/** Native action IDs must carry the payer's selections into the existing cost validators. */
class NativeCostChoiceActionTest : FunSpec({
    fun game(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(SpringleafDrum, CarrionFeeder, FomoriVault,
            ThrillOfPossibility, VillageRites))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("Springleaf Drum uses a chosen tap-cost creature, not a spell target") {
        val driver = game()
        val player = driver.activePlayer!!
        val drum = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        val creature = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == drum && legal.isManaAbility
        }.action

        val misrouted = ActionParameterizer.apply(offered, ActionParams(targets = listOf(creature)), driver.state)
        (driver.submit(misrouted).error != null) shouldBe true
        driver.state.getEntity(creature)!!.has<TappedComponent>() shouldBe false

        val completed = ActionParameterizer.apply(
            offered, ActionParams(tappedPermanents = listOf(creature)), driver.state
        )
        driver.submit(completed).error shouldBe null
        driver.state.getEntity(creature)!!.has<TappedComponent>() shouldBe true
    }

    test("offered Drum action rejects an unrelated discard choice") {
        val driver = game()
        val player = driver.activePlayer!!
        val drum = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val handCard = driver.putCardInHand(player, "Plains")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == drum && legal.isManaAbility
        }
        ("discardedCards" in ActionParameterizer.spec(offered).allowedFields) shouldBe false
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(offered, ActionParams(discardedCards = listOf(handCard)), driver.state)
        }
        (handCard in driver.state.getHand(player)) shouldBe true
    }

    test("a one-creature tap cost rejects extra and repeated selections") {
        val driver = game()
        val player = driver.activePlayer!!
        val drum = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        val first = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val second = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == drum && legal.isManaAbility
        }
        for (ids in listOf(listOf(first, second), listOf(first, first))) {
            val completed = ActionParameterizer.apply(offered, ActionParams(tappedPermanents = ids), driver.state)
            (driver.submit(completed).error != null) shouldBe true
            driver.state.getEntity(first)!!.has<TappedComponent>() shouldBe false
            driver.state.getEntity(second)!!.has<TappedComponent>() shouldBe false
        }
    }

    test("a one-creature spell sacrifice rejects overpayment") {
        val driver = game()
        val player = driver.activePlayer!!
        val rites = driver.putCardInHand(player, "Village Rites")
        val first = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val second = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.giveMana(player, Color.BLACK)
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? CastSpell)?.cardId == rites
        }
        val completed = ActionParameterizer.apply(
            offered, ActionParams(sacrificedPermanents = listOf(first, second)), driver.state
        )
        (driver.submit(completed).error != null) shouldBe true
        (first in driver.state.getBattlefield()) shouldBe true
        (second in driver.state.getBattlefield()) shouldBe true
    }

    test("native sacrifice selection pays an activated ability") {
        val driver = game()
        val player = driver.activePlayer!!
        val feeder = driver.putCreatureOnBattlefield(player, "Carrion Feeder")
        val fodder = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == feeder
        }

        val completed = ActionParameterizer.apply(
            offered, ActionParams(sacrificedPermanents = listOf(fodder)), driver.state
        )
        driver.submit(completed).error shouldBe null
        (fodder in driver.state.getBattlefield()) shouldBe false
    }

    test("native discard selection pays an activated ability") {
        val driver = game()
        val player = driver.activePlayer!!
        val vault = driver.putPermanentOnBattlefield(player, "Fomori Vault")
        val fodder = driver.putCardInHand(player, "Plains")
        driver.giveMana(player, Color.RED, 3)
        val offered = driver.legalActions(player).first { legal ->
            val action = legal.action as? ActivateAbility
            action?.sourceId == vault && action.abilityId == FomoriVault.activatedAbilities[1].id
        }

        val completed = ActionParameterizer.apply(
            offered, ActionParams(discardedCards = listOf(fodder)), driver.state
        )
        driver.submit(completed).error shouldBe null
        (fodder in driver.state.getHand(player)) shouldBe false
    }

    test("native discard and sacrifice selections also pay spell costs") {
        val discardGame = game()
        val caster = discardGame.activePlayer!!
        val thrill = discardGame.putCardInHand(caster, "Thrill of Possibility")
        val cardToDiscard = discardGame.putCardInHand(caster, "Plains")
        discardGame.giveMana(caster, Color.RED)
        discardGame.giveMana(caster, Color.GREEN)
        val discardAction = discardGame.legalActions(caster).first { legal ->
            (legal.action as? CastSpell)?.cardId == thrill
        }
        discardGame.submit(ActionParameterizer.apply(
            discardAction, ActionParams(discardedCards = listOf(cardToDiscard)), discardGame.state
        )).error shouldBe null
        (cardToDiscard in discardGame.state.getHand(caster)) shouldBe false

        val sacrificeGame = game()
        val ritesCaster = sacrificeGame.activePlayer!!
        val rites = sacrificeGame.putCardInHand(ritesCaster, "Village Rites")
        val creature = sacrificeGame.putCreatureOnBattlefield(ritesCaster, "Grizzly Bears")
        sacrificeGame.giveMana(ritesCaster, Color.BLACK)
        val sacrificeAction = sacrificeGame.legalActions(ritesCaster).first { legal ->
            (legal.action as? CastSpell)?.cardId == rites
        }
        sacrificeGame.submit(ActionParameterizer.apply(
            sacrificeAction, ActionParams(sacrificedPermanents = listOf(creature)), sacrificeGame.state
        )).error shouldBe null
        (creature in sacrificeGame.state.getBattlefield()) shouldBe false
    }
})
