package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class TokenBundleInheritedEntryRidersTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private fun tokens(game: TestGame) = game.state.getBattlefield().filter {
        game.state.getEntity(it)?.has<TokenComponent>() == true
    }
    private fun saveLoad(game: TestGame) { game.state = json.decodeFromString(json.encodeToString(game.state)) }
    private fun create(game: TestGame, effect: Effect): EffectResult {
        val result = services.effectExecutorRegistry.execute(game.state, effect, EffectContext(null, game.player1Id))
        result.error shouldBe null
        game.state = result.state
        return result
    }
    private fun choose(game: TestGame, prefix: String) {
        val decision = game.state.pendingDecision as ChooseOptionDecision
        game.submitDecision(OptionChosenResponse(decision.id,
            decision.options.indexOfFirst { it.startsWith(prefix) })).error shouldBe null
    }

    init {
        cardRegistry.register(card("Rider Bundle Substitute") {
            typeLine = "Artifact"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Clue", "Food", "Treasure"),
                EventPattern.TokenCreationEvent(tokenFilter = GameObjectFilter.Any.withAnySubtype("Clue", "Food", "Treasure"))))
        })
        cardRegistry.register(card("Rider Creature Substitute") {
            typeLine = "Artifact"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Frog", "Squirrel")))
        })
        cardRegistry.register(card("Rider Additional") {
            typeLine = "Artifact"
            replacementEffect(CreateAdditionalToken("Frog", inheritTapped = true,
                scale = AdditionalTokenScale.PER_MATCHING_TOKEN))
        })
        cardRegistry.register(card("Rider Defender") {
            typeLine = "Planeswalker — Test"
            startingLoyalty = 5
        })
        cardRegistry.register(card("Colorless Identity Token") {
            typeLine = "Artifact"
            colorIdentity = "R"
        })

        test("inherited counters exist during every copy entry choice and survive save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Rider Bundle Substitute")
                .withCardOnBattlefield(1, "Metallic Mimic")
                .withCardAttachedTo(1, "Mirrormind Crown", "Metallic Mimic").build()
            create(game, CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                creatureTypes = setOf("Food"), artifactToken = true,
                initialCounters = mapOf(CounterType.CHARGE to 2)))
            choose(game, "Rider Bundle Substitute")
            game.answerYesNo(true).error shouldBe null
            repeat(3) { index ->
                tokens(game).size shouldBe index + 1
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 2
                }
                saveLoad(game)
                val decision = game.state.pendingDecision as ChooseOptionDecision
                game.submitDecision(OptionChosenResponse(decision.id,
                    decision.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) })).error shouldBe null
            }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 3
        }

        test("copy-token dynamic initial counters survive multi-kind substitution") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Rider Bundle Substitute")
                .withCardOnBattlefield(1, "Food").build()
            create(game, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(game.findPermanent("Food")!!),
                count = DynamicAmount.Fixed(2), initialCounters = mapOf(CounterType.CHARGE to DynamicAmount.Fixed(3))))
            tokens(game).size shouldBe 6
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 3
            }
        }

        test("predefined substitutes ask each defender before exposing entry and keep one group across save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Rider Creature Substitute")
                .withCardOnBattlefield(2, "Rider Defender").withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            val walker = game.findPermanent("Rider Defender")!!
            val initial = create(game, CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                creatureTypes = setOf("Food"), artifactToken = true, count = DynamicAmount.Fixed(2),
                tapped = true, attacking = true, attackDefender = EnteringAttackDefender.CONTROLLER_CHOOSES,
                attackedPlayer = EffectTarget.SpecificEntity(game.player2Id),
                initialCounters = mapOf(CounterType.CHARGE to 2)))
            // Asking the defender emits a prompt, but no token entry may be exposed yet.
            initial.events.filterNot { it is DecisionRequestedEvent } shouldBe emptyList()
            initial.events.filterIsInstance<DecisionRequestedEvent>().map { it.decisionId } shouldBe
                listOf(game.state.pendingDecision!!.id)
            tokens(game) shouldBe emptyList()
            val groups = mutableListOf<String?>()
            repeat(4) { index ->
                saveLoad(game)
                val decision = game.state.pendingDecision as ChooseTargetsDecision
                decision.legalTargets[0]?.toSet() shouldBe setOf(game.player2Id, walker)
                val defender = if (index % 2 == 0) walker else game.player2Id
                val result = game.submitDecision(TargetsResponse(decision.id, mapOf(0 to listOf(defender))))
                result.error shouldBe null
                groups += result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
                    .map { it.simultaneousEntryGroup }
                tokens(game).size shouldBe index + 1
                val newest = tokens(game).last()
                game.state.getEntity(newest)?.get<AttackingComponent>()?.defenderId shouldBe defender
                game.state.getEntity(newest)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 2
                game.state.getEntity(newest)?.has<TappedComponent>() shouldBe true
            }
            game.state.pendingDecision shouldBe null
            groups.size shouldBe 4
            groups.distinct().size shouldBe 1
            groups.all { it != null } shouldBe true
        }

        test("newly entering counter multipliers do not change later templates of the same event") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Rider Bundle Substitute")
                .withCardOnBattlefield(1, "Corpsejack Menace").build()
            create(game, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(game.findPermanent("Corpsejack Menace")!!),
                count = DynamicAmount.Fixed(2), initialCounters = mapOf(CounterType.PLUS_ONE_PLUS_ONE to DynamicAmount.Fixed(1))))
            tokens(game).size shouldBe 2
            tokens(game).map { game.state.getEntity(it)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) }
                .toSet() shouldBe setOf(2)
        }

        test("per-token additions retain inherited counter and attacking riders through save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Rider Bundle Substitute")
                .withCardOnBattlefield(1, "Rider Additional").withCardOnBattlefield(2, "Rider Defender")
                .withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            create(game, CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                creatureTypes = setOf("Food"), artifactToken = true, tapped = true, attacking = true,
                attackDefender = EnteringAttackDefender.CONTROLLER_CHOOSES,
                initialCounters = mapOf(CounterType.CHARGE to 2)))
            choose(game, "Rider Bundle Substitute")
            tokens(game).size shouldBe 3
            repeat(3) {
                saveLoad(game)
                val decision = game.state.pendingDecision as ChooseTargetsDecision
                game.submitDecision(TargetsResponse(decision.id, mapOf(0 to listOf(game.player2Id)))).error shouldBe null
            }
            game.state.pendingDecision shouldBe null
            game.findPermanents("Frog").size shouldBe 3
            game.findPermanents("Frog").forEach { id ->
                game.state.getEntity(id)?.get<AttackingComponent>()?.defenderId shouldBe game.player2Id
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 2
                game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            }
        }

        test("actual token colors do not come from Commander color identity") {
            val game = scenario().withPlayers().build()
            create(game, CreatePredefinedTokenEffect("Colorless Identity Token"))
            game.state.getEntity(tokens(game).single())?.get<CardComponent>()?.colors shouldBe emptySet()
            create(game, CreatePredefinedTokenEffect("Frog"))
            game.state.getEntity(game.findPermanent("Frog")!!)?.get<CardComponent>()?.colors shouldBe setOf(Color.GREEN)
        }
    }
}
