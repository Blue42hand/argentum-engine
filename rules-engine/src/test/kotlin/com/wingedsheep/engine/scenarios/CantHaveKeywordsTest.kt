package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.KeywordsProhibitedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.permanent.abilities.CantHaveKeywordsExecutor
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CantHaveKeywordsEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe

class CantHaveKeywordsTest : ScenarioTestBase() {
    private val artifact = card("Typed Hexproof Test Artifact") {
        manaCost = "{2}"
        typeLine = "Artifact"
        keywords(Keyword.HEXPROOF)
        staticAbility { ability = GrantKeyword("HEXPROOF_FROM_BLUE", GroupFilter.source()) }
    }
    init {
        cardRegistry.register(artifact)
        test("prohibitions are target-general and remove every hexproof quality variant") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, artifact.name).build()
            val id = game.findPermanent(artifact.name)!!
            game.state.projectedState.getKeywords(id).contains("HEXPROOF_FROM_BLUE") shouldBe true
            val effect = Effects.CantHaveKeywords(setOf(Keyword.HEXPROOF), EffectTarget.ContextTarget(0)) as CantHaveKeywordsEffect
            val result = CantHaveKeywordsExecutor().execute(game.state, effect,
                EffectContext(sourceId = id, controllerId = game.player1Id, targets = listOf(ChosenTarget.Permanent(id))))
            result.error shouldBe null
            result.state.projectedState.getKeywords(id).any { it.startsWith("HEXPROOF") } shouldBe false
            (result.events.single() as KeywordsProhibitedEvent).targetId shouldBe id
        }
    }
}
