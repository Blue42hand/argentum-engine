package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.knowledge.HoldPolicy
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.TimingVerdict
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The combat-restriction half of [AiProfile.refuseWastedPayoffs]: a "target creature can't attack
 * this turn" spent where no attack can happen.
 *
 * Game `2878b4bb` (2026-10-19), turns 20, 22 and 24: Edifice of Authority ("{1}, {T}: Target
 * creature can't attack this turn. Put a brick counter on this artifact.") activated in the AI's own
 * precombat main on the human's Reaper from the Abyss. The artifact stayed tapped through the
 * human's turn, and on turn 23 the human swung with everything and took the AI from 18 to 4.
 *
 * Siegebreaker Giant ("{3}{R}: Target creature can't block this turn") is the mirror: it buys
 * something only on our own turn, before blocks.
 */
class RestrictionWindowTest : ScenarioTestBase() {

    private val intents by lazy { IntentCatalog.of(cardRegistry) }

    private val cantAttackAbility by lazy {
        intents.definition("Edifice of Authority")!!.script.activatedAbilities.first().id
    }

    private fun TestGame.choice(profile: AiProfile = AiProfile.LIVE): GameAction =
        AIPlayer.create(cardRegistry, player1Id, profile).chooseAction(state)

    private fun TestGame.edificeVerdict(refuseWastedPayoffs: Boolean = true): TimingVerdict =
        HoldPolicy(intents, refuseWastedPayoffs = refuseWastedPayoffs).verdictFor(
            state, player1Id, "Edifice of Authority",
            activation = ActivateAbility(
                player1Id, findPermanent("Edifice of Authority")!!, cantAttackAbility,
                targets = listOf(ChosenTarget.Permanent(findPermanent("Reaper from the Abyss")!!)),
            ),
        )

    private fun TestGame.giantVerdict(): TimingVerdict {
        val giant = findPermanent("Siegebreaker Giant")!!
        return HoldPolicy(intents, refuseWastedPayoffs = true).verdictFor(
            state, player1Id, "Siegebreaker Giant",
            activation = ActivateAbility(
                player1Id, giant, intents.definition("Siegebreaker Giant")!!.script.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(findPermanent("Reaper from the Abyss")!!)),
            ),
        )
    }

    private fun giantPosition(activePlayer: Int) = scenario()
        .withPlayers()
        .withTurnNumber(20)
        .withActivePlayer(activePlayer)
        .withLandsOnBattlefield(1, "Mountain", 4)
        .withCardOnBattlefield(1, "Siegebreaker Giant")
        .withCardOnBattlefield(2, "Reaper from the Abyss")
        .withLandsOnBattlefield(2, "Swamp", 5)
        .build()
        .advanceToPriority(1, Step.PRECOMBAT_MAIN)

    private fun TestGame.activatesEdifice(): Boolean =
        (choice() as? ActivateAbility)?.sourceId == findPermanent("Edifice of Authority")

    private fun position(activePlayer: Int, step: Step) = scenario()
        .withPlayers()
        .withTurnNumber(20)
        .withActivePlayer(activePlayer)
        .withLandsOnBattlefield(1, "Plains", 2)
        .withCardOnBattlefield(1, "Edifice of Authority")
        .withCardOnBattlefield(1, "Ondu Giant")
        .withCardOnBattlefield(2, "Reaper from the Abyss")
        .withLandsOnBattlefield(2, "Swamp", 5)
        .build()
        .advanceToPriority(1, step)

    init {
        test("T20: on our own turn, can't attack buys nothing and the brick is bought again on theirs") {
            val game = position(activePlayer = 1, step = Step.PRECOMBAT_MAIN)

            game.edificeVerdict() shouldBe TimingVerdict.NoWindow
            game.edificeVerdict(refuseWastedPayoffs = false) shouldBe TimingVerdict.Neutral
            game.activatesEdifice() shouldBe false
        }

        test("on their turn before attacks, keeping their attacker home is the window") {
            val game = position(activePlayer = 2, step = Step.PRECOMBAT_MAIN)

            game.edificeVerdict() shouldNotBe TimingVerdict.NoWindow
            val action = game.choice() as? ActivateAbility
            action?.sourceId shouldBe game.findPermanent("Edifice of Authority")
            action?.targets shouldBe listOf(ChosenTarget.Permanent(game.findPermanent("Reaper from the Abyss")!!))
        }

        test("after their attack the brick is free, since the artifact untaps before their next turn") {
            val game = position(activePlayer = 2, step = Step.END)

            game.edificeVerdict() shouldNotBe TimingVerdict.NoWindow
        }

        test("can't block on their turn keeps nothing out of a block") {
            giantPosition(activePlayer = 2).giantVerdict() shouldBe TimingVerdict.NoWindow
        }

        test("can't block on our own turn before blocks is the window") {
            giantPosition(activePlayer = 1).giantVerdict() shouldNotBe TimingVerdict.NoWindow
        }
    }
}
