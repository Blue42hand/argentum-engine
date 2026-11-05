package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AstralCornucopiaScenarioTest : ScenarioTestBase() {
    init {
        fun board(lands: Int) = scenario().withPlayers()
            .withCardInHand(1, "Astral Cornucopia")
            .withLandsOnBattlefield(1, "Forest", lands)
            .withCardInLibrary(1, "Forest")
            .withCardInLibrary(2, "Forest")
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

        test("X = 2 costs six mana, enters with two charge counters, and taps for two chosen-color mana") {
            val game = board(6).build()
            val card = game.findCardsInHand(1, "Astral Cornucopia").single()

            game.execute(CastSpell(game.player1Id, card, xValue = 2)).error shouldBe null
            game.resolveStack()
            val cornucopia = game.findPermanent("Astral Cornucopia")!!
            game.state.getEntity(cornucopia)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 2

            val manaAbility = cardRegistry.requireCard("Astral Cornucopia").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, cornucopia, manaAbility,
                manaColorChoice = Color.BLUE)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue shouldBe 2
            game.state.stack.isEmpty() shouldBe true
        }

        test("X = 2 cannot be cast for five mana") {
            val game = board(5).build()
            val card = game.findCardsInHand(1, "Astral Cornucopia").single()

            game.execute(CastSpell(game.player1Id, card, xValue = 2)).error shouldNotBe null
            game.findPermanent("Astral Cornucopia") shouldBe null
        }

        test("X = 0 enters with no charge counters") {
            val game = board(0).build()
            val card = game.findCardsInHand(1, "Astral Cornucopia").single()

            game.execute(CastSpell(game.player1Id, card, xValue = 0)).error shouldBe null
            game.resolveStack()
            val cornucopia = game.findPermanent("Astral Cornucopia")!!
            (game.state.getEntity(cornucopia)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) ?: 0) shouldBe 0
        }
    }
}

/**
 * Astral Cornucopia (BNG #157) — {X}{X}{X} Artifact.
 *
 *   This artifact enters with X charge counters on it.
 *   {T}: Choose a color. Add one mana of that color for each charge counter on this artifact.
 *
 * The cost carries three X pips, so the entry counters must read the announced X — not the
 * 3X mana spent — and the mana ability must scale with the counters on it.
 */
class AstralCornucopiaUpstreamScenarioTest : ScenarioTestBase() {

    init {
        test("cast for X = 2 it costs six, enters with two charge counters and taps for two of one color") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Astral Cornucopia")
                .withLandsOnBattlefield(1, "Island", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castXSpell(1, "Astral Cornucopia", 2).error shouldBe null
            game.resolveStack()

            val cornucopia = game.findPermanent("Astral Cornucopia").shouldNotBeNull()
            withClue("X = 2 means two charge counters, not six") {
                game.state.getEntity(cornucopia)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 2
            }

            val abilityId = cardRegistry.requireCard("Astral Cornucopia").script.activatedAbilities[0].id
            game.execute(
                ActivateAbility(game.player1Id, cornucopia, abilityId, manaColorChoice = Color.RED)
            ).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
            withClue("two charge counters yield two mana of the chosen color") {
                pool?.getAmount(Color.RED) shouldBe 2
            }
        }
    }
}
