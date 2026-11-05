package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.TurnTracker
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GenericTokenCopiedEntryTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private fun tokens(game: TestGame) = game.state.getBattlefield().filter {
        game.state.getEntity(it)?.has<TokenComponent>() == true
    }
    private fun createdBy(game: TestGame, player: com.wingedsheep.sdk.model.EntityId) =
        services.dynamicAmountEvaluator.evaluate(game.state, DynamicAmount.TurnTracking(Player.You, TurnTracker.TOKENS_CREATED),
            EffectContext(null, player))
    private fun wolves() = CreateTokenEffect(power = 1, toughness = 1, colors = emptySet(),
        creatureTypes = setOf("Wolf"), count = DynamicAmount.Fixed(2))
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
    }
    private fun answerType(game: TestGame): ExecutionResult {
        game.state = json.decodeFromString(json.encodeToString(game.state))
        val decision = game.state.pendingDecision as ChooseOptionDecision
        val goblin = decision.options.indexOfFirst { it.equals("Goblin", ignoreCase = true) }
        (goblin >= 0) shouldBe true
        return game.submitDecision(OptionChosenResponse(decision.id, goblin)).also { it.error shouldBe null }
    }

    init {
        cardRegistry.register(card("Generic Choice Copy") {
            typeLine = "Creature — Shapeshifter"
            power = 2; toughness = 2
            replacementEffect(EntersWithCounters(CounterType.CHARGE, 2, selfOnly = true))
            replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE, allowedCreatureTypes = listOf("Goblin", "Elf")))
        })
        cardRegistry.register(card("Generic Walker Copy") {
            typeLine = "Planeswalker — Test"
            startingLoyalty = 4
        })
        cardRegistry.register(card("Generic Token Redirector") {
            typeLine = "Enchantment"
            replacementEffect(RedirectTokenCreationController())
        })

        cardRegistry.register(card("Generic Dragon Substitute") {
            typeLine = "Enchantment"
            replacementEffect(ReplaceTokenCreationWithToken(listOf(
                CreateTokenEffect(power = 5, toughness = 5, colors = emptySet(), creatureTypes = setOf("Dragon")))))
        })
        cardRegistry.register(card("Generic Token Pair") {
            typeLine = "Enchantment"
            replacementEffect(ReplaceTokenCreationWithTokens(listOf("Frog", "Squirrel")))
        })
        cardRegistry.register(card("Generic Tapped Copy") {
            typeLine = "Creature — Zombie"
            power = 2; toughness = 2
            replacementEffect(EntersTapped())
        })

        test("generic copied entries preserve NextUse counters riders and pipeline IDs across save-load") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Generic Choice Copy").build()
            reflect(game, "Generic Choice Copy")
            val initial = execute(game, CompositeEffect(listOf(
                wolves().copy(keywords = setOf(Keyword.PROWESS), initialCounters = mapOf(CounterType.CHARGE to 1),
                    sacrificeAtStep = Step.END),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 0)),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 1)))))
            initial.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD } shouldBe emptyList()
            val entries = mutableListOf<ZoneChangeEvent>()
            repeat(2) { index ->
                tokens(game).size shouldBe index + 1
                game.state.grantedReplacementEffects.size shouldBe 1
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Generic Choice Copy"
                    game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 3
                }
                entries += answerType(game).events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
            }
            game.state.pendingDecision shouldBe null
            game.state.grantedReplacementEffects shouldBe emptyList()
            tokens(game).size shouldBe 2
            entries.map { it.entityId }.toSet() shouldBe tokens(game).toSet()
            entries.size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) shouldBe 4
            }
            // The original Wolf's printed prowess was replaced by the copied creature's abilities.
            game.state.grantedTriggeredAbilities.filter { it.entityId in tokens(game) } shouldBe emptyList()
            game.state.delayedTriggers.size shouldBe 2
        }

        test("generic copied planeswalkers receive intrinsic loyalty before entry events") {
            val game = scenario().withPlayers().withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .withCardOnBattlefield(1, "Generic Walker Copy").build()
            reflect(game, "Generic Walker Copy")
            val result = execute(game, wolves().copy(attacking = true, tapped = true,
                attackDefender = EnteringAttackDefender.CONTROLLER_CHOOSES))
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Generic Walker Copy"
                game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.LOYALTY) shouldBe 4
                game.state.getEntity(id)?.has<AttackingComponent>() shouldBe false
            }
            result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
                .map { it.entityName }.toSet() shouldBe setOf("Generic Walker Copy")
            game.state.grantedReplacementEffects shouldBe emptyList()
        }

        test("each-player generic creation resumes remaining recipients and publishes all four tokens") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Generic Choice Copy").build()
            reflect(game, "Generic Choice Copy")
            execute(game, CompositeEffect(listOf(
                wolves().copy(controller = EffectTarget.PlayerRef(Player.Each)),
                AddCountersEffect(CounterType.CHARGE, 1, EffectTarget.PipelineTarget(CREATED_TOKENS, 3)))))
            repeat(4) { index ->
                tokens(game).size shouldBe index + 1
                game.state.grantedReplacementEffects.size shouldBe 1
                answerType(game)
            }
            game.state.pendingDecision shouldBe null
            game.state.grantedReplacementEffects shouldBe emptyList()
            createdBy(game, game.player1Id) shouldBe 2
            createdBy(game, game.player2Id) shouldBe 2
            tokens(game).groupingBy { game.state.getEntity(it)?.get<ControllerComponent>()?.playerId }.eachCount() shouldBe
                mapOf(game.player1Id to 2, game.player2Id to 2)
            tokens(game).count { game.state.getEntity(it)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) == 3 } shouldBe 1
        }

        test("redirected copied entries keep delayed riders and never ask a nonattacker for defenders") {
            val game = scenario().withPlayers().withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .withCardOnBattlefield(1, "Generic Choice Copy")
                .withCardOnBattlefield(2, "Generic Token Redirector").build()
            reflect(game, "Generic Choice Copy")
            execute(game, wolves().copy(tapped = true, attacking = true,
                attackDefender = EnteringAttackDefender.CONTROLLER_CHOOSES, sacrificeAtStep = Step.END))
            repeat(2) { answerType(game) }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
                game.state.getEntity(id)?.has<AttackingComponent>() shouldBe false
            }
            game.state.delayedTriggers.size shouldBe 2
            game.state.delayedTriggers.map { it.controllerId }.toSet() shouldBe setOf(game.player1Id)
            createdBy(game, game.player1Id) shouldBe 2
            createdBy(game, game.player2Id) shouldBe 0
        }
        test("redirect substitution and copied-entry pause completes the original recipient exactly once") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Generic Choice Copy")
                .withCardOnBattlefield(2, "Generic Token Redirector")
                .withCardOnBattlefield(2, "Generic Dragon Substitute").build()
            reflect(game, "Generic Choice Copy")
            execute(game, wolves())
            repeat(2) { index ->
                tokens(game).size shouldBe index + 1
                answerType(game)
            }
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Generic Choice Copy"
            }
        }

        test("generic and predefined tokens honor copied printed enters-tapped replacements") {
            for (effect in listOf<Effect>(wolves(), CreatePredefinedTokenEffect("Frog", count = 2))) {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Generic Tapped Copy").build()
                reflect(game, "Generic Tapped Copy")
                val result = execute(game, effect)
                result.events.filterIsInstance<TappedEvent>() shouldBe emptyList()
                game.state.pendingDecision shouldBe null
                tokens(game).size shouldBe 2
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name shouldBe "Generic Tapped Copy"
                    game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
                }
            }
        }
        test("ordinary generic creature tokens still enter attacking their named defender") {
            val game = scenario().withPlayers().withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            execute(game, wolves().copy(tapped = true, attacking = true,
                attackedPlayer = EffectTarget.SpecificEntity(game.player2Id)))
            game.state.pendingDecision shouldBe null
            tokens(game).size shouldBe 2
            tokens(game).forEach { id ->
                game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
                game.state.getEntity(id)?.get<AttackingComponent>()?.defenderId shouldBe game.player2Id
            }
        }
        test("explicit native recipients create their tokens even when another player's effect instructs them") {
            for (kind in listOf("generic", "predefined", "copy")) {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Generic Tapped Copy").build()
                val recipient = EffectTarget.SpecificEntity(game.player2Id)
                val effect: Effect = when (kind) {
                    "generic" -> wolves().copy(controller = recipient)
                    "predefined" -> CreatePredefinedTokenEffect("Frog", count = 2, controller = recipient)
                    else -> CreateTokenCopyOfTargetEffect(
                        target = EffectTarget.SpecificEntity(game.findPermanent("Generic Tapped Copy")!!),
                        count = DynamicAmount.Fixed(2), controller = recipient)
                }
                execute(game, effect)
                tokens(game).size shouldBe 2
                createdBy(game, game.player1Id) shouldBe 0
                createdBy(game, game.player2Id) shouldBe 2
            }
        }

        test("bundle creation credits the instructed recipient and preserves that creator through redirection") {
            for (redirected in listOf(false, true)) {
                var setup = scenario().withPlayers().withCardOnBattlefield(2, "Generic Token Pair")
                if (redirected) setup = setup.withCardOnBattlefield(2, "Generic Token Redirector")
                val game = setup.build()
                val recipient = if (redirected) game.player1Id else game.player2Id
                execute(game, wolves().copy(controller = EffectTarget.SpecificEntity(recipient)))
                tokens(game).size shouldBe 4
                tokens(game).forEach { id ->
                    game.state.getEntity(id)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                }
                createdBy(game, recipient) shouldBe 4
                createdBy(game, if (recipient == game.player1Id) game.player2Id else game.player1Id) shouldBe 0
            }
        }
    }
}
