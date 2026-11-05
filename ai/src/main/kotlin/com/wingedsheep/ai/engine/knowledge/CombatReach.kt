package com.wingedsheep.ai.engine.knowledge

import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.effects.AnimateLandEffect
import com.wingedsheep.sdk.scripting.effects.BecomeCreatureEffect
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.FightEffect
import com.wingedsheep.sdk.scripting.effects.ForEachEffect
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import com.wingedsheep.sdk.scripting.effects.MassAnimateEffect
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.PreventDamageEffect
import com.wingedsheep.sdk.scripting.effects.RegenerateEffect
import com.wingedsheep.sdk.scripting.effects.RemoveFromCombatEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What an activated ability can do to a combat already under way, read off its effect tree — never
 * off its card's name.
 *
 * The question [com.wingedsheep.ai.engine.CombatResponses] asks of every ability an opponent's
 * board offers while the AI is scoring a fight: is this one worth simulating as their answer? The
 * reading is deliberately coarse. It only has to keep the simulation budget off abilities that
 * cannot touch a fight (draw a card, scry, gain life, mill); the simulation itself decides whether a
 * relevant one is actually worth using, so a false positive costs one engine step and a false
 * negative is a trick the AI does not see coming.
 */
internal enum class CombatReach {
    /**
     * Changes how a fight resolves once blocks are in: power and toughness either way, a granted
     * keyword (first strike, deathtouch, indestructible, trample, lifelink…), damage to a creature,
     * removal, regeneration or damage prevention, taking a creature out of combat.
     */
    FIGHT,

    /**
     * Puts a new body on the battlefield — a token, an animated land or permanent. Only worth
     * anything to a defending player before blockers are declared.
     */
    BLOCKER;

    companion object {
        /** [IntentTag]s whose effect reshapes a fight: the analyzer's removal and protection reads. */
        private val FIGHT_TAGS = setOf(
            IntentTag.REMOVAL, IntentTag.SWEEPER, IntentTag.FIGHT, IntentTag.PUMP, IntentTag.PROTECTION,
        )

        /** Abilities come from card definitions and grants, so the reading is cached by identity. */
        private val cache: MutableMap<ActivatedAbility, Set<CombatReach>> =
            Collections.synchronizedMap(IdentityHashMap())

        /** What [ability] can do to a fight; empty when nothing. */
        fun of(ability: ActivatedAbility): Set<CombatReach> = cache.getOrPut(ability) { of(ability.effect) }

        /** What [effect] can do to a fight; empty when nothing. */
        fun of(effect: Effect): Set<CombatReach> {
            val reach = mutableSetOf<CombatReach>()
            EffectWalker.leaves(effect).forEach { visit(it, reach) }
            // The analyzer already reads the removal shapes the switch below does not name —
            // destroy, bounce, exile, sacrifice, a -N/-N — and token makers.
            val tags = CardIntentAnalyzer.analyzeEffect(effect).tags
            if (tags.any { it in FIGHT_TAGS }) reach += FIGHT
            if (IntentTag.TOKEN_MAKER in tags) reach += BLOCKER
            return reach
        }

        private fun visit(leaf: Effect, reach: MutableSet<CombatReach>) {
            when (leaf) {
                // [EffectWalker] keeps a ForEach whole; a team pump is "for each creature you
                // control, +1/+1", so its body is what this reading needs.
                is ForEachEffect -> EffectWalker.leaves(leaf.body).forEach { visit(it, reach) }
                is ModifyStatsEffect, is GrantKeywordEffect, is DividedDamageEffect, is FightEffect,
                is RegenerateEffect, is PreventDamageEffect, is RemoveFromCombatEffect -> reach += FIGHT
                // Damage aimed only at a player is a clock, not a combat answer.
                is DealDamageEffect -> if (leaf.target !is EffectTarget.PlayerRef) reach += FIGHT
                is AnimateLandEffect, is BecomeCreatureEffect, is MassAnimateEffect -> reach += BLOCKER
                else -> Unit
            }
        }
    }
}
