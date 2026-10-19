package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.drk.cards.BloodMoon
import com.wingedsheep.mtg.sets.definitions.nem.cards.MoggSalvage
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoggSalvageScenarioTest : FunSpec({
    // Nonbasic lands let Blood Moon prove that both halves read projected subtypes.
    val nonbasicIsland = card("Salvage Test Island") { typeLine = "Land — Island" }
    val nonbasicForest = card("Salvage Test Forest") { typeLine = "Land — Forest" }

    val noncreatureArtifact = card("Salvage Test Relic") {
        manaCost = "{1}"
        typeLine = "Artifact"
    }

    fun newGame(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(MoggSalvage, BloodMoon, nonbasicIsland, nonbasicForest, noncreatureArtifact))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun freeCast(player: EntityId, spell: EntityId, artifact: EntityId) = CastSpell(
        playerId = player,
        cardId = spell,
        targets = listOf(ChosenTarget.Permanent(artifact)),
        useAlternativeCost = true,
        alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE
    )

    fun offered(driver: GameTestDriver, player: EntityId, spell: EntityId): Boolean =
        driver.legalActions(player).any {
            val action = it.action
            action is CastSpell && action.cardId == spell && action.useAlternativeCost &&
                action.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
        }

    test("free cast destroys even your own artifact and does not consume either land") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val mountain = driver.putLandOnBattlefield(you, "Mountain")
        val island = driver.putLandOnBattlefield(opponent, "Island")
        val artifact = driver.putCreatureOnBattlefield(you, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")

        offered(driver, you, spell) shouldBe true
        driver.submit(freeCast(you, spell, artifact)).error shouldBe null
        driver.bothPass()
        driver.getGraveyard(you).contains(artifact) shouldBe true
        driver.state.getBattlefield().contains(mountain) shouldBe true
        driver.state.getBattlefield().contains(island) shouldBe true
    }

    for ((mountain, island) in listOf(false to false, true to false, false to true)) {
        test("free casting is absent and rejected when Mountain=$mountain and opposing Island=$island") {
            val driver = newGame()
            val you = driver.activePlayer!!
            val opponent = driver.getOpponent(you)
            if (mountain) driver.putLandOnBattlefield(you, "Mountain")
            if (island) driver.putLandOnBattlefield(opponent, "Island")
            // Lands on the wrong side must not satisfy the missing half.
            driver.putLandOnBattlefield(you, "Island")
            driver.putLandOnBattlefield(opponent, "Mountain")
            val artifact = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
            val spell = driver.putCardInHand(you, "Mogg Salvage")

            offered(driver, you, spell) shouldBe false
            driver.submit(freeCast(you, spell, artifact)).error shouldBe
                "Alternative cost is not available: ${MoggSalvage.script.selfAlternativeCost!!.condition!!.description}"
        }
    }

    test("normal mana cost remains payable without either land condition") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val artifact = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")
        driver.giveMana(you, Color.RED, 3)

        driver.castSpell(you, spell, listOf(artifact)).error shouldBe null
        driver.bothPass()
        driver.getGraveyard(opponent).contains(artifact) shouldBe true
    }

    test("losing both required lands after casting does not stop resolution") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val mountain = driver.putLandOnBattlefield(you, "Mountain")
        val island = driver.putLandOnBattlefield(opponent, "Island")
        val artifact = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")

        driver.submit(freeCast(you, spell, artifact)).error shouldBe null
        driver.moveToGraveyard(mountain)
        driver.moveToGraveyard(island)
        driver.bothPass()
        driver.getGraveyard(opponent).contains(artifact) shouldBe true
    }

    test("a projected Mountain enables the free cost") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val land = driver.putLandOnBattlefield(you, "Salvage Test Forest")
        driver.putLandOnBattlefield(opponent, "Island")
        driver.putPermanentOnBattlefield(you, "Blood Moon")
        val artifact = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")

        driver.state.projectedState.hasSubtype(land, "Mountain") shouldBe true
        offered(driver, you, spell) shouldBe true
        driver.submit(freeCast(you, spell, artifact)).error shouldBe null
        driver.bothPass()
        driver.getGraveyard(opponent).contains(artifact) shouldBe true
    }

    test("an Island that loses its subtype disables a previously offered free cost") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        driver.putLandOnBattlefield(you, "Mountain")
        val island = driver.putLandOnBattlefield(opponent, "Salvage Test Island")
        val artifact = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")
        offered(driver, you, spell) shouldBe true

        driver.putPermanentOnBattlefield(you, "Blood Moon")
        driver.state.projectedState.hasSubtype(island, "Island") shouldBe false
        offered(driver, you, spell) shouldBe false
        driver.submit(freeCast(you, spell, artifact)).error shouldBe
            "Alternative cost is not available: ${MoggSalvage.script.selfAlternativeCost!!.condition!!.description}"
    }

    test("free casting still requires an artifact target") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val mountain = driver.putLandOnBattlefield(you, "Mountain")
        driver.putLandOnBattlefield(opponent, "Island")
        val spell = driver.putCardInHand(you, "Mogg Salvage")

        offered(driver, you, spell) shouldBe false
        (driver.submit(freeCast(you, spell, mountain)).error != null) shouldBe true
    }

    test("a noncreature artifact is legal and losing that target makes the spell fizzle") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        driver.putLandOnBattlefield(you, "Mountain")
        driver.putLandOnBattlefield(opponent, "Island")
        val artifact = driver.putPermanentOnBattlefield(opponent, "Salvage Test Relic")
        val survivor = driver.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val spell = driver.putCardInHand(you, "Mogg Salvage")

        driver.submit(freeCast(you, spell, artifact)).error shouldBe null
        driver.moveToGraveyard(artifact)
        driver.bothPass()
        driver.getGraveyard(you).contains(spell) shouldBe true
        driver.state.getBattlefield().contains(survivor) shouldBe true
        driver.stackSize shouldBe 0
    }

})
