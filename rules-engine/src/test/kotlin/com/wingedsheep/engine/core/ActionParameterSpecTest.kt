package com.wingedsheep.engine.core

import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class ActionParameterSpecTest : FunSpec({
    val player = EntityId("player")

    test("combat templates expose their native parameter fields") {
        ActionParameterizer.spec(
            DeclareAttackers(playerId = player, attackers = emptyMap())
        ).allowedFields shouldContainExactly mapOf(
            "attackers" to ActionParameterFieldKind.ENTITY_ID_MAP
        )

        ActionParameterizer.spec(
            DeclareBlockers(playerId = player, blockers = emptyMap())
        ).allowedFields shouldContainExactly mapOf(
            "blockers" to ActionParameterFieldKind.ENTITY_ID_ARRAY_MAP
        )
    }

    test("cast templates expose target and X fields") {
        ActionParameterizer.spec(
            CastSpell(playerId = player, cardId = EntityId("card"))
        ).allowedFields shouldContainExactly mapOf(
            "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "xValue" to ActionParameterFieldKind.INTEGER,
        )
    }

    test("parameterless actions expose an empty contract") {
        val spec = ActionParameterizer.spec(PassPriority(player))

        spec shouldBe ActionParameterSpec.EMPTY
        spec.acceptsParameters shouldBe false
    }
})
