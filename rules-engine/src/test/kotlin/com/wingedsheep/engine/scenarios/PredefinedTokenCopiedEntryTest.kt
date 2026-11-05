package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class PredefinedTokenCopiedEntryTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private fun tokens(game: TestGame) = game.state.getBattlefield().filter {
        game.state.getEntity(it)?.has<TokenComponent>() == true
    }
    private fun execute(game: TestGame, effect: Effect): EffectResult {
        val result = services.effectExecutorRegistry.execute(game.state, effect, EffectContext(null, game.player1Id))
        result.error shouldBe null
        game.state = result.state
        return result
    }
    private fun reflect(game: TestGame, name: String) {
        val chosen = game.findPermanent(name)!!
        val result = services.effectExecutorRegistry.execute(game.state, Effects.GrantReplacementEffect(
            EntersAsCopy(optional = false, copyOf = EffectTarget.SpecificEntity(chosen),
                appliesTo = EventPattern.ZoneChangeEvent(filter = GameObjectFilter.CreatureOrPlaneswalker, to = Zone.BATTLEFIELD)),
            EffectTarget.SpecificEntity(chosen), Duration.NextUse("one or more creatures or planeswalkers enter")),
            EffectContext(chosen, game.player1Id))
        result.error shouldBe null
        game.state = result.state
        game.state.grantedReplacementEffects.size shouldBe 1
    }
    private fun answerType(game: TestGame): ExecutionResult {
        game.state = json.decodeFromString(json.encodeToString(game.state))
        val decision = game.state.pendingDecision as ChooseOptionDecision
        return game.submitDecision(OptionChosenResponse(decision.id,
            decision.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) })).also { it.error shouldBe null }
    }

    init {
        cardRegistry.register(card("Predefined Choice Copy") {
            typeLine = "Creature — Shapeshifter"
            power = 2; toughness = 2
            replacementEffect(EntersWithCounters(CounterType.CHARGE, 2, selfOnly = true))
            replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE, allowedCreatureTypes = listOf("Goblin", "Elf")))
        })
        cardRegistry.register(card("Predefined Walker Copy") {
            typeLine = "Planeswalker — Test"
            startingLoyalty = 4
        })
        cardRegistry.register(card("Predefined Pair Substitute") {
            typeLine = "Artifact"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Frog", "Squirrel")))
        })

        test("NextUse copied predefined bundle entries each choose with counters across save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Predefined Choice Copy")
                .withCardOnBattlefield(1, "Predefined Pair Substitute").build()
            reflect(game, "Predefined Choice Copy")
            val initial = execute(game, CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                creatureTypes = setOf("Food"), artifactToken = true, count = DynamicAmount.Fixed(2),
                initialCounters = mapOf(CounterType.CHARGE to 1)))
            initial.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD } shouldBe emptyList()
            val entries = mutableListOf<ZoneChangeEvent>()
            repeat(4) { index ->
                tokens(game).size shouldBe index + 1
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Predefined Choice Copy"
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 3
                }
                game.state.grantedReplacementEffects.size shouldBe 1
                entries += answerType(game).events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
            }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 4
            game.state.grantedReplacementEffects shouldBe emptyList()
            entries.size shouldBe 4
            entries.map { it.simultaneousEntryGroup }.distinct().size shouldBe 1
            entries.all { it.simultaneousEntryGroup != null } shouldBe true
        }

        test("native predefined batch resumes every entry and publishes all IDs once") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Predefined Choice Copy").build()
            reflect(game, "Predefined Choice Copy")
            execute(game, CompositeEffect(listOf(
                CreatePredefinedTokenEffect("Frog", count = 2),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 0)),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 1)))))
            repeat(2) { index ->
                tokens(game).size shouldBe index + 1
                answerType(game)
            }
            game.state.pendingDecision shouldBe null
            game.state.grantedReplacementEffects shouldBe emptyList()
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Predefined Choice Copy"
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 3
            }
        }

        test("predefined tokens copied into planeswalkers enter with intrinsic loyalty") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Predefined Walker Copy")
                .withCardOnBattlefield(1, "Predefined Pair Substitute").build()
            reflect(game, "Predefined Walker Copy")
            val result = execute(game, CreatePredefinedTokenEffect("Frog"))
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Predefined Walker Copy"
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.LOYALTY) shouldBe 4
            }
            result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
                .map { it.entityName }.toSet() shouldBe setOf("Predefined Walker Copy")
            game.state.grantedReplacementEffects shouldBe emptyList()
        }
    }
}
