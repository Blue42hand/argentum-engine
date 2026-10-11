package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TurnTheEarthScenarioTest : ScenarioTestBase() {
    private fun board(flashback: Boolean = false): TestGame = scenario().withPlayers()
        .apply {
            if (flashback) withCardInGraveyard(1, "Turn the Earth")
            else withCardInHand(1, "Turn the Earth")
        }
        .withCardInGraveyard(1, "Grizzly Bears")
        .withCardInGraveyard(1, "Forest")
        .withCardInGraveyard(2, "Island")
        .withCardInGraveyard(2, "Mountain")
        .withCardInHand(2, "Coffin Purge")
        .withCardInHand(2, "Counterspell")
        .withLandsOnBattlefield(1, "Forest", 2)
        .withLandsOnBattlefield(2, "Swamp", 1)
        .withLandsOnBattlefield(2, "Island", 2)
        .withCardInLibrary(1, "Forest")
        .withCardInLibrary(1, "Forest")
        .withCardInLibrary(2, "Island")
        .withCardInLibrary(2, "Island")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun TestGame.target(owner: Int, name: String): ChosenTarget.Card =
        ChosenTarget.Card(findCardsInGraveyard(owner, name).single(),
            if (owner == 1) player1Id else player2Id, Zone.GRAVEYARD)

    private fun TestGame.cast(targets: List<ChosenTarget> = emptyList(), flashback: Boolean = false) =
        execute(CastSpell(player1Id,
            if (flashback) findCardsInGraveyard(1, "Turn the Earth").single()
            else findCardsInHand(1, "Turn the Earth").single(),
            targets = targets, useAlternativeCost = flashback,
            alternativeCostType = if (flashback) AlternativeCostType.FLASHBACK else null))

    init {
        test("choosing zero targets gains two life without shuffling either library") {
            val game = board()
            val ownLibrary = game.state.getLibrary(game.player1Id)
            val otherLibrary = game.state.getLibrary(game.player2Id)
            game.cast().error shouldBe null
            val events = game.resolveStack().flatMap { it.events }
            game.getLifeTotal(1) shouldBe 22
            game.getLifeTotal(2) shouldBe 20
            events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 0
            game.state.getLibrary(game.player1Id) shouldBe ownLibrary
            game.state.getLibrary(game.player2Id) shouldBe otherLibrary
            game.isInGraveyard(1, "Turn the Earth") shouldBe true
        }

        test("three mixed-owner cards return to their owners and each affected library shuffles once") {
            val game = board()
            val ours = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val forest = game.findCardsInGraveyard(1, "Forest").single()
            val theirs = game.findCardsInGraveyard(2, "Island").single()
            game.cast(listOf(game.target(1, "Grizzly Bears"), game.target(1, "Forest"), game.target(2, "Island"))).error shouldBe null
            val shuffles = game.resolveStack().flatMap { it.events }.filterIsInstance<LibraryShuffledEvent>()
            (ours in game.state.getLibrary(game.player1Id)) shouldBe true
            (forest in game.state.getLibrary(game.player1Id)) shouldBe true
            (theirs in game.state.getLibrary(game.player2Id)) shouldBe true
            (theirs in game.state.getLibrary(game.player1Id)) shouldBe false
            shuffles.groupingBy { it.playerId }.eachCount() shouldBe mapOf(game.player1Id to 1, game.player2Id to 1)
            game.getLifeTotal(1) shouldBe 22
            game.findCardsInGraveyard(2, "Mountain").size shouldBe 1
        }

        test("one opposing target shuffles only that opponent's library") {
            val game = board()
            game.cast(listOf(game.target(2, "Island"))).error shouldBe null
            val shuffles = game.resolveStack().flatMap { it.events }.filterIsInstance<LibraryShuffledEvent>()
            shuffles.map { it.playerId } shouldBe listOf(game.player2Id)
            game.getLifeTotal(1) shouldBe 22
        }

        test("a target exiled in response is skipped while another legal target resolves and gains life") {
            val game = board()
            val removed = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val remaining = game.findCardsInGraveyard(2, "Island").single()
            game.cast(listOf(game.target(1, "Grizzly Bears"), game.target(2, "Island"))).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingGraveyardCard(2, "Coffin Purge", 1, "Grizzly Bears").error shouldBe null
            val shuffles = game.resolveStack().flatMap { it.events }.filterIsInstance<LibraryShuffledEvent>()
            (removed in game.state.getExile(game.player1Id)) shouldBe true
            (remaining in game.state.getLibrary(game.player2Id)) shouldBe true
            shuffles.map { it.playerId } shouldBe listOf(game.player2Id)
            game.getLifeTotal(1) shouldBe 22
        }

        test("all targets becoming illegal prevents both shuffling and life gain") {
            val game = board()
            game.cast(listOf(game.target(1, "Grizzly Bears"))).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingGraveyardCard(2, "Coffin Purge", 1, "Grizzly Bears").error shouldBe null
            val events = game.resolveStack().flatMap { it.events }
            events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 0
            game.getLifeTotal(1) shouldBe 20
            game.isInGraveyard(1, "Turn the Earth") shouldBe true
        }

        test("four targets are rejected without paying mana or moving the spell") {
            val game = board()
            game.cast(listOf(game.target(1, "Grizzly Bears"), game.target(1, "Forest"),
                game.target(2, "Island"), game.target(2, "Mountain"))).error shouldNotBe null
            game.isInHand(1, "Turn the Earth") shouldBe true
            game.findPermanents("Forest").count { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe 0
        }

        test("flashback costs one generic and one green and exiles the resolving spell") {
            val game = board(flashback = true)
            game.cast(flashback = true).error shouldBe null
            game.findPermanents("Forest").count { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe 2
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 22
            game.isInExile(1, "Turn the Earth") shouldBe true
            game.isInGraveyard(1, "Turn the Earth") shouldBe false
        }

        test("countering a flashback spell exiles it and grants no life") {
            val game = board(flashback = true)
            game.cast(flashback = true).error shouldBe null
            val spell = game.state.stack.single()
            game.passPriority().error shouldBe null
            game.execute(CastSpell(game.player2Id, game.findCardsInHand(2, "Counterspell").single(),
                targets = listOf(ChosenTarget.Spell(spell)))).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.isInExile(1, "Turn the Earth") shouldBe true
        }
    }
}
