package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c15.Commander2015Set
import com.wingedsheep.mtg.sets.definitions.lea.cards.WrathOfGod
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
