package com.wingedsheep.engine.handlers.actions.spell

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.toEntityId
import com.wingedsheep.engine.legalactions.AdditionalCostData
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/** A read-only price for the choices announced so far, not permission to cast or pay them.
 * All X symbols are materialized. The mana bill already credits declared alternative payments. */
data class CastManaCostQuote(
    val manaCostString: String,
    val delveGenericCap: Int,
    val tapForGenericMax: Int,
    val tapForGenericCostString: String,
)

/** Admit partial pipeline choices only from a current, native offer. Incomplete selections are
 * allowed here; final casting still validates all rules. Unknown variants, IDs and unbounded
 * numbers must never reach a cost evaluator through a read-only request. */
internal object CastManaCostQuoteAdmission {
    fun accepts(action: CastSpell, offer: LegalAction): Boolean {
        val template = offer.action as? CastSpell ?: return false
        if (action.copy(
                xValue = template.xValue, declaredCostTimes = template.declaredCostTimes,
                targets = template.targets, targetGroupCounts = template.targetGroupCounts,
                additionalCostPayment = template.additionalCostPayment,
                alternativePayment = template.alternativePayment,
                chosenModes = template.chosenModes, modeTargetsOrdered = template.modeTargetsOrdered,
                damageDistribution = template.damageDistribution,
                modeDamageDistribution = template.modeDamageDistribution,
                additionalManaForCounters = template.additionalManaForCounters,
                conspiredCreatures = template.conspiredCreatures, casualtyCreature = template.casualtyCreature,
            ) != template) return false
        if (action.declaredCostTimes != template.declaredCostTimes &&
            action.declaredCostTimes !in 1..(offer.maxOptionalCostTimes ?: return false)) return false
        if (action.declaredCostTimes < 1) return false
        if (action.xValue != template.xValue &&
            (!offer.hasXCost || action.xValue == null || action.xValue !in offer.minX..(offer.maxAffordableX ?: return false))) return false
        if (action.additionalManaForCounters !in 0..(offer.maxAdditionalManaForCounters ?: 0)) return false
        val modal = offer.modalEnumeration
        if (action.chosenModes != template.chosenModes) {
            if (modal == null || action.chosenModes.size > modal.chooseCount ||
                action.chosenModes.any { selected -> modal.modes.none { it.index == selected && it.available } } ||
                (!modal.allowRepeat && action.chosenModes.distinct().size != action.chosenModes.size)) return false
        }
        val requirements = offer.targetRequirements.orEmpty() + action.chosenModes.flatMap { selected ->
            modal?.modes?.firstOrNull { it.index == selected }?.targetRequirements.orEmpty()
        }
        val validTargets = (offer.validTargets.orEmpty() + requirements.flatMap { it.validTargets }).toSet()
        val targetLimit = if (requirements.isEmpty()) {
            (if (offer.xConstrainsTargetCount) action.xValue ?: 0 else offer.targetCount).toLong()
        } else requirements.sumOf { if (it.xConstrainsCount) (action.xValue ?: 0).toLong() else it.maxTargets.toLong() }
        val modeTargets = action.modeTargetsOrdered.flatten()
        // Match CastValidator's modal normalization: the flat list repeats the per-mode prefix
        // (and may append splice targets). Count that prefix once, preserving repeated targets
        // chosen independently by different modes; never collapse it with distinct().
        if (action.modeTargetsOrdered.size > action.chosenModes.size) return false
        if (modeTargets.isNotEmpty() && action.targets.isNotEmpty() &&
            action.targets.take(modeTargets.size) != modeTargets) return false
        val targets = if (action.modeTargetsOrdered.isEmpty()) action.targets
            else modeTargets + action.targets.drop(modeTargets.size)
        if (targets.size.toLong() > targetLimit ||
            (action.targets + modeTargets).any { it.toEntityId() !in validTargets }) return false
        action.targetGroupCounts?.let { counts ->
            if (counts.size > maxOf(1, requirements.size) || counts.any { it < 0 } ||
                counts.sumOf { it.toLong() } != targets.size.toLong()) return false
        }
        val selectedTargets = targets.map { it.toEntityId() }.toSet()
        val distributions = listOfNotNull(action.damageDistribution) + action.modeDamageDistribution.values
        if (distributions.any { it.keys.any { id -> id !in selectedTargets } || it.values.any { n -> n < 0 } }) return false
        if (action.modeDamageDistribution.keys.any { it !in action.chosenModes }) return false

        val alt = action.alternativePayment
        if (alt != null) {
            if (!subset(alt.delvedCards, offer.delveCards.orEmpty().map { it.entityId }) ||
                (alt.delvedCards.isNotEmpty() && !offer.hasDelve)) return false
            val convoke = offer.convokeCreatures.orEmpty().associateBy { it.entityId }
            if (alt.convokedCreatures.isNotEmpty() && !offer.hasConvoke) return false
            if (alt.convokedCreatures.any { (id, payment) ->
                    val candidate = convoke[id]
                    candidate == null || (payment.color != null && payment.color !in candidate.colors)
                }) return false
            if (!subset(alt.tapForGenericPermanents.toList(), offer.tapForGenericPermanents.orEmpty().map { it.entityId }) ||
                (alt.tapForGenericPermanents.isNotEmpty() && !offer.hasTapForGeneric)) return false
            if (alt.harmonizeCreature != null && (!offer.hasHarmonize ||
                    offer.harmonizeCreatures.orEmpty().none { it.entityId == alt.harmonizeCreature })) return false
            val taps = alt.convokedCreatures.keys.toList() + alt.tapForGenericPermanents + listOfNotNull(alt.harmonizeCreature)
            if (taps.distinct().size != taps.size) return false
        }
        val costs = flatten(listOfNotNull(offer.additionalCostInfo) + action.chosenModes.mapNotNull { selected ->
            modal?.modes?.firstOrNull { it.index == selected }?.additionalCostInfo
        } + listOfNotNull(modal?.additionalCostPerExtraMode))
        if (!subset(action.conspiredCreatures, costs.flatMap { it.validTapTargets }) ||
            (action.casualtyCreature != null && action.casualtyCreature !in costs.flatMap { it.validSacrificeTargets })) return false
        return paymentAllowed(action.additionalCostPayment, template.additionalCostPayment, costs)
    }

