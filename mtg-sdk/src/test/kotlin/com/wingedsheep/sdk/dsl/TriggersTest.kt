package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TriggersTest : FunSpec({

    test("entersBattlefield can restrict the source zone") {
        Triggers.a(GameObjectFilter.Land.youControl()).enters(from = Zone.EXILE) shouldBe com.wingedsheep.sdk.scripting.TriggerSpec(
            event = EventPattern.ZoneChangeEvent(
                filter = GameObjectFilter.Land.youControl(),
                from = Zone.EXILE,
                to = Zone.BATTLEFIELD,
            ),
            binding = TriggerBinding.ANY,
        )
    }
})
