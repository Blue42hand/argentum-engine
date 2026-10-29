package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Native entry choices must drain before the enclosing token bundle publishes its collection. */
class TokenBundleEntryContinuationTest : ScenarioTestBase() {
    init {
        cardRegistry.register(card("Entry Bundle Substitute") {
            manaCost = "{0}"; typeLine = "Artifact"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Clue", "Food", "Treasure"),
                EventPattern.TokenCreationEvent(tokenFilter = GameObjectFilter.Any.withAnySubtype("Clue", "Food", "Treasure"))))
        })
        test("multiple native entry choices survive save/load and publish every created copy") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Entry Bundle Substitute")
                .withCardOnBattlefield(1, "Metallic Mimic").build()
            val original = game.findPermanent("Metallic Mimic")!!
            val result = services.effectExecutorRegistry.execute(game.state, CompositeEffect(listOf(
                CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(original), count = DynamicAmount.Fixed(2)),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 0)),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 1))
            )), EffectContext(null, game.player1Id))
            result.error shouldBe null
            game.state = result.state
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            repeat(2) {
                game.state = json.decodeFromString(json.encodeToString(game.state))
                val decision = game.state.pendingDecision as ChooseOptionDecision
                game.submitDecision(OptionChosenResponse(decision.id,
                    decision.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) })).error shouldBe null
            }
            game.state.pendingDecision shouldBe null
            val copies = game.findPermanents("Metallic Mimic").filter { it != original }
            copies.size shouldBe 2
            copies.map { game.state.getEntity(it)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) }
                .toSet() shouldBe setOf(1)
        }
        test("substituted creature copies already attack while their entry choice is pending") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Entry Bundle Substitute")
                .withCardOnBattlefield(1, "Metallic Mimic")
                .withCardAttachedTo(1, "Mirrormind Crown", "Metallic Mimic")
                .withActivePlayer(1)
                .inPhase(com.wingedsheep.sdk.core.Phase.COMBAT, com.wingedsheep.sdk.core.Step.DECLARE_ATTACKERS)
                .build()
            val result = services.effectExecutorRegistry.execute(game.state,
                CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
                    creatureTypes = setOf("Food"), artifactToken = true, tapped = true, attacking = true),
                EffectContext(null, game.player1Id))
            result.error shouldBe null
            game.state = result.state
            val order = game.state.pendingDecision as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(order.id,
                order.options.indexOfFirst { it.startsWith("Entry Bundle Substitute") })).error shouldBe null
            (game.state.pendingDecision as YesNoDecision).prompt.contains("Metallic Mimic") shouldBe true
            game.answerYesNo(true).error shouldBe null
            val entering = game.findPermanents("Metallic Mimic").first {
                game.state.getEntity(it)?.has<com.wingedsheep.engine.state.components.identity.TokenComponent>() == true
            }
            game.state.getEntity(entering)?.has<com.wingedsheep.engine.state.components.combat.AttackingComponent>() shouldBe true
            game.state.getEntity(entering)?.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe true
            repeat(3) {
                val choice = game.state.pendingDecision as ChooseOptionDecision
                game.submitDecision(OptionChosenResponse(choice.id,
                    choice.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) })).error shouldBe null
            }
            game.state.pendingDecision shouldBe null
            game.findPermanents("Metallic Mimic").size shouldBe 4
        }
    }
}
