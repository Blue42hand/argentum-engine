package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.token.TokenCreationReplacementHelper
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TokenCopyEntryContinuationTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private fun tokens(game: TestGame) = game.state.getBattlefield().filter {
        game.state.getEntity(it)?.has<TokenComponent>() == true
    }
    private fun chooseRiot(game: TestGame, counter: Boolean): ExecutionResult {
        game.state = json.decodeFromString(json.encodeToString(game.state))
        val decision = game.state.pendingDecision as ChooseOptionDecision
        val word = if (counter) "counter" else "haste"
        val index = decision.options.indexOfFirst { it.contains(word, ignoreCase = true) }
        (index >= 0) shouldBe true
        return game.submitDecision(OptionChosenResponse(decision.id, index)).also { it.error shouldBe null }
    }
    private fun copyEffect(game: TestGame, source: Boolean, name: String, exceptions: CopyExceptions): Effect =
        if (source) CreateTokenCopyOfSourceEffect(count = 2, exceptions = exceptions, exileAtStep = Step.END)
        else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(game.findPermanent(name)!!),
            count = DynamicAmount.Fixed(2), exceptions = exceptions, exileAtStep = Step.END)

    init {
        cardRegistry.register(card("Copy Entry Tapped Fixture") {
            typeLine = "Creature — Zombie"; power = 2; toughness = 2
            replacementEffect(EntersTapped())
        })
        cardRegistry.register(card("Copy Entry Equipment Fixture") { typeLine = "Artifact — Equipment" })
        cardRegistry.register(card("Copy Entry Choice Fixture") {
            typeLine = "Creature — Shapeshifter"; power = 2; toughness = 2
            replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE, allowedCreatureTypes = listOf("Goblin", "Elf")))
        })

        for (source in listOf(false, true)) {
            test("non-bundle ${if (source) "source" else "target"} copies keep riot world riders and pipeline across save-load") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Spider-Punk").build()
                val original = game.findPermanent("Spider-Punk")!!
                val copies = copyEffect(game, source, "Spider-Punk",
                    CopyExceptions(removedSupertypes = setOf(Supertype.LEGENDARY)))
                val initial = services.effectExecutorRegistry.execute(game.state, CompositeEffect(listOf(
                    copies,
                    AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 0)),
                    AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 1)))),
                    EffectContext(original, game.player1Id))
                initial.error shouldBe null
                game.state = initial.state
                initial.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD } shouldBe emptyList()
                val entered = mutableListOf<ZoneChangeEvent>()
                repeat(2) { index ->
                    tokens(game).size shouldBe index + 1
                    entered += chooseRiot(game, true).events.filterIsInstance<ZoneChangeEvent>()
                    tokens(game).size shouldBe index + 1
                    entered += chooseRiot(game, false).events.filterIsInstance<ZoneChangeEvent>()
                }
                game.state.pendingDecision shouldBe null
                tokens(game).size shouldBe 2
                entered.filter { it.toZone == Zone.BATTLEFIELD }.map { it.entityId }.sortedBy { it.value } shouldBe
                    tokens(game).sortedBy { it.value }
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 1
                    game.state.projectedState.hasKeyword(id, Keyword.HASTE) shouldBe true
                }
                game.state.delayedTriggers.size shouldBe 2
            }

            test("non-bundle ${if (source) "source" else "target"} copy riot counter placement excludes earlier sibling multipliers") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Spider-Punk")
                    .withCardOnBattlefield(1, "Corpsejack Menace").build()
                val original = game.findPermanent("Corpsejack Menace")!!
                val result = services.effectExecutorRegistry.execute(game.state,
                    copyEffect(game, source, "Corpsejack Menace", CopyExceptions(addedSubtypes = setOf(Subtype("Spider")))),
                    EffectContext(original, game.player1Id))
                result.error shouldBe null; game.state = result.state
                repeat(2) { chooseRiot(game, true) }
                game.state.pendingDecision shouldBe null
                tokens(game).size shouldBe 2
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                }
            }

            test("non-bundle ${if (source) "source" else "target"} copy NextUse survives both entry choices then expires") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Copy Entry Tapped Fixture")
                    .withCardOnBattlefield(1, "Copy Entry Choice Fixture").build()
                val original = game.findPermanent("Copy Entry Tapped Fixture")!!
                val chosen = game.findPermanent("Copy Entry Choice Fixture")!!
                val grant = services.effectExecutorRegistry.execute(game.state, Effects.GrantReplacementEffect(
                    EntersAsCopy(optional = false, copyOf = EffectTarget.SpecificEntity(chosen),
                        appliesTo = EventPattern.ZoneChangeEvent(filter = GameObjectFilter.Creature, to = Zone.BATTLEFIELD)),
                    EffectTarget.SpecificEntity(chosen), Duration.NextUse("one or more creatures enter")),
                    EffectContext(chosen, game.player1Id))
                grant.error shouldBe null
                game.state = grant.state
                val result = services.effectExecutorRegistry.execute(game.state,
                    copyEffect(game, source, "Copy Entry Tapped Fixture", CopyExceptions.None),
                    EffectContext(original, game.player1Id))
                result.error shouldBe null; game.state = result.state
                repeat(2) { index ->
                    tokens(game).size shouldBe index + 1
                    game.state.grantedReplacementEffects.size shouldBe 1
                    game.state = json.decodeFromString(json.encodeToString(game.state))
                    val decision = game.state.pendingDecision as ChooseOptionDecision
                    val goblin = decision.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) }
                    (goblin >= 0) shouldBe true
                    game.submitDecision(OptionChosenResponse(decision.id, goblin)).error shouldBe null
                }
                game.state.pendingDecision shouldBe null
                game.state.grantedReplacementEffects shouldBe emptyList()
                tokens(game).size shouldBe 2
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Copy Entry Choice Fixture"
                    game.state.getEntity(id)?.has<TappedComponent>() shouldBe false
                }
            }
        }

        test("every direct copy factory honors copied printed enters-tapped without tap transition events") {
            for (kind in listOf("source", "target", "chosen", "equipped", "attached")) {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Copy Entry Tapped Fixture")
                    .withCardOnBattlefield(1, "Copy Entry Equipment Fixture").build()
                val original = game.findPermanent("Copy Entry Tapped Fixture")!!
                val equipment = game.findPermanent("Copy Entry Equipment Fixture")!!
                game.state = game.state.updateEntity(equipment) { it.with(AttachedToComponent(original)) }
                val context = EffectContext(if (kind == "equipped") equipment else original, game.player1Id)
                val effect: Effect = when (kind) {
                    "source" -> CreateTokenCopyOfSourceEffect()
                    "chosen" -> CreateTokenCopyOfChosenPermanentEffect(GameObjectFilter.Creature)
                    "equipped" -> CreateTokenCopyOfEquippedCreatureEffect()
                    else -> CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(original))
                }
                val result = if (kind == "attached") TokenCreationReplacementHelper.createAttachedPermanentCopies(
                    game.state, original, game.player1Id, 1, cardRegistry, StaticAbilityHandler(cardRegistry),
                    services.predicateEvaluator, effect, context)
                else services.effectExecutorRegistry.execute(game.state, effect, context)
                result.error shouldBe null; game.state = result.state
                game.state.pendingDecision shouldBe null
                tokens(game).size shouldBe 1
                game.state.getEntity(tokens(game).single())?.has<TappedComponent>() shouldBe true
                result.events.filterIsInstance<TappedEvent>() shouldBe emptyList()
            }
        }
    }
}
