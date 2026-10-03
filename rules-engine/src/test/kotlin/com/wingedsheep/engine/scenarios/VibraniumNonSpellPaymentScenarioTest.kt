package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ManaSourcesSelectedResponse
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AttackTax
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class VibraniumNonSpellPaymentScenarioTest : FunSpec({
    val maker = card("Test Create Vibranium For Payment") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateVibranium() }
    }
    val wardedBear = card("Test Vibranium Warded Bear") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
        keywordAbility(KeywordAbility.ward("{1}"))
    }
    val smash = card("Test Vibranium Smash") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell {
            val target = target("target creature", Targets.Creature)
            effect = Effects.Destroy(target)
        }
    }
    val prison = card("Test Vibranium Attack Tax") {
        manaCost = "{1}"
        typeLine = "Enchantment"
        staticAbility { ability = AttackTax(DynamicAmount.Fixed(1)) }
    }

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(PredefinedTokens.Vibranium, maker, wardedBear, smash, prison))
        it.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 20)
    }

    fun createVibranium(driver: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId): com.wingedsheep.sdk.model.EntityId {
        val spell = driver.putCardInHand(player, maker.name)
        driver.castSpell(player, spell).isSuccess shouldBe true
        driver.bothPass()
        return driver.findPermanent(player, "Vibranium") ?: error("Vibranium token was not created")
    }

    fun tapVibranium(driver: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId, token: com.wingedsheep.sdk.model.EntityId) {
        val result = driver.submit(ActivateAbility(player, token, PredefinedTokens.Vibranium.activatedAbilities.single().id))
        result.error shouldBe null
    }

    test("Vibranium mana floated during ward payment pays the tax and is consumed") {
        val game = driver()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val token = createVibranium(game, player)
        val bear = game.putCreatureOnBattlefield(opponent, wardedBear.name)
        val spell = game.putCardInHand(player, smash.name)

        game.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(bear))).isSuccess shouldBe true
        game.bothPass()
        game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()

        tapVibranium(game, player, token)
        val decision = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 1
        game.submitDecision(player, ManaSourcesSelectedResponse(decision.id, emptyList(), autoPay = false)).isSuccess shouldBe true
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 0
        repeat(3) { if (game.state.priorityPlayerId != null) game.bothPass() }
        game.findPermanent(opponent, wardedBear.name) shouldBe null
    }

    test("paying ward preserves the unused restricted unit from a mixed pool") {
        val game = driver()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val token1 = game.putPermanentOnBattlefield(player, "Vibranium")
        val token2 = game.putPermanentOnBattlefield(player, "Vibranium")
        val bear = game.putCreatureOnBattlefield(opponent, wardedBear.name)
        val spell = game.putCardInHand(player, smash.name)

        game.castSpellWithTargets(player, spell, listOf(ChosenTarget.Permanent(bear))).isSuccess shouldBe true
        game.bothPass()
        game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        tapVibranium(game, player, token1)
        tapVibranium(game, player, token2)
        game.giveMana(player, Color.GREEN, 1)
        val decision = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 2

        game.submitDecision(player, ManaSourcesSelectedResponse(decision.id, emptyList(), autoPay = false)).isSuccess shouldBe true
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 1
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.green shouldBe 1
        repeat(3) { if (game.state.priorityPlayerId != null) game.bothPass() }
        game.findPermanent(opponent, wardedBear.name) shouldBe null
    }

    test("Vibranium mana floated during attack tax permits the attack") {
        val game = driver()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        val token = game.putPermanentOnBattlefield(player, "Vibranium")
        val attacker = game.putCreatureOnBattlefield(player, "Grizzly Bears")
        game.putPermanentOnBattlefield(opponent, prison.name)
        game.passPriorityUntil(Step.DECLARE_ATTACKERS)

        game.declareAttackers(player, listOf(attacker), opponent).isPaused shouldBe true
        game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        tapVibranium(game, player, token)
        val decision = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submitDecision(player, ManaSourcesSelectedResponse(decision.id, emptyList(), autoPay = false)).isSuccess shouldBe true
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.restrictedMana?.size shouldBe 0
        game.state.getEntity(attacker) shouldNotBe null
    }
})
