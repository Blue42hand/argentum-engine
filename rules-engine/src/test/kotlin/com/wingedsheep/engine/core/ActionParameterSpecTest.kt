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

    test("cast templates expose target, X, and common nonmana payment fields") {
        ActionParameterizer.spec(
            CastSpell(playerId = player, cardId = EntityId("card"))
        ).allowedFields shouldContainExactly mapOf(
            "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "xValue" to ActionParameterFieldKind.INTEGER,
            "declaredCostTimes" to ActionParameterFieldKind.INTEGER,
            "tappedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "sacrificedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "discardedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "exiledCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "bouncedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "delvedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
        )
    }

    test("ability templates expose the common nonmana payment choices") {
        ActionParameterizer.spec(
            ActivateAbility(playerId = player, sourceId = EntityId("source"),
                abilityId = com.wingedsheep.sdk.scripting.AbilityId("ability"))
        ).allowedFields shouldContainExactly mapOf(
            "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "xValue" to ActionParameterFieldKind.INTEGER,
            "tappedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "sacrificedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "discardedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "exiledCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            "bouncedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
        )
    }

    test("parameterless actions expose an empty contract") {
        val spec = ActionParameterizer.spec(PassPriority(player))

        spec shouldBe ActionParameterSpec.EMPTY
        spec.acceptsParameters shouldBe false
    }
    test("return choices preserve other template payment fields and legacy choices") {
        val bounce = EntityId("return-choice")
        val existing = com.wingedsheep.sdk.scripting.AdditionalCostPayment(
            discardedCards = listOf(EntityId("discard-choice")),
            bouncedPermanents = listOf(bounce),
        )
        val cast = CastSpell(playerId = player, cardId = EntityId("spell"), additionalCostPayment = existing)
        val ability = ActivateAbility(playerId = player, sourceId = EntityId("source"),
            abilityId = com.wingedsheep.sdk.scripting.AbilityId("ability"), costPayment = existing)
        val state = com.wingedsheep.engine.state.GameState()
        (ActionParameterizer.apply(cast, ActionParams(xValue = 2), state) as CastSpell)
            .additionalCostPayment shouldBe existing
        (ActionParameterizer.apply(ability, ActionParams(xValue = 2), state) as ActivateAbility)
            .costPayment shouldBe existing
        val replacement = EntityId("new-return-choice")
        (ActionParameterizer.apply(cast, ActionParams(bouncedPermanents = listOf(replacement)), state) as CastSpell)
            .additionalCostPayment shouldBe existing.copy(bouncedPermanents = listOf(replacement))
        (ActionParameterizer.apply(ability, ActionParams(bouncedPermanents = listOf(replacement)), state) as ActivateAbility)
            .costPayment shouldBe existing.copy(bouncedPermanents = listOf(replacement))
    }

})
