package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.mtg.sets.definitions.scg.cards.CarrionFeeder
import com.wingedsheep.mtg.sets.definitions.big.cards.FomoriVault
import com.wingedsheep.mtg.sets.definitions.eld.cards.ThrillOfPossibility
import com.wingedsheep.mtg.sets.definitions.m21.cards.VillageRites
import com.wingedsheep.mtg.sets.definitions.eoe.cards.SecludedStarforge
import com.wingedsheep.mtg.sets.definitions.ktk.cards.TreasureCruise
import com.wingedsheep.mtg.sets.definitions.ktk.cards.EmptyThePits
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

/** Native action IDs must carry the payer's selections into the existing cost validators. */
class NativeCostChoiceActionTest : FunSpec({
    fun game(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(SpringleafDrum, CarrionFeeder, FomoriVault,
            ThrillOfPossibility, VillageRites, SecludedStarforge))
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

    test("tap rejects an opponent creature at the offered boundary") {
        val driver = game()
        val player = driver.activePlayer!!
        val drum = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opponentCreature = driver.putCreatureOnBattlefield(driver.getOpponent(player), "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == drum && legal.isManaAbility
        }
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(
                offered, ActionParams(tappedPermanents = listOf(opponentCreature)), driver.state
            )
        }
        driver.state.getEntity(opponentCreature)!!.has<TappedComponent>() shouldBe false
    }

    test("direct Tap X payment requires distinct controlled battlefield artifacts") {
        val driver = game()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val forge = driver.putPermanentOnBattlefield(player, "Secluded Starforge")
        val first = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        val second = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        val theirs = driver.putPermanentOnBattlefield(opponent, "Springleaf Drum")
        val handCard = driver.putCardInHand(player, "Springleaf Drum")
        val target = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.giveMana(player, Color.GREEN, 2)
        val offered = driver.legalActions(player).first { legal ->
            val action = legal.action as? ActivateAbility
            action?.sourceId == forge && action.abilityId == SecludedStarforge.activatedAbilities[1].id
        }.action
        for (ids in listOf(listOf(first, first), listOf(first, theirs), listOf(first, handCard))) {
            val direct = ActionParameterizer.apply(
                offered, ActionParams(targets = listOf(target), xValue = 2, tappedPermanents = ids), driver.state
            )
            (driver.submit(direct).error != null) shouldBe true
            driver.state.getEntity(first)!!.has<TappedComponent>() shouldBe false
            driver.state.getEntity(second)!!.has<TappedComponent>() shouldBe false
            driver.state.getEntity(theirs)!!.has<TappedComponent>() shouldBe false
        }
        val valid = ActionParameterizer.apply(
            offered, ActionParams(targets = listOf(target), xValue = 2,
                tappedPermanents = listOf(first, second)), driver.state
        )
        driver.submit(valid).error shouldBe null
        driver.state.getEntity(first)!!.has<TappedComponent>() shouldBe true
        driver.state.getEntity(second)!!.has<TappedComponent>() shouldBe true
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

    test("sacrifice rejects an opponent creature at the offered boundary and direct payment") {
        val driver = game()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val feeder = driver.putCreatureOnBattlefield(player, "Carrion Feeder")
        driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opponentCreature = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == feeder
        }
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(
                offered, ActionParams(sacrificedPermanents = listOf(opponentCreature)), driver.state
            )
        }
        val direct = ActionParameterizer.apply(
            offered.action, ActionParams(sacrificedPermanents = listOf(opponentCreature)), driver.state
        )
        (driver.submit(direct).error != null) shouldBe true
        (opponentCreature in driver.state.getBattlefield()) shouldBe true
    }

    test("server legal-action info exposes and enforces the same sacrifice contract") {
        val driver = game()
        val player = driver.activePlayer!!
        val feeder = driver.putCreatureOnBattlefield(player, "Carrion Feeder")
        val own = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val theirs = driver.putCreatureOnBattlefield(driver.getOpponent(player), "Grizzly Bears")
        val offered = driver.legalActions(player).first { legal ->
            (legal.action as? ActivateAbility)?.sourceId == feeder
        }
        val info = LegalActionEnricher(ManaSolver(driver.cardRegistry), driver.cardRegistry)
            .enrich(listOf(offered), driver.state, player).single()
        ("sacrificedPermanents" in info.parameterSpec.allowedFields) shouldBe true
        ("discardedCards" in info.parameterSpec.allowedFields) shouldBe false
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(info, ActionParams(sacrificedPermanents = listOf(theirs)), driver.state)
        }
        val valid = ActionParameterizer.apply(
            info, ActionParams(sacrificedPermanents = listOf(own)), driver.state
        )
        driver.submit(valid).error shouldBe null
        (own in driver.state.getBattlefield()) shouldBe false
    }

    test("Treasure Cruise native offer carries chosen delve cards into payment") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TreasureCruise)
        driver.initMirrorMatch(Deck.of("Island" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val cruise = driver.putCardInHand(player, "Treasure Cruise")
        repeat(3) { driver.putLandOnBattlefield(player, "Island") }
        val graveyard = (1..7).map { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == cruise
        }
        val info = LegalActionEnricher(ManaSolver(driver.cardRegistry), driver.cardRegistry)
            .enrich(listOf(offered), driver.state, player).single()

        offered.affordable shouldBe true
        info.minDelveNeeded shouldBe 5
        info.validDelveCards!!.map { it.entityId }.toSet() shouldBe graveyard.toSet()
        info.parameterSpec.allowedFields["delvedCards"] shouldBe
            com.wingedsheep.engine.core.ActionParameterFieldKind.ENTITY_ID_ARRAY
        val withoutChoice = ActionParameterizer.apply(info, ActionParams.EMPTY, driver.state)
        (driver.submit(withoutChoice).error != null) shouldBe true
        driver.state.getGraveyard(player).containsAll(graveyard) shouldBe true

        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(info, ActionParams(delvedCards = listOf(cruise)), driver.state)
        }
        val selected = graveyard.take(5)
        val gymAction = ActionParameterizer.apply(offered, ActionParams(delvedCards = selected), driver.state)
        (gymAction as CastSpell).alternativePayment?.delvedCards shouldBe selected
        val repeated = ActionParameterizer.apply(
            info, ActionParams(delvedCards = List(5) { graveyard.first() }), driver.state
        )
        (driver.submit(repeated).error != null) shouldBe true
        driver.state.getExile(player).none { it in graveyard } shouldBe true
        val completed = ActionParameterizer.apply(info, ActionParams(delvedCards = selected), driver.state)
        (completed as CastSpell).alternativePayment?.delvedCards shouldBe selected
        driver.submit(completed).error shouldBe null
        driver.state.getGraveyard(player).none { it in selected } shouldBe true
        driver.state.getExile(player).containsAll(selected) shouldBe true
        driver.state.getGraveyard(player).containsAll(graveyard.drop(5)) shouldBe true
    }

    test("delve rejects an offered card after it leaves the graveyard") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TreasureCruise)
        driver.initMirrorMatch(Deck.of("Island" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val cruise = driver.putCardInHand(player, "Treasure Cruise")
        repeat(3) { driver.putLandOnBattlefield(player, "Island") }
        val graveyard = (1..7).map { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == cruise
        }
        val chosen = ActionParameterizer.apply(
            offered, ActionParams(delvedCards = graveyard), driver.state
        )
        driver.replaceState(ZoneTransitionService.moveToZone(
            driver.state, graveyard.first(), Zone.EXILE
        ).state)

        (driver.submit(chosen).error != null) shouldBe true
        driver.state.getGraveyard(player).containsAll(graveyard.drop(1)) shouldBe true
        driver.state.getExile(player).count { it in graveyard } shouldBe 1
    }

    test("delve never pays the remaining blue mana with graveyard cards") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TreasureCruise)
        driver.initMirrorMatch(Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val cruise = driver.putCardInHand(player, "Treasure Cruise")
        driver.putLandOnBattlefield(player, "Forest")
        val island = driver.putLandOnBattlefield(player, "Island")
        val graveyard = (1..6).map { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == cruise
        }
        val action = ActionParameterizer.apply(
            offered, ActionParams(delvedCards = graveyard), driver.state
        )
        driver.replaceState(ZoneTransitionService.moveToZone(driver.state, island, Zone.HAND).state)

        (driver.submit(action).error != null) shouldBe true
        driver.state.getGraveyard(player).containsAll(graveyard) shouldBe true
    }

    test("native action schema leaves X delve to the existing payment path") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + EmptyThePits)
        driver.initMirrorMatch(Deck.of("Swamp" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val pits = driver.putCardInHand(player, "Empty the Pits")
        repeat(5) { driver.putLandOnBattlefield(player, "Swamp") }
        driver.putCardInGraveyard(player, "Grizzly Bears")
        val offered = driver.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == pits
        }
        offered.hasXCost shouldBe true
        ("delvedCards" in ActionParameterizer.spec(offered).allowedFields) shouldBe false
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(
                offered, ActionParams(delvedCards = offered.delveCards!!.map { it.entityId }), driver.state
            )
        }
    }

    test("delve rejects more exiles than the spell has generic mana to pay") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TreasureCruise)
        driver.initMirrorMatch(Deck.of("Island" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val cruise = driver.putCardInHand(player, "Treasure Cruise")
        repeat(8) { driver.putLandOnBattlefield(player, "Island") }
        val graveyard = (1..8).map { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player).first {
            (it.action as? CastSpell)?.cardId == cruise
        }
        val completed = ActionParameterizer.apply(
            offered, ActionParams(delvedCards = graveyard), driver.state
        )

        (driver.submit(completed).error?.contains("Too many cards selected for delve")) shouldBe true
        driver.state.getGraveyard(player).containsAll(graveyard) shouldBe true
        driver.state.getExile(player).none { it in graveyard } shouldBe true
    }

    test("discard rejects a card from another player's hand") {
        val driver = game()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val vault = driver.putPermanentOnBattlefield(player, "Fomori Vault")
        driver.putCardInHand(player, "Plains")
        val opponentCard = driver.putCardInHand(opponent, "Plains")
        driver.giveMana(player, Color.RED, 3)
        val offered = driver.legalActions(player).first { legal ->
            val action = legal.action as? ActivateAbility
            action?.sourceId == vault && action.abilityId == FomoriVault.activatedAbilities[1].id
        }
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(
                offered, ActionParams(discardedCards = listOf(opponentCard)), driver.state
            )
        }
        val direct = ActionParameterizer.apply(
            offered.action, ActionParams(discardedCards = listOf(opponentCard)), driver.state
        )
        (driver.submit(direct).error != null) shouldBe true
        (opponentCard in driver.state.getHand(opponent)) shouldBe true
        val invented = EntityId("not-a-hand-card")
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(offered, ActionParams(discardedCards = listOf(invented)), driver.state)
        }
        val inventedDirect = ActionParameterizer.apply(
            offered.action, ActionParams(discardedCards = listOf(invented)), driver.state
        )
        (driver.submit(inventedDirect).error != null) shouldBe true
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
