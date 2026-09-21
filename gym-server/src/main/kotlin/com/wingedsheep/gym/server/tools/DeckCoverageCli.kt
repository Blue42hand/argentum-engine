package com.wingedsheep.gym.server.tools

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.server.config.createGymCardRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Machine-readable compatibility probe for externally maintained deck lists.
 *
 * Intended for engine-adapter qualification as well as Commander Gym migration audits.
 *
 * This deliberately uses the same full [CardRegistry] as the Gym server instead of inferring card
 * support from source filenames. It does not copy deck contents into Argentum; callers pass local
 * files at execution time.
 *
 * Supported inputs:
 * - Commander Gym / Archidekt-style: commander first, then main deck; lines may be "1 Card" or bare.
 * - MTGA-style sections: Deck/Main/Mainboard + Commander; Sideboard/Companion are ignored.
 *
 * Run through the Gradle task:
 *
 *   ./gradlew -q :gym-server:commanderGymDeckCoverage \
 *     -PdeckFiles="/path/a.txt;/path/b.txt" \
 *     -PcoverageOutput="/tmp/coverage.json" \
 *     -PregistryNamesOutput="/tmp/registry-card-names.txt"
 */
fun main(args: Array<String>) {
    val options = CoverageCliOptions.parse(args)
    require(options.deckFiles.isNotEmpty()) { "At least one deck file is required" }

    val registry = createGymCardRegistry()
    options.registryNamesOutput?.let { writeRegistryNames(it, registry) }

    val decks = options.deckFiles.map { analyzeDeck(it, registry) }
    val report = DeckCoverageReport(
        registryCardNames = registry.size,
        decks = decks,
    )
    val json = JSON.encodeToString(report)

    val output = options.output
    if (output == null) {
        println(json)
    } else {
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, json + System.lineSeparator())
    }
}

@Serializable
data class DeckCoverageReport(
    val schema: Int = 1,
    val registryCardNames: Int,
    val decks: List<DeckCoverage>,
)

@Serializable
data class DeckCoverage(
    val file: String,
    val commander: String?,
    val commanderImplementedExact: Boolean?,
    val commanderImplementedAfterFrontFaceNormalization: Boolean?,
    val librarySlots: Int,
    val totalSlotsIncludingCommander: Int,
    val uniqueNamesIncludingCommander: Int,
    val exactImplementedSlots: Int,
    val normalizedImplementedSlots: Int,
    val exactCoveragePercent: Double,
    val normalizedCoveragePercent: Double,
    val missing: List<MissingCard>,
    val frontFaceNormalizations: List<FrontFaceNormalization>,
)

@Serializable
data class MissingCard(
    val name: String,
    val copies: Int,
)

@Serializable
data class FrontFaceNormalization(
    val sourceName: String,
    val registryName: String,
    val copies: Int,
)

internal data class ParsedDeck(
    val commander: String?,
    val library: List<String>,
)

internal fun writeRegistryNames(path: Path, registry: CardRegistry) {
    path.parent?.let { Files.createDirectories(it) }
    Files.write(
        path,
        registry.allCardNames().sorted(),
    )
}

internal fun analyzeDeck(path: Path, registry: CardRegistry): DeckCoverage {
    val parsed = parseDeck(path)
    val allCards = buildList {
        parsed.commander?.let(::add)
        addAll(parsed.library)
    }
    val counts = allCards.groupingBy { it }.eachCount().toSortedMap()

    val exactImplementedSlots = allCards.count(registry::hasCard)
    val normalizedNames = allCards.map { normalizeForRegistry(it, registry) }
    val normalizedImplementedSlots = normalizedNames.count { it != null }

    val missing = counts.entries
        .filter { (name, _) -> normalizeForRegistry(name, registry) == null }
        .map { (name, copies) -> MissingCard(name, copies) }

    val normalizations = counts.entries.mapNotNull { (name, copies) ->
        if (registry.hasCard(name)) return@mapNotNull null
        val normalized = normalizeForRegistry(name, registry) ?: return@mapNotNull null
        FrontFaceNormalization(
            sourceName = name,
            registryName = normalized,
            copies = copies,
        )
    }

    return DeckCoverage(
        file = path.toString(),
        commander = parsed.commander,
        commanderImplementedExact = parsed.commander?.let(registry::hasCard),
        commanderImplementedAfterFrontFaceNormalization =
            parsed.commander?.let { normalizeForRegistry(it, registry) != null },
        librarySlots = parsed.library.size,
        totalSlotsIncludingCommander = allCards.size,
        uniqueNamesIncludingCommander = counts.size,
        exactImplementedSlots = exactImplementedSlots,
        normalizedImplementedSlots = normalizedImplementedSlots,
        exactCoveragePercent = percent(exactImplementedSlots, allCards.size),
        normalizedCoveragePercent = percent(normalizedImplementedSlots, allCards.size),
        missing = missing,
        frontFaceNormalizations = normalizations,
    )
}

