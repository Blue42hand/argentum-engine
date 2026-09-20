package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mat.MarchOfTheMachineAftermathSet
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class RoccoStreetChefScenarioTest : FunSpec({

    fun newDriver() = GameTestDriver().apply {
        registerCards(TestCards.all + MarchOfTheMachineAftermathSet.cards + PredefinedTokens.Food)
        initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun counters(driver: GameTestDriver, permanent: EntityId): Int =
        driver.state.getEntity(permanent)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun resolveStack(driver: GameTestDriver) {
        repeat(40) {
            when {
                driver.state.pendingDecision != null -> driver.autoResolveDecision()
                driver.state.stack.isNotEmpty() -> driver.bothPass()
                else -> return
            }
        }
        error("Stack did not settle")
    }

    fun resolveEndStepExile(
        driver: GameTestDriver,
        roccoController: EntityId,
        controllerTopName: String,
        opponentTopName: String,
    ): Pair<EntityId, EntityId> {
        val opponent = driver.getOpponent(roccoController)
        driver.putPermanentOnBattlefield(roccoController, "Rocco, Street Chef")
        val controllerTop = driver.putCardOnTopOfLibrary(roccoController, controllerTopName)
        val opponentTop = driver.putCardOnTopOfLibrary(opponent, opponentTopName)

        driver.passPriorityUntil(Step.END)
        resolveStack(driver)
        return controllerTop to opponentTop
    }

    test("the end-step trigger exiles one card for each player and grants each their own card") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val (yourCard, theirCard) = resolveEndStepExile(driver, you, "Mountain", "Grizzly Bears")

        driver.getExile(you) shouldContain yourCard
        driver.getExile(opponent) shouldContain theirCard
        withClue("each player receives a permission only for the card they exiled") {
            driver.state.mayPlayPermissions.any {
                it.controllerId == you && it.cardIds == setOf(yourCard)
            } shouldBe true
            driver.state.mayPlayPermissions.any {
                it.controllerId == opponent && it.cardIds == setOf(theirCard)
            } shouldBe true
        }
    }

    test("an opponent casting Rocco's card from exile grows the target and creates Food") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val target = driver.putCreatureOnBattlefield(you, "Grizzly Bears")
        val (_, exiledBear) = resolveEndStepExile(driver, you, "Mountain", "Grizzly Bears")

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 500)
        driver.activePlayer shouldBe opponent
        withClue("Rocco's permission persists through the opponent's turn") {
            driver.state.mayPlayPermissions.any {
                it.controllerId == opponent && exiledBear in it.cardIds
            } shouldBe true
        }
        driver.giveMana(opponent, Color.GREEN, 2)
        val cast = driver.castSpell(opponent, exiledBear)
        withClue(cast.error ?: "cast from exile pauses for Rocco's target") {
            cast.error shouldBe null
        }
        driver.submitTargetSelection(you, listOf(target)).isSuccess shouldBe true
        resolveStack(driver)

        counters(driver, target) shouldBe 1
        (driver.findPermanent(you, "Food") != null) shouldBe true
        withClue("the opponent's creature spell still resolves after Rocco's trigger") {
            (driver.findPermanent(opponent, "Grizzly Bears") != null) shouldBe true
        }
    }

    test("an opponent playing Rocco's land from exile grows the target and creates Food") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val target = driver.putCreatureOnBattlefield(you, "Grizzly Bears")
        val (_, exiledForest) = resolveEndStepExile(driver, you, "Mountain", "Forest")

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 500)
        driver.activePlayer shouldBe opponent
        withClue("Rocco's permission persists through the opponent's turn") {
            driver.state.mayPlayPermissions.any {
                it.controllerId == opponent && exiledForest in it.cardIds
            } shouldBe true
        }
        val play = driver.submit(PlayLand(opponent, exiledForest))
        withClue(play.error ?: "land play pauses for Rocco's target") {
            play.error shouldBe null
        }
        driver.submitTargetSelection(you, listOf(target)).isSuccess shouldBe true
        resolveStack(driver)

        counters(driver, target) shouldBe 1
        (driver.findPermanent(you, "Food") != null) shouldBe true
    }

    test("a land played from hand does not trigger Rocco") {
        val driver = newDriver()
        val you = driver.activePlayer!!
        val target = driver.putCreatureOnBattlefield(you, "Grizzly Bears")
        driver.putPermanentOnBattlefield(you, "Rocco, Street Chef")
        val forest = driver.putCardInHand(you, "Forest")

        driver.submit(PlayLand(you, forest)).isSuccess shouldBe true
        resolveStack(driver)

        counters(driver, target) shouldBe 0
        driver.findPermanent(you, "Food") shouldBe null
    }
})
