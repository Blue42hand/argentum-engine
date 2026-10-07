package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.gameserver.deck.DeckValidationResult
import com.wingedsheep.gameserver.deck.DeckValidator
import com.wingedsheep.gameserver.protocol.DeckEntryDTO
import com.wingedsheep.gameserver.protocol.DeckRequestConverter
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.PrintingRef
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Deck-scoped REST endpoints — operations that act on or describe deck lists:
 *
 * - `GET  /api/decks/examples`        — built-in example decks the picker offers as starting points.
 * - `POST /api/decks/validate`        — server-authoritative deck validation (count rules, unknown cards, ≥60).
 * - `POST /api/decks/legal-formats`   — batch legality check for the saved-deck browser.
 * - `GET  /api/decks/formats`         — supported deck formats.
 *
 * Catalog endpoints (cards / sets) live under their own resources at `/api/cards` and `/api/sets`
 * respectively, since they describe the universe of cards rather than user-authored deck lists.
 */
@RestController
@RequestMapping("/api/decks")
class DecksController(
    private val deckValidator: DeckValidator,
    private val cardRegistry: CardRegistry,
    private val printingRegistry: PrintingRegistry,
) {

    data class ExampleDeckDTO(
        val id: String,
        val name: String,
        val description: String,
        val cards: Map<String, Int>,
        /**
         * Deck-construction format this example is built for. Null means "no format hint" —
         * the picker shows the example regardless of the active format. When set, the
         * deckbuilder pre-selects the format on load and the lobby picker filters the
         * Examples tab to examples matching its constrained format.
         */
        val format: DeckFormat? = null,
        /**
         * Designated commander card name for commander-shape examples (CR 903.5b). When
         * present, the deckbuilder pre-fills the commander slot and the picker validates
         * the deck under commander rules. The commander is included in [cards] (matching
         * the convention used by the import-from-text flow); consumers that need the
         * library-only list strip it before sending.
         */
        val commander: String? = null,
        /**
         * Optional preferred printing per card name. When present, the deckbuilder loads
         * these as the deck's pinned printings (so the art and Scryfall metadata reflect
         * the precon's printings rather than the canonical default). Sparse — names not in
         * the map fall back to the default printing. Modeled as a single ref per name
         * (not per copy) because a starter deck only carries one preferred art per card;
         * users who want art variants split copies in the deckbuilder after loading.
         */
        val printings: Map<String, PrintingRef>? = null,
        /**
         * Optional preferred printing for the commander. Separate from [printings] because
         * the commander itself is stored separately from the rest of the deck (CR 903.6a).
         */
        val commanderPrinting: PrintingRef? = null,
        /**
         * What the deck *is*, at a glance — colours, cover art, curve, key cards — for surfaces
         * that offer a starter deck without the whole card catalog loaded (the landing page's
         * launch panel). Computed once from the registry; null only before enrichment.
         */
        val summary: ExampleDeckSummaryDTO? = null,
    )

    data class ExampleDeckSummaryDTO(
        /** WUBRG letters, most-represented first, counted over non-land cards. */
        val colors: List<String>,
        val cardCount: Int,
        val creatures: Int,
        /** Non-creature, non-land cards. */
        val spells: Int,
        val lands: Int,
        /** Non-land cards per mana value, index 0..7 (7 = seven or more). */
        val curve: List<Int>,
        /** The deck's face: the commander, else its rarest non-land card. */
        val coverCard: String?,
        val coverImageUri: String?,
        /** Up to three standout non-land cards (rarest first), cover included. */
        val keyCards: List<String>,
    )

    private val enrichedExamples: List<ExampleDeckDTO> by lazy {
        EXAMPLE_DECKS.map { it.copy(summary = summarize(it)) }
    }

    private fun summarize(deck: ExampleDeckDTO): ExampleDeckSummaryDTO {
        val entries = deck.cards.mapNotNull { (name, n) -> cardRegistry.getCard(name)?.let { it to n } }
        val nonLand = entries.filter { (card, _) -> !card.typeLine.isLand }
        val colorWeight = mutableMapOf<String, Int>()
        for ((card, n) in nonLand) for (c in card.colors) colorWeight.merge(c.symbol.toString(), n, Int::plus)
        val curve = MutableList(8) { 0 }
        for ((card, n) in nonLand) curve[card.cmc.coerceIn(0, 7)] += n
        val byStandout = nonLand.map { it.first }.distinctBy { it.name }.sortedWith(
            compareByDescending<CardDefinition> { it.name == deck.commander }
                .thenByDescending { RARITY_RANK[it.metadata.rarity] ?: 0 }
                .thenByDescending { it.cmc }
                .thenBy { it.name },
        )
        val cover = byStandout.firstOrNull()
        val coverImage = cover?.let { card ->
            deck.printings?.get(card.name)?.let { printingRegistry.getPrinting(it)?.imageUri }
                ?: printingRegistry.defaultPrinting(card.name)?.imageUri
                ?: card.metadata.imageUri
        }
        return ExampleDeckSummaryDTO(
            colors = colorWeight.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { WUBRG.indexOf(it.key) })
                .map { it.key },
            cardCount = deck.cards.values.sum(),
            creatures = nonLand.filter { (card, _) -> card.typeLine.isCreature }.sumOf { it.second },
            spells = nonLand.filter { (card, _) -> !card.typeLine.isCreature }.sumOf { it.second },
            lands = entries.filter { (card, _) -> card.typeLine.isLand }.sumOf { it.second },
            curve = curve,
            coverCard = cover?.name,
            coverImageUri = coverImage,
            keyCards = byStandout.take(3).map { it.name },
        )
    }

    data class ValidateRequest(
        val deckList: Map<String, Int>,
        val format: DeckFormat? = null,
        /**
         * Optional designated commander for Commander/Brawl/Standard Brawl decks. When present
         * (and the format is commander-shaped) the validator runs the full commander rule set:
         * eligibility, color identity, and structural shape with the commander in the command
         * zone instead of the library.
         */
        val commander: String? = null,
        /**
         * Optional rich entries with pinned printings. When non-empty, this is authoritative
         * over [deckList] for both copy-counting and printing validation. The deckbuilder
         * picker emits this when the user has explicitly chosen non-default printings.
         */
        val cardEntries: List<DeckEntryDTO>? = null,
        val commanderPrinting: PrintingRef? = null,
    )

    @GetMapping("/examples")
    fun getExamples(): List<ExampleDeckDTO> = enrichedExamples

    @PostMapping("/validate")
    fun validate(@RequestBody request: ValidateRequest): DeckValidationResult {
        // Route through the Deck overload only when the caller has supplied a commander —
        // an absent field means the (legacy) Map-based validation path, which doesn't enforce
        // commander rules. When [cardEntries] is also present, [DeckRequestConverter] folds
        // the rich entries into the Deck so pinned printings are validated.
        if (request.commander != null) {
            val deck = DeckRequestConverter.toDeck(
                deckList = request.deckList,
                cardEntries = request.cardEntries,
                commander = request.commander,
                commanderPrinting = request.commanderPrinting,
            )
            return deckValidator.validate(deck, request.format)
        }
        return deckValidator.validate(
            deckList = request.deckList,
            format = request.format,
            cardEntries = request.cardEntries,
            commanderPrinting = request.commanderPrinting,
        )
    }

    /**
     * Returns, for each submitted deck list, the set of formats it is fully legal in. This is
     * the authoritative source for the deckbuilder's saved-deck legality badges and the lobby
     * picker's "legal in this format" filter — both of which used to compute legality on the
     * client. We run [DeckValidator.validate] against every [DeckFormat] and include only the
     * formats that come back valid (per-card legality + format-specific construction rules).
     *
     * Batched on purpose: the saved-decks browser renders 50+ deck cards at once, and per-deck
     * round-trips would dominate the open-overlay latency. Pass an empty map to get an empty
     * map back.
     */
    @PostMapping("/legal-formats")
    fun legalFormats(@RequestBody request: LegalFormatsRequest): Map<String, List<String>> {
        if (request.decks.isEmpty()) return emptyMap()
        return request.decks.mapValues { (_, deckList) ->
            DeckFormat.entries
                .asSequence()
                .filter { format -> deckValidator.validate(deckList, format).valid }
                .map { it.name }
                .toList()
        }
    }

    data class LegalFormatsRequest(
        val decks: Map<String, Map<String, Int>> = emptyMap()
    )

    @GetMapping("/formats")
    fun getFormats(): List<FormatInfo> =
        DeckFormat.entries.map { FormatInfo(it.name, it.displayName) }

    data class FormatInfo(val id: String, val name: String)

    companion object {
        // Pinned printings for the BLC Animated Army precon. Pulled from the BLC printing
        // metadata in `:mtg-sets` (collectorNumber on each BLC card definition / reprint).
        // The commander itself is pinned via [ExampleDeckDTO.commanderPrinting], not here.
        private val ANIMATED_ARMY_PRINTINGS = mapOf(
            // Creatures
            "Brightcap Badger" to PrintingRef("BLC", "28"),
            "Burnished Hart" to PrintingRef("BLC", "266"),
            "Etali, Primal Storm" to PrintingRef("BLC", "196"),
            "Evercoat Ursine" to PrintingRef("BLC", "30"),
            "Garruk's Packleader" to PrintingRef("BLC", "218"),
            "Ghalta, Primal Hunger" to PrintingRef("BLC", "220"),
            "Goreclaw, Terror of Qal Sisma" to PrintingRef("BLC", "222"),
            "Grothama, All-Devouring" to PrintingRef("BLC", "224"),
            "Grumgully, the Generous" to PrintingRef("BLC", "253"),
            "Kodama of the East Tree" to PrintingRef("BLC", "227"),
            "Llanowar Loamspeaker" to PrintingRef("BLC", "228"),
            "Lotus Cobra" to PrintingRef("BLC", "229"),
            "Prosperous Bandit" to PrintingRef("BLC", "25"),
            "Pyreswipe Hawk" to PrintingRef("BLC", "26"),
            "Rampaging Baloths" to PrintingRef("BLC", "233"),
            "Sakura-Tribe Elder" to PrintingRef("BLC", "236"),
            // Teapot Slinger and Wandertale Mentor are new BLB cards bundled into the
            // precon; no BLC reprint exists, so they ship as their BLB printings.
            "Teapot Slinger" to PrintingRef("BLB", "157"),
            "Tendershoot Dryad" to PrintingRef("BLC", "242"),
            "Trailtracker Scout" to PrintingRef("BLC", "35"),
            "Wandertale Mentor" to PrintingRef("BLB", "240"),
            "Wildsear, Scouring Maw" to PrintingRef("BLC", "8"),
            // Planeswalker
            "Domri, Anarch of Bolas" to PrintingRef("BLC", "98"),
            // Instants
            "Abrade" to PrintingRef("BLC", "191"),
            "Beast Within" to PrintingRef("BLC", "206"),
            "Big Score" to PrintingRef("BLC", "193"),
            "Chaos Warp" to PrintingRef("BLC", "115"),
            "Starstorm" to PrintingRef("BLC", "203"),
            // Sorceries
            "Blasphemous Act" to PrintingRef("BLC", "114"),
            "Cultivate" to PrintingRef("BLC", "212"),
            "Decimate" to PrintingRef("BLC", "251"),
            "Explore" to PrintingRef("BLC", "216"),
            "Farseek" to PrintingRef("BLC", "119"),
            "Harmonize" to PrintingRef("BLC", "120"),
            "Rampant Growth" to PrintingRef("BLC", "234"),
            // Artifacts
            "Arcane Signet" to PrintingRef("BLC", "127"),
            "Bootleggers' Stash" to PrintingRef("BLC", "207"),
            "Esika's Chariot" to PrintingRef("BLC", "215"),
            "Fellwar Stone" to PrintingRef("BLC", "269"),
            "Gilded Lotus" to PrintingRef("BLC", "271"),
            "Gruul Signet" to PrintingRef("BLC", "273"),
            "Hedron Archive" to PrintingRef("BLC", "275"),
            "Mind Stone" to PrintingRef("BLC", "280"),
            "Rolling Hamsphere" to PrintingRef("BLC", "39"),
            "Sol Ring" to PrintingRef("BLC", "129"),
            "Spine of Ish Sah" to PrintingRef("BLC", "285"),
            "Talisman of Impulse" to PrintingRef("BLC", "287"),
            "Thought Vessel" to PrintingRef("BLC", "289"),
            "Thran Dynamo" to PrintingRef("BLC", "290"),
            // Enchantments
            "Alchemist's Talent" to PrintingRef("BLC", "22"),
            "Berserkers' Onslaught" to PrintingRef("BLC", "192"),
            "Garruk's Uprising" to PrintingRef("BLC", "219"),
            "Gratuitous Violence" to PrintingRef("BLC", "197"),
            "Greater Good" to PrintingRef("BLC", "223"),
            "Outpost Siege" to PrintingRef("BLC", "199"),
            "Path of Discovery" to PrintingRef("BLC", "231"),
            "Primeval Bounty" to PrintingRef("BLC", "232"),
            "Rain of Riches" to PrintingRef("BLC", "200"),
            "Sunbird's Invocation" to PrintingRef("BLC", "116"),
            "Thickest in the Thicket" to PrintingRef("BLC", "34"),
            "Unnatural Growth" to PrintingRef("BLC", "245"),
            "Warstorm Surge" to PrintingRef("BLC", "117"),
            // Lands (basics fall back to BLB — BLC ships no basics of its own)
            "Cinder Glade" to PrintingRef("BLC", "299"),
            "Command Tower" to PrintingRef("BLC", "130"),
            "Copperline Gorge" to PrintingRef("BLC", "301"),
            "Evolving Wilds" to PrintingRef("BLC", "302"),
            "Exotic Orchard" to PrintingRef("BLC", "131"),
            "Forest" to PrintingRef("BLB", "278"),
            "Forgotten Cave" to PrintingRef("BLC", "305"),
            "Game Trail" to PrintingRef("BLC", "306"),
            "Gruul Turf" to PrintingRef("BLC", "310"),
            "Karplusan Forest" to PrintingRef("BLC", "314"),
            "Mossfire Valley" to PrintingRef("BLC", "316"),
            "Mosswort Bridge" to PrintingRef("BLC", "317"),
            "Mountain" to PrintingRef("BLB", "274"),
            "Path of Ancestry" to PrintingRef("BLC", "322"),
            "Raging Ravine" to PrintingRef("BLC", "324"),
            "Reliquary Tower" to PrintingRef("BLC", "132"),
            "Rootbound Crag" to PrintingRef("BLC", "326"),
            "Sheltered Thicket" to PrintingRef("BLC", "330"),
            "Temple of Abandon" to PrintingRef("BLC", "338"),
            "Terramorphic Expanse" to PrintingRef("BLC", "345"),
            "Tranquil Thicket" to PrintingRef("BLC", "350"),
            "Wooded Ridgeline" to PrintingRef("BLC", "353"),
        )

        // Bloomburrow-only tribal decks. Selesnya Rabbits, Rakdos Lizards, Golgari Squirrels,
        // and Simic Frogs are taken from the Bloomburrow Constructed Midweek Magic decklists
        // (https://mtgazone.com/midweek-magic-bloomburrow-constructed/). Boros Mice and Orzhov
        // Bats are hand-built tribal lists restricted to Bloomburrow cards in the registry.
        private const val WUBRG = "WUBRG"
        private val RARITY_RANK = mapOf(
            Rarity.COMMON to 0, Rarity.UNCOMMON to 1, Rarity.RARE to 2, Rarity.MYTHIC to 3,
        )

        private val EXAMPLE_DECKS = listOf(
            ExampleDeckDTO(
                id = "boros_mice",
                name = "Boros Mice",
                description = "RW Mice aggro from Bloomburrow.",
                cards = mapOf(
                    "Heartfire Hero" to 4,
                    "Flowerfoot Swordmaster" to 4,
                    "Emberheart Challenger" to 4,
                    "Manifold Mouse" to 4,
                    "Whiskervale Forerunner" to 4,
                    "Seedglaive Mentor" to 4,
                    "Might of the Meek" to 4,
                    "Crumb and Get It" to 4,
                    "Rabid Gnaw" to 4,
                    "Lupinflower Village" to 4,
                    "Rockface Village" to 4,
                    "Mountain" to 8,
                    "Plains" to 8
                )
            ),
            ExampleDeckDTO(
                id = "selesnya_rabbits",
                name = "Selesnya Rabbits",
                description = "GW Rabbits from Bloomburrow.",
                cards = mapOf(
                    "Pawpatch Recruit" to 4,
                    "Warren Elder" to 4,
                    "Burrowguard Mentor" to 4,
                    "Valley Questcaller" to 4,
                    "Finneas, Ace Archer" to 4,
                    "Harvestrite Host" to 4,
                    "Warren Warleader" to 4,
                    "Hop to It" to 4,
                    "Carrot Cake" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 7,
                    "Plains" to 13
                )
            ),
            ExampleDeckDTO(
                id = "rakdos_lizards",
                name = "Rakdos Lizards",
                description = "BR Lizards from Bloomburrow.",
                cards = mapOf(
                    "Iridescent Vinelasher" to 4,
                    "Hired Claw" to 4,
                    "Fireglass Mentor" to 4,
                    "Flamecache Gecko" to 4,
                    "Gev, Scaled Scorch" to 4,
                    "Thought-Stalker Warlock" to 4,
                    "Valley Flamecaller" to 4,
                    "Take Out the Trash" to 4,
                    "Fell" to 4,
                    "Fabled Passage" to 4,
                    "Rockface Village" to 2,
                    "Mountain" to 8,
                    "Swamp" to 10
                )
            ),
            ExampleDeckDTO(
                id = "golgari_squirrels",
                name = "Golgari Squirrels",
                description = "BG Squirrels from Bloomburrow.",
                cards = mapOf(
                    "Bonecache Overseer" to 4,
                    "Vinereap Mentor" to 4,
                    "Bakersbane Duo" to 2,
                    "Thornvault Forager" to 4,
                    "Osteomancer Adept" to 4,
                    "Valley Rotcaller" to 4,
                    "Bushy Bodyguard" to 2,
                    "Curious Forager" to 4,
                    "Camellia, the Seedmiser" to 4,
                    "Fell" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 10,
                    "Swamp" to 10
                )
            ),
            ExampleDeckDTO(
                id = "simic_frogs",
                name = "Simic Frogs",
                description = "GU Frogs from Bloomburrow.",
                cards = mapOf(
                    "Sunshower Druid" to 4,
                    "Valley Mightcaller" to 4,
                    "Pond Prophet" to 4,
                    "Three Tree Scribe" to 4,
                    "Dour Port-Mage" to 3,
                    "Long River Lurker" to 4,
                    "Clement, the Worrywort" to 3,
                    "Splash Lasher" to 2,
                    "Polliwallop" to 4,
                    "Splash Portal" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 11,
                    "Island" to 9
                )
            ),
            ExampleDeckDTO(
                id = "orzhov_bats",
                name = "Orzhov Bats",
                description = "WB Bats from Bloomburrow.",
                cards = mapOf(
                    "Essence Channeler" to 4,
                    "Starscape Cleric" to 4,
                    "Lifecreed Duo" to 4,
                    "Moonstone Harbinger" to 4,
                    "Starlit Soothsayer" to 4,
                    "Moonrise Cleric" to 4,
                    "Wax-Wane Witness" to 4,
                    "Zoraline, Cosmos Caller" to 3,
                    "Lunar Convocation" to 4,
                    "Sonar Strike" to 4,
                    "Fabled Passage" to 4,
                    "Uncharted Haven" to 3,
                    "Plains" to 7,
                    "Swamp" to 7
                )
            ),
            // Standard / Pioneer-leaning aggro-tempo and control lists. DFCs and Rooms use the
            // engine's canonical full name (e.g. "Unholy Annex // Ritual Chamber") even though
            // MTGA-format imports usually drop everything after the slash — the catalogue keys
            // off the full registered name, so abbreviating here would surface as a placeholder.
            ExampleDeckDTO(
                id = "uw_tempo",
                name = "UW Tempo",
                description = "Azorius tempo with auras and counterspells.",
                cards = mapOf(
                    "Malcolm, Alluring Scoundrel" to 4,
                    "Skrelv, Defector Mite" to 4,
                    "Sleep-Cursed Faerie" to 2,
                    "Kitsa, Otterball Elite" to 4,
                    "Bounce Off" to 4,
                    "Negate" to 2,
                    "No More Lies" to 4,
                    "Soul Partition" to 2,
                    "Spell Pierce" to 2,
                    "Combat Research" to 4,
                    "Shardmage's Rescue" to 2,
                    "Sheltered by Ghosts" to 4,
                    "Adarkar Wastes" to 4,
                    "Floodfarm Verge" to 3,
                    "Meticulous Archive" to 4,
                    "Seachrome Coast" to 4,
                    "Island" to 7
                )
            ),
            ExampleDeckDTO(
                id = "standard_monou",
                name = "Mono-Blue Control",
                description = "Mono-blue control with Haughty Djinn and Tolarian Terror.",
                cards = mapOf(
                    "Teferi, Temporal Pilgrim" to 1,
                    "Chrome Host Seedshark" to 1,
                    "Haughty Djinn" to 4,
                    "Hullbreaker Horror" to 2,
                    "Tolarian Terror" to 3,
                    "Blue Sun's Twilight" to 1,
                    "Consider" to 4,
                    "Dissipate" to 4,
                    "Essence Scatter" to 2,
                    "Fading Hope" to 4,
                    "Flow of Knowledge" to 2,
                    "Impulse" to 2,
                    "Memory Deluge" to 1,
                    "Negate" to 2,
                    "Spell Pierce" to 2,
                    "Thirst for Discovery" to 2,
                    "Island" to 23
                )
            ),
            ExampleDeckDTO(
                id = "standard_monob",
                name = "Mono-Black Aggro",
                description = "Mono-black aggro splashing Vampires and Rogues.",
                cards = mapOf(
                    "Bloodletter of Aclazotz" to 4,
                    "Cecil, Dark Knight" to 3,
                    "Deep-Cavern Bat" to 4,
                    "Forsaken Miner" to 4,
                    "Gatekeeper of Malakir" to 4,
                    "Mai, Scornful Striker" to 3,
                    "Unstoppable Slasher" to 4,
                    "Iridescent Vinelasher" to 4,
                    "Shoot the Sheriff" to 2,
                    "Unholy Annex // Ritual Chamber" to 4,
                    "Realm of Koh" to 4,
                    "Soulstone Sanctuary" to 2,
                    "Swamp" to 18
                )
            ),
            // The Standard metagame as of early October 2026: the best-placed list of each major
            // archetype from the MTGO Standard Challenges of 1–7 October 2026
            // (https://www.mtgo.com/decklists/2026/10), main decks only.
            ExampleDeckDTO(
                id = "standard_izzet_spellementals",
                name = "Izzet Spellementals",
                description = "Cheap cantrips and burn shrink Hearth Elemental, Eddymurk Crab and Sunderflock.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Burst Lightning" to 4,
                    "Eddymurk Crab" to 4,
                    "Hearth Elemental" to 4,
                    "Opt" to 4,
                    "Prismari Charm" to 4,
                    "Riverpyre Verge" to 4,
                    "Sleight of Hand" to 4,
                    "Spirebluff Canal" to 4,
                    "Steam Vents" to 4,
                    "Sunderflock" to 4,
                    "Get Out" to 3,
                    "Spell Snare" to 3,
                    "Traumatic Critique" to 3,
                    "Seasoned Cryomancer" to 2,
                    "Stormcarved Coast" to 2,
                    "Broadside Barrage" to 1,
                    "Island" to 6
                )
            ),
            ExampleDeckDTO(
                id = "standard_mono_green_landfall",
                name = "Mono-Green Landfall",
                description = "Extra land drops power up landfall threats and Mightform Harmonizer.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Escape Tunnel" to 4,
                    "Esper Origins" to 4,
                    "Fabled Passage" to 4,
                    "Icetill Explorer" to 4,
                    "Llanowar Elves" to 4,
                    "Mossborn Hydra" to 4,
                    "Sazh's Chocobo" to 4,
                    "Earthbender Ascension" to 3,
                    "Mightform Harmonizer" to 3,
                    "Sapling Nursery" to 3,
                    "Shared Roots" to 3,
                    "Ba Sing Se" to 2,
                    "Keen-Eyed Curator" to 2,
                    "Meltstrider's Resolve" to 1,
                    "Forest" to 15
                )
            ),
            ExampleDeckDTO(
                id = "standard_dimir_midrange",
                name = "Dimir Midrange",
                description = "Cheap interaction backed by Kaito, Enduring Curiosity and Floodpits Drowner.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Dream Beavers" to 4,
                    "Enduring Curiosity" to 4,
                    "Floodpits Drowner" to 4,
                    "Gloomlake Verge" to 4,
                    "Kaito, Bane of Nightmares" to 4,
                    "Watery Grave" to 4,
                    "Requiting Hex" to 3,
                    "Spyglass Siren" to 3,
                    "Bitter Triumph" to 2,
                    "Hidden Lair" to 2,
                    "Restless Reef" to 2,
                    "Shipwreck Marsh" to 2,
                    "Shoot the Sheriff" to 2,
                    "Soulstone Sanctuary" to 2,
                    "The Wondrous Wasp" to 2,
                    "We Say Thee Nay!" to 2,
                    "Preacher of the Schism" to 1,
                    "Spell Snare" to 1,
                    "Strategic Betrayal" to 1,
                    "Theorix Charm" to 1,
                    "Tishana's Tidebinder" to 1,
                    "Wan Shi Tong, Librarian" to 1,
                    "Yuriko, Hope from the Shadows" to 1,
                    "Island" to 4,
                    "Swamp" to 3
                )
            ),
            ExampleDeckDTO(
                id = "standard_boros_dragons",
                name = "Boros Dragons",
                description = "Burn early, then a stream of Hellkites, Smaug and Sarkhan.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Burst Lightning" to 4,
                    "Clarion Conqueror" to 4,
                    "Inspiring Vantage" to 4,
                    "Magmatic Hellkite" to 4,
                    "Momo, Friendly Flier" to 4,
                    "Multiversal Passage" to 4,
                    "Nova Hellkite" to 4,
                    "Sacred Foundry" to 4,
                    "Sarkhan, Dragon Ascendant" to 4,
                    "Smaug the Magnificent" to 4,
                    "Sunbillow Verge" to 4,
                    "Voice of Victory" to 4,
                    "Cavern of Souls" to 3,
                    "Maelstrom of the Spirit Dragon" to 3,
                    "Erode" to 2,
                    "Twinmaw Stormbrood" to 2,
                    "Mountain" to 1,
                    "Plains" to 1
                )
            ),
            ExampleDeckDTO(
                id = "standard_jund_sacrifice",
                name = "Jund Sacrifice",
                description = "Sacrifice fodder feeds Rottenmouth Viper and Obsessive Pursuit.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Biotech Specialist" to 4,
                    "Blazemire Verge" to 4,
                    "Blooming Marsh" to 4,
                    "Callous Inspector" to 4,
                    "Gene Pollinator" to 4,
                    "Greedy Freebooter" to 4,
                    "Obsessive Pursuit" to 4,
                    "Rottenmouth Viper" to 4,
                    "The Sackville-Bagginses" to 4,
                    "Deadly Precision" to 3,
                    "Lively Dirge" to 3,
                    "Overgrown Tomb" to 3,
                    "Starting Town" to 3,
                    "Stomping Ground" to 3,
                    "Fanatical Offering" to 2,
                    "Wastewood Verge" to 2,
                    "Blood Crypt" to 1,
                    "Mutagen Man, Living Ooze" to 1,
                    "Umbral Collar Zealot" to 1,
                    "Forest" to 1,
                    "Swamp" to 1
                )
            ),
            ExampleDeckDTO(
                id = "standard_azorius_control",
                name = "Azorius Control",
                description = "Counterspells, removal and The Theorist, Jace Beleren.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Consult the Star Charts" to 4,
                    "Countersculpt" to 4,
                    "Deserted Beach" to 4,
                    "Floodfarm Verge" to 4,
                    "Get Lost" to 4,
                    "Hallowed Fountain" to 4,
                    "Jace's Machinations" to 4,
                    "The Theorist, Jace Beleren" to 4,
                    "Demolition Field" to 3,
                    "No More Lies" to 3,
                    "Seam Rip" to 3,
                    "Erode" to 2,
                    "Fatehold Charm" to 2,
                    "Petrified Hamlet" to 2,
                    "Pinnacle Starcage" to 2,
                    "Restless Anchorage" to 2,
                    "Theorist's Sanctum" to 2,
                    "Day of Judgment" to 1,
                    "Three Steps Ahead" to 1,
                    "Island" to 3,
                    "Plains" to 2
                )
            ),
            ExampleDeckDTO(
                id = "standard_orzhov_lifegain",
                name = "Orzhov Lifegain",
                description = "Lifegain triggers grow Amalia and Hinterland Sanctifier.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Amalia Benavides Aguirre" to 4,
                    "Case of the Uneaten Feast" to 4,
                    "Essence Channeler" to 4,
                    "Godless Shrine" to 4,
                    "Hinterland Sanctifier" to 4,
                    "Starting Town" to 4,
                    "Concealed Courtyard" to 3,
                    "Erode" to 3,
                    "Haliya, Guided by Light" to 3,
                    "Lunar Convocation" to 3,
                    "Moseo, Vein's New Dean" to 3,
                    "Multiversal Passage" to 3,
                    "Emptiness" to 2,
                    "Liliana the Faultless" to 2,
                    "Shattered Sanctum" to 2,
                    "Starscape Cleric" to 2,
                    "Voice of Victory" to 2,
                    "Ajani Resolute" to 1,
                    "Bleachbone Verge" to 1,
                    "Deep-Cavern Bat" to 1,
                    "Plains" to 4,
                    "Swamp" to 1
                )
            ),
            ExampleDeckDTO(
                id = "standard_rakdos_aggro",
                name = "Rakdos Aggro",
                description = "Low-curve Lizards and burn that punish every stumble.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Blazemire Verge" to 4,
                    "Blood Crypt" to 4,
                    "Burst Lightning" to 4,
                    "Fireglass Mentor" to 4,
                    "Flamecache Gecko" to 4,
                    "Hired Claw" to 4,
                    "Iridescent Vinelasher" to 4,
                    "Master of Barbs" to 4,
                    "Stingerquill Charm" to 4,
                    "Gev, Scaled Scorch" to 3,
                    "Haunted Ridge" to 3,
                    "Ingris Stingerquill" to 3,
                    "Valley Flamecaller" to 3,
                    "Cavern of Souls" to 2,
                    "Rockface Village" to 2,
                    "Mountain" to 4,
                    "Swamp" to 4
                )
            ),
            ExampleDeckDTO(
                id = "standard_jeskai_control",
                name = "Jeskai Control",
                description = "Lessons, card draw and Jeskai Revelation behind Great Hall of the Biblioplex.",
                format = DeckFormat.STANDARD,
                cards = mapOf(
                    "Abandon Attachments" to 4,
                    "Accumulate Wisdom" to 4,
                    "Combustion Technique" to 4,
                    "Firebending Lesson" to 4,
                    "Geist of Saint Thalia" to 4,
                    "Great Hall of the Biblioplex" to 4,
                    "Jeskai Revelation" to 4,
                    "Steam Vents" to 4,
                    "Tablet of Discovery" to 4,
                    "Deserted Beach" to 3,
                    "Stormcarved Coast" to 3,
                    "It'll Quench Ya!" to 2,
                    "Riverpyre Verge" to 2,
                    "Spell Snare" to 2,
                    "Spirebluff Canal" to 2,
                    "Chandra, Torch of Defiance" to 1,
                    "Flashback" to 1,
                    "Inspiring Vantage" to 1,
                    "Mistrise Village" to 1,
                    "Ral, Crackling Wit" to 1,
                    "Sacred Foundry" to 1,
                    "Wan Shi Tong, Librarian" to 1,
                    "Winternight Stories" to 1,
                    "Island" to 1,
                    "Mountain" to 1
                )
            ),
            // Bloomburrow Commander preconstructed deck. "Animated Army" is the Gruul
            // (Bello, Bard of the Brambles) deck from the Bloomburrow Commander set.
            // Printings are pinned to BLC where the precon shipped them; the two new BLB
            // cards included in the precon (Teapot Slinger, Wandertale Mentor) and the
            // basic lands fall back to their BLB printings.
            ExampleDeckDTO(
                id = "animated_army",
                name = "Animated Army",
                description = "Bloomburrow Commander precon: Bello, Bard of the Brambles (GR).",
                format = DeckFormat.COMMANDER,
                commander = "Bello, Bard of the Brambles",
                commanderPrinting = PrintingRef(setCode = "BLC", collectorNumber = "1"),
                printings = ANIMATED_ARMY_PRINTINGS,
                cards = mapOf(
                    "Bello, Bard of the Brambles" to 1,
                    "Brightcap Badger" to 1,
                    "Burnished Hart" to 1,
                    "Etali, Primal Storm" to 1,
                    "Evercoat Ursine" to 1,
                    "Garruk's Packleader" to 1,
                    "Ghalta, Primal Hunger" to 1,
                    "Goreclaw, Terror of Qal Sisma" to 1,
                    "Grothama, All-Devouring" to 1,
                    "Grumgully, the Generous" to 1,
                    "Kodama of the East Tree" to 1,
                    "Llanowar Loamspeaker" to 1,
                    "Lotus Cobra" to 1,
                    "Prosperous Bandit" to 1,
                    "Pyreswipe Hawk" to 1,
                    "Rampaging Baloths" to 1,
                    "Sakura-Tribe Elder" to 1,
                    "Teapot Slinger" to 1,
                    "Tendershoot Dryad" to 1,
                    "Trailtracker Scout" to 1,
                    "Wandertale Mentor" to 1,
                    "Wildsear, Scouring Maw" to 1,
                    "Domri, Anarch of Bolas" to 1,
                    "Abrade" to 1,
                    "Beast Within" to 1,
                    "Big Score" to 1,
                    "Chaos Warp" to 1,
                    "Starstorm" to 1,
                    "Blasphemous Act" to 1,
                    "Cultivate" to 1,
                    "Decimate" to 1,
                    "Explore" to 1,
                    "Farseek" to 1,
                    "Harmonize" to 1,
                    "Rampant Growth" to 1,
                    "Arcane Signet" to 1,
                    "Bootleggers' Stash" to 1,
                    "Esika's Chariot" to 1,
                    "Fellwar Stone" to 1,
                    "Gilded Lotus" to 1,
                    "Gruul Signet" to 1,
                    "Hedron Archive" to 1,
                    "Mind Stone" to 1,
                    "Rolling Hamsphere" to 1,
                    "Sol Ring" to 1,
                    "Spine of Ish Sah" to 1,
                    "Talisman of Impulse" to 1,
                    "Thought Vessel" to 1,
                    "Thran Dynamo" to 1,
                    "Alchemist's Talent" to 1,
                    "Berserkers' Onslaught" to 1,
                    "Garruk's Uprising" to 1,
                    "Gratuitous Violence" to 1,
                    "Greater Good" to 1,
                    "Outpost Siege" to 1,
                    "Path of Discovery" to 1,
                    "Primeval Bounty" to 1,
                    "Rain of Riches" to 1,
                    "Sunbird's Invocation" to 1,
                    "Thickest in the Thicket" to 1,
                    "Unnatural Growth" to 1,
                    "Warstorm Surge" to 1,
                    "Cinder Glade" to 1,
                    "Command Tower" to 1,
                    "Copperline Gorge" to 1,
                    "Evolving Wilds" to 1,
                    "Exotic Orchard" to 1,
                    "Forest" to 10,
                    "Forgotten Cave" to 1,
                    "Game Trail" to 1,
                    "Gruul Turf" to 1,
                    "Karplusan Forest" to 1,
                    "Mossfire Valley" to 1,
                    "Mosswort Bridge" to 1,
                    "Mountain" to 8,
                    "Path of Ancestry" to 1,
                    "Raging Ravine" to 1,
                    "Reliquary Tower" to 1,
                    "Rootbound Crag" to 1,
                    "Sheltered Thicket" to 1,
                    "Temple of Abandon" to 1,
                    "Terramorphic Expanse" to 1,
                    "Tranquil Thicket" to 1,
                    "Wooded Ridgeline" to 1
                )
            )
        )
    }
}
