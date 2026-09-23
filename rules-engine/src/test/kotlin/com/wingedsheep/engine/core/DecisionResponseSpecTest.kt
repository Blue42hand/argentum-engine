package com.wingedsheep.engine.core

import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class DecisionResponseSpecTest : FunSpec({
    val player = EntityId("player")

    test("yes no exposes native response type and boolean field") {
        val decision = YesNoDecision(
            id = "r1",
            playerId = player,
            prompt = "Draw a card?",
            context = DecisionContext(),
        )

        decision.responseSpec() shouldBe DecisionResponseSpec(
            responseType = "YesNoResponse",
            requiredFields = mapOf("choice" to DecisionResponseFieldKind.BOOLEAN),
        )
    }

    test("cancel support follows the native pending decision") {
        val decision = ChooseTargetsDecision(
            id = "r2",
            playerId = player,
            prompt = "Choose a target",
            context = DecisionContext(),
            targetRequirements = emptyList(),
            legalTargets = emptyMap(),
            canCancel = true,
        )

        val spec = decision.responseSpec()
        spec.responseType shouldBe "TargetsResponse"
        spec.requiredFields shouldContainExactly mapOf(
            "selectedTargets" to DecisionResponseFieldKind.MAP
        )
        spec.cancelAllowed shouldBe true
    }

    test("select cards exposes entity id array response") {
        val decision = SelectCardsDecision(
            id = "r3",
            playerId = player,
            prompt = "Choose a card",
            context = DecisionContext(),
            options = listOf(EntityId("card")),
            minSelections = 0,
            maxSelections = 1,
        )

        decision.responseSpec().requiredFields shouldContainExactly mapOf(
            "selectedCards" to DecisionResponseFieldKind.ENTITY_ID_ARRAY
        )
    }
})
