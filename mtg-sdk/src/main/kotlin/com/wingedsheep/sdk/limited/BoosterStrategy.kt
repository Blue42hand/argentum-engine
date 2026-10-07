package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import kotlin.random.Random

/**
 * Generates a single booster pack from a set's card pool.
 *
 * Sets pick a strategy via [com.wingedsheep.sdk.model.MtgSet.boosterStrategy].
 * Strategies are pure functions of (card pool, random) -> list of cards;
 * they do not know about the booster generator, set codes, or basic lands
 * (the generator filters basic lands out before calling).
 *
 * New strategies can be added without touching the engine by writing a new
 * class that implements this interface.
 */
fun interface BoosterStrategy {
    fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition>
}

/**
 * Standard 15-card booster: N commons + N uncommons + 1 rare slot, with the
 * rare slot upgraded to a mythic with [mythicChance] when mythics exist.
 *
 * No card name is duplicated within a single pack.
 */
data class StandardBooster(
    val commons: Int = 11,
    val uncommons: Int = 3,
    val rares: Int = 1,
    val mythicChance: Double = 0.125,
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val picker = RarityPicker(pool, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }
        repeat(uncommons) { picker.pick(Rarity.UNCOMMON)?.let(booster::add) }
        repeat(rares) {
            val card = pickRareOrMythic(picker, random)
                ?: throw IllegalStateException("No cards available for booster generation")
            booster.add(card)
        }
        return booster
    }

    private fun pickRareOrMythic(picker: RarityPicker, random: Random): CardDefinition? {
        val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
        val firstChoice = if (rolledMythic) Rarity.MYTHIC else Rarity.RARE
        return picker.pick(firstChoice)
            ?: picker.pick(Rarity.RARE)
            ?: picker.pick(Rarity.UNCOMMON)
            ?: picker.pick(Rarity.COMMON)
    }
}

/**
 * Play Booster (Murders at Karlov Manor, February 2024, onward): the 14-card pack
 * that replaced both Draft and Set Boosters. Paper composition:
 *
 *  - Slots 1–7: 7 commons (slot 7 carries a 1.5% Special Guest chance — not modeled)
 *  - Slots 8–10: 3 uncommons
 *  - Slot 11: non-foil wildcard of any rarity
 *  - Slot 12: rare or mythic rare (12.5% mythic)
 *  - Slot 13: basic land
 *  - Slot 14: traditional-foil wildcard of any rarity
 *
 * Engine adaptation: foils, The List, and Special Guests are not modeled, so both
 * wildcard slots are plain weighted-rarity slots; the basic-land slot is omitted
 * because the generator strips basic lands from the pool and supplies them
 * separately at deck building. Net result: 13 playable non-land cards per pack.
 *
 * Wildcard rarity weights approximate WotC's published pack-level odds (~37% of
 * packs carry a second rare/mythic and ~4% a third → ~20% rare-or-mythic per
 * wildcard, split 7:1 rare:mythic).
 *
 * No card name is duplicated within a single pack.
 */
data class PlayBooster(
    val commons: Int = 7,
    val uncommons: Int = 3,
    val wildcards: Int = 2,
    val raresOrMythics: Int = 1,
    val mythicChance: Double = 0.125,
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val picker = RarityPicker(pool, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }
        repeat(uncommons) { picker.pick(Rarity.UNCOMMON)?.let(booster::add) }
        repeat(raresOrMythics) {
            val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
            val firstChoice = if (rolledMythic) Rarity.MYTHIC else Rarity.RARE
            val card = picker.pick(firstChoice)
                ?: picker.pick(Rarity.RARE)
                ?: picker.pick(Rarity.UNCOMMON)
                ?: picker.pick(Rarity.COMMON)
                ?: throw IllegalStateException("No cards available for booster generation")
            booster.add(card)
        }
        repeat(wildcards) {
            val pick = picker.pick(rollWildcardRarity(random))
                ?: picker.pick(Rarity.COMMON)
                ?: picker.pick(Rarity.UNCOMMON)
                ?: picker.pick(Rarity.RARE)
                ?: picker.pick(Rarity.MYTHIC)
            pick?.let(booster::add)
        }
        return booster
    }

    /** Wildcard slot rarity mix: 50% C, 30% U, 17.5% R, 2.5% M. */
    private fun rollWildcardRarity(random: Random): Rarity {
        val roll = random.nextDouble()
        return when {
            roll < 0.50 -> Rarity.COMMON
            roll < 0.80 -> Rarity.UNCOMMON
            roll < 0.975 -> Rarity.RARE
            else -> Rarity.MYTHIC
        }
    }
}

