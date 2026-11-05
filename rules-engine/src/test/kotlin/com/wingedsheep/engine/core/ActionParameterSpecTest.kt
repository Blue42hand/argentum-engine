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
            "targetGroups" to ActionParameterFieldKind.ENTITY_ID_ARRAY_ARRAY,
            "variablePermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
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
            "targetGroups" to ActionParameterFieldKind.ENTITY_ID_ARRAY_ARRAY,
            "variablePermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
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


    test("crew and saddle expose their native list fields") {
        ActionParameterizer.spec(CrewVehicle(player, EntityId("vehicle"), emptyList())).allowedFields shouldContainExactly
            mapOf("crew" to ActionParameterFieldKind.ENTITY_ID_ARRAY)
        ActionParameterizer.spec(SaddleMount(player, EntityId("mount"), emptyList())).allowedFields shouldContainExactly
            mapOf("saddle" to ActionParameterFieldKind.ENTITY_ID_ARRAY)
    }

    test("short cost aliases retain the offered-candidate guard") {
        val offered = EntityId("offered")
        val unoffered = EntityId("unoffered")
        val legal = com.wingedsheep.engine.legalactions.LegalAction(
            actionType = "CastSpell", description = "Cast test",
            action = CastSpell(player, EntityId("spell")),
            additionalCostInfo = com.wingedsheep.engine.legalactions.AdditionalCostData(
                description = "Sacrifice a creature", costType = "SacrificePermanent",
                validSacrificeTargets = listOf(offered),
            ),
        )
        ActionParameterizer.spec(legal).allowedFields["sacrifice"] shouldBe ActionParameterFieldKind.ENTITY_ID_ARRAY
        val state = com.wingedsheep.engine.state.GameState()
        val applied = ActionParameterizer.apply(legal, ActionParams(sacrifice = listOf(offered)), state) as CastSpell
        applied.additionalCostPayment?.sacrificedPermanents shouldBe listOf(offered)
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(legal, ActionParams(sacrifice = listOf(unoffered)), state)
        }
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(legal, ActionParams(
                sacrifice = listOf(offered), sacrificedPermanents = listOf(offered)
            ), state)
        }
    }

    test("target groups preserve empty requirement boundaries and reject flat-target ambiguity") {
        val opponent = EntityId("opponent")
        val state = com.wingedsheep.engine.state.GameState(turnOrder = listOf(player, opponent))
        val template = CastSpell(player, EntityId("spell"))
        val applied = ActionParameterizer.apply(template,
            ActionParams(targetGroups = listOf(emptyList(), listOf(opponent))), state) as CastSpell
        applied.targetGroupCounts shouldBe listOf(0, 1)
        applied.targets shouldBe listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Player(opponent))
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(template,
                ActionParams(targets = listOf(opponent), targetGroups = listOf(listOf(opponent))), state)
        }
    }
})
