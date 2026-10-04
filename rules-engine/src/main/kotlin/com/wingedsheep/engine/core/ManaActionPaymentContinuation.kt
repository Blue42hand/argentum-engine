package com.wingedsheep.engine.core

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import kotlinx.serialization.Serializable

/** Mana production before any cost of the announced action is paid. Prices stay locked across it. */
@Serializable
data class ManaActionPaymentContinuation(
    val action: GameAction,
    val cost: ManaCost,
    val lockedCastCost: ManaCost? = null,
    val lockedAbilityCost: AbilityCost? = null,
    val lockedAbilityX: Int? = null,
    val excludedSources: Set<com.wingedsheep.sdk.model.EntityId> = emptySet(),
    val paymentContext: com.wingedsheep.engine.mechanics.mana.SpellPaymentContext? = null,
    val castPayment: LockedCastPayment? = null,
) : AnswerContinuation

/** Announced cast costs and offered sources survive mana abilities inside the payment window. */
@Serializable
data class LockedCastPayment(
    val paymentXValue: Int,
    val additionalCosts: List<AdditionalCost>,
    val forageCostRequired: Boolean,
    val additionalLifeCost: Int,
    val dedicatedAlternativeCostType: AlternativeCostType?,
    val emerge: KeywordAbility.Emerge? = null,
    val availableSources: List<ManaSourceOption>,
)
