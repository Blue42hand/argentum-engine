package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.rtr.cards.Vandalblast
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VandalblastScenarioTest : ScenarioTestBase() {
    private val ownArtifact = card("Vandalblast Own Artifact") { typeLine = "Artifact" }
    private val otherArtifact = card("Vandalblast Other Artifact") { typeLine = "Artifact" }
    private val secondArtifact = card("Vandalblast Second Artifact") { typeLine = "Artifact" }
    private val hexproofArtifact = card("Vandalblast Hexproof Artifact") {
        typeLine = "Artifact"
        keywords(Keyword.HEXPROOF)
    }
    private val indestructibleArtifact = card("Vandalblast Indestructible Artifact") {
        typeLine = "Artifact"
        keywords(Keyword.INDESTRUCTIBLE)
    }

    private fun board(mountains: Int = 5) = scenario()
        .withPlayers("Caster", "Other")
        .withCardInHand(1, "Vandalblast")
        .withLandsOnBattlefield(1, "Mountain", mountains)
        .withCardOnBattlefield(1, ownArtifact.name)
        .withCardOnBattlefield(2, otherArtifact.name)
        .withCardOnBattlefield(2, secondArtifact.name)
        .withCardOnBattlefield(2, hexproofArtifact.name)
        .withCardOnBattlefield(2, indestructibleArtifact.name)
        .withCardOnBattlefield(2, "Grizzly Bears")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    init {

        test("overload benefits from battlefield cost reductions") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Vandalblast")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardOnBattlefield(1, "Ruby Medallion")
                .withCardOnBattlefield(2, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            withClue("Ruby Medallion reduces the overload alternative cost to {3}{R}") {
                game.castSpellWithOverload(1, "Vandalblast").error shouldBe null
            }
            game.resolveStack()
            game.isInGraveyard(2, "Sol Ring") shouldBe true
        }

        listOf(ownArtifact, otherArtifact, secondArtifact, hexproofArtifact, indestructibleArtifact)
            .forEach { cardRegistry.register(it) }

        test("normal casting destroys only the targeted artifact for one red mana") {
            val game = board(mountains = 1)
            game.castSpell(1, "Vandalblast", targetId = game.findPermanent(otherArtifact.name)!!).error shouldBe null
            game.resolveStack()
            game.isInGraveyard(2, otherArtifact.name) shouldBe true
            game.isOnBattlefield(secondArtifact.name) shouldBe true
            game.isOnBattlefield(ownArtifact.name) shouldBe true
            game.isOnBattlefield(hexproofArtifact.name) shouldBe true
        }

        test("normal casting rejects your own artifact") {
            val game = board()
            game.castSpell(1, "Vandalblast", targetId = game.findPermanent(ownArtifact.name)!!).error shouldNotBe null
            game.isOnBattlefield(ownArtifact.name) shouldBe true
        }

        test("normal casting cannot target a hexproof artifact you do not control") {
            val game = board()
            game.castSpell(1, "Vandalblast", targetId = game.findPermanent(hexproofArtifact.name)!!).error shouldNotBe null
            game.isOnBattlefield(hexproofArtifact.name) shouldBe true
        }

        test("overload destroys each other artifact without targeting and preserves your own") {
            val game = board()
            game.castSpellWithOverload(1, "Vandalblast").error shouldBe null
            game.resolveStack()
            listOf(otherArtifact, secondArtifact, hexproofArtifact).forEach {
                game.isInGraveyard(2, it.name) shouldBe true
            }
            game.isOnBattlefield(ownArtifact.name) shouldBe true
            game.isOnBattlefield(indestructibleArtifact.name) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }

        test("overload can be cast with no artifacts you do not control") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Vandalblast")
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withCardOnBattlefield(1, ownArtifact.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpellWithOverload(1, "Vandalblast").error shouldBe null
            game.resolveStack()
            game.isOnBattlefield(ownArtifact.name) shouldBe true
            game.isInGraveyard(1, "Vandalblast") shouldBe true
        }

        for (overloaded in listOf(false, true)) {
            test("teammate artifacts qualify when overloaded is $overloaded") {
                val driver = GameTestDriver()
                driver.registerCards(TestCards.all + listOf(Vandalblast, ownArtifact, otherArtifact))
                val seats = driver.initMultiplayer(
                    decks = List(4) { Deck.of("Mountain" to 40) },
                    format = Format.TwoHeadedGiant(),
                    teams = listOf(listOf(0, 1), listOf(2, 3)),
                )
                driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
                val you = seats[0]
                val teammate = seats[1]
                val own = driver.putPermanentOnBattlefield(you, ownArtifact.name)
                val allied = driver.putPermanentOnBattlefield(teammate, otherArtifact.name)
                val opposing = driver.putPermanentOnBattlefield(seats[2], otherArtifact.name)
                val spell = driver.putCardInHand(you, "Vandalblast")
                driver.giveMana(you, Color.RED, 5)
                val result = if (overloaded) driver.submit(CastSpell(
                    you, spell,
                    useAlternativeCost = true,
                    alternativeCostType = AlternativeCostType.OVERLOAD,
                    paymentStrategy = PaymentStrategy.FromPool,
                )) else driver.castSpell(you, spell, targets = listOf(allied))
                result.error shouldBe null
                var passes = 0
                while (driver.state.stack.isNotEmpty() && passes++ < 20) {
                    driver.passPriority(driver.state.priorityPlayerId!!).error shouldBe null
                }
                driver.state.stack.isEmpty() shouldBe true
                driver.getGraveyard(teammate).contains(allied) shouldBe true
                driver.getGraveyard(you).contains(own) shouldBe false
                driver.findPermanent(you, ownArtifact.name) shouldBe own
                driver.getGraveyard(seats[2]).contains(opposing) shouldBe overloaded
            }
        }
    }
}
