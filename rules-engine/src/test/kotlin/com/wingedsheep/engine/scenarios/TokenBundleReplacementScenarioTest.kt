package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.token.TokenBundleReplacements
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Generic replacement primitive: order, once-per-event bookkeeping, and continuation boundaries. */
class TokenBundleReplacementScenarioTest : ScenarioTestBase() {
    private val substitute = card("Bundle Substitute") {
        manaCost = "{0}"; typeLine = "Artifact"
        replacementEffect(ReplaceTokenCreationWithTokens(listOf("Clue", "Food", "Treasure"),
            EventPattern.TokenCreationEvent(tokenFilter = GameObjectFilter.Any.withAnySubtype("Clue", "Food", "Treasure"))))
    }
    private val additional = card("Bundle Additional") {
        manaCost = "{0}"; typeLine = "Artifact"
        replacementEffect(CreateAdditionalToken("Food"))
    }
    private fun board(copies: Int = 1, extra: Boolean = false): TestGame {
        val builder = scenario().withPlayers()
        repeat(copies) { builder.withCardOnBattlefield(1, "Bundle Substitute") }
        if (extra) builder.withCardOnBattlefield(1, "Bundle Additional")
        return builder.build()
    }
    private fun create(game: TestGame, effect: Effect, player: com.wingedsheep.sdk.model.EntityId = game.player1Id): EffectResult {
        val result = services.effectExecutorRegistry.execute(game.state, effect, EffectContext(null, player))
        result.error shouldBe null
        game.state = result.state
        return result
    }
    private fun counts(game: TestGame) = listOf("Clue", "Food", "Treasure").map { game.findPermanents(it).size }
    private fun choose(game: TestGame, prefix: String): ExecutionResult {
        val decision = game.state.pendingDecision as ChooseOptionDecision
        return game.submitDecision(OptionChosenResponse(decision.id, decision.options.indexOfFirst { it.startsWith(prefix) })).also { it.error shouldBe null }
    }

