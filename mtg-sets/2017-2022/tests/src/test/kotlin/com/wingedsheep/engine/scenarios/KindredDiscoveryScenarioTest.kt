package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.c17.cards.KindredDiscovery
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class KindredDiscoveryScenarioTest : FunSpec({
    test("draws for both entering and attacking creatures of the chosen type") {
        KindredDiscovery.script.replacementEffects.single().shouldBeInstanceOf<EntersWithChoice>()
        val trigger = KindredDiscovery.script.triggeredAbilities.single()
        val events = trigger.trigger.shouldBeInstanceOf<EventPattern.AnyOf>().events
        events.size shouldBe 2
        events[0].shouldBeInstanceOf<EventPattern.ZoneChangeEvent>()
        events[1].shouldBeInstanceOf<EventPattern.AttackEvent>()
        trigger.effect.shouldBeInstanceOf<DrawCardsEffect>()
    }
})
