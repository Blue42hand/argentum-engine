package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.AlternativeCostType
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
import com.wingedsheep.mtg.sets.definitions.inv.cards.PhyrexianAltar
import com.wingedsheep.mtg.sets.definitions.m21.cards.VillageRites
import com.wingedsheep.mtg.sets.definitions.lrw.cards.Smokebraider
import com.wingedsheep.mtg.sets.definitions.emn.cards.ElderDeepFiend
import com.wingedsheep.mtg.sets.definitions.spm.cards.SpiderUK
import com.wingedsheep.mtg.sets.definitions.tmt.cards.SplintersTechnique
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A cast offered using choice-dependent mana must be able to reach that mana choice. */
class AbradePaymentAgreementTest : FunSpec({
    test("web-slinging cannot reuse its selected creature as a mana cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SpiderUK, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Spider-UK")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        game.tapPermanent(creature)
        game.giveMana(caster, Color.WHITE)
        game.giveMana(caster, Color.GREEN)
        val cast = CastSpell(caster, card, useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.WEB_SLINGING,
            additionalCostPayment = AdditionalCostPayment(bouncedPermanents = listOf(creature)))

        game.submit(cast).isPaused shouldBe true
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.GREEN)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
    }

    test("sneak cannot reuse its selected attacker as a mana cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SplintersTechnique, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        val card = game.putCardInHand(caster, "Splinter's Technique")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        game.removeSummoningSickness(creature)
        game.passPriorityUntil(Step.DECLARE_ATTACKERS)
        game.declareAttackers(caster, listOf(creature), opponent).error shouldBe null
        game.passPriorityUntil(Step.DECLARE_BLOCKERS)
        game.declareBlockers(opponent, emptyMap()).error shouldBe null
        var guard = 0
        while (game.state.priorityPlayerId != null && game.state.priorityPlayerId != caster &&
            game.state.step == Step.DECLARE_BLOCKERS && guard++ < 4
        ) game.passPriority(game.state.priorityPlayerId!!)
        game.giveMana(caster, Color.BLACK)
        val cast = CastSpell(caster, card, useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.SNEAK,
            additionalCostPayment = AdditionalCostPayment(bouncedPermanents = listOf(creature)))

        game.submit(cast).isPaused shouldBe true
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.GREEN)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
    }

    test("emerge cannot reuse its selected sacrifice as a mana cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(ElderDeepFiend, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Elder Deep-Fiend")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        game.giveMana(caster, Color.BLUE)
        game.giveMana(caster, Color.GREEN, 3)
        val cast = CastSpell(caster, card, useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.EMERGE,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)))

        game.submit(cast).isPaused shouldBe true
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.BLUE)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
    }

    test("sacrificing an announced target for mana completes the cast") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        game.giveMana(caster, Color.RED)

        val target = ChosenTarget.Permanent(creature)
        game.submit(CastSpell(caster, card, targets = listOf(target), chosenModes = listOf(0),
            modeTargetsOrdered = listOf(listOf(target)))).isPaused shouldBe true
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.BLACK)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(window.id)).error shouldBe null
        (card in game.state.stack) shouldBe true
        (creature in game.state.getBattlefield()) shouldBe false
        game.bothPass().error shouldBe null
        (card in game.state.getGraveyard(caster)) shouldBe true
    }

    test("Treasure is activated directly instead of offered as a cast-window selection") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, PredefinedTokens.Treasure))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val treasure = game.putPermanentOnBattlefield(caster, "Treasure")
        val targetArtifact = game.putPermanentOnBattlefield(opponent, "Treasure")
        game.giveMana(caster, Color.RED)

        val target = ChosenTarget.Permanent(targetArtifact)
        game.submit(CastSpell(caster, card, targets = listOf(target), chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(target)))).isPaused shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        window.availableSources.none { it.entityId == treasure } shouldBe true
        game.submit(ActivateAbility(caster, treasure, PredefinedTokens.Treasure.activatedAbilities.single().id,
            manaColorChoice = Color.BLUE)).error shouldBe null
        val refreshed = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(refreshed.id)).error shouldBe null
        (card in game.state.stack) shouldBe true
    }

    test("cast window cannot spend Elemental-only mana on Abrade") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, SpringleafDrum, Smokebraider))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val restricted = game.putCreatureOnBattlefield(caster, "Smokebraider")
        val artifact = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        game.giveMana(caster, Color.RED)

        val target = ChosenTarget.Permanent(artifact)
        game.submit(CastSpell(caster, card, targets = listOf(target), chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(target)))).isPaused shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val result = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id, autoPay = true))
        (result.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
        game.state.getEntity(restricted)!!.has<TappedComponent>() shouldBe false
    }

    test("duplicate selected mana source cannot create extra mana") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, SpringleafDrum))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = if (caster == game.player1) game.player2 else game.player1
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val forest = game.putPermanentOnBattlefield(caster, "Forest")
        val artifact = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")

        val target = ChosenTarget.Permanent(artifact)
        game.submit(CastSpell(caster, card, targets = listOf(target), chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(target)))).isPaused shouldBe true
        game.submit(ActivateAbility(caster, drum, SpringleafDrum.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
            manaColorChoice = Color.RED)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val result = game.submitDecision(caster,
            ManaSourcesSelectedResponse(window.id, selectedSources = listOf(forest, forest)))
        (result.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe false
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.red shouldBe 1

        // The previous rejected response leaves the window open. Floating Forest's mana
        // manually removes it from the refreshed menu; the old selection must not tap it again.
        game.submit(ActivateAbility(caster, forest, AbilityId.intrinsicMana('G'))).error shouldBe null
        val refreshed = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val stale = game.submitDecision(caster,
            ManaSourcesSelectedResponse(refreshed.id, selectedSources = listOf(forest)))
        (stale.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.green shouldBe 1
    }

    test("mana activation cannot also consume a selected spell sacrifice cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(PhyrexianAltar, VillageRites))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val rites = game.putCardInHand(caster, "Village Rites")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val cast = CastSpell(caster, rites,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)))

        game.submit(cast).isPaused shouldBe true
        game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val ability = PhyrexianAltar.activatedAbilities.single()
        game.submit(ActivateAbility(caster, altar, ability.id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.BLACK)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (rites in game.state.getHand(caster)) shouldBe true
        (rites in game.state.stack) shouldBe false
        (creature in game.state.getBattlefield()) shouldBe false
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.black shouldBe 1
    }

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
        (game.submitDecision(caster, ManaSourcesSelectedResponse(window.id, autoPay = true)).error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        game.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.red shouldBe 1
        game.state.getEntity(drum)!!.has<TappedComponent>() shouldBe false
    }
})