    init {
        cardRegistry.register(listOf(substitute, additional))

        test("substitutes every qualifying token and preserves tapped entry instructions") {
            val game = board()
            val result = create(game, CreatePredefinedTokenEffect("Treasure", count = 2, tapped = true))
            counts(game) shouldBe listOf(2, 2, 2)
            result.updatedCollections[CREATED_TOKENS]?.size shouldBe 6
            game.state.getBattlefield().filter { game.state.getEntity(it)?.has<TokenComponent>() == true }
                .all { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe true
        }
        test("two substitute instances each apply once to the whole event") {
            val game = board(copies = 2)
            create(game, CreatePredefinedTokenEffect("Food", count = 2))
            counts(game) shouldBe listOf(6, 6, 6)
            game.state.pendingDecision shouldBe null
        }
        test("adding first transforms both the original and added Food") {
            val game = board(extra = true)
            create(game, CreatePredefinedTokenEffect("Treasure", count = 2))
            counts(game) shouldBe listOf(0, 0, 0)
            choose(game, "Bundle Additional")
            counts(game) shouldBe listOf(3, 3, 3)
        }
        test("substituting first adds exactly one Food to the mixed bundle") {
            val game = board(extra = true)
            create(game, CreatePredefinedTokenEffect("Treasure", count = 2))
            choose(game, "Bundle Substitute")
            counts(game) shouldBe listOf(2, 3, 2)
        }
        test("additional Food makes an initially unrelated token eligible") {
            val game = board(extra = true)
            create(game, CreateTokenEffect(count = DynamicAmount.Fixed(1), power = 1, toughness = 1, colors = emptySet(), creatureTypes = setOf("Soldier")))
            counts(game) shouldBe listOf(1, 1, 1)
            game.findPermanents("Soldier Token").size shouldBe 1
        }
        test("zero creation never adds tokens or offers replacements") {
            val game = board(extra = true)
            create(game, CreatePredefinedTokenEffect("Treasure", count = 0))
            counts(game) shouldBe listOf(0, 0, 0)
            game.state.pendingDecision shouldBe null
        }
        test("another player's creation is not substituted") {
            val game = board()
            create(game, CreatePredefinedTokenEffect("Treasure", count = 2), game.player2Id)
            counts(game) shouldBe listOf(0, 0, 2)
        }
        test("copying a qualifying permanent uses its characteristics") {
            val game = board()
            val source = create(game, CreatePredefinedTokenEffect("Treasure", controller = EffectTarget.SpecificEntity(game.player2Id)))
                .updatedCollections[CREATED_TOKENS]!!.single()
            create(game, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source)))
            counts(game) shouldBe listOf(1, 1, 2)
        }
        test("chosen permanent copy resumes through replacements") {
            val game = board()
            create(game, CreatePredefinedTokenEffect("Treasure", controller = EffectTarget.SpecificEntity(game.player2Id)))
            val treasure = game.findPermanent("Treasure")!!
            game.state = game.state.updateEntity(treasure) { it.with(ControllerComponent(game.player1Id)) }
            create(game, CreateTokenCopyOfChosenPermanentEffect(GameObjectFilter.Artifact))
            game.selectCards(listOf(treasure)).error shouldBe null
            counts(game) shouldBe listOf(1, 1, 2)
        }
        test("pending replacement choice round trips with no token side effects") {
            val game = board(extra = true)
            create(game, CreatePredefinedTokenEffect("Clue"))
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            game.state = json.decodeFromString(json.encodeToString(game.state))
            choose(game, "Bundle Substitute")
            counts(game) shouldBe listOf(1, 2, 1)
        }
        test("two investigations are two creation events and emit exactly two investigations") {
            val game = board(extra = true)
            create(game, InvestigateEffect(DynamicAmount.Fixed(2)))
            game.state.getEntity(game.player1Id)?.has<com.wingedsheep.engine.state.components.player.InvestigatedThisTurnComponent>() shouldBe false
            val first = choose(game, "Bundle Substitute")
            first.events.filterIsInstance<InvestigatedEvent>().map { it.firstThisTurn } shouldBe listOf(true)
            counts(game) shouldBe listOf(1, 2, 1)
            val decision = game.state.pendingDecision as ChooseOptionDecision
            val result = game.submitDecision(OptionChosenResponse(decision.id,
                decision.options.indexOfFirst { it.startsWith("Bundle Substitute") }))
            result.error shouldBe null
            result.events.filterIsInstance<InvestigatedEvent>().map { it.firstThisTurn } shouldBe listOf(false)
            counts(game) shouldBe listOf(2, 4, 2)
            game.state.pendingDecision shouldBe null
            game.state.getEntity(game.player1Id)?.has<com.wingedsheep.engine.state.components.player.InvestigatedThisTurnComponent>() shouldBe true
        }
        test("optional attached-copy replacement can be declined once, then the bundle finishes") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Bundle Substitute")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Mirrormind Crown", "Grizzly Bears").build()
            create(game, CreatePredefinedTokenEffect("Treasure"))
            choose(game, "Mirrormind Crown")
            game.answerYesNo(false).error shouldBe null
            counts(game) shouldBe listOf(1, 1, 1)
            create(game, CreatePredefinedTokenEffect("Treasure"))
            game.state.pendingDecision shouldBe null
            counts(game) shouldBe listOf(2, 2, 2)
        }
        test("optional attached-copy replacement replaces the complete mixed bundle") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Bundle Substitute")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Mirrormind Crown", "Grizzly Bears").build()
            create(game, CreatePredefinedTokenEffect("Treasure", count = 2))
            choose(game, "Bundle Substitute")
            game.answerYesNo(true).error shouldBe null
            counts(game) shouldBe listOf(0, 0, 0)
            game.findPermanents("Grizzly Bears").size shouldBe 7
        }
        test("a multiplier applies to every final kind exactly once") {
            val doubler = card("Bundle Doubler") {
                manaCost = "{0}"; typeLine = "Artifact"
                replacementEffect(MultiplyTokenCreation(2))
            }
            cardRegistry.register(doubler)
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Bundle Substitute")
                .withCardOnBattlefield(1, "Bundle Doubler").build()
            create(game, CreatePredefinedTokenEffect("Treasure", count = 2))
            choose(game, "Bundle Substitute")
            counts(game) shouldBe listOf(4, 4, 4)
        }
        test("projected replacement control follows a stolen source") {
            val game = board()
            val source = game.findPermanent("Bundle Substitute")!!
            create(game, GainControlEffect(EffectTarget.SpecificEntity(source)), game.player2Id)
            create(game, CreatePredefinedTokenEffect("Food"), game.player2Id)
            counts(game) shouldBe listOf(1, 1, 1)
            create(game, CreatePredefinedTokenEffect("Food"))
            counts(game) shouldBe listOf(1, 2, 1)
        }
        test("ability removal disables a replacement until abilities return") {
            val blanker = card("Bundle Blanker") {
                manaCost = "{0}"; typeLine = "Enchantment"
                staticAbility { ability = LoseAllAbilities(com.wingedsheep.sdk.scripting.filters.unified.GroupFilter(GameObjectFilter.Artifact)) }
            }
            cardRegistry.register(blanker)
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Bundle Substitute")
                .withCardOnBattlefield(1, "Bundle Blanker").build()
            create(game, CreatePredefinedTokenEffect("Treasure"))
            counts(game) shouldBe listOf(0, 0, 1)
        }
        test("source-copy creation also enters the replacement path") {
            val game = board()
            create(game, CreatePredefinedTokenEffect("Food", controller = EffectTarget.SpecificEntity(game.player2Id)))
            val result = services.effectExecutorRegistry.execute(game.state, CreateTokenCopyOfSourceEffect(),
                EffectContext(game.findPermanent("Food"), game.player1Id))
            result.error shouldBe null
            game.state = result.state
            counts(game) shouldBe listOf(1, 2, 1)
        }
        test("all CREATED_TOKENS survive replacement-choice suspension into later composite siblings") {
            val game = board(extra = true)
            create(game, CompositeEffect(listOf(CreatePredefinedTokenEffect("Treasure")) +
                (0..3).map { index -> AddCountersEffect(com.wingedsheep.sdk.core.CounterType.CHARGE, 1,
                    EffectTarget.PipelineTarget(CREATED_TOKENS, index)) }))
            choose(game, "Bundle Substitute")
            val tokens = game.state.getBattlefield().filter { game.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.size shouldBe 4
            tokens.map { game.state.getEntity(it)?.get<CountersComponent>()?.getCount(com.wingedsheep.sdk.core.CounterType.CHARGE) ?: 0 }
                .sum() shouldBe 4
        }
        test("substituted tokens retain delayed sacrifice and initial counters") {
            val game = board()
            create(game, CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                creatureTypes = setOf("Food"), artifactToken = true, tapped = true,
                sacrificeAtStep = com.wingedsheep.sdk.core.Step.END,
                initialCounters = mapOf(com.wingedsheep.sdk.core.CounterType.CHARGE to 2)))
            counts(game) shouldBe listOf(1, 1, 1)
            game.state.delayedTriggers.size shouldBe 3
            val tokens = game.state.getBattlefield().filter { game.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.all { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe true
            tokens.map { game.state.getEntity(it)?.get<CountersComponent>()?.getCount(com.wingedsheep.sdk.core.CounterType.CHARGE) }
                .toSet() shouldBe setOf(2)
        }
        test("large numbers of substitutes stay coalesced rather than allocating a token tree") {
            val game = board(copies = 18)
            val context = EffectContext(null, game.player1Id)
            val planner = TokenBundleReplacements(cardRegistry, services.dynamicAmountEvaluator,
                services.targetFinder, services.effectExecutorRegistry::execute)
            val food = cardRegistry.requireCard("Food")
            var bundle = TokenCreationBundle(game.player1Id, context, listOf(
                TokenCreationBatch(CreatePredefinedTokenEffect("Food"),
                    prospectiveCard = CardComponent(cardDefinitionId = food.name, name = food.name,
                        manaCost = food.manaCost, typeLine = food.typeLine, ownerId = game.player1Id), count = 1)))
            game.findPermanents("Bundle Substitute").forEach { id ->
                bundle = planner.apply(game.state, bundle, TokenBundleReplacement(
                    com.wingedsheep.engine.replacement.ReplacementEffectIdentity.BattlefieldIdentity(id, 0),
                    id, game.player1Id, substitute.script.replacementEffects.single(), "Substitute"))
            }
            bundle.batches.size shouldBe 3
            bundle.batches.map { it.count } shouldBe listOf(129140163, 129140163, 129140163)
        }
    }
}
