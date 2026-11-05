package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.combat.MustAttackGroupExecutor
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CanAttackAsThoughHasty
import com.wingedsheep.sdk.scripting.CompositeStaticAbility
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.MustAttack
import com.wingedsheep.sdk.scripting.MustAttackDefender
import com.wingedsheep.sdk.scripting.effects.MustAttackGroupEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Projection and declaration must agree after granted abilities and removals resolve in either order. */
class GrantedCombatRequirementTimestampTest : FunSpec({
    val grant = card("Timestamp Combat Requirement") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.GrantStaticAbility(
                CompositeStaticAbility(listOf(
                    MustAttack(defender = MustAttackDefender.APlayer),
                    MustAttack(defender = MustAttackDefender.PlayerOtherThanYou),
                    CanAttackAsThoughHasty(),
                )), creature,
            )
        }
    }
    val mute = card("Timestamp Remove Abilities") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.RemoveAllAbilities(creature)
        }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(grant, mute))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.resolve(name: String, target: EntityId) {
        val caster = activePlayer!!
        val spell = putCardInHand(caster, name)
        castSpell(caster, spell, listOf(target)).error shouldBe null
        bothPass()
    }
    fun GameTestDriver.otherThan(creature: EntityId): List<EntityId> =
        GoadRules(state, cardRegistry, PredicateEvaluator(cardRegistry = cardRegistry))
            .playersToAttackOtherThan(creature)

    test("a later loss removes a gained requirement from projection and declaration") {
        val d = driver()
        val me = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.removeSummoningSickness(creature)
        d.resolve(grant.name, creature)
        d.state.projectedState.mustAttackPlayer(creature) shouldBe true
        d.otherThan(creature) shouldBe listOf(me)

        d.resolve(mute.name, creature)
        d.state.projectedState.mustAttack(creature) shouldBe false
        d.state.projectedState.mustAttackPlayer(creature) shouldBe false
        d.state.projectedState.canAttackAsThoughHasty(creature) shouldBe false
        d.otherThan(creature) shouldBe emptyList()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(me, emptyMap()).error shouldBe null
    }

    test("an ability granted after loss functions in projection and declaration") {
        val d = driver()
        val me = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        // Leave summoning sickness in place: this also tests the post-layer attack permission.
        d.resolve(mute.name, creature)
        d.resolve(grant.name, creature)
        d.state.projectedState.mustAttackPlayer(creature) shouldBe true
        d.state.projectedState.canAttackAsThoughHasty(creature) shouldBe true
        d.otherThan(creature) shouldBe listOf(me)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(me, emptyMap()).error shouldNotBe null
        d.declareAttackers(me, mapOf(creature to d.state.getOpponents(me).first())).error shouldBe null
    }

    test("a face-down permanent retains an externally gained requirement") {
        val d = driver()
        val me = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.replaceState(d.state.updateEntity(creature) { it.with(FaceDownComponent) })
        d.resolve(grant.name, creature)
        d.state.projectedState.mustAttackPlayer(creature) shouldBe true
        d.state.projectedState.canAttackAsThoughHasty(creature) shouldBe true
        d.otherThan(creature) shouldBe listOf(me)
    }

    test("a resolved rule requiring attacks survives losing creature abilities") {
        val d = driver()
        val me = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.removeSummoningSickness(creature)
        val result = MustAttackGroupExecutor().execute(
            d.state,
            MustAttackGroupEffect(GroupFilter.AllCreatures, MustAttackDefender.Any, Duration.EndOfTurn),
            EffectContext(sourceId = null, controllerId = me),
        )
        result.error shouldBe null
        d.replaceState(result.state)
        d.resolve(mute.name, creature)
        d.state.projectedState.mustAttack(creature) shouldBe true
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(me, emptyMap()).error shouldNotBe null
    }
})