/**
 * Reality Fracture Play Booster: a [PlayBooster] whose three-card echoed slot always holds one
 * complete echoed pair plus one echoed card from a different pair. Paper composition:
 *
 *  - 6 commons (one may be a Special Guest — not modeled)
 *  - 1 uncommon
 *  - 1 common-or-uncommon (23% common, 77% uncommon)
 *  - 3 echoed cards: both halves of one pair + 1 echoed card not from that pair
 *  - 1 rare or mythic rare
 *  - 1 traditional-foil wildcard of any rarity (echoed cards included)
 *  - 1 land
 *
 * Echoed cards come only from the echoed slot and the wildcard; the common, uncommon, and
 * rare/mythic slots draw from the rest of the set. Per-card weights follow the paper sheets
 * (taw/magic-search-engine `fra-play.yaml`): a pair and the third echoed card are weighted 5:2:1
 * per card for uncommon:rare:mythic; the rare/mythic slot 2:1 per card for rare:mythic; the
 * wildcard 40:22:6:3 per card for common:uncommon:rare:mythic.
 *
 * Engine adaptation: foils, borderless treatments, and Special Guests are not modeled and the land
 * slot is omitted (basics are supplied at deck building), giving 13 cards per pack.
 *
 * Pairs missing a half from the pool (unimplemented or host-banned) are never opened. If no
 * complete pair remains, the echoed slot falls back to three unpaired echoed cards, then to
 * regular uncommons, so packs keep their size.
 *
 * @param echoedPairs Both halves of each echoed pair, by card name. Every named card is treated
 *                    as echoed and kept out of the regular slots.
 */
data class EchoedPairsPlayBooster(
    val echoedPairs: List<Pair<String, String>>,
    val commons: Int = 6,
    val uncommons: Int = 1,
    val commonOrUncommon: Int = 1,
    val commonOrUncommonCommonChance: Double = 0.23,
    val raresOrMythics: Int = 1,
    val wildcards: Int = 1,
) : BoosterStrategy {

    private val echoedNames: Set<String> = echoedPairs.flatMapTo(hashSetOf()) { listOf(it.first, it.second) }

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val (echoed, regular) = pool.partition { it.name in echoedNames }
        val regularPicker = RarityPicker(regular, random)
        val used = mutableSetOf<String>()
        val booster = mutableListOf<CardDefinition>()
        fun MutableList<CardDefinition>.addPick(card: CardDefinition?) {
            card ?: return
            add(card)
            used.add(card.name)
        }

        repeat(commons) { booster.addPick(regularPicker.pick(Rarity.COMMON) ?: regularPicker.pick(Rarity.UNCOMMON)) }
        repeat(uncommons) { booster.addPick(regularPicker.pick(Rarity.UNCOMMON) ?: regularPicker.pick(Rarity.COMMON)) }
        repeat(commonOrUncommon) {
            val first = if (random.nextDouble() < commonOrUncommonCommonChance) Rarity.COMMON else Rarity.UNCOMMON
            booster.addPick(regularPicker.pick(first) ?: regularPicker.pick(Rarity.COMMON) ?: regularPicker.pick(Rarity.UNCOMMON))
        }

        val echoedSlot = pickEchoedSlot(echoed, random)
        echoedSlot.forEach { booster.addPick(it) }
        repeat(ECHOED_SLOT_SIZE - echoedSlot.size) { booster.addPick(regularPicker.pick(Rarity.UNCOMMON)) }

        repeat(raresOrMythics) {
            val card = weightedPick(regular.filter { it.name !in used }, random, ::rareSlotWeight)
                ?: regularPicker.pick(Rarity.UNCOMMON)
                ?: regularPicker.pick(Rarity.COMMON)
                ?: throw IllegalStateException("No cards available for booster generation")
            regularPicker.markUsed(card.name)
            booster.addPick(card)
        }

        repeat(wildcards) {
            val card = weightedPick(pool.filter { it.name !in used }, random, ::wildcardWeight)
            card?.let { regularPicker.markUsed(it.name) }
            booster.addPick(card)
        }
        return booster
    }

    /** One complete pair plus an echoed card from another pair; best-effort when pairs are missing. */
    private fun pickEchoedSlot(echoed: List<CardDefinition>, random: Random): List<CardDefinition> {
        val byName = echoed.associateBy { it.name }
        val completePairs = echoedPairs.mapNotNull { (a, b) ->
            val first = byName[a] ?: return@mapNotNull null
            val second = byName[b] ?: return@mapNotNull null
            first to second
        }
        val pair = weightedPick(completePairs, random) { echoedWeight(it.first) }
        val slot = pair?.toList().orEmpty().toMutableList()
        while (slot.size < ECHOED_SLOT_SIZE) {
            val taken = slot.mapTo(hashSetOf()) { it.name }
            val next = weightedPick(echoed.filter { it.name !in taken }, random, ::echoedWeight) ?: break
            slot.add(next)
        }
        return slot
    }

    private fun echoedWeight(card: CardDefinition): Int = when (card.metadata.rarity) {
        Rarity.MYTHIC -> 1
        Rarity.RARE -> 2
        else -> 5
    }

    private fun rareSlotWeight(card: CardDefinition): Int = when (card.metadata.rarity) {
        Rarity.MYTHIC -> 1
        Rarity.RARE -> 2
        else -> 0
    }

    private fun wildcardWeight(card: CardDefinition): Int = when (card.metadata.rarity) {
        Rarity.MYTHIC -> 3
        Rarity.RARE -> 6
        Rarity.UNCOMMON -> 22
        else -> 40
    }

    private companion object {
        const val ECHOED_SLOT_SIZE = 3
    }
}

