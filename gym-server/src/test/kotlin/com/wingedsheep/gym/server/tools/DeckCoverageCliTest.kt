package com.wingedsheep.gym.server.tools

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class DeckCoverageCliTest : FunSpec({

    fun registry() = CardRegistry().apply {
        register(
            listOf(
                CardDefinition.creature(
                    name = "Commander One",
                    manaCost = ManaCost.parse("{1}{G}"),
                    subtypes = emptySet(),
                    power = 2,
                    toughness = 2,
                ),
                CardDefinition.creature(
                    name = "Supported Card",
                    manaCost = ManaCost.parse("{1}{G}"),
                    subtypes = emptySet(),
                    power = 2,
                    toughness = 2,
                ),
                CardDefinition.creature(
                    name = "Front Face",
                    manaCost = ManaCost.parse("{2}{G}"),
                    subtypes = emptySet(),
                    power = 3,
                    toughness = 3,
                ),
            )
        )
    }

    test("commander-first portable lists are parsed and reported") {
        val file = Files.createTempFile("commander-gym-deck", ".txt")
        Files.writeString(
            file,
            """
            # commander first
            1 Commander One
            2 Supported Card
            1 Missing Card
            1 Front Face // Back Face
            """.trimIndent()
        )

        val report = analyzeDeck(file, registry())

        report.commander shouldBe "Commander One"
        report.librarySlots shouldBe 4
        report.totalSlotsIncludingCommander shouldBe 5
        report.commanderImplementedExact shouldBe true
        report.exactImplementedSlots shouldBe 3
        report.normalizedImplementedSlots shouldBe 4
        report.exactCoveragePercent shouldBe 60.0
        report.normalizedCoveragePercent shouldBe 80.0
        report.missing shouldContainExactly listOf(MissingCard("Missing Card", 1))
        report.frontFaceNormalizations shouldContainExactly listOf(
            FrontFaceNormalization("Front Face // Back Face", "Front Face", 1)
        )
    }

    test("explicit MTGA Commander sections override positional fallback") {
        val file = Files.createTempFile("arena-commander-deck", ".txt")
        Files.writeString(
            file,
            """
            Deck
            2 Supported Card (TST) 123
            1 Missing Card

            Commander
            1 Commander One
            """.trimIndent()
        )

        val parsed = parseDeck(file)

        parsed.commander shouldBe "Commander One"
        parsed.library shouldContainExactly listOf(
            "Supported Card",
            "Supported Card",
            "Missing Card",
        )
    }

    test("bare one-card-per-line Commander Gym lists use the first card as commander") {
        val file = Files.createTempFile("simple-commander-deck", ".txt")
        Files.writeString(
            file,
            """
            Commander One
            Supported Card
            Missing Card
            """.trimIndent()
        )

        val parsed = parseDeck(file)

        parsed.commander shouldBe "Commander One"
        parsed.library shouldContainExactly listOf("Supported Card", "Missing Card")
    }
})
