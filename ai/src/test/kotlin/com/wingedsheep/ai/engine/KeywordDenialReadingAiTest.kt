package com.wingedsheep.ai.engine

import com.wingedsheep.ai.audit.AiReadingAudit
import com.wingedsheep.ai.engine.knowledge.CardIntentAnalyzer
import com.wingedsheep.ai.engine.knowledge.CombatReach
import com.wingedsheep.ai.engine.knowledge.HoldPolicy
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.IntentTag
import com.wingedsheep.ai.engine.knowledge.TargetingDenial
import com.wingedsheep.ai.engine.knowledge.TimingVerdict
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class KeywordDenialReadingAiTest : ScenarioTestBase() {
    private val protected = card("Keyword Denial Reading Test Creature") {
        manaCost = "{2}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
        keywords(Keyword.HEXPROOF, Keyword.SHROUD)
    }
    private val lighthouse by lazy { cardRegistry.requireCard("Arcane Lighthouse") }
    private val ability by lazy { lighthouse.activatedAbilities.single { !it.isManaAbility } }
    private val intents by lazy { IntentCatalog.of(cardRegistry) }

    init {
        cardRegistry.register(protected)

        test("keyword denial is visible to intent and combat readers without new audit gaps") {
            CardIntentAnalyzer.analyzeEffect(ability.effect).tags shouldContain IntentTag.NEUTRALIZE
            CombatReach.of(ability) shouldContain CombatReach.FIGHT
            AiReadingAudit.audit(lighthouse) shouldBe emptyList()
        }

        test("main-phase targeting setup is not delayed until combat and makes removal legal") {
            val game = scenario().withPlayers().withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withCardOnBattlefield(1, lighthouse.name)
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(2, protected.name)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Forest")
                .build()
            val target = game.findPermanent(protected.name)!!
            val before = game.state
            game.castSpell(1, "Terror", target).error shouldNotBe null
            game.state shouldBe before
            val activation = ActivateAbility(game.player1Id, game.findPermanent(lighthouse.name)!!, ability.id)
            val policy = HoldPolicy(intents, holdExpiringGrantsForCombat = true,
                refuseUnspendableGrants = true, refuseWastedPayoffs = true)
            policy.verdictFor(game.state, game.player1Id, lighthouse.name, activation = activation) shouldBe TimingVerdict.Neutral
            game.execute(activation).error shouldBe null
            game.resolveStack()
            game.castSpell(1, "Terror", target).error shouldBe null
            game.resolveStack()
            game.state.projectedState.getBattlefieldControlledBy(game.player2Id) shouldNotContain target
        }

        test("removing defender is not misread as opening targeting or neutralizing an enemy") {
            val effect = Effects.CantHaveKeywords(setOf(Keyword.DEFENDER), EffectTarget.ContextTarget(0))
            TargetingDenial.reads(effect) shouldBe false
            CardIntentAnalyzer.analyzeEffect(effect).tags shouldNotContain IntentTag.NEUTRALIZE
        }
    }
}
