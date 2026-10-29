package com.wingedsheep.ai.engine.knowledge

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.components.battlefield.AbilityActivatedThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.ActivatedAbility
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * [com.wingedsheep.ai.engine.AiProfile.refuseEmptyPayoffs]: an activation whose whole payoff lands
 * on nothing is floored below passing.
 *
 * Each shape is a position from the 2026-10-10 `live`-profile AI-vs-AI logs: Oakhollow Village
 * putting counters on "each Frog, Rabbit, Raccoon, or Squirrel that entered this turn" when none
 * had (game 1, turns 19–25), Mandibular Kite re-equipped to the creature already wearing it (game 3
 * turn 19), and Dúnedain Blade moved three times in one postcombat main phase (game 1 turn 32).
 * The precombat shape is from the 2026-10-11 BLB-vs-LTR log: Dúnedain Blade shuffled between Shire
 * Shirriff and Mushroom Watchdogs on turns 22 and 24 with nothing attacking.
 */
class EmptyPayoffTest : ScenarioTestBase() {

    private val intents by lazy { IntentCatalog.of(cardRegistry) }
    private val off by lazy { HoldPolicy(intents) }
    private val on by lazy { HoldPolicy(intents, refuseEmptyPayoffs = true) }

    private fun nonManaAbility(cardName: String): ActivatedAbility =
        cardRegistry.getCard(cardName)!!.script.activatedAbilities.first { !it.isManaAbility }

    /** The last equip ability on [cardName] — the unrestricted "Equip {N}" on Dúnedain Blade. */
    private fun equipAbility(cardName: String): ActivatedAbility =
        cardRegistry.getCard(cardName)!!.script.activatedAbilities.last { it.isEquipAbility }

    private fun TestGame.villageVerdict(policy: HoldPolicy): TimingVerdict {
        val activation = ActivateAbility(player1Id, findPermanent("Oakhollow Village")!!, nonManaAbility("Oakhollow Village").id)
        return policy.verdictFor(state, player1Id, "Oakhollow Village", activation = activation)
    }

    private fun TestGame.equipVerdict(policy: HoldPolicy, equipment: String, onto: String): TimingVerdict {
        val activation = ActivateAbility(
            player1Id, findPermanent(equipment)!!, equipAbility(equipment).id,
            targets = listOf(ChosenTarget.Permanent(findPermanent(onto)!!)),
        )
        return policy.verdictFor(state, player1Id, equipment, activation = activation)
    }

    /** Marks [name] as having had an ability activated this turn, as an earlier equip would have. */
    private fun TestGame.markActivatedThisTurn(name: String) {
        val id = findPermanent(name)!!
        state = state.updateEntity(id) { it.with(AbilityActivatedThisTurnComponent().withAnyActivated()) }
    }

