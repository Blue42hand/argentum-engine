package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.conditions.Exists
import com.wingedsheep.sdk.scripting.conditions.SourceIsModified
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EntryEvaluationViewsContractTest : FunSpec({
    val evaluator = PredicateEvaluator(cardRegistry = null)
    val player = EntityId.generate()
    val otherPlayer = EntityId.generate()
    val entrant = EntityId.generate()
    val other = EntityId.generate()
    val foreign = EntityId.generate()

    fun creature(name: String, subtype: Subtype, cost: String) = ComponentContainer()
        .with(CardComponent(
            cardDefinitionId = name,
            name = name,
            manaCost = ManaCost.parse(cost),
            typeLine = TypeLine.creature(setOf(subtype)),
            ownerId = player,
        ))
        .with(OwnerComponent(player))
        .with(ControllerComponent(player))

    val world = GameState(turnOrder = listOf(player, otherPlayer))
        .withEntity(player, ComponentContainer())
        .withEntity(otherPlayer, ComponentContainer())
        .withEntity(entrant, creature("Entrant", Subtype.GOBLIN, "{2}"))
        .withEntity(other, creature("Other", Subtype.ELF, "{2}"))
        .withEntity(foreign, creature("Foreign", Subtype.ELF, "{1}")
            .with(OwnerComponent(otherPlayer)).with(ControllerComponent(otherPlayer)))
        .addToZone(ZoneKey(player, Zone.GRAVEYARD), entrant)
        .addToZone(ZoneKey(player, Zone.GRAVEYARD), other)
        .addToZone(ZoneKey(otherPlayer, Zone.GRAVEYARD), foreign)
    val prepared = world.updateEntity(entrant) { entity ->
        entity.with(entity.get<CardComponent>()!!.copy(
            manaCost = ManaCost.parse("{5}"),
            typeLine = TypeLine(cardTypes = setOf(CardType.ARTIFACT)),
        )).with(ControllerComponent(otherPlayer)).with(CastChoicesComponent(chosen = mapOf(
            ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice("Elf")
        )))
    }
    val views = EntryEvaluationViews(entrant, world, prepared)
    val context = EffectContext(sourceId = entrant, controllerId = player)

    test("graveyard candidates use the original world even when the entrant has the same ID") {
        val creatures = DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature)
        evaluator.amounts.evaluate(prepared, creatures, context, entryViews = views) shouldBe 2
        evaluator.amounts.evaluate(prepared, creatures, context, prepared.projectedState, views) shouldBe 2
        evaluator.amounts.evaluate(prepared, creatures, context) shouldBe 1
    }

    test("conditional Exists and nested Count ignore a prepared input projection") {
        val worldGoblin = Exists(Player.You, Zone.GRAVEYARD,
            GameObjectFilter(cardPredicates = listOf(CardPredicate.HasSubtype(Subtype.GOBLIN))))
        val amount = DynamicAmount.Conditional(worldGoblin,
            DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature),
            DynamicAmount.Fixed(0))
        evaluator.conditions.evaluate(prepared, worldGoblin, context, prepared.projectedState, views) shouldBe true
        evaluator.amounts.evaluate(prepared, amount, context, prepared.projectedState, views) shouldBe 2
        evaluator.amounts.evaluate(prepared, amount, context) shouldBe 0
    }

    test("predicate dynamic cap counts world cards when predicate input is prepared") {
        val cap = DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature)
        val predicate = CardPredicate.ManaValueAtMostDynamic(cap)
        val predicateContext = PredicateContext.fromEffectContext(context, views)
        evaluator.matchesCardPredicate(prepared, prepared.projectedState, other,
            predicate, predicateContext) shouldBe true
    }

    test("prepared source choice filters original graveyard candidates") {
        val chosenType = DynamicAmount.Count(Player.You, Zone.GRAVEYARD,
            GameObjectFilter(cardPredicates = listOf(CardPredicate.HasChosenSubtype)))
        evaluator.amounts.evaluate(prepared, chosenType, context, entryViews = views) shouldBe 1
        evaluator.amounts.evaluate(world, chosenType, context) shouldBe 0
    }

    test("kicker condition reads the prepared source inside a conditional amount") {
        val kicked = prepared.updateEntity(entrant) { entity ->
            entity.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.KICKED to ChoiceValue.Flag)))
        }
        val kickedViews = views.copy(entrantAsWouldExist = kicked)
        evaluator.conditions.evaluate(world, WasKicked, context, entryViews = kickedViews) shouldBe true
        evaluator.conditions.evaluate(world, WasKicked, context) shouldBe false
        evaluator.amounts.evaluate(world, DynamicAmount.Conditional(
            WasKicked, DynamicAmount.Fixed(3), DynamicAmount.Fixed(0)
        ), context, entryViews = kickedViews) shouldBe 3
    }

    test("source modified condition sees its prepared entry counter without changing world candidates") {
        val withCounter = prepared.updateEntity(entrant) { entity ->
            entity.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        }
        val counterViews = views.copy(entrantAsWouldExist = withCounter)
        evaluator.conditions.evaluate(world, SourceIsModified, context, entryViews = counterViews) shouldBe true
        evaluator.conditions.evaluate(world, SourceIsModified, context) shouldBe false
        evaluator.amounts.evaluate(world, DynamicAmount.Conditional(
            SourceIsModified,
            DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature),
            DynamicAmount.Fixed(0),
        ), context, entryViews = counterViews) shouldBe 2
    }

    test("explicit entrant controller roles select the prepared player, then count original candidates") {
        val sourceControllerCards = DynamicAmount.Count(Player.ControllerOfSource, Zone.GRAVEYARD)
        val affectedControllerCards = DynamicAmount.Count(Player.ControllerOfAffectedEntity, Zone.GRAVEYARD)
        evaluator.amounts.evaluate(world, sourceControllerCards, context, entryViews = views) shouldBe 1
        evaluator.amounts.evaluate(world, affectedControllerCards,
            context.copy(affectedEntityId = entrant), entryViews = views) shouldBe 1
        evaluator.amounts.evaluate(world, sourceControllerCards, context) shouldBe 2
        evaluator.conditions.evaluate(world,
            Exists(Player.ControllerOfSource, Zone.GRAVEYARD,
                GameObjectFilter(cardPredicates = listOf(CardPredicate.HasSubtype(Subtype.GOBLIN)))),
            context, entryViews = views) shouldBe false
    }

    test("self reference and composite condition retain both entry views") {
        val creatures = DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature)
        val selfManaValue = DynamicAmount.EntityProperty(EffectTarget.Self, EntityNumericProperty.ManaValue)
        val combined = DynamicAmount.Add(creatures, selfManaValue)
        evaluator.amounts.evaluate(world, combined, context, entryViews = views) shouldBe 7
        evaluator.amounts.evaluate(world,
            DynamicAmount.Conditional(
                Compare(combined, ComparisonOperator.EQ, DynamicAmount.Fixed(7)),
                DynamicAmount.Fixed(10), DynamicAmount.Fixed(0),
            ), context, entryViews = views) shouldBe 10
    }
})
