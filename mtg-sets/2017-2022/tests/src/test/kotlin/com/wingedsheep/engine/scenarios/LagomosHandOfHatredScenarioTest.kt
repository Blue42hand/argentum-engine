package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LagomosHandOfHatredScenarioTest : ScenarioTestBase() {
    private val source = "Lagomos, Hand of Hatred"
    private val theft = card("Test Lagomos Theft") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell { effect = Effects.GainControl(target(TargetFilter.Creature)) }
    }
    private fun board(active: Int = 1, empty: Boolean = false, sick: Boolean = false): ScenarioBuilder {
        val b = scenario().withPlayers()
            .withCardOnBattlefield(1, source, summoningSickness = sick)
            .withCardsInHand(1, "Lightning Bolt", 5)
            .withCardInHand(1, "Unsummon")
            .withCardInHand(2, theft.name)
            .withLandsOnBattlefield(1, "Mountain", 8)
            .withLandsOnBattlefield(1, "Island", 2)
            .withLandsOnBattlefield(1, "Swamp", 2)
            .withLandsOnBattlefield(2, "Island", 4)
            .withActivePlayer(active).withPriorityPlayer(active)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(2) { b.withCardOnBattlefield(1, "Grizzly Bears") }
        repeat(3) { b.withCardOnBattlefield(2, "Grizzly Bears", isToken = it == 2) }
        if (!empty) listOf("Forest", "Plains", "Swamp").forEach { b.withCardInLibrary(1, it) }
        repeat(10) { b.withCardInLibrary(2, "Island") }
        return b
    }
    private fun TestGame.pay() {
        if (getPendingDecision() is SelectManaSourcesDecision) submitManaSourcesAutoPay().error shouldBe null
    }
    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
    }
    private fun TestGame.kill(id: EntityId) {
        if (state.priorityPlayerId != player1Id) passPriority().error shouldBe null
        castSpell(1, "Lightning Bolt", id).error shouldBe null
        pay()
        finish()
    }
    private fun TestGame.activate() = execute(ActivateAbility(player1Id, findPermanent(source)!!,
        cardRegistry.getCard(source)!!.activatedAbilities.single().id))
    private fun TestGame.fiveDeaths() {
        findPermanents("Grizzly Bears").toList().forEach { kill(it) }
    }
    private fun TestGame.token(): EntityId = state.getBattlefield().single { state.getEntity(it)!!.has<TokenComponent>() }

    init {
        cardRegistry.register(theft)
        test("four deaths fail but five across players including a token allow a mandatory private search") {
            val g = board().build()
            val victims = g.findPermanents("Grizzly Bears").toList()
            victims.take(4).forEach { g.kill(it) }
            g.activate().error shouldNotBe null
            g.state.getEntity(g.findPermanent(source)!!)!!.has<TappedComponent>() shouldBe false
            g.kill(victims.last())
            g.activate().error shouldBe null
            g.state.getEntity(g.findPermanent(source)!!)!!.has<TappedComponent>() shouldBe true
            g.finish()
            val d = g.getPendingDecision() as SelectCardsDecision
            d.minSelections shouldBe 1
            d.maxSelections shouldBe 1
            g.submitDecision(CardsSelectedResponse(d.id, emptyList())).error shouldNotBe null
            val selected = d.options.first()
            g.submitDecision(CardsSelectedResponse(d.id, listOf(selected))).error shouldBe null
            g.state.getHand(g.player1Id).contains(selected) shouldBe true
            g.state.getLibrary(g.player1Id).size shouldBe 2
        }
        test("the unrestricted search resolves without a decision when the library is empty") {
            val g = board(empty = true).build()
            g.fiveDeaths()
            g.activate().error shouldBe null
            g.finish()
            g.getPendingDecision() shouldBe null
            g.state.getLibrary(g.player1Id).size shouldBe 0
        }
        test("a qualified tap ability remains subject to summoning sickness") {
            val g = board(sick = true).build()
            g.fiveDeaths()
            g.activate().error shouldNotBe null
        }
        test("own combat makes a red 2/1 with haste and trample and sacrifices it after Lagomos leaves") {
            val g = board().build()
            // Remove the fixture token first so token identity is unambiguous.
            g.kill(g.findPermanents("Grizzly Bears").last())
            g.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
            g.state.stack.size shouldBe 1
            g.castSpell(1, "Unsummon", g.findPermanent(source)!!).error shouldBe null
            g.pay()
            g.finish()
            val token = g.token()
            g.state.projectedState.getPower(token) shouldBe 2
            g.state.projectedState.getToughness(token) shouldBe 1
            g.state.projectedState.getColors(token) shouldBe setOf(Color.RED.name)
            g.state.projectedState.getKeywords(token) shouldContain Keyword.HASTE.name
            g.state.projectedState.getKeywords(token) shouldContain Keyword.TRAMPLE.name
            g.isInHand(1, source) shouldBe true
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.finish()
            g.state.getBattlefield().contains(token) shouldBe false
        }
        test("an opponent combat does not create an Elemental") {
            val g = board(active = 2).build()
            g.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
            g.state.stack.isEmpty() shouldBe true
            g.state.getBattlefield().count { g.state.getEntity(it)!!.has<TokenComponent>() } shouldBe 1
        }
        test("the original controller cannot sacrifice a token stolen before its delayed trigger") {
            val g = board().build()
            g.kill(g.findPermanents("Grizzly Bears").last())
            g.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
            g.finish()
            val token = g.token()
            g.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
            if (g.state.priorityPlayerId != g.player2Id) g.passPriority().error shouldBe null
            g.castSpell(2, theft.name, token).error shouldBe null
            g.pay()
            g.finish()
            g.state.projectedState.getController(token) shouldBe g.player2Id
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.finish()
            g.state.getBattlefield().contains(token) shouldBe true
        }
        test("an already activated search resolves after its source leaves") {
            val g = board().build()
            g.fiveDeaths()
            g.activate().error shouldBe null
            // The source leaving after activation does not cancel the search.
            g.castSpell(1, "Unsummon", g.findPermanent(source)!!).error shouldBe null
            g.pay()
            g.finish()
            val d = g.getPendingDecision() as SelectCardsDecision
            g.submitDecision(CardsSelectedResponse(d.id, listOf(d.options.first()))).error shouldBe null
        }
        test("cleanup resets all-player deaths and disables an untapped nonsummoning-sick source") {
            val g = board().build()
            g.fiveDeaths()
            g.activate().error shouldBe null
            g.finish()
            val d = g.getPendingDecision() as SelectCardsDecision
            g.submitDecision(CardsSelectedResponse(d.id, listOf(d.options.first()))).error shouldBe null
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.finish()
            g.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.finish()
            g.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            g.state.activePlayerId shouldBe g.player1Id
            listOf(g.player1Id, g.player2Id).forEach {
                (g.state.getEntity(it)!!.get<CreaturesDiedThisTurnComponent>()?.count ?: 0) shouldBe 0
            }
            val sourceEntity = g.state.getEntity(g.findPermanent(source)!!)!!
            sourceEntity.has<TappedComponent>() shouldBe false
            sourceEntity.has<SummoningSicknessComponent>() shouldBe false
            g.activate().error shouldNotBe null
        }
    }
}