/**
 * Scryfall exports transform/modal double-face cards as "Front // Back", while Argentum's runtime
 * identity is normally the front-face card name. Treat that as an explicit adapter normalization,
 * not as card implementation. The report preserves both exact and normalized coverage.
 */
internal fun normalizeForRegistry(name: String, registry: CardRegistry): String? {
    if (registry.hasCard(name)) return name
    val front = name.substringBefore(" // ", missingDelimiterValue = name)
    return front.takeIf { it != name && registry.hasCard(it) }
}

internal fun parseDeck(path: Path): ParsedDeck {
    require(Files.isRegularFile(path)) { "Deck file does not exist: $path" }

    var section = DeckSection.DECK
    var sawCommanderSection = false
    val deckCards = mutableListOf<String>()
    var commander: String? = null

    for (raw in Files.readAllLines(path)) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

        when (line.lowercase()) {
            "deck", "main", "mainboard" -> {
                section = DeckSection.DECK
                continue
            }
            "commander" -> {
                section = DeckSection.COMMANDER
                sawCommanderSection = true
                continue
            }
            "sideboard" -> {
                section = DeckSection.SIDEBOARD
                continue
            }
            "companion" -> {
                section = DeckSection.COMPANION
                continue
            }
        }

        val entry = parseCardLine(line) ?: continue
        when (section) {
            DeckSection.DECK -> repeat(entry.count) { deckCards += entry.name }
            DeckSection.COMMANDER -> if (entry.count > 0) commander = entry.name
            DeckSection.SIDEBOARD, DeckSection.COMPANION -> Unit
        }
    }

    // Commander Gym's portable deck files put the commander first. Match Argentum's own
    // commander-import fallback when there is no explicit Commander section.
    if (commander == null && !sawCommanderSection && deckCards.isNotEmpty()) {
        commander = deckCards.removeAt(0)
    }

    return ParsedDeck(commander = commander, library = deckCards)
}

private data class ParsedCardLine(val count: Int, val name: String)

private val COUNTED_CARD = Regex("""^\s*(\d+)[xX]?\s+(.+?)\s*$""")
private val SET_SUFFIX = Regex("""\s*\([^)]*\)\s+\S+\s*$""")

private fun parseCardLine(line: String): ParsedCardLine? {
    val counted = COUNTED_CARD.matchEntire(line)
    if (counted != null) {
        val count = counted.groupValues[1].toInt()
        if (count <= 0) return null
        return ParsedCardLine(
            count = count,
            name = stripSetSuffix(counted.groupValues[2]),
        )
    }

    // Commander Gym also keeps simple one-card-per-line lists.
    return ParsedCardLine(count = 1, name = stripSetSuffix(line))
}

private fun stripSetSuffix(rawName: String): String =
    SET_SUFFIX.replace(rawName, "").trim()

private fun percent(numerator: Int, denominator: Int): Double =
    if (denominator == 0) 0.0
    else kotlin.math.round(numerator.toDouble() * 10_000.0 / denominator) / 100.0

private enum class DeckSection { DECK, COMMANDER, SIDEBOARD, COMPANION }

private data class CoverageCliOptions(
    val deckFiles: List<Path>,
    val output: Path?,
    val registryNamesOutput: Path?,
) {
    companion object {
        fun parse(args: Array<String>): CoverageCliOptions {
            val deckFiles = mutableListOf<Path>()
            var output: Path? = null
            var registryNamesOutput: Path? = null
            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--output" -> {
                        require(i + 1 < args.size) { "--output requires a path" }
                        output = Path.of(args[++i])
                    }
                    "--registry-names-output" -> {
                        require(i + 1 < args.size) { "--registry-names-output requires a path" }
                        registryNamesOutput = Path.of(args[++i])
                    }
                    else -> deckFiles.add(Path.of(arg))
                }
                i++
            }
            return CoverageCliOptions(deckFiles, output, registryNamesOutput)
        }
    }
}

private val JSON = Json {
    prettyPrint = true
    encodeDefaults = true
}
