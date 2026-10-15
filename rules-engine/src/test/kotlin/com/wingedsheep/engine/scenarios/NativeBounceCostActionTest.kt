package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.mtg.sets.definitions.scg.cards.WirewoodSymbiote
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

private class BounceCostFixture {
    val driver = GameTestDriver().apply {
        registerCards(TestCards.all + WirewoodSymbiote)
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val player = driver.activePlayer!!
    val source = driver.putCreatureOnBattlefield(player, "Wirewood Symbiote")
    val elf = driver.putCreatureOnBattlefield(player, "Llanowar Elves")
    val target = driver.putCreatureOnBattlefield(player, "Grizzly Bears").also(driver::tapPermanent)
    val offered = driver.legalActions(player).first { (it.action as? ActivateAbility)?.sourceId == source }
    val info = LegalActionEnricher(
        ManaSolver(driver.cardRegistry, PredicateEvaluator(driver.cardRegistry)), driver.cardRegistry
    ).enrich(listOf(offered), driver.state, player).single()
}

class NativeBounceCostActionTest : FunSpec({
    test("serialized bounce choice pays the native cost through both offered action channels") {
        for (enriched in listOf(false, true)) {
            val f = BounceCostFixture()
            val params = Json.decodeFromString<ActionParams>(
                """{"targets":["${f.target.value}"],"bouncedPermanents":["${f.elf.value}"]}"""
            )
            ("bouncedPermanents" in f.info.parameterSpec.allowedFields) shouldBe true
            val completed = if (enriched) ActionParameterizer.apply(f.info, params, f.driver.state)
                else ActionParameterizer.apply(f.offered, params, f.driver.state)
            f.driver.submit(completed).error shouldBe null
            (f.elf in f.driver.state.getHand(f.player)) shouldBe true
            (f.target in f.driver.state.getBattlefield()) shouldBe true
            f.driver.passPriority(f.player)
            f.driver.passPriority(f.driver.getOpponent(f.player))
            f.driver.state.getEntity(f.target)!!.has<TappedComponent>() shouldBe false
        }
    }

    test("omitting the bounce payment remains a native rejection without mutation") {
        val f = BounceCostFixture()
        val completed = ActionParameterizer.apply(f.info, ActionParams(targets = listOf(f.target)), f.driver.state)
        (f.driver.submit(completed).error?.contains("Not enough permanents chosen to bounce")) shouldBe true
        (f.elf in f.driver.state.getBattlefield()) shouldBe true
        f.driver.state.getEntity(f.target)!!.has<TappedComponent>() shouldBe true
    }

    test("unoffered bounce IDs are rejected by both native offer boundaries") {
        val f = BounceCostFixture()
        val theirs = f.driver.putCreatureOnBattlefield(f.driver.getOpponent(f.player), "Llanowar Elves")
        for (id in listOf(theirs, f.target, EntityId("invented"))) {
            val params = ActionParams(bouncedPermanents = listOf(id))
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(f.offered, params, f.driver.state) }
            shouldThrow<IllegalArgumentException> { ActionParameterizer.apply(f.info, params, f.driver.state) }
        }
        (f.elf in f.driver.state.getBattlefield()) shouldBe true
    }

    test("bounce choices cannot be attached to parameterless actions") {
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(PassPriority(EntityId("player")),
                ActionParams(bouncedPermanents = listOf(EntityId("elf"))), GameState())
        }
    }

    test("a cast bounce choice preserves the other template cost selections") {
        val discarded = EntityId("discarded")
        val bounced = EntityId("bounced")
        val template = CastSpell(EntityId("player"), EntityId("spell"),
            additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(discarded)))
        val completed = ActionParameterizer.apply(template,
            ActionParams(bouncedPermanents = listOf(bounced)), GameState()) as CastSpell
        completed.additionalCostPayment!!.discardedCards shouldBe listOf(discarded)
        completed.additionalCostPayment!!.bouncedPermanents shouldBe listOf(bounced)
    }
})
