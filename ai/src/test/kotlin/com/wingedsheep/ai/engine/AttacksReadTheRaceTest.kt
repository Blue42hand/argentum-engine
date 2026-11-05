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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain

/**
 * Attack plans scored by the race ([AiProfile.attacksReadTheRace]): our next swing priced beside
 * theirs, a tapped attacker charged for its missing block once, and the crack-back checked against
 * the defender declining a block that costs it a creature.
 *
 * Built from the 2026-10-15 losses with vanilla creatures. Seat 1 is the AI, declaring attackers;
 * seat 2's creatures are its would-be blockers.
 */
class AttacksReadTheRaceTest : ScenarioTestBase() {

    private companion object {
        const val SCENARIO_SEED = 20261016L
        // [AiProfile.pressWhenAhead] is off too: on its own it also sends the team in 42518dd1
        // (a dominant board pressing a player on 6), so it would hide the reproduction.
        val WITHOUT = AiProfile.LIVE.copy(id = "live-without-race", attacksReadTheRace = false, pressWhenAhead = false)
    }

    private data class Creature(val name: String, val tapped: Boolean = false, val sick: Boolean = false)

    private fun position(
        ours: List<Creature>,
        theirs: List<Creature>,
        ourLife: Int,
        theirLife: Int,
    ): TestGame {
        var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
            .withLifeTotal(1, ourLife)
            .withLifeTotal(2, theirLife)
        ours.forEach { builder = builder.withCardOnBattlefield(1, it.name, tapped = it.tapped, summoningSickness = it.sick) }
        theirs.forEach { builder = builder.withCardOnBattlefield(2, it.name, tapped = it.tapped, summoningSickness = it.sick) }
        return builder.build().advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
    }

    private fun TestGame.attackersChosenBy(profile: AiProfile): Set<EntityId> =
        (AIPlayer.create(cardRegistry, seatId(1), profile).chooseAction(state) as? DeclareAttackers)
            ?.attackers?.keys.orEmpty()

    private fun TestGame.names(ids: Collection<EntityId>): List<String> =
        ids.map { state.getEntity(it)?.get<CardComponent>()?.name ?: it.value }

    init {
        test("a vigilant fatty attacks into a smaller board") {
            val game = position(
                ours = listOf(Creature("Vigilant Baloth")),
                theirs = listOf(Creature("Grizzly Bears"), Creature("Hill Giant")),
                ourLife = 20,
                theirLife = 20,
            )
            val attackers = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(attackers)}") {
                attackers shouldContain game.findPermanent("Vigilant Baloth")!!
            }
        }

        // Game 42518dd1 turn 17: 17–6 up, six power against one untapped 3/3 reach blocker, and
        // no attack. Sending the team costs a flier and leaves the opponent on 2 facing a board
        // that kills them through both their blockers next turn.
        test("42518dd1 turn 17: clearly ahead, the team attacks past a lone reach blocker") {
            val game = position(
                ours = listOf(
                    Creature("Wind Drake"),
                    Creature("Sunspire Griffin"),
                    Creature("Muck Rats"),
                    Creature("Plated Seastrider"),
                    Creature("Pensive Minotaur", sick = true),
                ),
                theirs = listOf(Creature("Norwood Archers"), Creature("Grizzly Bears", tapped = true)),
                ourLife = 17,
                theirLife = 6,
            )
            val before = game.attackersChosenBy(WITHOUT)
            withClue("reproduction: attacked with ${game.names(before)}") { before.shouldBeEmpty() }

            val after = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(after)}") {
                game.names(after) shouldContain "Wind Drake"
                game.names(after) shouldContain "Sunspire Griffin"
            }
        }

        test("a lone small attacker stays home against bigger untapped blockers") {
            val game = position(
                ours = listOf(Creature("Grizzly Bears")),
                theirs = listOf(Creature("Hill Giant"), Creature("Rumbling Baloth")),
                ourLife = 20,
                theirLife = 20,
            )
            val attackers = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(attackers)}") { attackers.shouldBeEmpty() }
        }

        // Game c71b39d3 turn 24: at 1 life the AI swung Skrull 4/4 beside a vigilant 4/7 flier.
        // The simulated defender blocked the Skrull and lost a creature doing it, which left one
        // attacker against the flier — safe. The human took the hit instead, kept both creatures,
        // and swung two attackers into one blocker. Here the predicted block is Craw Wurm trading
        // with the Baloth; declined, the Wurm and the Griffin come back at a lone Angel.
        test("c71b39d3 turn 24: at 1 life the second blocker stays home") {
            val game = position(
                ours = listOf(Creature("Serra Angel"), Creature("Rumbling Baloth")),
                theirs = listOf(Creature("Craw Wurm"), Creature("Sunspire Griffin")),
                ourLife = 1,
                theirLife = 15,
            )
            val baloth = game.findPermanent("Rumbling Baloth")!!
            val before = game.attackersChosenBy(WITHOUT)
            withClue("reproduction: attacked with ${game.names(before)}") { before shouldContain baloth }

            val after = game.attackersChosenBy(AiProfile.LIVE)
            withClue("attacked with ${game.names(after)}") {
                after shouldNotContain baloth
            }
        }

        test("still attacks when the opponent's blockers are all tapped and the swing back is survivable") {
            val game = position(
                ours = listOf(Creature("Hill Giant"), Creature("Grizzly Bears")),
                theirs = listOf(Creature("Grizzly Bears", tapped = true)),
                ourLife = 20,
                theirLife = 9,
            )
            game.attackersChosenBy(AiProfile.LIVE).shouldNotBeEmpty()
        }
    }
}
