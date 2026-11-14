package com.wingedsheep.engine.view

import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.PlayerProtectionComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.GrantProtectionToController
import com.wingedsheep.sdk.scripting.ProtectionScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Public visibility follows native effective protection; no card-specific timing inference. */
class PlayerProtectionVisibilityTest : FunSpec({
    val grant = card("Test Public Protection Grant") {
        manaCost = "{W}"
        typeLine = "Creature — Human"
        power = 1; toughness = 1
        staticAbility { ability = GrantProtectionToController(ProtectionScope.Everything) }
    }
    val conditional = card("Test Conditional Protection Grant") {
        manaCost = "{W}"
        typeLine = "Creature — Human"
        power = 1; toughness = 1
        staticAbility { ability = ConditionalStaticAbility(
            ability = GrantProtectionToController(ProtectionScope.Everything),
            condition = Conditions.YouControlAtLeast(2, Filters.Creature)) }
    }
    fun game(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + grant + conditional)
        it.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun badge(g: GameTestDriver, target: EntityId, viewer: EntityId, spectator: Boolean = false): Boolean =
        ClientStateTransformer(g.cardRegistry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))
            .transform(g.state, viewer, isSpectator = spectator).players.single { it.playerId == target }
            .activeEffects.any { it.effectId == "player_protection_everything" }

    test("one-shot protection is visible to its player, opponents and spectators and disappears with the grant") {
        val g = game(); val p = g.activePlayer!!; val other = g.getOpponent(p)
        badge(g,p,p) shouldBe false
        g.replaceState(g.state.updateEntity(p) { it.with(PlayerProtectionComponent(listOf(ProtectionScope.Everything))) })
        badge(g,p,p) shouldBe true
        badge(g,p,other) shouldBe true
        badge(g,p,other,true) shouldBe true
        badge(g,other,p) shouldBe false
        g.replaceState(g.state.updateEntity(p) { it.without<PlayerProtectionComponent>() })
        badge(g,p,p) shouldBe false
    }
    test("unpreventable combat damage is visible alongside protection") {
        val g = game(); val p = g.activePlayer!!
        g.replaceState(g.state.updateEntity(p) { it.with(PlayerProtectionComponent(listOf(ProtectionScope.Everything))) }
            .copy(damageCantBePreventedThisTurn = true))
        val view = ClientStateTransformer(g.cardRegistry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))
            .transform(g.state,p).players.single { it.playerId == p }
        view.activeEffects.any { it.effectId == "player_protection_everything" } shouldBe true
        view.activeEffects.any { it.effectId == "player_combat_damage_unpreventable" } shouldBe true
    }
    test("color-specific protection does not claim protection from everything") {
        val g = game(); val p = g.activePlayer!!
        g.replaceState(g.state.updateEntity(p) { it.with(PlayerProtectionComponent(listOf(ProtectionScope.Color(Color.BLUE)))) })
        badge(g,p,p) shouldBe false
    }
    test("conditional continuous grants track their current gate") {
        val g = game(); val p = g.activePlayer!!
        val source = g.putCreatureOnBattlefield(p,conditional.name)
        badge(g,p,p) shouldBe false
        val body = g.putCreatureOnBattlefield(p,"Grizzly Bears")
        badge(g,p,p) shouldBe true
        g.moveToGraveyard(body)
        badge(g,p,p) shouldBe false
        g.moveToGraveyard(source)
        badge(g,p,p) shouldBe false
    }
    test("a continuous grant follows its projected controller and leaves with its source") {
        val g = game(); val p = g.activePlayer!!; val other = g.getOpponent(p)
        val source = g.putCreatureOnBattlefield(p,grant.name)
        badge(g,p,p) shouldBe true
        g.replaceState(g.state.updateEntity(source) { it.with(ControllerComponent(other)) })
        badge(g,p,p) shouldBe false
        badge(g,other,p) shouldBe true
        g.moveToGraveyard(source)
        badge(g,other,p) shouldBe false
    }
})
