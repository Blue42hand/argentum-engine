package com.wingedsheep.engine.view

import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.CostReductionSource
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DelveTargetCostPresentationTest : FunSpec({
    val conditional = card("Test Conditional Delve") {
        manaCost = "{6}{U}"
        colorIdentity = "U"
        typeLine = "Instant"
        keywords(Keyword.DELVE)
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.Destroy(creature)
        }
        staticAbility {
            ability = ModifySpellCost(
                target = SpellCostTarget.SelfCast,
                modification = CostModification.ReduceGenericBy(
                    CostReductionSource.FixedIfAnyTargetMatches(
                        amount = 2,
                        filter = GameObjectFilter.Creature.withKeyword(Keyword.FLYING),
                    ),
                ),
            )
        }
    }
    val fixed = card("Test Fixed Target Delve") {
        manaCost = "{6}{U}"
        colorIdentity = "U"
        typeLine = "Instant"
        keywords(Keyword.DELVE)
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.Destroy(creature)
        }
    }
    val targetingTax = card("Test Targeting Tax") {
        manaCost = "{2}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 3
        staticAbility {
            ability = ModifySpellCost(
                target = SpellCostTarget.OpponentsCastTargeting(GroupFilter.source()),
                modification = CostModification.IncreaseGeneric(2),
            )
        }
    }

    fun setup(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(conditional, fixed, targetingTax))
        initMirrorMatch(Deck.of("Island" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun info(driver: GameTestDriver, name: String, graveyardCount: Int = 6): Pair<com.wingedsheep.engine.legalactions.LegalAction, LegalActionInfo> {
        val player = driver.activePlayer!!
        val spell = driver.putCardInHand(player, name)
        driver.putLandOnBattlefield(player, "Island")
        repeat(graveyardCount) { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player).first { (it.action as? CastSpell)?.cardId == spell }
        val presented = LegalActionEnricher(
            ManaSolver(driver.cardRegistry, PredicateEvaluator(driver.cardRegistry)),
            driver.cardRegistry,
        ).enrich(listOf(offered), driver.state, player).single()
        return offered to presented
    }

    test("optimistic target discount never caps a legal six-card Delve payment") {
        val driver = setup()
        val player = driver.activePlayer!!
        driver.putCreatureOnBattlefield(driver.getOpponent(player), "Birds of Paradise")
        val ground = driver.putCreatureOnBattlefield(driver.getOpponent(player), "Grizzly Bears")
        val (offered, presented) = info(driver, "Test Conditional Delve")
        offered.manaCostString shouldBe "{4}{U}"
        presented.maxDelveCards.shouldBeNull()
        val graveyard = driver.state.getGraveyard(player).take(6)
        val cast = ActionParameterizer.apply(
            presented, ActionParams(targets = listOf(ground), delvedCards = graveyard), driver.state,
        )
        driver.submit(cast).error shouldBe null
    }

    test("battlefield targeting tax cannot make a fixed-looking offer cap legal Delve payment") {
        val driver = setup()
        val player = driver.activePlayer!!
        val protected = driver.putCreatureOnBattlefield(driver.getOpponent(player), "Test Targeting Tax")
        val (offered, presented) = info(driver, "Test Fixed Target Delve", graveyardCount = 8)
        offered.manaCostString shouldBe "{6}{U}"
        presented.maxDelveCards.shouldBeNull()
        val graveyard = driver.state.getGraveyard(player).take(8)
        val cast = ActionParameterizer.apply(
            presented, ActionParams(targets = listOf(protected), delvedCards = graveyard), driver.state,
        )
        driver.submit(cast).error shouldBe null
    }

    test("targeted offers remain unbounded even without an observed modifier") {
        val driver = setup()
        val player = driver.activePlayer!!
        driver.putCreatureOnBattlefield(driver.getOpponent(player), "Grizzly Bears")
        val (offered, presented) = info(driver, "Test Fixed Target Delve")
        offered.manaCostString shouldBe "{6}{U}"
        presented.maxDelveCards.shouldBeNull()
    }
})
