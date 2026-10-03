package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VibraniumTokenScenarioTest : ScenarioTestBase() {
    private val maker = card("Test Make Vibranium") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateVibranium() }
    }
    private val tappedMaker = card("Test Make Tapped Vibranium") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateVibranium(count = 2, tapped = true) }
    }
    private val smash = card("Test Smash Vibranium") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell {
            val artifact = target("target artifact", Targets.Artifact)
            effect = Effects.Destroy(artifact)
        }
    }
    private val artifact = card("Test Vibranium Artifact") {
        manaCost = "{1}"
        typeLine = "Artifact"
    }
    private val nonartifact = card("Test Vibranium Sorcery") {
        manaCost = "{1}"
        typeLine = "Sorcery"
        spell { effect = Effects.DrawCards(1) }
    }
    private val abilitySource = card("Test Vibranium Ability Source") {
        manaCost = "{0}"
        typeLine = "Creature — Human"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Mana("{1}")
            effect = Effects.GainLife(1)
        }
    }

    init {
        cardRegistry.register(listOf(maker, tappedMaker, smash, artifact, nonartifact, abilitySource))

        test("CreateVibranium registers two tapped indestructible artifact tokens") {
            val game = scenario().withPlayers()
                .withCardInHand(1, tappedMaker.name)
                .build()

            game.castSpell(1, tappedMaker.name).error shouldBe null
            game.resolveStack()

            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 2
            for (token in tokens) {
                game.state.getEntity(token)?.has<TokenComponent>() shouldBe true
                game.state.getEntity(token)?.has<TappedComponent>() shouldBe true
                game.state.projectedState.hasType(token, "ARTIFACT") shouldBe true
            }
        }

        test("a destroy spell leaves a created Vibranium token on the battlefield") {
            val game = scenario().withPlayers()
                .withCardInHand(1, maker.name)
                .withCardInHand(1, smash.name)
                .build()

            game.castSpell(1, maker.name).error shouldBe null
            game.resolveStack()
            val token = game.findPermanent("Vibranium")!!

            game.castSpell(1, smash.name, targetId = token).error shouldBe null
            game.resolveStack()
            game.findPermanent("Vibranium") shouldBe token
        }

        test("Vibranium mana pays for an artifact spell but rejects a nonartifact spell") {
            val game = scenario().withPlayers()
                .withCardInHand(1, maker.name)
                .withCardInHand(1, nonartifact.name)
                .withCardInHand(1, artifact.name)
                .build()

            game.castSpell(1, maker.name).error shouldBe null
            game.resolveStack()
            val token = game.findPermanent("Vibranium")!!
            game.execute(ActivateAbility(game.player1Id, token, PredefinedTokens.Vibranium.activatedAbilities.single().id)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 1

            game.castSpell(1, nonartifact.name).error shouldNotBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 1

            game.castSpell(1, artifact.name).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 0
            game.resolveStack()
            game.findPermanent(artifact.name) shouldNotBe null
        }

        test("Vibranium mana pays for a nonartifact permanent's activated ability") {
            val game = scenario().withPlayers()
                .withCardInHand(1, maker.name)
                .withCardOnBattlefield(1, abilitySource.name)
                .build()

            game.castSpell(1, maker.name).error shouldBe null
            game.resolveStack()
            val token = game.findPermanent("Vibranium")!!
            game.execute(ActivateAbility(game.player1Id, token, PredefinedTokens.Vibranium.activatedAbilities.single().id)).error shouldBe null

            val source = game.findPermanent(abilitySource.name)!!
            game.execute(ActivateAbility(game.player1Id, source, abilitySource.activatedAbilities.single().id)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 0
            game.resolveStack()
            game.state.getEntity(game.player1Id)?.get<LifeTotalComponent>()?.life shouldBe 21
        }
    }
}
