package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.msc.cards.DoraMilajeElite
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DoraMilajeEliteScenarioTest : ScenarioTestBase() {
    private val source = "Dora Milaje Elite"

    private fun board(opponentLands: Int = 7) = scenario().withPlayers()
        .withCardInHand(1, source)
        .withCardInHand(1, "Boomerang")
        .withCardInHand(1, "Unsummon")
        .withLandsOnBattlefield(1, "Plains", 4)
        .withLandsOnBattlefield(1, "Island", 2)
        .withLandsOnBattlefield(2, "Forest", opponentLands)
        .withCardInLibrary(1, "Plains")
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(2, "Forest")
        .withCardInLibrary(2, "Mountain")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun resolve(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
        game.hasPendingDecision() shouldBe false
        game.state.stack.size shouldBe 0
    }

    private fun castDora(game: TestGame) {
        game.castSpell(1, source).error shouldBe null
        if (game.hasPendingDecision()) game.submitManaSourcesAutoPay().error shouldBe null
    }

    private fun leaveEnterTriggerOnStack(game: TestGame) {
        castDora(game)
        game.passPriority().error shouldBe null
        game.passPriority().error shouldBe null
        game.findPermanent(source) shouldNotBe null
        game.state.stack.size shouldBe 1
    }

    private fun sacrifice(game: TestGame) {
        val id = game.findPermanent(source)!!
        val ability = cardRegistry.requireCard(source).activatedAbilities.single().id
        game.execute(ActivateAbility(game.player1Id, id, ability)).error shouldBe null
        game.findPermanent(source) shouldBe null
        game.state.getGraveyard(game.player1Id).contains(id) shouldBe true
        game.state.stack.size shouldBe 1
    }

    private fun protectionBoard() = board()
        .withCardOnBattlefield(1, source)
        .withCardOnBattlefield(1, "Krenko, Mob Boss")
        .withCardOnBattlefield(1, "Mox Opal")
        .withCardOnBattlefield(1, "Inventors' Fair")
        .withCardOnBattlefield(1, "Sol Ring")
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withCardOnBattlefield(2, "Krenko, Mob Boss")

    private fun assertProtected(game: TestGame, name: String, value: Boolean) {
        game.state.projectedState.hasKeyword(game.findPermanent(name)!!, Keyword.INDESTRUCTIBLE) shouldBe value
    }

    private fun multiplayer(opponentLands: List<Int>): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + PredefinedTokens.allTokens + DoraMilajeElite)
        val players = driver.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        repeat(3) { driver.putLandOnBattlefield(players[0], "Plains") }
        opponentLands.forEachIndexed { i, n -> repeat(n) { driver.putLandOnBattlefield(players[i + 1], "Plains") } }
        val card = driver.putCardInHand(players[0], source)
        driver.giveMana(players[0], Color.WHITE, 2)
        driver.castSpell(players[0], card).error shouldBe null
        var passes = 0
        while (driver.stackSize > 0 && passes++ < 18) driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.stackSize shouldBe 0
        return driver
    }

    init {
        test("entering while behind creates exactly one tapped indestructible Vibranium and preserves first strike") {
            val game = board().build()
            castDora(game)
            resolve(game)
            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 1
            game.state.getEntity(tokens.single())!!.has<TokenComponent>() shouldBe true
            game.state.getEntity(tokens.single())!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.hasKeyword(tokens.single(), Keyword.INDESTRUCTIBLE) shouldBe true
            game.state.projectedState.hasKeyword(game.findPermanent(source)!!, Keyword.FIRST_STRIKE) shouldBe true
        }

        test("equal land counts do not trigger") {
            val game = board(6).build()
            castDora(game)
            resolve(game)
            game.findPermanents("Vibranium").size shouldBe 0
        }

        test("being ahead on lands does not trigger") {
            val game = board(5).build()
            castDora(game)
            resolve(game)
            game.findPermanents("Vibranium").size shouldBe 0
        }

        test("the intervening condition is rechecked when an opponent land is bounced in response") {
            val game = board().build()
            leaveEnterTriggerOnStack(game)
            val land = game.findPermanents("Forest").first()
            game.castSpell(1, "Boomerang", land).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay().error shouldBe null
            resolve(game)
            game.findPermanents("Vibranium").size shouldBe 0
            game.findPermanents("Forest").size shouldBe 6
        }

        test("the enters trigger resolves after its source is bounced") {
            val game = board().build()
            leaveEnterTriggerOnStack(game)
            game.castSpell(1, "Unsummon", game.findPermanent(source)!!).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay().error shouldBe null
            resolve(game)
            game.findPermanent(source) shouldBe null
            game.findPermanents("Vibranium").size shouldBe 1
        }

        test("two smaller opponent land counts are not incorrectly summed in a three-seat game") {
            val driver = multiplayer(listOf(2, 2))
            driver.getPermanents(driver.player1).count { driver.getCardName(it) == "Vibranium" } shouldBe 0
        }

        test("one individually larger opponent creates a Vibranium in a three-seat game") {
            val driver = multiplayer(listOf(2, 4))
            driver.getPermanents(driver.player1).count { driver.getCardName(it) == "Vibranium" } shouldBe 1
        }

        test("sacrifice is paid immediately and protects controlled legendary creatures artifacts and lands only") {
            val game = protectionBoard().build()
            val opponent = game.findPermanents("Krenko, Mob Boss").single { game.state.projectedState.getController(it) == game.player2Id }
            sacrifice(game)
            assertProtected(game, "Mox Opal", false)
            resolve(game)
            val own = game.findPermanents("Krenko, Mob Boss").single { it != opponent }
            game.state.projectedState.hasKeyword(own, Keyword.INDESTRUCTIBLE) shouldBe true
            game.state.projectedState.hasKeyword(opponent, Keyword.INDESTRUCTIBLE) shouldBe false
            assertProtected(game, "Mox Opal", true)
            assertProtected(game, "Inventors' Fair", true)
            assertProtected(game, "Sol Ring", false)
            assertProtected(game, "Grizzly Bears", false)
        }

        test("indestructible prevents destruction but expires at cleanup") {
            val game = protectionBoard().withCardInHand(1, "Wrath of God").build()
            sacrifice(game)
            resolve(game)
            game.castSpell(1, "Wrath of God").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay().error shouldBe null
            resolve(game)
            game.findPermanents("Krenko, Mob Boss").size shouldBe 1
            game.findPermanent("Grizzly Bears") shouldBe null
            game.passUntilPhase(Phase.ENDING, Step.CLEANUP)
            assertProtected(game, "Krenko, Mob Boss", false)
            assertProtected(game, "Mox Opal", false)
            assertProtected(game, "Inventors' Fair", false)
        }

        test("sacrificing with no controlled legendary permanents still resolves") {
            val game = board().withCardOnBattlefield(1, source).build()
            sacrifice(game)
            resolve(game)
            game.findPermanents("Vibranium").size shouldBe 0
        }

        test("first strike kills a two-toughness blocker before it damages Dora") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + PredefinedTokens.allTokens + DoraMilajeElite)
            driver.initMirrorMatch(Deck.of("Plains" to 40))
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val attacker = driver.putCreatureOnBattlefield(driver.player1, source)
            val blocker = driver.putCreatureOnBattlefield(driver.player2, "Grizzly Bears")
            driver.removeSummoningSickness(attacker)
            driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
            driver.declareAttackers(driver.player1, listOf(attacker), driver.player2).error shouldBe null
            driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
            driver.declareBlockers(driver.player2, mapOf(blocker to listOf(attacker))).error shouldBe null
            driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
            driver.findPermanent(driver.player1, source) shouldNotBe null
            driver.findPermanent(driver.player2, "Grizzly Bears") shouldBe null
        }
    }
}
