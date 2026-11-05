package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * [AiProfile.selectionSeesWhoseCards] and [AiProfile.topOrBottomWeighsTheDraw], rebuilt from the
 * 2026-10-15 production games. Each test pins the logged misplay on the profile without the
 * flags, then the fix on [AiProfile.LIVE].
 *
 * The AI is seat 1 throughout.
 */
class SelectionSeesWhoseCardsTest : ScenarioTestBase() {

    private val before = AiProfile.LIVE.copy(
        id = "live-without-selection-fate",
        selectionSeesWhoseCards = false,
        topOrBottomWeighsTheDraw = false,
    )
    private val after = AiProfile.LIVE

    private fun TestGame.nameOf(id: EntityId): String =
        state.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

    /**
     * Resolve everything on the stack, the AI answering seat 1's decisions under [profile] and
     * [theirs] answering seat 2's. Returns the AI's answers, in order.
     */
    private fun TestGame.resolveWith(
        profile: AiProfile,
        theirs: ((PendingDecision) -> DecisionResponse)? = null,
    ): List<Pair<PendingDecision, DecisionResponse>> {
        val ai = AIPlayer.create(cardRegistry, player1Id, profile)
        val opponent = AIPlayer.create(cardRegistry, player2Id, AiProfile.LIVE)
        val answers = mutableListOf<Pair<PendingDecision, DecisionResponse>>()
        var guard = 0
        while (guard++ < 30) {
            val decision = state.pendingDecision
            if (decision != null) {
                val response = if (decision.playerId == player1Id) {
                    ai.respondToDecision(state, decision).also { answers += decision to it }
                } else {
                    theirs?.invoke(decision) ?: opponent.respondToDecision(state, decision)
                }
                execute(SubmitDecision(decision.playerId, response)).error shouldBe null
                continue
            }
            if (state.stack.isEmpty()) break
            resolveStack()
        }
        return answers
    }

    /** The cards the AI chose in its (only) card selection. */
    private fun List<Pair<PendingDecision, DecisionResponse>>.selected(game: TestGame): List<String> =
        single { it.first is SelectCardsDecision }.second.let { (it as CardsSelectedResponse).selectedCards }
            .map { game.nameOf(it) }

    /** Run [block] once per profile from the same starting position. */
    private fun TestGame.each(block: TestGame.(AiProfile) -> Unit) {
        val saved = state
        for (profile in listOf(before, after)) {
            state = saved
            block(profile)
        }
    }

