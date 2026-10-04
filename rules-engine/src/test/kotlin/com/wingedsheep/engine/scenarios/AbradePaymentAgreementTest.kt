package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.Outcome
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
import com.wingedsheep.mtg.sets.definitions.shm.cards.GleefulSabotage
import com.wingedsheep.mtg.sets.definitions.snc.cards.RobTheArchives
import com.wingedsheep.mtg.sets.definitions.ori.cards.PyromancersGoggles
import com.wingedsheep.mtg.sets.definitions.mh3.cards.HerigastEruptingNullkite
import com.wingedsheep.mtg.sets.definitions.otj.cards.TerrorOfThePeaks
import com.wingedsheep.mtg.sets.definitions.usg.cards.SkirgeFamiliar
import com.wingedsheep.mtg.sets.definitions.chk.cards.DesperateRitual
import com.wingedsheep.mtg.sets.definitions.chk.cards.LavaSpike
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.scripting.effects.PlayerActionTiming
import com.wingedsheep.engine.core.TakePlayerAction
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.Json
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A cast offered using choice-dependent mana must be able to reach that mana choice. */
class AbradePaymentAgreementTest : FunSpec({
    test("a spliced card discarded for mana cannot remain in an announced cast") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SkirgeFamiliar, DesperateRitual, LavaSpike))
        game.initMirrorMatch(Deck.of("Mountain" to 40))
        val caster = game.activePlayer!!
        val opponent = game.getOpponent(caster)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Lava Spike")
        val ritual = game.putCardInHand(caster, "Desperate Ritual")
        val familiar = game.putCreatureOnBattlefield(caster, "Skirge Familiar")
        game.giveMana(caster, Color.RED, 2)

        game.submit(CastSpell(caster, card, targets = listOf(ChosenTarget.Player(opponent)),
            splicedCardIds = listOf(ritual))).isPaused shouldBe true
        game.submit(ActivateAbility(caster, familiar, SkirgeFamiliar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(discardedCards = listOf(ritual)))).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
    }

    test("a frozen target life tax cannot be skipped after producing mana with life") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(Abrade, TerrorOfThePeaks))
        game.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 4)
        val caster = game.activePlayer!!
        val opponent = game.getOpponent(caster)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Abrade")
        val terror = game.putCreatureOnBattlefield(opponent, "Terror of the Peaks")
        game.giveMana(caster, Color.RED)
        game.replaceState(game.services.effectExecutorRegistry.execute(game.state,
            Effects.GrantPlayerAction(Costs.pay.PayLife(1), Effects.AddColorlessMana(1),
                PlayerActionTiming.ManaAbility, "Life mana"), EffectContext(null, caster)).state)
        val manaAction = game.state.playerActionPermissions.single().id
        val target = ChosenTarget.Permanent(terror)

        val announced = game.submit(CastSpell(caster, card, targets = listOf(target), chosenModes = listOf(0),
            modeTargetsOrdered = listOf(listOf(target))))
        withClue("cast announcement: ${announced.error}, ${announced.outcome}") { announced.isPaused shouldBe true }
        repeat(2) { game.submit(TakePlayerAction(caster, manaAction)).error shouldBe null }
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        game.getLifeTotal(caster) shouldBe 2
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
    }

    test("manual and auto cast-window choices preserve Goggles mana's copy rider") {
        val redSpell = CardDefinition(
            name = "Test Window Ember", manaCost = ManaCost.parse("{1}{R}"),
            typeLine = TypeLine.parse("Instant"), oracleText = "You gain 2 life.",
            script = CardScript.spell(effect = Effects.GainLife(2)),
        )
        for (autoPay in listOf(false, true)) {
            val game = GameTestDriver()
            game.registerCards(TestCards.all + listOf(redSpell, SpringleafDrum, PyromancersGoggles))
            game.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 20)
            val caster = game.activePlayer!!
            game.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val card = game.putCardInHand(caster, "Test Window Ember")
            val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
            val goggles = game.putPermanentOnBattlefield(caster, "Pyromancer's Goggles")
            val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")

            game.submit(CastSpell(caster, card)).isPaused shouldBe true
            game.submit(ActivateAbility(caster, drum, SpringleafDrum.activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
                manaColorChoice = Color.BLUE)).error shouldBe null
            val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.submitDecision(caster, ManaSourcesSelectedResponse(window.id,
                selectedSources = if (autoPay) emptyList() else listOf(goggles), autoPay = autoPay)).error shouldBe null
            repeat(3) { game.bothPass().error shouldBe null }
            game.getLifeTotal(caster) shouldBe 24
        }
    }

    test("granted emerge remains payable after its granter fuels mana") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(HerigastEruptingNullkite, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Grizzly Bears")
        val herigast = game.putCreatureOnBattlefield(caster, "Herigast, Erupting Nullkite")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val sacrificed = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val cast = CastSpell(caster, card, useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.EMERGE,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrificed)))

        game.submit(cast).isPaused shouldBe true
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        game.replaceState(json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), game.state)))
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(herigast)),
            manaColorChoice = Color.GREEN)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(window.id)).error shouldBe null
        (card in game.state.stack) shouldBe true
        (sacrificed in game.state.getBattlefield()) shouldBe false
    }

    test("conspire cannot reuse a creature tapped for mana during its cast window") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(GleefulSabotage, SpringleafDrum))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        val opponent = game.getOpponent(caster)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Gleeful Sabotage")
        val drum = game.putPermanentOnBattlefield(caster, "Springleaf Drum")
        val creature = game.putCreatureOnBattlefield(caster, "Centaur Courser")
        val otherCreature = game.putCreatureOnBattlefield(caster, "Centaur Courser")
        val target = game.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        game.giveMana(caster, Color.GREEN)
        val cast = CastSpell(caster, card, targets = listOf(ChosenTarget.Permanent(target)),
            conspiredCreatures = listOf(creature, otherCreature))

        game.submit(cast).isPaused shouldBe true
        game.submit(ActivateAbility(caster, drum, SpringleafDrum.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
            manaColorChoice = Color.BLUE)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
    }

    test("casualty cannot reuse a creature sacrificed for mana during its cast window") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(RobTheArchives, PhyrexianAltar))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Rob the Archives")
        val altar = game.putPermanentOnBattlefield(caster, "Phyrexian Altar")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        game.giveMana(caster, Color.RED)

        game.submit(CastSpell(caster, card, casualtyCreature = creature)).isPaused shouldBe true
        game.submit(ActivateAbility(caster, altar, PhyrexianAltar.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)),
            manaColorChoice = Color.BLUE)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        val rejected = game.submitDecision(caster, ManaSourcesSelectedResponse(window.id))
        (rejected.error != null) shouldBe true
        (card in game.state.getHand(caster)) shouldBe true
        (card in game.state.stack) shouldBe false
    }

    test("legacy Emerge selection keeps its creature when Treasure supplies the missing mana") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(ElderDeepFiend, PredefinedTokens.Treasure))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val card = game.putCardInHand(caster, "Elder Deep-Fiend")
        val creature = game.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val treasure = game.putPermanentOnBattlefield(caster, "Treasure")
        game.giveMana(caster, Color.BLUE)
        game.giveMana(caster, Color.GREEN, 3)
        val legacyCast = CastSpell(caster, card, useAlternativeCost = true,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(creature)))

        game.submit(legacyCast).isPaused shouldBe true
        game.submit(ActivateAbility(caster, treasure, PredefinedTokens.Treasure.activatedAbilities.single().id,
            manaColorChoice = Color.BLUE)).error shouldBe null
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(caster, ManaSourcesSelectedResponse(window.id)).error shouldBe null
        (card in game.state.stack) shouldBe true
        (creature in game.state.getBattlefield()) shouldBe false
    }

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

private val ExecutionResult.isPaused: Boolean
    get() = outcome is Outcome.Paused
