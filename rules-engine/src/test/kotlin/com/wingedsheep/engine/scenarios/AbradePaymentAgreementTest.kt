package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.ManaSourcesSelectedResponse
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.hou.cards.Abrade
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A cast offered using choice-dependent mana must be able to reach that mana choice. */
class AbradePaymentAgreementTest : FunSpec({
    test("Abrade offered with floating red and Springleaf Drum does not fail at auto-payment") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, SpringleafDrum))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val artifact = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        game.giveMana(caster, Color.RED)

        val offered = game.legalActions(caster).filter {
            it.actionType == "CastSpellMode" && (it.action as? CastSpell)?.cardId == card
        }
        offered.size shouldBe 2
        offered.all { it.affordable } shouldBe true

        val target = ChosenTarget.Permanent(artifact)
        val action = (offered.first { (it.action as CastSpell).chosenModes == listOf(1) }.action as CastSpell)
            .copy(targets = listOf(target), modeTargetsOrdered = listOf(listOf(target)))
        val result = game.submit(action)
        result.isPaused shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        window.requiredCost shouldBe "{1}{R}"
        window.availableSources.none { it.entityId == drum } shouldBe true

        game.submit(ActivateAbility(
            caster, drum, SpringleafDrum.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
            manaColorChoice = Color.RED,
        )).error shouldBe null
        val refreshed = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(refreshed.id)).error shouldBe null
        (card in game.state.stack) shouldBe true
        game.state.getEntity(creature)!!.has<TappedComponent>() shouldBe true
    }

    test("declining the mana window cancels the cast without spending floating mana") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, SpringleafDrum))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val artifact = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        game.giveMana(caster, Color.RED)
        val target = ChosenTarget.Permanent(artifact)
        game.submit(CastSpell(
            caster, card, targets = listOf(target), chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(target))
        )).isPaused shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(window.id, declined = true)).error shouldBe null
        (card in game.state.getHand(caster)) shouldBe true
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.red shouldBe 1
        game.state.getEntity(drum)!!.has<TappedComponent>() shouldBe false
    }

    test("failed auto-pay in the mana window leaves the cast and pool unchanged") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, SpringleafDrum))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val artifact = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        game.giveMana(caster, Color.RED)
        val target = ChosenTarget.Permanent(artifact)
        game.submit(CastSpell(
            caster, card, targets = listOf(target), chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(target))
        )).isPaused shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(window.id, autoPay = true)).error shouldBe null
        (card in game.state.getHand(caster)) shouldBe true
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.red shouldBe 1
        game.state.getEntity(drum)!!.has<TappedComponent>() shouldBe false
    }
})
