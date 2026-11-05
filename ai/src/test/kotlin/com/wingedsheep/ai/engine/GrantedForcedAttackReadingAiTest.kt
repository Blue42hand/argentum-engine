package com.wingedsheep.ai.engine

import com.wingedsheep.ai.audit.AiReadingAudit
import com.wingedsheep.ai.engine.knowledge.CardIntentAnalyzer
import com.wingedsheep.ai.engine.knowledge.ForcedAttackWindow
import com.wingedsheep.ai.engine.knowledge.HoldPolicy
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.IntentTag
import com.wingedsheep.ai.engine.knowledge.TimingVerdict
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.MustAttack
import com.wingedsheep.sdk.scripting.effects.GrantStaticAbilityEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GrantedForcedAttackReadingAiTest : ScenarioTestBase() {
    private val bident by lazy { cardRegistry.requireCard("Bident of Thassa") }
    private val ability by lazy { bident.activatedAbilities.single() }
    private val intents by lazy { IntentCatalog.of(cardRegistry) }
    private val policy by lazy { HoldPolicy(intents, forcedAttacksOnTheirTurn = true) }

    private fun position(active: Int, phase: Phase, step: Step) = scenario().withPlayers()
        .withTurnNumber(5).withActivePlayer(active).inPhase(phase, step)
        .withCardOnBattlefield(1, "Bident of Thassa")
        .withLandsOnBattlefield(1, "Island", 2)
        .withCardOnBattlefield(1, "Horned Turtle")
        .withCardOnBattlefield(2, "Grizzly Bears")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Forest")
        .build().also { if (active == 2) it.advanceToPriority(1, step) }

    private fun TestGame.verdict(policy: HoldPolicy = this@GrantedForcedAttackReadingAiTest.policy): TimingVerdict =
        policy.verdictFor(state, player1Id, bident.name,
            activation = ActivateAbility(player1Id, findPermanent(bident.name)!!, ability.id))

    init {
        test("a controller-held forced-attack rule has forced-attack intent and a complete reading") {
            CardIntentAnalyzer.analyzeEffect(ability.effect).tags shouldContain IntentTag.FORCED_ATTACK
            ForcedAttackWindow.readsActivation(ability.effect) shouldBe true
            AiReadingAudit.audit(bident) shouldBe emptyList()
        }

        test("Bident is rewarded before the opponent attacks and held outside that turn's attack window") {
            val before = position(2, Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            before.verdict().shouldBeInstanceOf<TimingVerdict.Adjust>().reason shouldBe "forces their attack"
            position(1, Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).verdict() shouldBe TimingVerdict.NoWindow
            position(2, Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN).verdict() shouldBe TimingVerdict.NoWindow
            before.verdict(HoldPolicy(intents, forcedAttacksOnTheirTurn = false)) shouldBe TimingVerdict.Neutral
        }

        test("a rule on our creatures and a grant to another controller remain outside this reading") {
            val rule = ability.effect.shouldBeInstanceOf<GrantStaticAbilityEffect>()
            val own = rule.copy(ability = MustAttack(GroupFilter.AllCreaturesYouControl))
            ForcedAttackWindow.readsActivation(own) shouldBe false
            ForcedAttackWindow.readsActivation(rule.copy(target = EffectTarget.ContextTarget(0))) shouldBe false
            ForcedAttackWindow.readsActivation(rule.copy(duration = Duration.Permanent)) shouldBe false
        }
    }
}
