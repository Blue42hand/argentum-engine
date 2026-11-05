package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c15.Commander2015Set
import com.wingedsheep.mtg.sets.definitions.lea.cards.WrathOfGod
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val ExecutionResult.isSuccess: Boolean get() = outcome == Outcome.Done

class MerenOfClanNelTothScenarioTest : FunSpec({

    fun newDriver() = GameTestDriver().apply {
        registerCards(TestCards.all + Commander2015Set.cards + listOf(WrathOfGod))
        initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun experience(driver: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId): Int =
        driver.state.getEntity(player)?.get<CountersComponent>()
            ?.getCount(CounterType.EXPERIENCE) ?: 0

    fun resolveStack(driver: GameTestDriver) {
        repeat(20) {
            when {
                driver.state.pendingDecision != null -> driver.autoResolveDecision()
                driver.state.stack.isNotEmpty() -> driver.bothPass()
                else -> return
            }
        }
        error("Stack did not settle")
    }

    test("another creature you control dying gives you an experience counter") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        driver.putCreatureOnBattlefield(you, "Meren of Clan Nel Toth")
        val bear = driver.putCreatureOnBattlefield(you, "Grizzly Bears")
        val bolt = driver.putCardInHand(you, "Lightning Bolt")

        driver.giveMana(you, Color.RED, 1)
        driver.castSpell(you, bolt, listOf(bear)).isSuccess shouldBe true
        resolveStack(driver)

        withClue("the counter is stored on Meren's controller") {
            experience(driver, you) shouldBe 1
        }
    }

    test("Meren still grants experience when it dies simultaneously with another creature") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        val meren = driver.putCreatureOnBattlefield(you, "Meren of Clan Nel Toth")
        val bear = driver.putCreatureOnBattlefield(you, "Grizzly Bears")
        val wrath = driver.putCardInHand(you, "Wrath of God")

        driver.giveMana(you, Color.WHITE, 4)
        driver.castSpell(you, wrath).isSuccess shouldBe true
        resolveStack(driver)

        withClue("last-known Meren sees the other creature die in the same event") {
            (meren in driver.state.getGraveyard(you)) shouldBe true
            (bear in driver.state.getGraveyard(you)) shouldBe true
            experience(driver, you) shouldBe 1
        }
    }

    test("the end-step trigger returns a creature at or below the experience total to the battlefield") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        driver.putCreatureOnBattlefield(you, "Meren of Clan Nel Toth")
        val bear = driver.putCardInGraveyard(you, "Grizzly Bears")
        driver.addComponent(
            you,
            CountersComponent(mapOf(CounterType.EXPERIENCE to 2)),
        )

        driver.passPriorityUntil(Step.END)
        driver.submitTargetSelection(you, listOf(bear)).isSuccess shouldBe true
        resolveStack(driver)

        withClue("mana value 2 is no greater than two experience counters") {
            (bear in driver.state.getBattlefield()) shouldBe true
        }
    }

    test("the end-step trigger puts a creature above the experience total into your hand") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        driver.putCreatureOnBattlefield(you, "Meren of Clan Nel Toth")
        val giant = driver.putCardInGraveyard(you, "Hill Giant")
        driver.addComponent(
            you,
            CountersComponent(mapOf(CounterType.EXPERIENCE to 3)),
        )

        driver.passPriorityUntil(Step.END)
        driver.submitTargetSelection(you, listOf(giant)).isSuccess shouldBe true
        resolveStack(driver)

        withClue("mana value 4 is greater than three experience counters") {
            (giant in driver.state.getHand(you)) shouldBe true
        }
    }
})

/**
 * Meren of Clan Nel Toth — experience counters from other creatures you control dying; at your
 * end step the target creature card returns to the battlefield if its mana value is at most your
 * experience count, otherwise to your hand. The comparison is made as the ability resolves.
 */
class MerenOfClanNelTothUpstreamScenarioTest : ScenarioTestBase() {

    private fun experience(game: TestGame): Int =
        game.state.getEntity(game.player1Id)?.get<CountersComponent>()?.getCount(CounterType.EXPERIENCE) ?: 0

    private fun board(): TestGame {
        var builder = scenario()
            .withPlayers("Player", "Opponent")
            .withCardOnBattlefield(1, "Meren of Clan Nel Toth")
            .withCardOnBattlefield(1, "Raging Goblin")
            .withCardOnBattlefield(2, "Elite Vanguard")
            .withCardInHand(1, "Shock")
            .withCardInHand(1, "Lightning Bolt")
            .withLandsOnBattlefield(1, "Mountain", 2)
            .withCardInGraveyard(1, "Savannah Lions") // MV 1
            .withCardInGraveyard(1, "Grizzly Bears") // MV 2
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(3) { builder = builder.withCardInLibrary(1, "Mountain") }
        repeat(3) { builder = builder.withCardInLibrary(2, "Plains") }
        return builder.build()
    }

    init {
        context("Meren of Clan Nel Toth") {
            test("only another creature you control dying gives an experience counter") {
                val game = board()
                game.castSpell(1, "Shock", game.findPermanent("Elite Vanguard")!!).error shouldBe null
                game.resolveStack()
                withClue("An opponent's creature dying gives nothing") { experience(game) shouldBe 0 }

                game.castSpell(1, "Lightning Bolt", game.findPermanent("Raging Goblin")!!).error shouldBe null
                game.resolveStack()
                withClue("Your own creature dying gives one experience counter") { experience(game) shouldBe 1 }
            }

            test("a card with mana value at most your experience returns to the battlefield") {
                val game = board()
                game.castSpell(1, "Lightning Bolt", game.findPermanent("Raging Goblin")!!).error shouldBe null
                game.resolveStack()
                experience(game) shouldBe 1

                val lions = game.findCardsInGraveyard(1, "Savannah Lions").single()
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                game.selectTargets(listOf(lions)).error shouldBe null
                game.resolveStack()

                withClue("Savannah Lions (MV 1) with 1 experience returns to the battlefield") {
                    game.findPermanent("Savannah Lions") shouldBe lions
                    game.isInHand(1, "Savannah Lions") shouldBe false
                }
            }

            test("a card with mana value above your experience goes to your hand instead") {
                val game = board()
                game.castSpell(1, "Lightning Bolt", game.findPermanent("Raging Goblin")!!).error shouldBe null
                game.resolveStack()
                experience(game) shouldBe 1

                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears (MV 2) with 1 experience goes to hand") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                    game.isInHand(1, "Grizzly Bears") shouldBe true
                }
                game.findPermanent("Meren of Clan Nel Toth").shouldNotBeNull()
            }
        }
    }
}