    init {
        test("an opponent's discard takes the card they can use, not the one we can't") {
            // 383cddc3 T14: Solve for Disappointment against Lich's Relic and Guiding Hydra in a
            // Plains-only hand. Ranked as our own cards (we had Swamps and Plains), the Relic was
            // the least worth keeping; for its owner it is the one card they can't cast.
            val game = scenario().withPlayers()
                .withCardInHand(1, "Solve for Disappointment")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Plains", 3)
                .withCardInHand(2, "Lich's Relic")
                .withCardInHand(2, "Guiding Hydra")
                .withLandsOnBattlefield(2, "Plains", 6)
                .apply { repeat(6) { withCardInLibrary(1, "Swamp") } }
                .apply { repeat(6) { withCardInLibrary(2, "Plains") } }
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.each { profile ->
                castSpellTargetingPlayer(1, "Solve for Disappointment", 2).error shouldBe null
                val chosen = resolveWith(profile).selected(this)
                withClue("${profile.id}: $chosen") {
                    chosen shouldBe listOf(if (profile == before) "Lich's Relic" else "Guiding Hydra")
                }
            }
        }

        test("exiling from a revealed hand takes their bomb, not their cheapest creature") {
            // 3b0479cb T9: Null Summoner against Tenured Tethermage, Greenhouse Propagator and
            // Craftwork Crusher, the human on six lands. None of them was castable for us, so the
            // generic "keep the best" branch took whichever of *our* dead cards scored highest.
            val game = scenario().withPlayers()
                .withCardInHand(1, "Null Summoner")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInHand(2, "Tenured Tethermage")
                .withCardInHand(2, "Greenhouse Propagator")
                .withCardInHand(2, "Craftwork Crusher")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Mountain", 2)
                .withLandsOnBattlefield(2, "Forest", 4)
                .apply { repeat(6) { withCardInLibrary(1, "Island") } }
                .apply { repeat(6) { withCardInLibrary(2, "Forest") } }
                .apply { repeat(3) { withCardInLibrary(2, "Grizzly Bears") } }
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.each { profile ->
                castSpell(1, "Null Summoner").error shouldBe null
                val chosen = resolveWith(profile).selected(this)
                withClue("${profile.id}: $chosen") {
                    if (profile == before) chosen shouldBe listOf("Greenhouse Propagator")
                    else chosen shouldBe listOf("Craftwork Crusher")
                }
            }
        }

        test("a discard asked as \"Choose 1 card\" pitches the uncastable card, not the only land") {
            // 98be3ea5 T5: Tinybones' "each opponent discards a card" prompts "Choose 1 card",
            // so no prompt word said discard and the AI *kept* its best card — by discarding it:
            // the only Plains of a two-land hand of three- to five-drops.
            val game = scenario().withPlayers()
                .withCardInHand(2, "Tinybones, Pocket Nuisance")
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withCardOnBattlefield(1, "Yoshimaru, Scrappy Stray")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Plains", 1)
                .withCardInHand(1, "Ferocity of the Hunt")
                .withCardInHand(1, "Danitha, Sword of Hope")
                .withCardInHand(1, "Bloombrute")
                .withCardInHand(1, "Karn, Gilded Guardian")
                .withCardInHand(1, "Plains")
                .withCardInHand(1, "Inspired Tethermage")
                .apply { repeat(3) { withCardInLibrary(1, "Plains") } }
                .apply { repeat(3) { withCardInLibrary(1, "Forest") } }
                .apply { repeat(6) { withCardInLibrary(1, "Grizzly Bears") } }
                .apply { repeat(6) { withCardInLibrary(2, "Swamp") } }
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.each { profile ->
                castSpell(2, "Tinybones, Pocket Nuisance").error shouldBe null
                val chosen = resolveWith(profile).selected(this)
                withClue("${profile.id}: $chosen") {
                    if (profile == before) chosen shouldBe listOf("Plains")
                    else chosen shouldBe listOf("Karn, Gilded Guardian")
                }
            }
        }

        test("an up-to-one look that would put a land into a land-short hand takes it") {
            // d8d3a29c T2: Seismic Sense on two lands showed only a Forest ("Put in hand", up to
            // one) and the AI took nothing: the label kept it out of the unlabelled-search branch
            // and the fallback took the minimum, zero.
            val game = scenario().withPlayers()
                .withCardInHand(1, "Seismic Sense")
                .withCardInHand(1, "Bloombrute")
                .withCardInHand(1, "Bestial Incursion")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Giant Growth")
                .apply { repeat(6) { withCardInLibrary(1, "Grizzly Bears") } }
                .apply { repeat(6) { withCardInLibrary(2, "Swamp") } }
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.each { profile ->
                castSpell(1, "Seismic Sense").error shouldBe null
                val chosen = resolveWith(profile).selected(this)
                withClue("${profile.id}: $chosen") {
                    chosen shouldBe if (profile == before) emptyList() else listOf("Forest")
                }
            }
        }

        /** Seat 2 casts Plan for All Outcomes at Yoshimaru; the AI's top-or-bottom answer. */
        fun planOnYoshimaru(builder: ScenarioBuilder): List<String> {
            val game = builder
                .withCardInHand(2, "Plan for All Outcomes")
                .withLandsOnBattlefield(2, "Island", 4)
                .withCardOnBattlefield(1, "Yoshimaru, Scrappy Stray")
                .apply { repeat(6) { withCardInLibrary(2, "Island") } }
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val answers = mutableListOf<String>()
            game.each { profile ->
                castSpell(2, "Plan for All Outcomes").error shouldBe null
                val yoshimaru = findPermanent("Yoshimaru, Scrappy Stray")!!
                val responses = resolveWith(profile) { decision ->
                    decision as ChooseTargetsDecision
                    TargetsResponse(decision.id, mapOf(0 to listOf(yoshimaru)))
                }
                val (decision, response) = responses.single { it.first is ChooseOptionDecision }
                answers += (decision as ChooseOptionDecision).options[(response as OptionChosenResponse).optionIndex]
            }
            return answers
        }

        test("top or bottom: a land-short hand bottoms its two-drop to dig for a land") {
            // 98be3ea5 T7: two lands, five spells in hand, Plan for All Outcomes on Yoshimaru.
            // The two placements simulate alike, and the tie went to the first option, the top.
            val answers = planOnYoshimaru(
                scenario().withPlayers()
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInHand(1, "Danitha, Sword of Hope")
                    .withCardInHand(1, "Bloombrute")
                    .withCardInHand(1, "Karn, Gilded Guardian")
                    .withCardInHand(1, "Inspired Tethermage")
                    .withCardInHand(1, "Bestial Incursion")
                    .apply { repeat(3) { withCardInLibrary(1, "Plains") } }
                    .apply { repeat(3) { withCardInLibrary(1, "Forest") } }
                    .apply { repeat(6) { withCardInLibrary(1, "Grizzly Bears") } }
            )
            answers shouldBe listOf("Top of library", "Bottom of library")
        }

        test("top or bottom: a flooded board keeps its creature on top") {
            val answers = planOnYoshimaru(
                scenario().withPlayers()
                    .withLandsOnBattlefield(1, "Forest", 7)
                    .apply { repeat(8) { withCardInLibrary(1, "Forest") } }
                    .apply { repeat(4) { withCardInLibrary(1, "Grizzly Bears") } }
            )
            answers shouldBe listOf("Top of library", "Top of library")
        }
    }
}
