package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AbilityTriggeredEvent
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class YahenniUndyingPartisanScenarioTest : ScenarioTestBase() {
    private fun TestGame.countersOnYahenni(): Int {
        val yahenni = findPermanent("Yahenni, Undying Partisan")!!
        return state.getEntity(yahenni)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    private fun TestGame.sacrificeForIndestructible(fodderName: String = "Grizzly Bears") {
        val yahenni = findPermanent("Yahenni, Undying Partisan")!!
        val fodder = findPermanents(fodderName).single { it in state.getBattlefield(player1Id) }
        val ability = cardRegistry.requireCard("Yahenni, Undying Partisan").activatedAbilities.single().id
        execute(ActivateAbility(player1Id, yahenni, ability,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
        )).error shouldBe null
        (fodder in state.getGraveyard(player1Id)) shouldBe true
        resolveStack()
        state.projectedState.hasKeyword(yahenni, Keyword.INDESTRUCTIBLE) shouldBe true
    }

    init {
        test("an opposing creature's death adds one counter and increases both stats") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!

            game.castSpell(1, "Murder", game.findPermanent("Grizzly Bears")).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.countersOnYahenni() shouldBe 1
            game.state.projectedState.getPower(yahenni) shouldBe 3
            game.state.projectedState.getToughness(yahenni) shouldBe 3
        }

        test("a friendly creature's death gives Yahenni no counter") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

            game.castSpell(1, "Murder", game.findPermanent("Grizzly Bears")).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.countersOnYahenni() shouldBe 0
        }

        test("sacrificing another creature is an immediate cost and protects Yahenni from destruction") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(2, "Murder")
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!

            game.sacrificeForIndestructible()
            game.passPriority().error shouldBe null
            game.castSpell(2, "Murder", yahenni).error shouldBe null
            game.resolveStack()

            (yahenni in game.state.getBattlefield(game.player1Id)) shouldBe true
            game.countersOnYahenni() shouldBe 0
        }

        test("Yahenni cannot pay its own another-creature sacrifice cost") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!
            val ability = cardRegistry.requireCard("Yahenni, Undying Partisan").activatedAbilities.single().id

            game.execute(ActivateAbility(game.player1Id, yahenni, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(yahenni))
            )).error shouldNotBe null
            game.isOnBattlefield("Yahenni, Undying Partisan") shouldBe true
            game.state.projectedState.hasKeyword(yahenni, Keyword.INDESTRUCTIBLE) shouldBe false
        }

        for (invalid in listOf("Swamp", "opponent's creature")) {
            test("$invalid cannot pay Yahenni's sacrifice cost") {
                val game = scenario().withPlayers()
                    .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                    .withCardOnBattlefield(1, "Swamp")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!
                val fodder = game.findPermanent(if (invalid == "Swamp") "Swamp" else "Grizzly Bears")!!
                val ability = cardRegistry.requireCard("Yahenni, Undying Partisan").activatedAbilities.single().id

                game.execute(ActivateAbility(game.player1Id, yahenni, ability,
                    costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
                )).error shouldNotBe null
                (fodder in game.state.getBattlefield()) shouldBe true
                game.state.projectedState.hasKeyword(yahenni, Keyword.INDESTRUCTIBLE) shouldBe false
            }
        }

        test("indestructible from the sacrifice is gone on the next turn") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!

            game.sacrificeForIndestructible()
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.state.activePlayerId shouldBe game.player2Id

            game.state.projectedState.hasKeyword(yahenni, Keyword.INDESTRUCTIBLE) shouldBe false
        }

        test("a board wipe destroys two opposing creatures, but protected Yahenni gets two counters") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInHand(1, "Damnation")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!

            game.sacrificeForIndestructible()
            game.castSpell(1, "Damnation").error shouldBe null
            game.resolveStack()

            (yahenni in game.state.getBattlefield(game.player1Id)) shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe true
            game.countersOnYahenni() shouldBe 2
            game.state.projectedState.getPower(yahenni) shouldBe 4
        }

        test("opponent's creature dying simultaneously with Yahenni triggers once but cannot save Yahenni") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Yahenni, Undying Partisan")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Damnation")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!

            game.castSpell(1, "Damnation").error shouldBe null
            val events = game.resolveStack().flatMap { it.events }

            game.isInGraveyard(1, "Yahenni, Undying Partisan") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            events.filterIsInstance<AbilityTriggeredEvent>().count { it.sourceId == yahenni } shouldBe 1
            (game.state.getEntity(yahenni)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        }

        test("haste lets Yahenni attack on the turn it is cast") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Yahenni, Undying Partisan")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

            game.castSpell(1, "Yahenni, Undying Partisan").error shouldBe null
            game.resolveStack()
            val yahenni = game.findPermanent("Yahenni, Undying Partisan")!!
            game.state.projectedState.hasKeyword(yahenni, Keyword.HASTE) shouldBe true
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Yahenni, Undying Partisan" to 2)).error shouldBe null
        }
    }
}