/** Picks one of [items] with probability proportional to [weight]; null when nothing has positive weight. */
private fun <T> weightedPick(items: List<T>, random: Random, weight: (T) -> Int): T? {
    val total = items.sumOf { weight(it).coerceAtLeast(0) }
    if (total <= 0) return null
    var roll = random.nextInt(total)
    for (item in items) {
        roll -= weight(item).coerceAtLeast(0)
        if (roll < 0) return item
    }
    return null
}

/**
 * Dominaria / Kamigawa-style booster: every pack contains a legendary creature.
 *
 * The legendary occupies the slot matching its printed rarity:
 *   - Uncommon legendary → replaces one uncommon slot
 *   - Rare/mythic legendary → replaces the rare slot
 *
 * Falls back to [base] generation when the pool has no legendary creatures.
 */
data class GuaranteedLegendaryBooster(
    val base: StandardBooster = StandardBooster(),
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val legendaries = pool.filter { it.typeLine.isLegendary && it.typeLine.isCreature }
        if (legendaries.isEmpty()) return base.generate(pool, random)

        val legendary = legendaries.random(random)
        val poolWithoutLegendary = pool.filter { it.name != legendary.name }
        val picker = RarityPicker(poolWithoutLegendary, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(base.commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }

        val legendaryIsUncommon = legendary.metadata.rarity == Rarity.UNCOMMON
        val uncommonSlots = if (legendaryIsUncommon) (base.uncommons - 1).coerceAtLeast(0) else base.uncommons
        repeat(uncommonSlots) { picker.pick(Rarity.UNCOMMON)?.let(booster::add) }

        val legendaryIsRareOrMythic = legendary.metadata.rarity == Rarity.RARE ||
            legendary.metadata.rarity == Rarity.MYTHIC
        if (!legendaryIsRareOrMythic) {
            val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < base.mythicChance
            val rareSlot = (if (rolledMythic) picker.pick(Rarity.MYTHIC) else picker.pick(Rarity.RARE))
                ?: picker.pick(Rarity.RARE)
                ?: picker.pick(Rarity.UNCOMMON)
                ?: picker.pick(Rarity.COMMON)
            rareSlot?.let(booster::add)
        }

        booster.add(legendary)
        return booster
    }
}

/**
 * Commander Legends 2020 -shaped booster: 20 cards split into 13 commons, 3 uncommons,
 * 2 dedicated legendary slots, 1 non-legendary rare-or-mythic, and 1 bonus card of any rarity.
 *
 * The legendary slots hold a legendary creature or legendary planeswalker of any rarity. They
 * do not consume the non-legendary rare-or-mythic slot — both ship together, so a pack always
 * carries a non-leg R/M *and* two legendaries.
 *
 * The bonus slot replaces Commander Legends' traditional foil slot (the engine does not model
 * foils). Distribution is weighted to roughly mirror the foil-slot rarity mix: mostly common,
 * sometimes uncommon, occasionally rare or mythic.
 *
 * Sparse-legendary fallback: if the pool has fewer legendaries than required slots (or none at
 * all), the missing slots are filled with [piperFallback] when supplied, and otherwise demoted to
 * additional non-legendary uncommons. Callers that want to flag a Piper-substituted pool for
 * downstream commander selection should compare the resulting pack against the input pool.
 *
 * @param piperFallback A colourless legendary creature card (typically The Prismatic Piper) used
 *                      to back-fill legendary slots when the set has no legendaries. The fallback
 *                      is never drawn from the booster pool itself — supply it explicitly.
 */
