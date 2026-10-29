package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class LegendarySlotBoosterTest : DescribeSpec({
    fun card(name: String, rarity: Rarity, legendary: Boolean = false, creature: Boolean = true) = CardDefinition(
        manaCost = ManaCost.ZERO,
        name = name,
        typeLine = TypeLine(
            supertypes = if (legendary) setOf(Supertype.LEGENDARY) else emptySet(),
            cardTypes = setOf(if (creature) CardType.CREATURE else CardType.ARTIFACT),
        ),
        creatureStats = if (creature) CreatureStats(1, 1) else null,
        metadata = ScryfallMetadata(rarity = rarity),
    )

    val ordinary = (1..101).map { card("Common $it", Rarity.COMMON) } +
        (1..60).map { card("Uncommon $it", Rarity.UNCOMMON) } +
        (1..51).map { card("Rare $it", Rarity.RARE) } +
        (1..13).map { card("Mythic $it", Rarity.MYTHIC) } +
        card("Legendary Artifact", Rarity.RARE, legendary = true, creature = false)
    val legendaryUncommons = (1..20).map { card("Legend U $it", Rarity.UNCOMMON, legendary = true) }
    val legendaryRares = (1..14).map { card("Legend R $it", Rarity.RARE, legendary = true) } +
        (1..7).map { card("Legend M $it", Rarity.MYTHIC, legendary = true) }
    val pool = ordinary + legendaryUncommons + legendaryRares
    val strategy = LegendarySlotBooster(commons = 10, uncommons = 3, legendaryRareChance = 0.25)
    fun CardDefinition.isLegendaryCreature() = typeLine.isLegendary && typeLine.isCreature

    describe("Dominaria United draft collation") {
        it("opens exactly one legendary creature, ten commons, three uncommon slots and one rare") {
            repeat(500) { seed ->
                val pack = strategy.generate(pool, Random(seed))
                pack shouldHaveSize 14
                pack.map { it.name }.toSet().size shouldBe 14
                pack.count { it.isLegendaryCreature() } shouldBe 1
                pack.count { it.metadata.rarity == Rarity.COMMON } shouldBe 10
                pack.count { it.metadata.rarity == Rarity.RARE || it.metadata.rarity == Rarity.MYTHIC } shouldBe 1
                val legendary = pack.single { it.isLegendaryCreature() }
                val nonlegendaryUncommons = pack.count { it.metadata.rarity == Rarity.UNCOMMON && !it.isLegendaryCreature() }
                nonlegendaryUncommons shouldBe if (legendary.metadata.rarity == Rarity.UNCOMMON) 2 else 3
            }
        }

        it("puts a rare or mythic in the legendary slot in a quarter of packs") {
            val random = Random(7)
            val packs = 8000
            var legendaryRares = 0
            var mythics = 0
            repeat(packs) {
                val pack = strategy.generate(pool, random)
                if (pack.single { it.isLegendaryCreature() }.metadata.rarity != Rarity.UNCOMMON) legendaryRares++
                mythics += pack.count { it.metadata.rarity == Rarity.MYTHIC }
            }
            (legendaryRares.toDouble() / packs) shouldBe (0.25 plusOrMinus 0.02)
            (mythics.toDouble() / packs) shouldBe (0.125 plusOrMinus 0.015)
        }

        it("keeps a noncreature legendary in the ordinary rare slot") {
            val seen = (0 until 3000).any { seed ->
                strategy.generate(pool, Random(seed)).any { it.name == "Legendary Artifact" }
            }
            seen shouldBe true
        }
    }

    describe("sparse pools") {
        it("uses the rare tier when every legendary uncommon is banned") {
            repeat(100) { seed ->
                val pack = strategy.generate(ordinary + legendaryRares, Random(seed))
                pack shouldHaveSize 14
                pack.single { it.isLegendaryCreature() }.metadata.rarity shouldBe
                    pack.single { it.metadata.rarity == Rarity.RARE || it.metadata.rarity == Rarity.MYTHIC }.metadata.rarity
            }
        }

        it("uses the uncommon tier when every legendary rare is banned") {
            repeat(100) { seed ->
                val pack = strategy.generate(ordinary + legendaryUncommons, Random(seed))
                pack shouldHaveSize 14
                pack.single { it.isLegendaryCreature() }.metadata.rarity shouldBe Rarity.UNCOMMON
            }
        }

        it("falls back to a standard pack without legendary creatures") {
            val pack = strategy.generate(ordinary, Random(1))
            pack shouldHaveSize 14
            pack.count { it.metadata.rarity == Rarity.UNCOMMON } shouldBe 3
        }
    }
})
