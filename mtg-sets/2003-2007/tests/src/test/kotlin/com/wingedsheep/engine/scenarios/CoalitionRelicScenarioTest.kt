package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.fut.cards.CoalitionRelic
import com.wingedsheep.sdk.scripting.effects.AddDynamicManaEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.RemoveAllCountersOfTypeEffect
import com.wingedsheep.sdk.scripting.effects.StoreNumberEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class CoalitionRelicScenarioTest : FunSpec({
    test("has two tap abilities and cashes charge counters into mana at first main") {
        CoalitionRelic.script.activatedAbilities.size shouldBe 2
        CoalitionRelic.script.activatedAbilities[0].isManaAbility shouldBe true
        CoalitionRelic.script.activatedAbilities[1].isManaAbility shouldBe false
        val effects = (CoalitionRelic.script.triggeredAbilities.single().effect as CompositeEffect).effects
        effects.map { it::class } shouldContainExactly listOf(
            StoreNumberEffect::class,
            RemoveAllCountersOfTypeEffect::class,
            AddDynamicManaEffect::class,
        )
    }
})
