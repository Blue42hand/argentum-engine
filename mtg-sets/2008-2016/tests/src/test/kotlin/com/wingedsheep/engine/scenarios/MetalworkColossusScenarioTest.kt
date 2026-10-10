package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MetalworkColossusScenarioTest : ScenarioTestBase() {
    private val source = "Metalwork Colossus"

    private fun board() = scenario().withPlayers()
        .withCardInHand(1, source)
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Forest")
        .withCardInLibrary(2, "Mountain")
        .withCardInLibrary(2, "Swamp")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun cost(game: TestGame) = CostCalculator(
        cardRegistry, predicateEvaluator = services.predicateEvaluator,
    ).calculateEffectiveCost(game.state, cardRegistry.requireCard(source), game.player1Id).genericAmount

    private fun resolve(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
        game.hasPendingDecision() shouldBe false
        game.state.stack.size shouldBe 0
    }

    private fun cast(game: TestGame) {
        game.castSpell(1, source).error shouldBe null
        if (game.state.pendingDecision is SelectManaSourcesDecision) {
            game.submitManaSourcesAutoPay().error shouldBe null
        }
        resolve(game)
        game.findPermanent(source) shouldNotBe null
    }

    private fun recover(game: TestGame, sacrifices: List<com.wingedsheep.sdk.model.EntityId>) = game.execute(
        ActivateAbility(
            game.player1Id,
            game.findCardsInGraveyard(1, source).single(),
            cardRegistry.requireCard(source).activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = sacrifices),
        ),
    )

    init {
        test("reduction sums only your noncreature artifacts and a battlefield X contributes zero") {
            val game = board()
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(1, "Worn Powerstone")
                .withCardOnBattlefield(1, "Thran Dynamo")
                .withCardOnBattlefield(1, "Everflowing Chalice")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withCardOnBattlefield(2, "Gilded Lotus")
                .withLandsOnBattlefield(1, "Island", 3)
                .build()
            cost(game) shouldBe 3
            cast(game)
            game.state.projectedState.getPower(game.findPermanent(source)!!) shouldBe 10
        }

        test("projected artifact creatures do not reduce the cost") {
            val game = board()
                .withCardOnBattlefield(1, "March of the Machines")
                .withCardOnBattlefield(1, "Thran Dynamo")
                .withLandsOnBattlefield(1, "Island", 11)
                .build()
            game.state.projectedState.isCreature(game.findPermanent("Thran Dynamo")!!) shouldBe true
            cost(game) shouldBe 11
            cast(game)
        }

        test("eleven or more total mana value permits a zero mana cast") {
            val game = board()
                .withCardOnBattlefield(1, "Dreamstone Hedron")
                .withCardOnBattlefield(1, "Gilded Lotus")
                .withCardOnBattlefield(1, "Sol Ring")
                .build()
            cost(game) shouldBe 0
            cast(game)
        }

        test("casting cost stays locked when a contributing artifact is sacrificed for mana") {
            val game = board()
                .withCardOnBattlefield(1, "Krark-Clan Ironworks")
                .withCardOnBattlefield(1, "Thran Dynamo", tapped = true)
                .withLandsOnBattlefield(1, "Island", 1)
                .build()
            cost(game) shouldBe 3
            game.castSpell(1, source).error shouldBe null
            (game.state.pendingDecision as SelectManaSourcesDecision).requiredCost shouldBe "{3}"
            val ironworks = game.findPermanent("Krark-Clan Ironworks")!!
            val dynamo = game.findPermanent("Thran Dynamo")!!
            game.execute(ActivateAbility(
                game.player1Id, ironworks,
                cardRegistry.requireCard("Krark-Clan Ironworks").activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(dynamo)),
            )).error shouldBe null
            game.findPermanent("Thran Dynamo") shouldBe null
            (game.state.pendingDecision as SelectManaSourcesDecision).requiredCost shouldBe "{3}"
            game.submitManaSourcesDecision(listOf(game.findPermanent("Island")!!)).error shouldBe null
            resolve(game)
            game.findPermanent(source) shouldNotBe null
        }

        test("two distinct artifacts are sacrificed as costs before returning from the graveyard") {
            val game = board().withCardInGraveyard(1, source)
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(1, "Mind Stone")
                .build()
            val sourceId = game.findCardsInGraveyard(1, source).single()
            val ring = game.findPermanent("Sol Ring")!!
            val stone = game.findPermanent("Mind Stone")!!
            recover(game, listOf(ring, stone)).error shouldBe null
            game.state.getGraveyard(game.player1Id).containsAll(listOf(sourceId, ring, stone)) shouldBe true
            game.findPermanent("Sol Ring") shouldBe null
            game.findPermanent("Mind Stone") shouldBe null
            game.state.stack.size shouldBe 1
            resolve(game)
            game.state.getHand(game.player1Id).contains(sourceId) shouldBe true
        }

        test("one artifact or the same artifact twice cannot pay the recovery cost") {
            val game = board().withCardInGraveyard(1, source)
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(1, "Mind Stone")
                .build()
            val ring = game.findPermanent("Sol Ring")!!
            recover(game, listOf(ring)).error shouldNotBe null
            recover(game, listOf(ring, ring)).error shouldNotBe null
            game.findPermanent("Sol Ring") shouldBe ring
            game.findPermanent("Mind Stone") shouldNotBe null
            game.state.stack.size shouldBe 0
        }

        test("recovery is graveyard only and can sacrifice artifact creatures") {
            val game = board().withCardInGraveyard(1, source)
                .withCardOnBattlefield(1, source)
                .withCardOnBattlefield(1, "Myr Retriever")
                .withCardOnBattlefield(1, "Sol Ring")
                .build()
            val artifactCreature = game.findPermanent(source)!!
            val ring = game.findPermanent("Sol Ring")!!
            game.execute(ActivateAbility(
                game.player1Id, artifactCreature,
                cardRegistry.requireCard(source).activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(artifactCreature, ring)),
            )).error shouldNotBe null
            recover(game, listOf(artifactCreature, ring)).error shouldBe null
            resolve(game)
            game.findCardsInHand(1, source).size shouldBe 2
            game.findCardsInGraveyard(1, source).size shouldBe 1
        }

        test("exiling the graveyard source in response prevents its return and costs remain paid") {
            val game = board().withCardInGraveyard(1, source)
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(1, "Mind Stone")
                .withCardOnBattlefield(1, "Tormod's Crypt")
                .build()
            val sourceId = game.findCardsInGraveyard(1, source).single()
            val ring = game.findPermanent("Sol Ring")!!
            val stone = game.findPermanent("Mind Stone")!!
            recover(game, listOf(ring, stone)).error shouldBe null
            val crypt = game.findPermanent("Tormod's Crypt")!!
            game.execute(ActivateAbility(
                game.player1Id, crypt,
                cardRegistry.requireCard("Tormod's Crypt").activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Player(game.player1Id)),
            )).error shouldBe null
            resolve(game)
            game.state.getExile(game.player1Id).containsAll(listOf(sourceId, ring, stone)) shouldBe true
            game.findCardsInHand(1, source).size shouldBe 1
        }
    }
}
