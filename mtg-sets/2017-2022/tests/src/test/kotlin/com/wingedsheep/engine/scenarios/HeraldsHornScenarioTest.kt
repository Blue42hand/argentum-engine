package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.c17.cards.HeraldsHorn
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf

class HeraldsHornScenarioTest : FunSpec({
    test("reduces matching creature spells and checks the top card each upkeep") {
        HeraldsHorn.script.replacementEffects.single().shouldBeInstanceOf<EntersWithChoice>()
        HeraldsHorn.script.staticAbilities.single().shouldBeInstanceOf<ModifySpellCost>()
        val trigger = HeraldsHorn.script.triggeredAbilities.single()
        trigger.trigger.shouldBeInstanceOf<EventPattern.StepEvent>()
        val effects = trigger.effect.shouldBeInstanceOf<CompositeEffect>().effects
        effects.map { it::class } shouldContainExactly listOf(
            GatherCardsEffect::class,
            FilterCollectionEffect::class,
            SelectFromCollectionEffect::class,
            MoveCollectionEffect::class,
        )
    }
})
