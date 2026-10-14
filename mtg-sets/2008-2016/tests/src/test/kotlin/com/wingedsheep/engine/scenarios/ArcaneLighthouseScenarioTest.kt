package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.c14.cards.ArcaneLighthouse
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import io.kotest.matchers.shouldBe

class ArcaneLighthouseScenarioTest : ScenarioTestBase() {
    private val protected = card("Lighthouse Protected Test Creature") {
        manaCost = "{2}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
        keywords(Keyword.HEXPROOF, Keyword.SHROUD)
    }
    private val later = card("Lighthouse Later Test Creature") {
        manaCost = "{2}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
        keywords(Keyword.HEXPROOF)
    }
    private val ward = card("Lighthouse Test Ward") {
        manaCost = "{1}"
        typeLine = "Enchantment"
        staticAbility {
            ability = GrantKeyword(Keyword.HEXPROOF, GroupFilter(GameObjectFilter.Creature.youControl()))
        }
    }
    private val gift = card("Lighthouse Persistent Test Gift") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target(TargetFilter.CreatureYouControl)
            effect = Effects.GrantKeyword(Keyword.HEXPROOF, creature, Duration.Permanent) then
                Effects.ModifyStats(1, 1, creature, Duration.Permanent)
        }
    }

    private fun board() = scenario()
        .withPlayers("Player", "Opponent")
        .withCardOnBattlefield(1, "Arcane Lighthouse")
        .withLandsOnBattlefield(1, "Plains", 1)
        .withCardOnBattlefield(1, protected.name)
        .withCardOnBattlefield(2, protected.name)
        .withCardOnBattlefield(2, "Grizzly Bears")
        .withCardInHand(2, later.name)
        .withCardInHand(2, gift.name)
        .withCardInHand(2, ward.name)
        .withCardInLibrary(1, "Plains")
        .withCardInLibrary(2, "Island")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun strip(game: TestGame) {
        game.execute(ActivateAbility(game.player1Id, game.findPermanent("Arcane Lighthouse")!!,
            ArcaneLighthouse.activatedAbilities[1].id)).error shouldBe null
        game.resolveStack()
    }

    init {
        cardRegistry.register(listOf(protected, later, ward, gift))

        test("only opponents' creatures lose both hexproof and shroud") {
            val game = board()
            val theirs = game.findPermanents(protected.name).first { game.state.projectedState.getController(it) == game.player2Id }
            val ours = game.findPermanents(protected.name).first { game.state.projectedState.getController(it) == game.player1Id }
            strip(game)
            game.state.projectedState.hasKeyword(theirs, Keyword.HEXPROOF) shouldBe false
            game.state.projectedState.hasKeyword(theirs, Keyword.SHROUD) shouldBe false
            game.state.projectedState.hasKeyword(ours, Keyword.HEXPROOF) shouldBe true
            game.state.projectedState.hasKeyword(ours, Keyword.SHROUD) shouldBe true
        }

        test("creatures entering later are not part of the resolution snapshot") {
            val game = board()
            strip(game)
            val id = game.state.getHand(game.player2Id).first { game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == later.name }
            game.state = game.zones.moveToZone(game.state, id, Zone.BATTLEFIELD).state
            game.state.projectedState.hasKeyword(id, Keyword.HEXPROOF) shouldBe true
        }

        test("the restriction survives Lighthouse leaving the battlefield") {
            val game = board()
            val theirs = game.findPermanents(protected.name).first { game.state.projectedState.getController(it) == game.player2Id }
            strip(game)
            game.state = game.zones.moveToZone(game.state, game.findPermanent("Arcane Lighthouse")!!, Zone.GRAVEYARD).state
            game.state.projectedState.hasKeyword(theirs, Keyword.HEXPROOF) shouldBe false
        }

        test("a resolving permanent grant is not stored while prohibited but its stat bonus applies") {
            val game = board()
            val bear = game.findPermanent("Grizzly Bears")!!
            strip(game)
            game.passPriority()
            game.castSpell(2, gift.name, bear).error shouldBe null
            game.resolveStack()
            game.state.projectedState.hasKeyword(bear, Keyword.HEXPROOF) shouldBe false
            game.state.projectedState.getPower(bear) shouldBe 3
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.projectedState.hasKeyword(bear, Keyword.HEXPROOF) shouldBe false
            game.state.projectedState.getPower(bear) shouldBe 3
        }

        test("keyword counters cannot restore hexproof and printed abilities return after cleanup") {
            val game = board()
            val theirs = game.findPermanents(protected.name).first { game.state.projectedState.getController(it) == game.player2Id }
            game.state = game.state.updateEntity(theirs) { it.with(CountersComponent(mapOf(CounterType.HEXPROOF to 1))) }
            strip(game)
            game.state.projectedState.hasKeyword(theirs, Keyword.HEXPROOF) shouldBe false
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.projectedState.hasKeyword(theirs, Keyword.HEXPROOF) shouldBe true
            game.state.projectedState.hasKeyword(theirs, Keyword.SHROUD) shouldBe true
        }

        test("a later static grant cannot apply until the restriction expires") {
            val game = board()
            val bear = game.findPermanent("Grizzly Bears")!!
            strip(game)
            val id = game.state.getHand(game.player2Id).first { game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == ward.name }
            game.state = game.zones.moveToZone(game.state, id, Zone.BATTLEFIELD).state
            game.state.projectedState.hasKeyword(bear, Keyword.HEXPROOF) shouldBe false
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.projectedState.hasKeyword(bear, Keyword.HEXPROOF) shouldBe true
        }

        test("a returning creature is a new object and no longer restricted") {
            val game = board()
            val theirs = game.findPermanents(protected.name).first { game.state.projectedState.getController(it) == game.player2Id }
            strip(game)
            game.state = game.zones.moveToZone(game.state, theirs, Zone.GRAVEYARD).state
            game.state = game.zones.moveToZone(game.state, theirs, Zone.BATTLEFIELD).state
            game.state.projectedState.hasKeyword(theirs, Keyword.HEXPROOF) shouldBe true
        }

        test("an empty opponent board still allows the ability") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Arcane Lighthouse")
                .withLandsOnBattlefield(1, "Plains", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            strip(game)
            game.state.stack.isEmpty() shouldBe true
        }
    }
}
