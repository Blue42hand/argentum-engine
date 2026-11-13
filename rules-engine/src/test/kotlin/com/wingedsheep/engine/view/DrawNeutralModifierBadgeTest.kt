package com.wingedsheep.engine.view

import com.wingedsheep.engine.event.DelayedTriggeredAbility
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.effects.DelayedTriggerExpiry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/**
 * A badge says when its effect ends. Without that, a creature pumped until end of turn looks the
 * same as one changed for good, and a trigger watching for the rest of the game reads as if it
 * lapsed tonight.
 */
class DrawNeutralModifierBadgeTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        return driver
    }

    fun transformer(d: GameTestDriver): ClientStateTransformer =
        ClientStateTransformer(cardRegistry = d.cardRegistry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))

    fun floating(
        target: EntityId,
        controller: EntityId,
        modification: SerializableModification,
        duration: Duration,
        id: String = "test-effect",
        sourceId: EntityId? = null,
        sourceName: String = "Giant Growth",
        condition: Condition? = null,
    ) = ActiveFloatingEffect(
        id = EntityId(id),
        effect = FloatingEffectData(
            Layer.ABILITY, Sublayer.MODIFICATIONS, modification, setOf(target), sourceCondition = condition,
        ),
        duration = duration,
        sourceId = sourceId,
        sourceName = sourceName,
        controllerId = controller,
        timestamp = 1,
    )

    fun GameTestDriver.badges(state: GameState, card: EntityId): List<ClientCardEffect> =
        transformer(this).transform(state, player2).cards.getValue(card).activeEffects

    test("a whole unconditional targeting-only group certifies both viewers") {
        val d=createDriver();val bears=d.putCreatureOnBattlefield(d.player1,"Grizzly Bears")
        val effect=floating(bears,d.player1,SerializableModification.CantHaveKeywords(setOf("HEXPROOF","SHROUD")),Duration.EndOfTurn)
        val state=d.state.copy(floatingEffects=listOf(effect))
        for(viewer in listOf(d.player1,d.player2)) {
            val badge=transformer(d).transform(state,viewer).cards.getValue(bears).activeEffects.single()
            badge.drawNeutral shouldBe true
            badge.description shouldBe "can't have hexproof or shroud (until end of turn)"
        }
    }

    test("a mixed group cannot inherit the certificate from its display summary") {
        val d=createDriver();val bears=d.putCreatureOnBattlefield(d.player1,"Grizzly Bears")
        val safe=floating(bears,d.player1,SerializableModification.CantHaveKeywords(setOf("HEXPROOF","SHROUD")),Duration.EndOfTurn)
        val unknown=floating(bears,d.player1,SerializableModification.AddColor(setOf("RED")),Duration.EndOfTurn,id="other").let {
            it.copy(effect=it.effect.copy(layer=Layer.COLOR))
        }
        val pure=d.badges(d.state.copy(floatingEffects=listOf(safe)),bears).single()
        val mixed=d.badges(d.state.copy(floatingEffects=listOf(safe,unknown)),bears).single()
        mixed.description shouldBe pure.description
        mixed.drawNeutral shouldBe null
    }

    test("unknown keyword families and conditions remain uncertified") {
        val d=createDriver();val bears=d.putCreatureOnBattlefield(d.player1,"Grizzly Bears")
        val unknown=floating(bears,d.player1,SerializableModification.CantHaveKeywords(setOf("DREDGE")),Duration.EndOfTurn)
        d.badges(d.state.copy(floatingEffects=listOf(unknown)),bears).single().drawNeutral shouldBe null
        val conditional=floating(bears,d.player1,SerializableModification.CantHaveKeywords(setOf("HEXPROOF")),Duration.EndOfTurn,
            condition=Conditions.IsYourTurn)
        d.badges(d.state.copy(floatingEffects=listOf(conditional)),bears).single().drawNeutral shouldBe null
    }

    test("a hidden source never grants a new positive certificate") {
        val d=createDriver();val bears=d.putCreatureOnBattlefield(d.player1,"Grizzly Bears")
        val source=d.putCreatureOnBattlefield(d.player1,"Grizzly Bears")
        val hidden=d.state.updateEntity(source) { it.with(FaceDownComponent) }
        val effect=floating(bears,d.player1,SerializableModification.CantHaveKeywords(setOf("HEXPROOF")),Duration.EndOfTurn,
            sourceId=source,sourceName="Secret Morph")
        val badge=d.badges(hidden.copy(floatingEffects=listOf(effect)),bears).single()
        badge.drawNeutral shouldBe null
        badge.name shouldNotBe "Secret Morph"
    }
})
