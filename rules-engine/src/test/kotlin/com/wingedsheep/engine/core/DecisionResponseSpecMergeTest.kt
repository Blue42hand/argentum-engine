package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.engine.mechanics.dexterity.FlipTable
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class DecisionResponseSpecMergeTest : FunSpec({
    val player = EntityId("player")
    val json = Json { serializersModule = engineSerializersModule }
    val flip = FlipOntoBattlefieldDecision(
        id = "flip", playerId = player, prompt = "Flip", context = DecisionContext(),
        table = FlipTable(760.0, 600.0, emptyList(), emptyList(), 380.0, 650.0),
    )

    test("flip contract matches native fractional velocity and spin fields") {
        val spec = flip.responseSpec()
        spec.responseType shouldBe "FlipThrowResponse"
        spec.requiredFields shouldBe mapOf(
            "velocityX" to DecisionResponseFieldKind.NUMBER,
            "velocityY" to DecisionResponseFieldKind.NUMBER,
            "spin" to DecisionResponseFieldKind.NUMBER,
        )
        spec.cancelAllowed shouldBe false
        val response = FlipThrowResponse(flip.id, 125.25, -830.5, 3.125)
        val encoded = json.encodeToString<DecisionResponse>(response)
        val fields = json.parseToJsonElement(encoded).jsonObject
        fields.keys shouldBe spec.requiredFields.keys + setOf("type", "decisionId")
        val decoded = json.decodeFromString<DecisionResponse>(encoded)
        decoded shouldBe response
        DecisionValidators.validate(flip, decoded) shouldBe null

        for (requiredField in spec.requiredFields.keys) {
            shouldThrow<SerializationException> {
                json.decodeFromString<DecisionResponse>(JsonObject(fields - requiredField).toString())
            }
        }
        shouldThrow<SerializationException> {
            json.decodeFromString<DecisionResponse>(
                """{"type":"FlipThrowResponse","decisionId":"flip","aimX":125.25,"aimY":230.5,"strength":0.74,"sway":0.0}"""
            )
        }
    }

    test("native flip validator rejects nonfinite values while leaving finite range clamping to physics") {
        val valid = FlipThrowResponse(flip.id, -100000.5, 950000.25, -200.125)
        DecisionValidators.validate(flip, valid) shouldBe null
        for (invalid in listOf(
            valid.copy(velocityX = Double.NaN),
            valid.copy(velocityY = Double.POSITIVE_INFINITY),
            valid.copy(spin = Double.NEGATIVE_INFINITY),
        )) {
            DecisionValidators.validate(flip, invalid) shouldNotBe null
        }
        DecisionValidators.validate(flip, NumberChosenResponse(flip.id, 1)) shouldNotBe null
    }

    test("trigger contract orders trigger indices even when several abilities have the same source") {
        val source = EntityId("shared-source")
        val decision = OrderTriggersDecision(
            id = "order", playerId = player, prompt = "Order", context = DecisionContext(),
            triggers = listOf(
                TriggerToOrder(source, "Source", "First ability"),
                TriggerToOrder(source, "Source", "Second ability"),
                TriggerToOrder(source, "Source", "Third ability"),
            ),
        )
        decision.responseSpec() shouldBe DecisionResponseSpec(
            responseType = "TriggerOrderResponse",
            requiredFields = mapOf("resolutionOrder" to DecisionResponseFieldKind.INTEGER_ARRAY),
        )
        val response = TriggerOrderResponse(decision.id, listOf(2, 0, 1))
        val decoded = json.decodeFromString<DecisionResponse>(json.encodeToString<DecisionResponse>(response))
        decoded shouldBe response
        DecisionValidators.validate(decision, decoded) shouldBe null
        for (order in listOf(listOf(0, 0, 1), listOf(0, 1), listOf(0, 1, 3), listOf(-1, 0, 1))) {
            DecisionValidators.validate(decision, TriggerOrderResponse(decision.id, order)) shouldNotBe null
        }
        DecisionValidators.validate(decision, OrderedResponse(decision.id, listOf(source))) shouldNotBe null
    }
})
