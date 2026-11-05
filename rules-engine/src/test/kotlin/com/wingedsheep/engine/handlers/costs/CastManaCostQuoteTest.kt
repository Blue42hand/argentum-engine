package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.enumerators.applyOptionalCostPaymentMetadata
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.CardFace
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CastManaCostQuoteTest : FunSpec({
    val reducer = card("Quote Discount") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = ModifySpellCost(target = SpellCostTarget.YouCast(GameObjectFilter.Any),
            modification = CostModification.ReduceGenericBy(CostReductionSource.Fixed(3))) }
    }
    val lifeSource = card("Quote Life Source") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = PayLifeForColoredMana(Color.BLACK) }
    }
    val helper = card("Quote Helper") {
        manaCost = "{G}"
        typeLine = "Creature — Human"
        power = 1
        toughness = 1
    }
    val artifact = card("Quote Artifact") {
        manaCost = "{0}"
        typeLine = "Artifact"
    }
    fun repeated(name: String, base: String = "{0}", repeatCost: String = "{1}", alternatives: Boolean = false) = card(name) {
        manaCost = base
        typeLine = "Creature — Human"
        power = 1
        toughness = 1
        squad(repeatCost)
        if (alternatives) keywords(Keyword.DELVE, Keyword.CONVOKE, Keyword.IMPROVISE)
    }
    val modalSpell = card("Quote Modal Spell") {
        manaCost = "{1}"
        typeLine = "Sorcery"
        spell {
            modal(chooseCount = 2, minChooseCount = 1, additionalManaCostPerExtraMode = "{1}") {
                mode("Deal 1 damage to target player") {
                    effect = Effects.DealDamage(1, target(Targets.Player))
                }
                mode("Target player loses 1 life") {
                    effect = Effects.LoseLife(1, target(Targets.Player))
                }
            }
        }
    }
    val adventure = card("Quote Adventure Body") {
        manaCost = "{6}"
        typeLine = "Creature — Human"
        power = 1
        toughness = 1
        keywords(Keyword.CONVOKE)
    }.copy(layout = CardLayout.ADVENTURE, cardFaces = listOf(CardFace(
        name = "Quote Journey", manaCost = ManaCost.parse("{1}"), typeLine = TypeLine.parse("Sorcery"),
        keywords = setOf(Keyword.DELVE), script = CardScript(spellEffect = Effects.GainLife(1)),
    )))
    val spell = repeated("Quote Squad")
    val withAlternatives = repeated("Quote Alternative Squad", alternatives = true)
    val black = repeated("Quote Black Squad", repeatCost = "{B}")
    val xSpell = repeated("Quote X Squad", base = "{X}")
    fun game() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(reducer, lifeSource, helper, artifact, spell, withAlternatives, black, xSpell, modalSpell, adventure))
        it.initMirrorMatch(Deck.of("Forest" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
        repeat(6) { _ -> it.putLandOnBattlefield(it.activePlayer!!, "Forest") }
    }
    fun offer(d: GameTestDriver, id: EntityId, count: Int = 1): LegalAction = d.legalActions(d.activePlayer!!).first {
        val a = it.action as? CastSpell
        a?.cardId == id && a.declaredCostSlot == ChoiceSlot.SQUAD &&
            (a.declaredCostTimes == count || count in 1..(it.maxOptionalCostTimes ?: 0))
    }
    fun quote(d: GameTestDriver, action: CastSpell, offered: LegalAction) =
        d.services.castSpellHandler.quoteManaCost(d.state, action, offered)

    listOf(2 to "{0}", 4 to "{1}").forEach { (count, expected) ->
        test("native quote prices repeat count $count before reduction and matches explicit payment") {
            val d = game()
            val player = d.activePlayer!!
            d.putPermanentOnBattlefield(player, reducer.name)
            val id = d.putCardInHand(player, spell.name)
            val offered = offer(d, id, count)
            val action = (offered.action as CastSpell).copy(declaredCostTimes = count)
            val before = d.state
            val result = quote(d, action, offered).shouldNotBeNull()
            ManaCost.parse(result.manaCostString) shouldBe if (count == 2) ManaCost.ZERO else ManaCost.parse(expected)
            result.delveGenericCap shouldBe if (count == 2) 0 else 1
            d.state shouldBe before
            d.stackSize shouldBe 0
            val sources = if (count == 2) emptyList() else d.getPermanents(player).filter { d.getCardName(it) == "Forest" }.take(1)
            d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(sources))).error shouldBe null
            d.stackSize shouldBe 1
            d.getPermanents(player).count { d.state.getEntity(it)!!.has<TappedComponent>() } shouldBe sources.size
        }
    }
    test("metadata enrichment skips lightweight enumeration and irrelevant offers without copying") {
        val d = game()
        val player = d.activePlayer!!
        val id = d.putCardInHand(player, withAlternatives.name)
        val offered = offer(d, id)
        fun context(mode: EnumerationMode) = EnumerationContext(d.state, player, d.cardRegistry,
            d.services.manaSolver, d.services.costCalculator, d.services.predicateEvaluator,
            d.services.conditionEvaluator, d.services.turnManager, mode)
        val relevant = listOf(offered.copy(hasConvoke = false, convokeCreatures = null, hasDelve = false, delveCards = null))
        (applyOptionalCostPaymentMetadata(context(EnumerationMode.ACTIONS_ONLY), relevant) === relevant) shouldBe true
        val irrelevant = listOf(offered.copy(action = (offered.action as CastSpell).copy(declaredCostSlot = null)))
        (applyOptionalCostPaymentMetadata(context(EnumerationMode.FULL), irrelevant) === irrelevant) shouldBe true
    }
    test("selected Adventure face supplies metadata while native quote and payment retain face pricing") {
        val d = game()
        val player = d.activePlayer!!
        val graveyardCard = d.putCardInGraveyard(player, "Forest")
        d.putPermanentOnBattlefield(player, helper.name)
        val id = d.putCardInHand(player, adventure.name)
        val offered = d.legalActions(player).first { (it.action as? CastSpell)?.let { a -> a.cardId == id && a.faceIndex == 0 } == true }
        val action = offered.action as CastSpell
        val context = EnumerationContext(d.state, player, d.cardRegistry, d.services.manaSolver,
            d.services.costCalculator, d.services.predicateEvaluator, d.services.conditionEvaluator, d.services.turnManager)
        // Exercise the metadata adapter with an optional-cost variant of this selected face.
        val partial = offered.copy(action = action.copy(declaredCostSlot = ChoiceSlot.SQUAD),
            hasConvoke = false, convokeCreatures = null, hasDelve = false, delveCards = null)
        val enriched = applyOptionalCostPaymentMetadata(context, listOf(partial)).single()
        enriched.hasConvoke shouldBe false // The creature front's keyword must not leak.
        enriched.hasDelve shouldBe true
        enriched.delveCards!!.any { it.entityId == graveyardCard } shouldBe true
        val before = d.state
        ManaCost.parse(quote(d, action, offered).shouldNotBeNull().manaCostString) shouldBe ManaCost.parse("{1}")
        d.state shouldBe before
        val land = d.getLands(player).first()
        d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(listOf(land)))).error shouldBe null
        d.stackSize shouldBe 1
        d.state.getEntity(land)!!.has<TappedComponent>() shouldBe true
    }
    test("actual zero-cost repeat offer retains native payment candidates for a larger count") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, reducer.name)
        val creature = d.putPermanentOnBattlefield(player, helper.name)
        val rock = d.putPermanentOnBattlefield(player, artifact.name)
        val graveyardCard = d.putCardInGraveyard(player, "Forest")
        // The real enumerator emits its compact count picker only above 32 affordable repeats.
        d.giveMana(player, Color.GREEN, 40)
        val id = d.putCardInHand(player, withAlternatives.name)
        val offered = d.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == id && it.maxOptionalCostTimes != null
        }
        ManaCost.parse(offered.manaCostString!!).cmc shouldBe 0
        offered.hasDelve shouldBe true
        offered.delveCards!!.any { it.entityId == graveyardCard } shouldBe true
        offered.hasConvoke shouldBe true
        offered.convokeCreatures!!.any { it.entityId == creature } shouldBe true
        offered.hasTapForGeneric shouldBe true
        offered.tapForGenericPermanents!!.any { it.entityId == rock } shouldBe true
        val before = d.state
        val chosenCount = (offered.action as CastSpell).copy(declaredCostTimes = 4)
        val quoted = quote(d, chosenCount, offered).shouldNotBeNull()
        quoted.delveGenericCap shouldBe 1
        quoted.tapForGenericMax shouldBe 1
        val paidByDelve = quote(d, chosenCount.copy(alternativePayment =
            AlternativePaymentChoice(delvedCards = listOf(graveyardCard))), offered).shouldNotBeNull()
        ManaCost.parse(paidByDelve.manaCostString) shouldBe ManaCost.ZERO
        d.state shouldBe before
    }
    listOf("delve", "convoke", "improvise").forEach { kind ->
        test("quote credits selected $kind exactly once and leaves payment resources untouched") {
            val d = game()
            val player = d.activePlayer!!
            d.putPermanentOnBattlefield(player, reducer.name)
            val resource = when (kind) {
                "delve" -> d.putCardInGraveyard(player, "Forest")
                "convoke" -> d.putPermanentOnBattlefield(player, helper.name)
                else -> d.putPermanentOnBattlefield(player, artifact.name)
            }
            val id = d.putCardInHand(player, withAlternatives.name)
            val offered = offer(d, id, 4)
            val action = (offered.action as CastSpell).copy(declaredCostTimes = 4)
            val first = quote(d, action, offered).shouldNotBeNull()
            first.delveGenericCap shouldBe 1
            first.tapForGenericMax shouldBe 1
            val selected = action.copy(alternativePayment = when (kind) {
                "delve" -> AlternativePaymentChoice(delvedCards = listOf(resource))
                "convoke" -> AlternativePaymentChoice(convokedCreatures = mapOf(resource to ConvokePayment()))
                else -> AlternativePaymentChoice(tapForGenericPermanents = setOf(resource))
            })
            val before = d.state
            val final = quote(d, selected, offered).shouldNotBeNull()
            ManaCost.parse(final.manaCostString) shouldBe ManaCost.ZERO
            d.state shouldBe before
            d.submit(selected.copy(paymentStrategy = PaymentStrategy.Explicit(emptyList()))).error shouldBe null
            d.stackSize shouldBe 1
            if (kind == "delve") (resource in d.getExile(player)) shouldBe true
            else d.state.getEntity(resource)!!.has<TappedComponent>() shouldBe true
        }
    }
    test("X is materialized once after native repeat and generic reduction") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, reducer.name)
        val id = d.putCardInHand(player, xSpell.name)
        val offered = offer(d, id, 2)
        val action = (offered.action as CastSpell).copy(declaredCostTimes = 2, xValue = 4)
        val before = d.state
        val result = quote(d, action, offered).shouldNotBeNull()
        ManaCost.parse(result.manaCostString) shouldBe ManaCost.parse("{3}")
        result.delveGenericCap shouldBe 3
        d.state shouldBe before
        val lands = d.getPermanents(player).filter { d.getCardName(it) == "Forest" }.take(3)
        d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(lands))).error shouldBe null
        lands.forEach { d.state.getEntity(it)!!.has<TappedComponent>() shouldBe true }
    }
    test("life-payable colored symbols remain explicit manual life choices") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, lifeSource.name)
        val id = d.putCardInHand(player, black.name)
        val offered = offer(d, id, 2)
        val action = (offered.action as CastSpell).copy(declaredCostTimes = 2)
        val before = d.state
        val result = quote(d, action, offered).shouldNotBeNull()
        ManaCost.parse(result.manaCostString) shouldBe ManaCost.parse("{B/P}{B/P}")
        d.state shouldBe before
        d.getLifeTotal(player) shouldBe 20
        d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLACK, Color.BLACK)))).error shouldBe null
        d.getLifeTotal(player) shouldBe 16
    }
    test("any-type cast permission is reflected in quote and actual payment") {
        val d = game()
        val player = d.activePlayer!!
        // The zone optional-cost enumerator currently prices its offer before permission
        // relaxation. Supply matching sources so it advertises this variant, then prove the
        // quote and actual payment honor the permission using only off-color sources.
        val swamps = List(2) { d.putLandOnBattlefield(player, "Swamp") }
        val id = d.putCardInExile(player, black.name)
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = player,
            timestamp = d.state.timestamp, withAnyManaType = true,
        )))
        val offered = offer(d, id, 2)
        val action = (offered.action as CastSpell).copy(declaredCostTimes = 2)
        val before = d.state
        val result = quote(d, action, offered).shouldNotBeNull()
        ManaCost.parse(result.manaCostString) shouldBe ManaCost.parse("{2}")
        d.state shouldBe before
        val lands = d.getPermanents(player).filter { d.getCardName(it) == "Forest" }.take(2)
        d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(lands))).error shouldBe null
        lands.forEach { d.state.getEntity(it)!!.has<TappedComponent>() shouldBe true }
        swamps.forEach { d.state.getEntity(it)!!.has<TappedComponent>() shouldBe false }
    }
    listOf(listOf(0), listOf(0, 1)).forEach { modes ->
        test("modal quote counts each binding once with ${modes.size} modes sharing a target") {
            val d = game()
            val player = d.activePlayer!!
            val id = d.putCardInHand(player, modalSpell.name)
            val offered = d.legalActions(player).first { (it.action as? CastSpell)?.cardId == id }
            val target = ChosenTarget.Player(d.getOpponent(player))
            val action = (offered.action as CastSpell).copy(chosenModes = modes,
                targets = modes.map { target }, modeTargetsOrdered = modes.map { listOf(target) })
            val before = d.state
            val quoted = quote(d, action, offered).shouldNotBeNull()
            ManaCost.parse(quoted.manaCostString) shouldBe ManaCost.parse("{${modes.size}}")
            d.state shouldBe before
            // A mismatched flat prefix is rejected just as the native cast binder rejects it.
            quote(d, action.copy(targets = listOf(ChosenTarget.Player(player))), offered) shouldBe null
            val lands = d.getLands(player).take(modes.size)
            d.submit(action.copy(paymentStrategy = PaymentStrategy.Explicit(lands))).error shouldBe null
            d.stackSize shouldBe 1
            lands.forEach { d.state.getEntity(it)!!.has<TappedComponent>() shouldBe true }
        }
    }
    test("unoffered variants counts sources and hidden identities never receive a quote") {
        val d = game()
        val player = d.activePlayer!!
        val id = d.putCardInHand(player, withAlternatives.name)
        val offered = offer(d, id)
        val action = offered.action as CastSpell
        val hidden = d.state.getLibrary(d.getOpponent(player)).first()
        val before = d.state
        quote(d, action.copy(cardId = hidden), offered) shouldBe null
        quote(d, action.copy(targets = listOf(ChosenTarget.Permanent(hidden))), offered) shouldBe null
        quote(d, action.copy(playerId = d.getOpponent(player)), offered) shouldBe null
        quote(d, action.copy(useWithoutPayingManaCost = true), offered) shouldBe null
        quote(d, action.copy(declaredCostTimes = Int.MAX_VALUE), offered) shouldBe null
        quote(d, action.copy(xValue = Int.MAX_VALUE), offered) shouldBe null
        quote(d, action.copy(alternativePayment = AlternativePaymentChoice(delvedCards = listOf(hidden))), offered) shouldBe null
        quote(d, action.copy(additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(hidden))), offered) shouldBe null
        d.state shouldBe before
    }
})