    init {

        // --- Group effect over an empty group ---------------------------------------------------

        test("Oakhollow Village with no qualifying creature entered this turn: no window") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Oakhollow Village")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Intrepid Rabbit")
                .withCardOnBattlefield(1, "Grizzly Bears", enteredThisTurn = true)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.villageVerdict(off) shouldBe TimingVerdict.Neutral
            game.villageVerdict(on) shouldBe TimingVerdict.NoWindow
        }

        test("a Rabbit that entered this turn keeps the Village live") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Oakhollow Village")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Intrepid Rabbit", enteredThisTurn = true)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.villageVerdict(on) shouldBe TimingVerdict.Neutral
        }

        test("an opponent's Rabbit that entered this turn is not ours to grow") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Oakhollow Village")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(2, "Intrepid Rabbit", enteredThisTurn = true)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.villageVerdict(on) shouldBe TimingVerdict.NoWindow
        }

        test("game 1 turn 19: the agent floors the empty Village activation") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Oakhollow Village")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardOnBattlefield(1, "Intrepid Rabbit")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Island", 3)
                .withCardOnBattlefield(2, "Hill Giant")
                .withTurnNumber(19)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_EMPTYPAYOFF, AiProfile.LIVE)) {
                val (action, option) = scoredOption(game, profile, "Oakhollow Village") { true }
                withClue("${profile.id} chose $action") {
                    option.note shouldBe "hold policy: wrong window — floored below passing"
                    ((action as? ActivateAbility)?.abilityId == nonManaAbility("Oakhollow Village").id) shouldBe false
                }
            }
        }

        // --- Attach to the current host ---------------------------------------------------------

        test("Mandibular Kite re-equipped to the creature already wearing it: no window") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 4)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Mandibular Kite", "Eldrazi Repurposer")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.equipVerdict(off, "Mandibular Kite", onto = "Eldrazi Repurposer") shouldBe TimingVerdict.Neutral
            game.equipVerdict(on, "Mandibular Kite", onto = "Eldrazi Repurposer") shouldBe TimingVerdict.NoWindow
            // Moving it precombat onto a creature that is no better an attacker is floored too (see
            // the precombat section below); a move onto a creature that can attack while the holder
            // can't is a real choice the leaf score keeps pricing.
            game.equipVerdict(on, "Mandibular Kite", onto = "Grizzly Bears") shouldBe TimingVerdict.NoWindow
        }

        test("game 3 turn 19: the agent floors equipping the Kite to its own host") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 4)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardAttachedTo(1, "Mandibular Kite", "Eldrazi Repurposer")
                .withLandsOnBattlefield(2, "Island", 3)
                .withCardOnBattlefield(2, "Hill Giant")
                .withTurnNumber(19)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_EMPTYPAYOFF, AiProfile.LIVE)) {
                val (action, option) = scoredOption(game, profile, "Mandibular Kite") { true }
                withClue("${profile.id} chose $action") {
                    option.note shouldBe "hold policy: wrong window — floored below passing"
                    ((action as? ActivateAbility)?.sourceId == game.findPermanent("Mandibular Kite")) shouldBe false
                }
            }
        }

        // --- A second Equipment move after combat -----------------------------------------------

        test("Dúnedain Blade moved again in the postcombat main phase: no window") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.POSTCOMBAT_MAIN)
            game.markActivatedThisTurn("Dúnedain Blade")

            game.equipVerdict(off, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.Neutral
            game.equipVerdict(on, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.NoWindow
        }

        test("the first postcombat move is left to the score") {
            val post = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.POSTCOMBAT_MAIN)
            post.equipVerdict(on, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.Neutral
        }

        // --- A precombat Equipment move onto no better an attacker ------------------------------

        test("precombat, moving the Blade between two equally able attackers: no window") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.equipVerdict(off, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.Neutral
            game.equipVerdict(on, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.NoWindow
        }

        test("precombat, moving the Blade onto a creature that can't attack this turn: no window") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = true)
                .withCardOnBattlefield(1, "Hill Giant", summoningSickness = true)
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.equipVerdict(on, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.NoWindow
        }

        test("precombat, a move that buys an attack or evasion is left to the score") {
            // The holder is summoning sick; the new holder can attack.
            val sick = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = true)
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)
            sick.equipVerdict(on, "Dúnedain Blade", onto = "Hill Giant") shouldBe TimingVerdict.Neutral

            // Both can attack, but the new holder flies and the old one doesn't.
            val flier = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Wax-Wane Witness")
                .withCardAttachedTo(1, "Dúnedain Blade", "Grizzly Bears")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)
            flier.equipVerdict(on, "Dúnedain Blade", onto = "Wax-Wane Witness") shouldBe TimingVerdict.Neutral
        }

        test("precombat, equipping an unattached Blade is left to the score") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Dúnedain Blade")
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.equipVerdict(on, "Dúnedain Blade", onto = "Grizzly Bears") shouldBe TimingVerdict.Neutral
        }

        test("BLB vs LTR game 1 turn 22: the agent floors shuffling the Blade with nothing attacking") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 10)
                .withCardOnBattlefield(1, "Shire Shirriff")
                .withCardOnBattlefield(1, "Mushroom Watchdogs")
                .withCardAttachedTo(1, "Dúnedain Blade", "Shire Shirriff")
                .withLandsOnBattlefield(2, "Plains", 8)
                .withCardOnBattlefield(2, "Wax-Wane Witness")
                .withCardOnBattlefield(2, "Brightblade Stoat")
                .withTurnNumber(22)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_EMPTYPAYOFF, AiProfile.LIVE)) {
                val (action, option) = scoredOption(game, profile, "Dúnedain Blade") { true }
                withClue("${profile.id} chose $action") {
                    option.note shouldBe "hold policy: wrong window — floored below passing"
                    ((action as? ActivateAbility)?.sourceId == game.findPermanent("Dúnedain Blade")) shouldBe false
                }
            }
        }

        test("an unattached Blade is still equipped onto an attacker precombat") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(1, "Dúnedain Blade")
                .withLandsOnBattlefield(2, "Island", 3)
                .withTurnNumber(10)
                .build().advanceToPriority(1, Step.PRECOMBAT_MAIN)

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_EMPTYPAYOFF, AiProfile.LIVE)) {
                val action = AIPlayer.create(cardRegistry, game.player1Id, profile).chooseAction(game.state)
                withClue("${profile.id} chose $action") {
                    val activation = action as? ActivateAbility
                    activation?.sourceId shouldBe game.findPermanent("Dúnedain Blade")
                    activation?.targets shouldBe listOf(ChosenTarget.Permanent(game.findPermanent("Hill Giant")!!))
                }
            }
        }
    }

    /** What the Strategist made of [cardName]'s ability matching [which], and the action it chose. */
    private fun scoredOption(
        game: TestGame,
        profile: AiProfile,
        cardName: String,
        which: (AiActionOption) -> Boolean,
    ): Pair<GameAction, AiActionOption> {
        val options = mutableListOf<AiActionOption>()
        val sink = AiInsightSink { _, insight ->
            options += insight.options.filter { it.cardName == cardName && which(it) }
        }
        val action = AIPlayer.create(cardRegistry, game.player1Id, profile, insightSink = sink).chooseAction(game.state)
        val floored = options.firstOrNull { it.note != null } ?: options.firstOrNull()
        return action to (floored ?: error("${profile.id} never scored $cardName"))
    }
}
