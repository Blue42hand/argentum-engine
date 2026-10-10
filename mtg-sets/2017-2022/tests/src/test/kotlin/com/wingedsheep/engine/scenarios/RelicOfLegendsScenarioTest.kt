package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Relic of Legends has two independent mana abilities; only the first taps the Relic itself. */
class RelicOfLegendsScenarioTest : ScenarioTestBase() {
    private val relicAbilities by lazy { cardRegistry.requireCard("Relic of Legends").activatedAbilities }

    init {
        test("tapping Relic itself adds each of the five colors immediately without using the stack") {
            for (color in listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)) {
                val game = scenario().withPlayers()
                    .withCardOnBattlefield(1, "Relic of Legends")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val relic = game.findPermanent("Relic of Legends")!!

                val result = game.execute(
                    ActivateAbility(game.player1Id, relic, relicAbilities[0].id, manaColorChoice = color)
                )
                withClue("Relic's first ability must add $color: ${result.error}") {
                    result.error shouldBe null
                    game.state.getEntity(relic)?.has<TappedComponent>() shouldBe true
                    val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                    pool.getAmount(color) shouldBe 1
                    pool.total shouldBe 1
                    game.state.stack.size shouldBe 0
                }
            }
        }

        test("a legendary creature pays the second ability even when Relic is tapped") {
            for (color in listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)) {
                val game = scenario().withPlayers()
                    .withCardOnBattlefield(1, "Relic of Legends", tapped = true)
                    .withCardOnBattlefield(1, "Ragavan, Nimble Pilferer", summoningSickness = true)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val relic = game.findPermanent("Relic of Legends")!!
                val ragavan = game.findPermanent("Ragavan, Nimble Pilferer")!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = relic,
                        abilityId = relicAbilities[1].id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(ragavan)),
                        manaColorChoice = color
                    )
                )
                withClue("The newly controlled legend can pay for $color: ${result.error}") {
                    result.error shouldBe null
                    game.state.getEntity(relic)?.has<TappedComponent>() shouldBe true
                    game.state.getEntity(ragavan)?.has<TappedComponent>() shouldBe true
                    val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                    pool.getAmount(color) shouldBe 1
                    pool.total shouldBe 1
                    game.state.stack.size shouldBe 0
                }
            }
        }

        test("nonlegendary, opposing, and already tapped creatures cannot pay the second ability") {
            val cases = listOf(
                Triple("nonlegendary", 1, "Grizzly Bears"),
                Triple("opposing legend", 2, "Ragavan, Nimble Pilferer"),
                Triple("tapped legend", 1, "Ragavan, Nimble Pilferer")
            )
            for ((reason, owner, candidateName) in cases) {
                val game = scenario().withPlayers()
                    .withCardOnBattlefield(1, "Relic of Legends")
                    .withCardOnBattlefield(owner, candidateName, tapped = reason == "tapped legend")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val relic = game.findPermanent("Relic of Legends")!!
                val candidate = game.findPermanent(candidateName)!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = relic,
                        abilityId = relicAbilities[1].id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(candidate)),
                        manaColorChoice = Color.GREEN
                    )
                )
                withClue("$reason must be rejected as a cost") {
                    result.error shouldNotBe null
                    game.state.getEntity(relic)?.has<TappedComponent>() shouldBe false
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.total shouldBe 0
                    game.state.stack.size shouldBe 0
                }
            }
        }

        test("a printed legend that is currently a land cannot pay the creature cost") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Relic of Legends")
                .withCardOnBattlefield(1, "Ragavan, Nimble Pilferer")
                .withCardAttachedTo(1, "Imprisoned in the Moon", "Ragavan, Nimble Pilferer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val relic = game.findPermanent("Relic of Legends")!!
            val ragavan = game.findPermanent("Ragavan, Nimble Pilferer")!!
            withClue("the Aura changes current creature type, but not the legendary supertype") {
                game.state.projectedState.isLegendary(ragavan) shouldBe true
                game.state.projectedState.isCreature(ragavan) shouldBe false
            }

            val result = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = relic,
                    abilityId = relicAbilities[1].id,
                    costPayment = AdditionalCostPayment(tappedPermanents = listOf(ragavan)),
                    manaColorChoice = Color.BLUE
                )
            )
            result.error shouldNotBe null
            game.state.getEntity(ragavan)?.has<TappedComponent>() shouldBe false
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.total shouldBe 0
        }

        test("a legendary animated Relic token can tap itself for its second mana ability") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Relic of Legends")
                .withCardOnBattlefield(1, "Adagia, Windswept Bastion")
                .withCardOnBattlefield(1, "March of the Machines")
                .withLandsOnBattlefield(1, "Plains", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val relic = game.findPermanent("Relic of Legends")!!
            val adagia = game.findPermanent("Adagia, Windswept Bastion")!!
            game.state = game.state.updateEntity(adagia) { container ->
                container.with(CountersComponent().withAdded(CounterType.CHARGE, 12))
            }
            val copyAbility = cardRegistry.requireCard("Adagia, Windswept Bastion")
                .activatedAbilities.last { !it.isManaAbility }.id

            val copyResult = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = adagia,
                    abilityId = copyAbility,
                    targets = listOf(ChosenTarget.Permanent(relic))
                )
            )
            withClue("Adagia can copy the original artifact: ${copyResult.error}") {
                copyResult.error shouldBe null
            }
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val token = game.findPermanents("Relic of Legends")
                .single { it != relic && game.state.getEntity(it)?.has<TokenComponent>() == true }
            val projected = game.state.projectedState
            withClue("Adagia makes the copy legendary and March animates it") {
                projected.isLegendary(token) shouldBe true
                projected.isCreature(token) shouldBe true
                game.state.getEntity(token)?.has<SummoningSicknessComponent>() shouldBe true
            }

            val manaResult = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = token,
                    abilityId = relicAbilities[1].id,
                    costPayment = AdditionalCostPayment(tappedPermanents = listOf(token)),
                    manaColorChoice = Color.GREEN
                )
            )
            withClue("the source can pay its own second ability's separate tap cost: ${manaResult.error}") {
                manaResult.error shouldBe null
                game.state.getEntity(token)?.has<TappedComponent>() shouldBe true
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.getAmount(Color.GREEN) shouldBe 1
                game.state.stack.size shouldBe 0
            }
        }
    }
}
