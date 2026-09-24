package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.values.EntityReference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the last-known-information classification of every [EntityReference] (CR 113.7a / 603.10 /
 * 608.2h) and the [EntitySnapshot] derived counter accessors that replaced the former per-count
 * scalars. `lkiPolicyFor` is an exhaustive `when`, so a new reference variant cannot be added
 * without choosing its policy here — this test documents and guards those choices.
 */
class LkiPolicyTest : FunSpec({

    test("references that read a permanent after it has left fall back to LKI") {
        listOf(
            EntityReference.Source,
            EntityReference.Triggering,
            EntityReference.EnchantedCreature,
            EntityReference.Sacrificed(),
            EntityReference.TappedAsCost(),
            EntityReference.FromCostStorage("chosen"),
        ).forEach { ref ->
            lkiPolicyFor(ref) shouldBe LkiPolicy.LIVE_THEN_LKI
        }
    }

    test("references that only ever name a live permanent never fall back to LKI") {
        listOf(
            EntityReference.Target(),
            EntityReference.RingBearer(),
            EntityReference.AffectedEntity,
            EntityReference.IterationEntity,
            EntityReference.AmassedArmy,
        ).forEach { ref ->
            lkiPolicyFor(ref) shouldBe LkiPolicy.LIVE_ONLY
        }
    }

    test("snapshot counter accessors derive the former scalar counts from the counters map") {
        val snapshot = EntitySnapshot(
            entityId = EntityId("e1"),
            counters = mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3, CounterType.MINUS_ONE_MINUS_ONE to 1, CounterType.LOYALTY to 4),
        )
        snapshot.plusOnePlusOneCounters shouldBe 3
        snapshot.minusOneMinusOneCounters shouldBe 1
        snapshot.totalCounters shouldBe 8
    }
})
