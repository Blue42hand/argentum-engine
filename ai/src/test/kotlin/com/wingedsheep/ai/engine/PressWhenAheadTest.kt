package com.wingedsheep.ai.engine

import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.seatId
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * Pressing a winning position ([AiProfile.pressWhenAhead]): the side that is ahead is credited for
 * the damage an attack deals, scaled by how far the board the combat leaves dominates ([BoardRole]).
 *
 * Vanilla creatures only. Seat 1 is the AI, declaring attackers; seat 2's creatures are its
 * would-be blockers. Each behaviour change pins its reproduction on [AiProfile.LIVE_WITHOUT_PRESSURE]
 * beside the new play on [AiProfile.LIVE], so a later change that makes the old profile attack too
 * shows up as a stale reproduction rather than a silent pass.
 */
class PressWhenAheadTest : ScenarioTestBase() {

    private companion object {
        const val SCENARIO_SEED = 20261018L
    }

    private data class Creature(val name: String, val tapped: Boolean = false)

    private fun position(
        ours: List<Creature>,
        theirs: List<Creature>,
        ourLife: Int = 20,
        theirLife: Int = 20,
    ): TestGame {
        var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
            .withLifeTotal(1, ourLife)
            .withLifeTotal(2, theirLife)
        ours.forEach { builder = builder.withCardOnBattlefield(1, it.name, tapped = it.tapped) }
        theirs.forEach { builder = builder.withCardOnBattlefield(2, it.name, tapped = it.tapped) }
        return builder.build().advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
    }

    private fun TestGame.attackersChosenBy(profile: AiProfile): Set<EntityId> =
        (AIPlayer.create(cardRegistry, seatId(1), profile).chooseAction(state) as? DeclareAttackers)
            ?.attackers?.keys.orEmpty()

    private fun TestGame.names(ids: Collection<EntityId>): List<String> =
        ids.map { state.getEntity(it)?.get<CardComponent>()?.name ?: it.value }

    private fun TestGame.ours(name: String): List<EntityId> =
        state.getBattlefield(seatId(1)).filter { state.getEntity(it)?.get<CardComponent>()?.name == name }

    private fun TestGame.dominance(seat: Int): Double {
        val opponent = state.turnOrder.first { it != seatId(seat) }
        return BoardRole.dominance(state, state.projectedState, seatId(seat), opponent)
    }

    private fun bears(n: Int) = List(n) { Creature("Grizzly Bears") }

    init {
        // The reported shape: "it just sits there, giving the player the opportunity to grow in
        // strength again". Eight power against one 3/3, both on 20. Sending the team loses a Bear
        // to the Giant's block and deals six. A lone Bear is the worst attack on the board, so the
        // one-attacker-at-a-time search never climbed to the swarm, and the swarm itself scored
        // under passing: six damage into 20 life is priced on the defender's half-point-a-life curve.
        test("four Bears facing a lone Hill Giant swing as a team, trading a Bear for six damage") {
            val game = position(ours = bears(4), theirs = listOf(Creature("Hill Giant")))

            val before = game.attackersChosenBy(AiProfile.LIVE_WITHOUT_PRESSURE)
            withClue("reproduction: attacked with ${game.names(before)}") { before.shouldBeEmpty() }

            val after = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(after)}") {
                after shouldBe game.ours("Grizzly Bears").toSet()
            }
        }

        // Even trades favour the larger army: Hill Giant for Hill Giant leaves Serra Angel and a
        // Giant against an empty board. Without pressure only the Angel, which nothing blocks, goes.
        test("ahead by a Serra Angel, the Giants trade with theirs instead of waiting") {
            val game = position(
                ours = listOf(Creature("Serra Angel"), Creature("Hill Giant"), Creature("Hill Giant")),
                theirs = listOf(Creature("Hill Giant"), Creature("Hill Giant")),
            )

            val before = game.attackersChosenBy(AiProfile.LIVE_WITHOUT_PRESSURE)
            withClue("reproduction: attacked with ${game.names(before)}") {
                game.names(before) shouldBe listOf("Serra Angel")
            }

            val after = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(after)}") {
                after shouldContainAll game.ours("Hill Giant") + game.ours("Serra Angel")
            }
        }

        test("behind on board, it stays home") {
            val game = position(
                ours = bears(2),
                theirs = listOf(Creature("Hill Giant"), Creature("Craw Wurm")),
                ourLife = 8,
            )
            val attackers = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(attackers)}") { attackers.shouldBeEmpty() }
        }

        test("at parity, it stays home") {
            val game = position(
                ours = listOf(Creature("Hill Giant"), Creature("Grizzly Bears")),
                theirs = listOf(Creature("Hill Giant"), Creature("Grizzly Bears")),
            )
            val attackers = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(attackers)}") { attackers.shouldBeEmpty() }
        }

        // The same four Bears, at 4 life, against a Giant and a Wind Drake that untaps for the
        // swing back. More material is not the lead here: the Drake alone takes half our life a
        // turn, so the race reads us as the one behind, and sending all four would leave nothing to
        // block the Giant — 3 + 2 is lethal. Pressure never buys a lethal crack-back: a plan whose
        // crack-back kills earns no credit, and here the board earns none to begin with.
        test("more creatures at 4 life facing a lethal crack-back is not a lead to press") {
            val game = position(
                ours = bears(4),
                theirs = listOf(Creature("Hill Giant"), Creature("Wind Drake", tapped = true)),
                ourLife = 4,
            )
            withClue("the race reads the low life total") { game.dominance(1) shouldBeLessThan 0.0 }

            val attackers = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(attackers)}") { attackers.size shouldBeLessThan 4 }
        }

        test("dominance is antisymmetric, and an even trade grows the leader's") {
            val ahead = position(ours = bears(4), theirs = bears(2))
            val leader = ahead.dominance(1)
            leader shouldBeGreaterThan 0.0
            ahead.dominance(2) shouldBe (-leader plusOrMinus 1e-9)

            position(ours = bears(3), theirs = bears(1)).dominance(1) shouldBeGreaterThan leader

            position(ours = bears(1), theirs = listOf(Creature("Craw Wurm"))).dominance(1) shouldBeLessThan 0.0
        }
    }
}
