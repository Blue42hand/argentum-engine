package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MindlessAutomatonScenarioTest : ScenarioTestBase() {
    private fun game() = scenario()
        .withPlayers("Automaton", "Opponent")
        .withCardInHand(1, "Mindless Automaton")
        .withCardInHand(1, "Forest")
        .withLandsOnBattlefield(1, "Mountain", 5)
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Plains")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun TestGame.castAutomaton() {
        castSpell(1, "Mindless Automaton").error shouldBe null
        resolveStack()
    }

    private fun TestGame.activate(index: Int) = execute(ActivateAbility(
        playerId = player1Id,
        sourceId = findPermanent("Mindless Automaton")!!,
        abilityId = cardRegistry.getCard("Mindless Automaton")!!.script.activatedAbilities[index].id
    ))

    init {
        test("entry counters protect the printed zero toughness before priority") {
            val game = game()
            game.castAutomaton()
            val automaton = game.findPermanent("Mindless Automaton")!!
            game.state.getEntity(automaton)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            game.state.projectedState.getPower(automaton) shouldBe 2
            game.state.projectedState.getToughness(automaton) shouldBe 2
        }

        test("discard is paid before the counter ability resolves and can be responded to") {
            val game = game()
            game.castAutomaton()
            val forest = game.findCardsInHand(1, "Forest").single()
            val automaton = game.findPermanent("Mindless Automaton")!!
            game.execute(ActivateAbility(
                playerId = game.player1Id,
                sourceId = automaton,
                abilityId = cardRegistry.getCard("Mindless Automaton")!!.script.activatedAbilities[0].id,
                costPayment = AdditionalCostPayment(discardedCards = listOf(forest))
            )).error shouldBe null
            game.findCardsInGraveyard(1, "Forest") shouldBe listOf(forest)
            game.state.projectedState.getPower(automaton) shouldBe 2
            game.state.stack.size shouldBe 1
            game.resolveStack()
            game.state.projectedState.getPower(automaton) shouldBe 3
        }

        test("removing the last counters kills the source before the independent draw resolves") {
            val game = game()
            game.castAutomaton()
            val handSize = game.state.getHand(game.player1Id).size
            game.activate(1).error shouldBe null
            game.findPermanent("Mindless Automaton") shouldBe null
            game.findCardsInGraveyard(1, "Mindless Automaton").size shouldBe 1
            game.state.getHand(game.player1Id).size shouldBe handSize
            game.state.stack.size shouldBe 1
            game.resolveStack()
            game.state.getHand(game.player1Id).size shouldBe handSize + 1
        }

        test("marked lethal damage is checked after paying counters and before drawing") {
            val game = game()
            game.castAutomaton()
            val automaton = game.findPermanent("Mindless Automaton")!!
            game.state = game.state.updateEntity(automaton) {
                it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3)))
                    .with(DamageComponent(1))
            }
            val handSize = game.state.getHand(game.player1Id).size
            game.activate(1).error shouldBe null
            game.findPermanent("Mindless Automaton") shouldBe null
            game.state.getHand(game.player1Id).size shouldBe handSize
            game.resolveStack()
            game.state.getHand(game.player1Id).size shouldBe handSize + 1
        }

        test("one counter cannot pay the draw cost and leaves the state unchanged") {
            val game = game()
            game.castAutomaton()
            val automaton = game.findPermanent("Mindless Automaton")!!
            game.state = game.state.updateEntity(automaton) {
                it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
            }
            val before = game.state
            game.activate(1).error shouldNotBe null
            game.state shouldBe before
        }
    }
}
