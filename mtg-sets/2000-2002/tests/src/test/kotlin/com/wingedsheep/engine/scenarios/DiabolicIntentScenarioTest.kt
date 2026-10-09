package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.engine.state.components.player.CantSearchLibrariesComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DiabolicIntentScenarioTest : ScenarioTestBase() {
    private val library = listOf("Swamp", "Dark Ritual", "Sol Ring", "Grizzly Bears")

    private fun board(cards: List<String> = library): TestGame = scenario()
        .withPlayers()
        .withCardInHand(1, "Diabolic Intent")
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withLandsOnBattlefield(1, "Swamp", 2)
        .withCardInLibrary(2, "Island")
        .apply { cards.forEach { withCardInLibrary(1, it) } }
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build().also { it.state = it.state.copy(rng = GameRng.seeded(42)) }

    private fun TestGame.resolveIntent() {
        castSpellWithAdditionalSacrifice(1, "Diabolic Intent", "Grizzly Bears").error.shouldBeNull()
        isInGraveyard(1, "Grizzly Bears") shouldBe true
        resolveStack()
    }

    init {
        library.forEach { name ->
            test("can find $name alongside other card types") {
                val game = board()
                val expected = library.flatMap { game.findCardsInLibrary(1, it) }.toSet()
                val selected = game.findCardsInLibrary(1, name).single()
                val opponentCard = game.findCardsInLibrary(2, "Island").single()
                game.resolveIntent()
                val decision = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                decision.options.toSet() shouldBe expected
                decision.minSelections shouldBe 1
                decision.maxSelections shouldBe 1
                game.selectCards(listOf(opponentCard)).error.shouldNotBeNull()
                game.selectCards(emptyList()).error.shouldNotBeNull()
                game.selectCards(decision.options.take(2)).error.shouldNotBeNull()
                val result = game.selectCards(listOf(selected))
                result.error.shouldBeNull()
                result.events.any { it is LibraryShuffledEvent } shouldBe true
                game.findCardsInHand(1, name) shouldBe listOf(selected)
                game.state.pendingDecision.shouldBeNull()
                game.isInGraveyard(1, "Diabolic Intent") shouldBe true
            }
        }

        test("a lone noncreature card is found automatically") {
            val game = board(listOf("Sol Ring"))
            game.resolveIntent()
            game.findCardsInHand(1, "Sol Ring").size shouldBe 1
            game.state.pendingDecision.shouldBeNull()
        }

        test("an empty library resolves without a selection") {
            val game = board(emptyList())
            game.resolveIntent()
            game.state.pendingDecision.shouldBeNull()
            game.isInGraveyard(1, "Diabolic Intent") shouldBe true
        }

        test("a search prohibition still prevents finding any card") {
            val game = board()
            game.state = game.state.updateEntity(game.player1Id) { it.with(CantSearchLibrariesComponent()) }
            game.resolveIntent()
            game.state.pendingDecision.shouldBeNull()
            library.forEach { game.findCardsInHand(1, it).size shouldBe 0 }
            game.state.getLibrary(game.player1Id).size shouldBe library.size
        }

        test("the additional cost still rejects a noncreature sacrifice") {
            val game = board()
            val land = game.findPermanents("Swamp").first()
            val spell = game.findCardsInHand(1, "Diabolic Intent").single()
            game.execute(CastSpell(game.player1Id, spell, additionalCostPayment =
                AdditionalCostPayment(sacrificedPermanents = listOf(land)))).error.shouldNotBeNull()
            game.execute(CastSpell(game.player1Id, spell)).error.shouldNotBeNull()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }
    }
}
