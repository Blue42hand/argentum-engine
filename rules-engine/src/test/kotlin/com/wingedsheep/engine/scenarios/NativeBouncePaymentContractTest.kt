package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.mtg.sets.definitions.scg.cards.WirewoodSymbiote
import com.wingedsheep.mtg.sets.definitions.vis.cards.QuirionRanger
import com.wingedsheep.mtg.sets.definitions.chk.cards.UyoSilentProphet
import com.wingedsheep.mtg.sets.definitions.lrw.cards.FamiliarsRuse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Mechanic contract tests across printed bounce costs; no alternate action or payment is inferred. */
class NativeBouncePaymentContractTest : FunSpec({
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(WirewoodSymbiote, QuirionRanger, UyoSilentProphet, FamiliarsRuse))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun info(d: GameTestDriver, action: com.wingedsheep.engine.legalactions.LegalAction) =
        LegalActionEnricher(ManaSolver(d.cardRegistry, PredicateEvaluator(d.cardRegistry)), d.cardRegistry)
            .enrich(listOf(action), d.state, d.activePlayer!!).single()

    test("Wirewood native params separately carry return cost and real untap target through serialization") {
        val d = game()
        val p = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(p, "Wirewood Symbiote")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val target = d.putCreatureOnBattlefield(d.getOpponent(p), "Grizzly Bears")
        d.tapPermanent(target)
        val offer = d.legalActions(p).first { (it.action as? ActivateAbility)?.sourceId == source }
        offer.additionalCostInfo!!.validBounceTargets shouldBe listOf(elf)
        offer.additionalCostInfo!!.bounceCount shouldBe 1
        ActionParameterizer.spec(offer).allowedFields["bouncedPermanents"] shouldBe ActionParameterFieldKind.ENTITY_ID_ARRAY
        val params = ActionParams(targets = listOf(target), bouncedPermanents = listOf(elf))
        Json.decodeFromString<ActionParams>(Json.encodeToString(params)) shouldBe params
        val completed = ActionParameterizer.apply(info(d, offer), params, d.state) as ActivateAbility
        completed.costPayment!!.bouncedPermanents shouldBe listOf(elf)
        completed.targets.single() shouldBe com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(target)
        d.submit(completed).error shouldBe null
        (elf in d.getHand(p)) shouldBe true
        d.state.getEntity(target)!!.has<TappedComponent>() shouldBe true
        d.bothPass()
        d.state.getEntity(target)!!.has<TappedComponent>() shouldBe false
        d.legalActions(p).none { (it.action as? ActivateAbility)?.sourceId == source } shouldBe true
    }

    test("both native offer forms reject absent unrelated opponent repeated and surplus bounce selections atomically") {
        val d = game()
        val p = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(p, "Wirewood Symbiote")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val secondElf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val theirs = d.putCreatureOnBattlefield(d.getOpponent(p), "Llanowar Elves")
        val hidden = d.putCardInHand(p, "Llanowar Elves")
        val offer = d.legalActions(p).first { (it.action as? ActivateAbility)?.sourceId == source }
        val enriched = info(d, offer)
        val before = d.state
        for (selection in listOf(emptyList(), listOf(bear), listOf(theirs), listOf(hidden),
            listOf(EntityId("unknown-bounce")), listOf(elf, elf), listOf(elf, secondElf))) {
            val params = ActionParams(targets = listOf(bear), bouncedPermanents = selection)
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(offer, params, d.state) }
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(enriched, params, d.state) }
            d.state shouldBe before
        }
    }

    test("payment engine revalidates a once-offered object after it leaves the battlefield") {
        val d = game()
        val p = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(p, "Wirewood Symbiote")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val offer = d.legalActions(p).first { (it.action as? ActivateAbility)?.sourceId == source }
        val completed = ActionParameterizer.apply(offer,
            ActionParams(targets = listOf(target), bouncedPermanents = listOf(elf)), d.state)
        d.moveToGraveyard(elf)
        val before = d.state
        (d.submit(completed).error != null) shouldBe true
        d.state shouldBe before
        (elf in d.getHand(p)) shouldBe false
    }

    test("Quirion Ranger offer restricts the return payment to controlled Forests") {
        val d = game()
        val p = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(p, "Quirion Ranger")
        val forest = d.putLandOnBattlefield(p, "Forest")
        val island = d.putLandOnBattlefield(p, "Island")
        val theirs = d.putLandOnBattlefield(d.getOpponent(p), "Forest")
        val offer = d.legalActions(p).first { (it.action as? ActivateAbility)?.sourceId == source }
        offer.additionalCostInfo!!.validBounceTargets shouldBe listOf(forest)
        for (invalid in listOf(island, theirs)) {
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(offer,
                ActionParams(targets = listOf(source), bouncedPermanents = listOf(invalid)), d.state) }
        }
        d.submit(ActionParameterizer.apply(offer,
            ActionParams(targets = listOf(source), bouncedPermanents = listOf(forest)), d.state)).error shouldBe null
        (forest in d.getHand(p)) shouldBe true
    }

    test("Uyo offer requires exactly two distinct lands while retaining its stack target") {
        val d = game()
        val p = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(p, "Uyo, Silent Prophet")
        val lands = List(3) { d.putLandOnBattlefield(p, "Island") }
        val bolt = d.putCardInHand(p, "Lightning Bolt")
        d.giveMana(p, Color.RED)
        d.castSpellWithTargets(p, bolt, listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Player(d.getOpponent(p))))
        val target = d.getTopOfStack()!!
        d.giveColorlessMana(p, 2)
        val offer = d.legalActions(p).first { (it.action as? ActivateAbility)?.sourceId == source }
        offer.additionalCostInfo!!.bounceCount shouldBe 2
        for (invalid in listOf(lands.take(1), listOf(lands[0], lands[0]), lands)) {
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(offer,
                ActionParams(targets = listOf(target), bouncedPermanents = invalid), d.state) }
        }
        val completed = ActionParameterizer.apply(offer,
            ActionParams(targets = listOf(target), bouncedPermanents = lands.take(2)), d.state) as ActivateAbility
        completed.targets.single() shouldBe com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell(target)
        d.submit(completed).error shouldBe null
        d.getHand(p).containsAll(lands.take(2)) shouldBe true
        (lands.last() in d.state.getBattlefield()) shouldBe true
    }

    test("bounce additional spell cost reaches CastSpell payment without replacing its target") {
        val d = game()
        val p = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bolt = d.putCardInHand(p, "Lightning Bolt")
        val ruse = d.putCardInHand(p, "Familiar's Ruse")
        d.giveMana(p, Color.RED)
        d.castSpellWithTargets(p, bolt, listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Player(d.getOpponent(p))))
        val target = d.getTopOfStack()!!
        d.giveMana(p, Color.BLUE, 2)
        val offer = d.legalActions(p).first { (it.action as? CastSpell)?.cardId == ruse }
        val enriched = info(d, offer)
        shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(enriched, ActionParams(targets = listOf(target)), d.state) }
        val completed = ActionParameterizer.apply(enriched,
            ActionParams(targets = listOf(target), bouncedPermanents = listOf(creature)), d.state) as CastSpell
        completed.additionalCostPayment!!.bouncedPermanents shouldBe listOf(creature)
        completed.targets.single() shouldBe com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell(target)
        d.submit(completed).error shouldBe null
        (creature in d.getHand(p)) shouldBe true
    }

    test("ordinary actions do not advertise or silently consume bounce choices") {
        val d = game()
        val p = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val pass = d.legalActions(p).first { it.action is PassPriority }
        ("bouncedPermanents" in ActionParameterizer.spec(pass).allowedFields) shouldBe false
        shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(pass,
            ActionParams(bouncedPermanents = listOf(creature)), d.state) }
        Json.decodeFromString<ActionParams>("{}").isEmpty shouldBe true
    }
})
