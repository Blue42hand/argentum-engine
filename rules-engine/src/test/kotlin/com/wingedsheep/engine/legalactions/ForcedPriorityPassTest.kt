package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.PayLifeOrEnterTappedSpellContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class ForcedPriorityPassTest : ScenarioTestBase() {
    private val enumerator by lazy { LegalActionEnumerator.create(cardRegistry) }

    init {
        test("certifies a sole native pass in the active player's main phase") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            enumerator.forcedPriorityPass(game.state, game.player1Id) shouldBe PassPriority(game.player1Id)
            enumerator.forcedPriorityPass(game.state, game.player2Id) shouldBe null
        }

        test("mana abilities remain a real choice even with no payable spell") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withLandsOnBattlefield(1, "Forest", 1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            enumerator.forcedPriorityPass(game.state, game.player1Id) shouldBe null
        }

        test("land play remains a real choice") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withCardInHand(1, "Forest")
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            enumerator.forcedPriorityPass(game.state, game.player1Id) shouldBe null
        }

        test("unaffordable offers do not invent a choice") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withCardInHand(1, "Grizzly Bears")
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            enumerator.forcedPriorityPass(game.state, game.player1Id) shouldBe PassPriority(game.player1Id)
        }

        test("pending decisions terminal games and combat declarations are never certified") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            enumerator.forcedPriorityPass(game.state.copy(gameOver = true), game.player1Id) shouldBe null
            enumerator.forcedPriorityPass(game.state.copy(continuationStack = listOf(Suspension(
                YesNoDecision("required", game.player1Id, "Choose", DecisionContext()),
                PayLifeOrEnterTappedSpellContinuation(game.player1Id, game.player1Id, game.player1Id, 2)))), game.player1Id) shouldBe null
            enumerator.forcedPriorityPass(game.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS), game.player1Id) shouldBe null
        }
    }
}
