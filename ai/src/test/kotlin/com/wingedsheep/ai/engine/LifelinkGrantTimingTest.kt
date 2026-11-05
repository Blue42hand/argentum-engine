package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.knowledge.HoldPolicy
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.TimingVerdict
import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * An end-of-turn **lifelink** grant bought before the creature is in a fight that deals damage —
 * Hardened Academic's `Discard a card: This creature gains lifelink until end of turn`, off the
 * 2026-10-19 production log review.
 *
 *  - Game `37ad54e6` turn 9: the opponent declares two attackers, the AI pitches Ancestral Anger
 *    for lifelink in that declare-attackers step, and then declares no blocks.
 *  - Game `f17ff187` turn 3: the AI attacks an empty board with the Academic at 20 life and pitches
 *    a card to gain two life.
 */
class LifelinkGrantTimingTest : ScenarioTestBase() {

    private val intents by lazy { IntentCatalog.of(cardRegistry) }

    /** The grant floors [AiProfile.LIVE] shipped before [AiProfile.lifelinkWaitsForBlocks]. */
    private val legacyPolicy by lazy {
        HoldPolicy(intents, holdExpiringGrantsForCombat = true, refuseUnspendableGrants = true)
    }

    /** The grant floors [AiProfile.LIVE] ships. */
    private val livePolicy by lazy {
        HoldPolicy(
            intents,
            holdExpiringGrantsForCombat = true,
            refuseUnspendableGrants = true,
            lifelinkWaitsForBlocks = true,
        )
    }

    private fun TestGame.academicActivation(): ActivateAbility {
        val ability = cardRegistry.getCard("Hardened Academic")!!.script.activatedAbilities
            .first { !it.isManaAbility }
        return ActivateAbility(player1Id, findPermanent("Hardened Academic")!!, ability.id)
    }

    private fun TestGame.verdict(policy: HoldPolicy = livePolicy): TimingVerdict =
        policy.verdictFor(state, player1Id, "Hardened Academic", activation = academicActivation())

    private fun TestGame.choice(): GameAction =
        AIPlayer.create(cardRegistry, player1Id, AiProfile.LIVE).chooseAction(state)

    private fun GameAction.isAcademicActivation(game: TestGame): Boolean =
        this is ActivateAbility && sourceId == game.findPermanent("Hardened Academic")

    /** The opponent's turn, their Hill Giant attacking us, the Academic untapped at home. */
    private fun theirAttack(life: Int = 20, attacker: String = "Hill Giant") = scenario().withPlayers()
        .withActivePlayer(2)
        .withLifeTotal(1, life)
        .withCardOnBattlefield(1, "Hardened Academic")
        .withCardInHand(1, "Craw Wurm")
        .withCardOnBattlefield(2, attacker)
        .build()
        .advanceToDeclaration(2, Step.DECLARE_ATTACKERS)
        .also { it.declareAttackers(mapOf(attacker to 1)).error shouldBe null }

    /** Our turn, the Academic attacking into [theirCreatures]. */
    private fun ourAttack(life: Int = 20, vararg theirCreatures: String) = scenario().withPlayers()
        .withTurnNumber(3)
        .withLifeTotal(1, life)
        .withCardOnBattlefield(1, "Hardened Academic")
        .withCardInHand(1, "Craw Wurm")
        .apply { theirCreatures.forEach { withCardOnBattlefield(2, it) } }
        .build()
        .advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
        .also { it.declareAttackers(mapOf("Hardened Academic" to 2)).error shouldBe null }

    init {

        test("37ad54e6 T9: their attack is in, nothing blocks yet — lifelink waits for blocks") {
            val game = theirAttack().advanceToPriority(1, Step.DECLARE_ATTACKERS)

            game.verdict(legacyPolicy) shouldBe TimingVerdict.Neutral
            game.verdict() shouldBe TimingVerdict.NoWindow
            game.choice().isAcademicActivation(game) shouldBe false
        }

        test("the Academic is blocking: the lifelink is the evaluator's call again") {
            val game = theirAttack(attacker = "Raging Goblin")
                .advanceToDeclaration(1, Step.DECLARE_BLOCKERS)
                .also { it.declareBlockers(mapOf("Hardened Academic" to listOf("Raging Goblin"))).error shouldBe null }
                .advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.verdict() shouldNotBe TimingVerdict.NoWindow
        }

        test("blocking at 2 life: the AI buys the lifelink once the block is in") {
            val game = theirAttack(life = 2, attacker = "Raging Goblin")
                .advanceToDeclaration(1, Step.DECLARE_BLOCKERS)
                .also { it.declareBlockers(mapOf("Hardened Academic" to listOf("Raging Goblin"))).error shouldBe null }
                .advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.choice().isAcademicActivation(game) shouldBe true
        }

        test("not blocking once blocks are in: nothing is left to spend the lifelink") {
            val game = theirAttack().advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.verdict() shouldBe TimingVerdict.NoWindow
        }

        test("f17ff187 T3: our attacker before blocks — lifelink waits for blocks") {
            val game = ourAttack(20, "Wind Drake").advanceToPriority(1, Step.DECLARE_ATTACKERS)

            game.verdict(legacyPolicy) shouldBe TimingVerdict.Neutral
            game.verdict() shouldBe TimingVerdict.NoWindow
            game.choice().isAcademicActivation(game) shouldBe false
        }

        test("f17ff187 T3: unblocked at 20 life, two life is not worth a card") {
            val game = ourAttack(20).advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.verdict() shouldNotBe TimingVerdict.NoWindow
            game.choice().isAcademicActivation(game) shouldBe false
        }

        test("unblocked at 2 life, the two life is worth the card") {
            val game = ourAttack(2, "Hill Giant").advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.choice().shouldBeInstanceOf<ActivateAbility>().isAcademicActivation(game) shouldBe true
        }
    }
}