    private fun flatten(costs: List<AdditionalCostData>): List<AdditionalCostData> =
        costs.flatMap { listOf(it) + flatten(it.alsoRequired) }

    private fun subset(selected: List<EntityId>, candidates: List<EntityId>): Boolean =
        selected.size == selected.distinct().size && selected.all { it in candidates }

    private fun paymentAllowed(payment: AdditionalCostPayment?, template: AdditionalCostPayment?, costs: List<AdditionalCostData>): Boolean {
        if (payment == template) return true
        val p = payment ?: return template == null || template.isEmpty
        val base = template ?: AdditionalCostPayment()
        // These fields are not edited by the public cast pipeline. Preserve prefilled choices.
        if (p.lifePaid != base.lifePaid || p.lifeGainRecipient != base.lifeGainRecipient ||
            p.millCount != base.millCount || p.cardsPutOnLibrary != base.cardsPutOnLibrary) return false
        if (!subset(p.sacrificedPermanents, costs.flatMap { it.validSacrificeTargets }) ||
            !subset(p.discardedCards, costs.flatMap { it.validDiscardTargets }) ||
            !subset(p.exiledCards, costs.flatMap { it.validExileTargets }) ||
            !subset(p.beheldCards, costs.flatMap { it.validBeholdTargets }) ||
            !subset(p.revealedCards, costs.flatMap { it.validRevealTargets }) ||
            !subset(p.tappedPermanents, costs.flatMap { it.validTapTargets }) ||
            !subset(p.bouncedPermanents, costs.flatMap { it.validBounceTargets }) ||
            !subset(p.blightTargets, costs.flatMap { it.validBlightTargets }) ||
            !subset(p.counterPlacementTargets, costs.flatMap { it.validCounterPlacementTargets }) ||
            !subset(p.variableCostPermanents.orEmpty(), costs.flatMap {
                it.validSacrificeTargets + it.validTapTargets + it.validExileTargets + it.tapForPowerCreatures.map { c -> c.entityId }
            })) return false
        if (p.blightAmount !in 0..(costs.maxOfOrNull { it.blightVariableMaxX } ?: 0) ||
            p.payXLifeAmount !in 0..(costs.maxOfOrNull { it.payXLifeMaxX } ?: 0)) return false
        val counters = costs.flatMap { it.counterRemovalCreatures }.associateBy { it.entityId }
        val removals = p.distributedCounterRemovals
        if (removals.any { it.count <= 0 || it.entityId !in counters } ||
            removals.map { it.entityId to it.counterType }.distinct().size != removals.size) return false
        if (removals.any {
                val offered = counters.getValue(it.entityId)
                val available = offered.availableCountersByType[it.counterType]
                    ?: if (offered.availableCountersByType.isEmpty() && it.counterType == "+1/+1") offered.availableCounters else 0
                it.count > available
            }) return false
        return true
    }
}
