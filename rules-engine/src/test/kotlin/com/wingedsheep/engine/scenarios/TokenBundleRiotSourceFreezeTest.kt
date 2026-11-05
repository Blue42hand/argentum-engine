package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
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

class TokenBundleRiotSourceFreezeTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private fun tokens(game: TestGame) = game.state.getBattlefield().filter {
        game.state.getEntity(it)?.has<TokenComponent>() == true
    }
    private fun saveLoad(game: TestGame) { game.state = json.decodeFromString(json.encodeToString(game.state)) }
    private fun chooseRiot(game: TestGame, counter: Boolean) {
        saveLoad(game)
        val decision = game.state.pendingDecision as ChooseOptionDecision
        val word = if (counter) "counter" else "haste"
        val index = decision.options.indexOfFirst { it.contains(word, ignoreCase = true) }
        (index >= 0) shouldBe true
        game.submitDecision(OptionChosenResponse(decision.id, index)).error shouldBe null
    }
    private fun copy(game: TestGame, name: String, exceptions: CopyExceptions) {
        val result = services.effectExecutorRegistry.execute(game.state,
            CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(game.findPermanent(name)!!),
                count = DynamicAmount.Fixed(2), exceptions = exceptions),
            EffectContext(null, game.player1Id))
        result.error shouldBe null
        game.state = result.state
    }
    init {
        // Enables the joint bundle pipeline without replacing these creature tokens.
        cardRegistry.register(card("Riot Bundle Gate") {
            typeLine = "Artifact"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Clue", "Food", "Treasure"),
                EventPattern.TokenCreationEvent(tokenFilter = GameObjectFilter.Any.withSubtype("Food"))))
        })

        test("copied Spider-Punks each retain printed plus original granted riot across save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Riot Bundle Gate")
                .withCardOnBattlefield(1, "Spider-Punk").build()
            copy(game, "Spider-Punk", CopyExceptions(removedSupertypes = setOf(Supertype.LEGENDARY)))
            repeat(2) { index ->
                tokens(game).size shouldBe index + 1
                chooseRiot(game, counter = true)
                tokens(game).size shouldBe index + 1
                chooseRiot(game, counter = false)
            }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                game.state.projectedState.hasKeyword(id, Keyword.HASTE) shouldBe true
            }
        }

        test("riot counter replacements exclude newly entering sibling multipliers after save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Riot Bundle Gate")
                .withCardOnBattlefield(1, "Spider-Punk")
                .withCardOnBattlefield(1, "Corpsejack Menace").build()
            copy(game, "Corpsejack Menace", CopyExceptions(addedSubtypes = setOf(Subtype("Spider"))))
            repeat(2) { chooseRiot(game, counter = true) }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            }
        }
    }
}
