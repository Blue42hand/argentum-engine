package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.BlockerCountLimit
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GlobalBlockerLimitOfferTest : FunSpec({
    fun limitCard(name: String, maximum: Int) = CardDefinition.enchantment(
        name = name,
        manaCost = ManaCost.parse("{1}"),
        script = CardScript(staticAbilities = listOf(BlockerCountLimit(maximum))),
    )

    fun setup(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(limitCard("Test One Blocker Limit", 1))
        driver.registerCard(limitCard("Test Two Blocker Limit", 2))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        return driver
    }

    test("native offer exposes the smallest global cap while preserving pairwise targets") {
        val driver = setup()
        val attacker = driver.activePlayer!!
        val defender = if (attacker == driver.player1) driver.player2 else driver.player1
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val creature = driver.putCreatureOnBattlefield(attacker, "Centaur Courser")
        val first = driver.putCreatureOnBattlefield(defender, "Savannah Lions")
        val second = driver.putCreatureOnBattlefield(defender, "Savannah Lions")
        driver.putPermanentOnBattlefield(attacker, "Test Two Blocker Limit")
        driver.putPermanentOnBattlefield(attacker, "Test One Blocker Limit")
        driver.removeSummoningSickness(creature)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(creature), defender)
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)

        val offer = driver.legalActions(defender).single { it.actionType == "DeclareBlockers" }
        offer.validBlockTargets shouldBe mapOf(first to listOf(creature), second to listOf(creature))
        offer.maxTotalBlockers shouldBe 1
        val services = EngineServices(driver.cardRegistry)
        val presented = LegalActionEnricher(services.manaSolver, driver.cardRegistry)
            .enrich(listOf(offer), driver.state, defender).single()
        presented.maxTotalBlockers shouldBe 1

        val rejected = driver.declareBlockers(defender, mapOf(
            first to listOf(creature), second to listOf(creature),
        ))
        rejected.error shouldBe "No more than 1 creature can block each combat"
        driver.declareBlockers(defender, mapOf(first to listOf(creature))).error shouldBe null
    }

    test("uncapped combat offer leaves the global cap absent") {
        val driver = setup()
        val attacker = driver.activePlayer!!
        val defender = if (attacker == driver.player1) driver.player2 else driver.player1
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val creature = driver.putCreatureOnBattlefield(attacker, "Centaur Courser")
        driver.putCreatureOnBattlefield(defender, "Savannah Lions")
        driver.removeSummoningSickness(creature)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(creature), defender)
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        driver.legalActions(defender).single { it.actionType == "DeclareBlockers" }
            .maxTotalBlockers.shouldBeNull()
    }

    for (suppression in listOf("face down", "lost abilities")) {
        test("$suppression source neither leaks a printed cap nor restricts native blocks") {
            val driver = setup()
            val attacker = driver.activePlayer!!
            val defender = if (attacker == driver.player1) driver.player2 else driver.player1
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val creature = driver.putCreatureOnBattlefield(attacker, "Centaur Courser")
            val first = driver.putCreatureOnBattlefield(defender, "Savannah Lions")
            val second = driver.putCreatureOnBattlefield(defender, "Savannah Lions")
            val source = driver.putPermanentOnBattlefield(attacker, "Test One Blocker Limit")
            driver.removeSummoningSickness(creature)
            driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
            driver.declareAttackers(attacker, listOf(creature), defender)
            driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
            driver.legalActions(defender).single { it.actionType == "DeclareBlockers" }
                .maxTotalBlockers shouldBe 1

            if (suppression == "face down") {
                driver.replaceState(driver.state.updateEntity(source) { it.with(FaceDownComponent) })
            } else {
                val result = driver.services.effectExecutorRegistry.execute(
                    driver.state, Effects.RemoveAllAbilities(EffectTarget.Self),
                    EffectContext(sourceId = source, controllerId = attacker),
                )
                driver.replaceState(result.newState)
                driver.state.projectedState.hasLostAllAbilities(source) shouldBe true
            }
            driver.legalActions(defender).single { it.actionType == "DeclareBlockers" }
                .maxTotalBlockers.shouldBeNull()
            driver.declareBlockers(defender, mapOf(
                first to listOf(creature), second to listOf(creature),
            )).error shouldBe null
        }
    }

    test("each multiplayer defender gets the same independent one-blocker cap") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(limitCard("Test One Blocker Limit", 1))
        val players = driver.initMultiplayer(List(3) { Deck.of("Forest" to 40) })
        val attacker = players[0]
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val attackB = driver.putCreatureOnBattlefield(attacker, "Centaur Courser")
        val attackC = driver.putCreatureOnBattlefield(attacker, "Centaur Courser")
        val blockB = driver.putCreatureOnBattlefield(players[1], "Savannah Lions")
        val blockC = driver.putCreatureOnBattlefield(players[2], "Savannah Lions")
        driver.putPermanentOnBattlefield(attacker, "Test One Blocker Limit")
        driver.removeSummoningSickness(attackB)
        driver.removeSummoningSickness(attackC)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, mapOf(attackB to players[1], attackC to players[2])).error shouldBe null
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        driver.legalActions(players[1]).single { it.actionType == "DeclareBlockers" }
            .maxTotalBlockers shouldBe 1
        driver.declareBlockers(players[1], mapOf(blockB to listOf(attackB))).error shouldBe null
        driver.legalActions(players[2]).single { it.actionType == "DeclareBlockers" }
            .maxTotalBlockers shouldBe 1
        driver.declareBlockers(players[2], mapOf(blockC to listOf(attackC))).error shouldBe null
    }
})
