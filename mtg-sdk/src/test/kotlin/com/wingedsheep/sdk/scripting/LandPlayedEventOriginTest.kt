package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LandPlayedEventOriginTest : FunSpec({

    test("an exact exile origin is represented independently of a non-hand origin") {
        val event = Triggers.anyPlayerPlaysLand(fromZone = Zone.EXILE).event
            as EventPattern.LandPlayedEvent

        event.player shouldBe Player.Each
        event.fromZone shouldBe Zone.EXILE
        event.fromZoneOtherThan shouldBe null
        event.description shouldBe "a player plays a land from exile"
    }

    test("exact and excluded origins are mutually exclusive") {
        shouldThrow<IllegalArgumentException> {
            EventPattern.LandPlayedEvent(
                fromZone = Zone.EXILE,
                fromZoneOtherThan = Zone.HAND,
            )
        }
    }
})
