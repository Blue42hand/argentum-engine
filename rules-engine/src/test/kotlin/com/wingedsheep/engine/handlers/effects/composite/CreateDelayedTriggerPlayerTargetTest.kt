package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CreateDelayedTriggerPlayerTargetTest : FunSpec({

    test("captures the targeted spell controller as the delayed draw beneficiary") {
        val scheduler = EntityId("scheduler")
        val targetController = EntityId("target-controller")
        val bystander = EntityId("bystander")
        val source = EntityId("source")
        val targetSpell = EntityId("target-spell")
        val state = GameState(turnOrder = listOf(scheduler, bystander, targetController))
            .withEntity(scheduler, ComponentContainer())
            .withEntity(bystander, ComponentContainer())
            .withEntity(targetController, ComponentContainer())
            .withEntity(targetSpell, ComponentContainer().with(ControllerComponent(targetController)))

        val result = CreateDelayedTriggerExecutor().execute(
            state,
            CreateDelayedTriggerEffect(
                step = Step.UPKEEP,
                effect = DrawCardsEffect(
                    2,
                    EffectTarget.PlayerRef(Player.ControllerOf("target spell")),
                ),
            ),
            EffectContext(
                sourceId = source,
                controllerId = scheduler,
                targets = listOf(ChosenTarget.Spell(targetSpell)),
            ),
        )

        result.error shouldBe null
        val delayed = result.state.delayedTriggers.single()
        val draw = delayed.effect.shouldBeInstanceOf<DrawCardsEffect>()
        draw.target shouldBe EffectTarget.SpecificEntity(targetController)
        delayed.controllerId shouldBe scheduler
        delayed.fireAtStep shouldBe Step.UPKEEP
        delayed.fireOnPlayerId shouldBe null
    }

    test("does not collapse multi-player draw references while scheduling") {
        val scheduler = EntityId("scheduler")
        val opponentA = EntityId("opponent-a")
        val opponentB = EntityId("opponent-b")
        val source = EntityId("source")
        val state = GameState(turnOrder = listOf(scheduler, opponentA, opponentB))

        val originalTarget = EffectTarget.PlayerRef(Player.EachOpponent)
        val result = CreateDelayedTriggerExecutor().execute(
            state,
            CreateDelayedTriggerEffect(
                step = Step.UPKEEP,
                effect = DrawCardsEffect(1, originalTarget),
            ),
            EffectContext(sourceId = source, controllerId = scheduler),
        )

        result.error shouldBe null
        val draw = result.state.delayedTriggers.single().effect.shouldBeInstanceOf<DrawCardsEffect>()
        draw.target shouldBe originalTarget
    }
})