data class CommanderDraftBooster(
    val commons: Int = 13,
    val uncommons: Int = 3,
    val legendaries: Int = 2,
    val nonLegendaryRareOrMythic: Int = 1,
    val bonus: Int = 1,
    val mythicChance: Double = 0.125,
    val piperFallback: CardDefinition? = null,
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val (legendaryPool, nonLegendaryPool) = pool.partition { it.isCommanderSlotEligible() }

        val nonLegPicker = RarityPicker(nonLegendaryPool, random)
        val legPicker = RarityPicker(legendaryPool, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(commons) { nonLegPicker.pick(Rarity.COMMON)?.let(booster::add) }
        repeat(uncommons) { nonLegPicker.pick(Rarity.UNCOMMON)?.let(booster::add) }

        repeat(legendaries) {
            val pick = pickLegendary(legPicker, random)
                ?: piperFallback
                ?: nonLegPicker.pick(Rarity.UNCOMMON)
                ?: nonLegPicker.pick(Rarity.COMMON)
            pick?.let(booster::add)
        }

        repeat(nonLegendaryRareOrMythic) {
            val rolledMythic = nonLegPicker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
            val firstChoice = if (rolledMythic) Rarity.MYTHIC else Rarity.RARE
            val pick = nonLegPicker.pick(firstChoice)
                ?: nonLegPicker.pick(Rarity.RARE)
                ?: nonLegPicker.pick(Rarity.MYTHIC)
                ?: nonLegPicker.pick(Rarity.UNCOMMON)
                ?: nonLegPicker.pick(Rarity.COMMON)
            pick?.let(booster::add)
        }

        repeat(bonus) {
            val rarity = rollBonusRarity(random)
            val pick = nonLegPicker.pick(rarity)
                ?: nonLegPicker.pick(Rarity.RARE)
                ?: nonLegPicker.pick(Rarity.UNCOMMON)
                ?: nonLegPicker.pick(Rarity.COMMON)
            pick?.let(booster::add)
        }

        return booster
    }

    private fun pickLegendary(picker: RarityPicker, random: Random): CardDefinition? {
        val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
        val firstChoice = if (rolledMythic) Rarity.MYTHIC else Rarity.RARE
        return picker.pick(firstChoice)
            ?: picker.pick(Rarity.RARE)
            ?: picker.pick(Rarity.MYTHIC)
            ?: picker.pick(Rarity.UNCOMMON)
            ?: picker.pick(Rarity.COMMON)
    }

    /** Approximates Commander Legends' foil-slot rarity mix: 50% C, 33% U, 14% R, 3% M. */
    private fun rollBonusRarity(random: Random): Rarity {
        val roll = random.nextDouble()
        return when {
            roll < 0.50 -> Rarity.COMMON
            roll < 0.83 -> Rarity.UNCOMMON
            roll < 0.97 -> Rarity.RARE
            else -> Rarity.MYTHIC
        }
    }

    private fun CardDefinition.isCommanderSlotEligible(): Boolean =
        typeLine.isLegendary && (typeLine.isCreature || CardType.PLANESWALKER in typeLine.cardTypes)
}

/** Picks cards by rarity without repeating names within a single booster. */
private class RarityPicker(pool: List<CardDefinition>, private val random: Random) {
    private val byRarity: Map<Rarity, MutableList<CardDefinition>> =
        pool.groupBy { it.metadata.rarity }.mapValues { it.value.toMutableList() }
    private val used = mutableSetOf<String>()

    fun hasAny(rarity: Rarity): Boolean = !byRarity[rarity].isNullOrEmpty()

    /** Exclude [name] from later picks — for cards a strategy placed without this picker. */
    fun markUsed(name: String) {
        used.add(name)
    }

    fun pick(rarity: Rarity): CardDefinition? {
        val available = byRarity[rarity]?.filter { it.name !in used } ?: return null
        if (available.isEmpty()) return null
        val picked = available.random(random)
        used.add(picked.name)
        return picked
    }
}
