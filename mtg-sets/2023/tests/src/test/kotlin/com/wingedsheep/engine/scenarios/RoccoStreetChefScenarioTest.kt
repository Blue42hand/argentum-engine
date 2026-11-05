package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mat.MarchOfTheMachineAftermathSet
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private val ExecutionResult.isSuccess: Boolean get() = outcome == Outcome.Done

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

/**
 * Rocco, Street Chef — "At the beginning of your end step, each player exiles the top card of
 * their library. Until your next end step, each player may play the card they exiled this way.
 * Whenever a player plays a land from exile or casts a spell from exile, you put a +1/+1 counter
 * on target creature and create a Food token."
 */
class RoccoStreetChefUpstreamScenarioTest : ScenarioTestBase() {

    private fun TestGame.rocco(): EntityId = findPermanent("Rocco, Street Chef")!!

    private fun TestGame.roccoCounters(): Int =
        state.getEntity(rocco())?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun TestGame.exiled(playerId: EntityId, name: String): EntityId =
        state.getExile(playerId).first { state.getEntity(it)?.get<CardComponent>()?.name == name }

    private fun TestGame.mayPlay(playerId: EntityId, cardId: EntityId): Boolean =
        state.mayPlayPermissions.any { it.controllerId == playerId && cardId in it.cardIds }

    /** Rocco's exile-play trigger targets a creature; aim it at Rocco. */
    private fun TestGame.resolveTargetingRocco() {
        if (state.pendingDecision != null) selectTargets(listOf(rocco())).error shouldBe null
        resolveStack()
    }

    private fun TestGame.passUntilTurnOf(playerId: EntityId) {
        var guard = 0
        do {
            passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            if (state.activePlayerId != playerId) passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        } while (state.activePlayerId != playerId && guard++ < 4)
    }

    private fun roccoGame(): TestGame = scenario()
        .withPlayers("Player1", "Player2")
        .withCardOnBattlefield(1, "Rocco, Street Chef")
        .withLandsOnBattlefield(1, "Forest", 2)
        .withCardInLibrary(1, "Grizzly Bears")
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Llanowar Elves")
        .withCardInLibrary(2, "Mountain")
        .withCardInLibrary(2, "Island")
        .withCardInLibrary(2, "Island")
        .withActivePlayer(1)
        .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
        .build()

    init {
        test("each player exiles their top card and may play it — an opponent's land play feeds Rocco") {
            val g = roccoGame()
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.resolveStack()

            val bears = g.exiled(g.player1Id, "Grizzly Bears")
            val mountain = g.exiled(g.player2Id, "Mountain")
            withClue("each player may play only the card they exiled") {
                g.mayPlay(g.player1Id, bears) shouldBe true
                g.mayPlay(g.player2Id, mountain) shouldBe true
                g.mayPlay(g.player1Id, mountain) shouldBe false
            }

            g.passUntilTurnOf(g.player2Id)
            g.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            g.execute(PlayLand(g.player2Id, mountain)).error shouldBe null
            g.resolveTargetingRocco()

            g.isOnBattlefield("Mountain") shouldBe true
            withClue("Rocco's controller — not the player who played the land — gets the counter and the Food") {
                g.roccoCounters() shouldBe 1
                g.findPermanents("Food").single().let { g.state.projectedState.getController(it) } shouldBe g.player1Id
            }
        }

        test("casting your exiled card triggers too, and the window closes as your next end step begins") {
            val g = roccoGame()
            g.passUntilPhase(Phase.ENDING, Step.END)
            g.resolveStack()
            val bears = g.exiled(g.player1Id, "Grizzly Bears")
            val mountain = g.exiled(g.player2Id, "Mountain")

            g.passUntilTurnOf(g.player1Id)
            g.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            g.castSpellFromExile(1, "Grizzly Bears").error shouldBe null
            g.resolveTargetingRocco()
            g.resolveStack()
            g.isOnBattlefield("Grizzly Bears") shouldBe true
            g.roccoCounters() shouldBe 1
            g.findPermanents("Food").size shouldBe 1

            g.passUntilPhase(Phase.ENDING, Step.END)
            withClue("the opponent's unplayed Mountain stopped being playable as Rocco's controller's end step began") {
                g.mayPlay(g.player2Id, mountain) shouldBe false
            }
            g.resolveStack()
            withClue("and the new end-step trigger handed out a fresh round") {
                g.mayPlay(g.player1Id, g.exiled(g.player1Id, "Island")) shouldBe true
                g.mayPlay(g.player2Id, g.exiled(g.player2Id, "Island")) shouldBe true
            }
            g.state.getExile(g.player2Id).contains(mountain) shouldBe true
            g.mayPlay(g.player1Id, bears) shouldBe false
        }
    }
}
