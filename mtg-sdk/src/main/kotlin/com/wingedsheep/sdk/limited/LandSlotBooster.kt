package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.model.CardDefinition
import kotlin.random.Random

/**
 * Reserves named nonbasic lands for one dedicated slot, then delegates the other slots to [base].
 * [basicLands] supplies the basic-land arts that the generator removes from its ordinary pool.
 * Each supplied basic printing has weight [basicWeight]; each available nonbasic name has weight
 * [nonbasicWeight]. Host-banned nonbasics are absent from [pool] and cannot enter the land slot.
 * Basic lands remain available independently of the host ban list, as in limited deck building.
 */
data class LandSlotBooster(
    val base: BoosterStrategy,
    val nonbasicLandNames: Set<String>,
    val basicLands: List<CardDefinition>,
    val basicWeight: Int = 1,
    val nonbasicWeight: Int = 1,
) : BoosterStrategy {
    init {
        require(basicLands.all { it.typeLine.isBasicLand }) { "Land-slot basics must be basic lands" }
        require(basicWeight > 0 && nonbasicWeight > 0) { "Land-slot weights must be positive" }
    }

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val nonbasics = pool.filter { it.name in nonbasicLandNames }.distinctBy { it.name }
        val regular = pool.filter { it.name !in nonbasicLandNames && !it.typeLine.isBasicLand }
        val booster = base.generate(regular, random)
        val used = booster.mapTo(hashSetOf()) { it.name }
        val land = weightedPick((nonbasics + basicLands).filter { it.name !in used }, random) {
            if (it.typeLine.isBasicLand) basicWeight else nonbasicWeight
        }
        return if (land == null) booster else booster + land
    }
}
