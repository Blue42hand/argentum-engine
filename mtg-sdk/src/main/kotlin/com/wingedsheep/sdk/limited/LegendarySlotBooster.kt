package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import kotlin.random.Random

/**
 * Dominaria United-style booster: exactly one legendary creature per pack, drawn from its own
 * print sheets, with every other slot nonlegendary-creature.
 *
 * With [legendaryRareChance] the legendary creature is a rare or mythic and takes the rare slot,
 * beside [uncommons] nonlegendary uncommons. Otherwise it is an uncommon and takes one uncommon
 * slot, beside `uncommons - 1` nonlegendary uncommons and a nonlegendary rare or mythic. Either
 * way the pack holds [commons] commons, [uncommons] uncommon-or-legendary cards and one rare.
 * The mythic upgrade ([mythicChance]) applies to whichever rare slot the pack has.
 *
 * Unlike [GuaranteedLegendaryBooster], which picks one legendary uniformly from every rarity,
 * the rarity is rolled first, so a large legendary rare sheet doesn't crowd out the uncommons.
 * When a tier is empty (host bans, a thin pool) the other tier is used; with no legendary
 * creatures at all the pack is an ordinary [StandardBooster].
 */
data class LegendarySlotBooster(
    val commons: Int = 10,
    val uncommons: Int = 3,
    val legendaryRareChance: Double = 0.25,
    val mythicChance: Double = 0.125,
) : BoosterStrategy {
    init {
        require(uncommons >= 1) { "The legendary uncommon needs an uncommon slot" }
        require(legendaryRareChance in 0.0..1.0) { "legendaryRareChance must be a probability" }
    }

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val isLegendary = { card: CardDefinition -> card.typeLine.isLegendary && card.typeLine.isCreature }
        val (legendaries, ordinary) = pool.partition(isLegendary)
        if (legendaries.isEmpty()) {
            return StandardBooster(commons, uncommons, mythicChance = mythicChance).generate(pool, random)
        }

        val legendaryPicker = RarityPicker(legendaries, random)
        val rareTier = legendaryPicker.hasAny(Rarity.RARE) || legendaryPicker.hasAny(Rarity.MYTHIC)
        val wantsRare = random.nextDouble() < legendaryRareChance
        val legendaryIsRare = if (legendaryPicker.hasAny(Rarity.UNCOMMON)) wantsRare && rareTier else rareTier
        val legendary = if (legendaryIsRare) {
            pickRareOrMythic(legendaryPicker, random)
        } else {
            legendaryPicker.pick(Rarity.UNCOMMON) ?: legendaryPicker.pick(Rarity.COMMON)
        } ?: return StandardBooster(commons, uncommons, mythicChance = mythicChance).generate(ordinary, random)

        val picker = RarityPicker(ordinary, random)
        val booster = mutableListOf<CardDefinition>()
        repeat(commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }
        repeat(if (legendaryIsRare) uncommons else uncommons - 1) {
            picker.pick(Rarity.UNCOMMON)?.let(booster::add)
        }
        booster.add(legendary)
        if (!legendaryIsRare) {
            (pickRareOrMythic(picker, random) ?: picker.pick(Rarity.UNCOMMON) ?: picker.pick(Rarity.COMMON))
                ?.let(booster::add)
        }
        return booster
    }

    private fun pickRareOrMythic(picker: RarityPicker, random: Random): CardDefinition? {
        val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
        return (if (rolledMythic) picker.pick(Rarity.MYTHIC) else picker.pick(Rarity.RARE))
            ?: picker.pick(Rarity.RARE)
            ?: picker.pick(Rarity.MYTHIC)
    }
}
