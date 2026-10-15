package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.mtg.sets.definitions.clb.cards.JaheiraFriendOfTheForest
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class JaheiraFriendOfTheForestScenarioTest : FunSpec({
    val token = card("Test Jaheira Artifact") { typeLine = "Artifact" }
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(JaheiraFriendOfTheForest, token))
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.activations(player: EntityId, source: EntityId) =
        legalActions(player).filter { it.affordable }.map { it.action }.filterIsInstance<ActivateAbility>().filter { it.sourceId == source }

    test("a new noncreature token taps for mana immediately and does not use the stack") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val artifact = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.addComponent(artifact, TokenComponent)
        val action = d.activations(player, artifact).single()
        d.submit(action).error shouldBe null
        d.isTapped(artifact) shouldBe true
        d.state.stack.size shouldBe 0
        d.state.getEntity(player)!!.get<ManaPoolComponent>()!!.green shouldBe 1
    }
    test("a creature token must lose summoning sickness before it can tap for mana") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.addComponent(creature, TokenComponent)
        d.activations(player, creature).isEmpty() shouldBe true
        val disabled = d.legalActions(player).map { it.action }.filterIsInstance<ActivateAbility>()
            .single { it.sourceId == creature }
        d.submit(disabled).error shouldNotBe null
        d.removeSummoningSickness(creature)
        d.activations(player, creature).size shouldBe 1
    }
    test("ordinary permanents do not receive the ability and opposing tokens are excluded") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(d.getOpponent(player), "Jaheira, Friend of the Forest")
        val artifact = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.addComponent(artifact, TokenComponent)
        d.activations(player, artifact).isEmpty() shouldBe true
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val nontoken = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.activations(player, nontoken).isEmpty() shouldBe true
        d.activations(player, artifact).size shouldBe 1
    }
})
