package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.permanent.attachments.AttachmentMover
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe

class AnimateDeadScenarioTest : ScenarioTestBase() {
    private fun board(body: String = "Craw Wurm") = scenario().withPlayers("Player", "Opponent")
        .withCardInHand(1, "Animate Dead").withCardInHand(1, cycleGraveyard.name).withCardInHand(1, "Disenchant").withCardInHand(1, "Momentary Blink")
        .withCardInGraveyard(2, body).withCardOnBattlefield(1, "Grizzly Bears")
        .withLandsOnBattlefield(1, "Swamp", 2).withLandsOnBattlefield(1, "Plains", 3)
        .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

    private fun cast(game: TestGame) {
        val card = game.state.getHand(game.player1Id).first { game.state.getEntity(it)
            ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Animate Dead" }
        val body = game.state.getGraveyard(game.player2Id).single()
        game.execute(CastSpell(game.player1Id, card,
            targets = listOf(ChosenTarget.Card(body, game.player2Id, Zone.GRAVEYARD)))).error shouldBe null
    }
    private val returnAura = card("Test Return Graveyard Aura") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            val aura = target(TargetFilter(GameObjectFilter.Enchantment, zone = Zone.GRAVEYARD))
            effect = Effects.Move(aura, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD)
        }
    }
    private val cycleGraveyard = card("Test Graveyard Round Trip") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            val body = target(TargetFilter.CreatureInGraveyard)
            effect = Effects.Pipeline {
                val original = gather(CardSource.ReferencedEntity(body))
                val exiled = moveTracked(original, CardDestination.ToZone(Zone.EXILE, Player.TargetOpponent))
                move(exiled, CardDestination.ToZone(Zone.GRAVEYARD, Player.TargetOpponent))
            }
        }
    }
    init {
        cardRegistry.register(cycleGraveyard)
        cardRegistry.register(returnAura)
        test("reanimates an opponent's card under your control, attaches, and gives minus one power") {
            val game = board(); cast(game); game.resolveStack()
            val aura = game.findPermanent("Animate Dead")!!; val body = game.findPermanent("Craw Wurm")!!
            game.state.projectedState.getController(body) shouldBe game.player1Id
            game.state.projectedState.getPower(body) shouldBe 5
            AttachmentMover.canAttach(game.state, game.zones.predicateEvaluator, cardRegistry,
                aura, game.findPermanent("Grizzly Bears")!!) shouldBe false
            game.castSpell(1, "Disenchant", aura).error shouldBe null; game.resolveStack()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isInGraveyard(1, "Animate Dead") shouldBe true
        }
        test("destroying the Aura before its entry trigger resolves does not return the creature") {
            val game = board(); cast(game)
            game.passPriority().error shouldBe null; game.passPriority().error shouldBe null
            val aura = game.findPermanent("Animate Dead")!!
            game.castSpell(1, "Disenchant", aura).error shouldBe null; game.resolveStack()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe false
        }
        test("a shroud creature card can be targeted in a graveyard and attached without retargeting") {
            val game = board("Deadly Insect"); cast(game); game.resolveStack()
            game.isOnBattlefield("Deadly Insect") shouldBe true
            game.isOnBattlefield("Animate Dead") shouldBe true
            game.state.projectedState.getPower(game.findPermanent("Deadly Insect")!!) shouldBe 5
        }
        test("protection from black prevents attachment after return and the delayed trigger sacrifices the creature") {
            val game = board("White Knight")
            val body = game.state.getGraveyard(game.player2Id).single()
            val before = game.state.objectRef(body)
            cast(game); game.resolveStack()
            // It actually returned and died; remaining in the original graveyard is not enough.
            game.state.objectRef(body) shouldNotBe before
            game.isOnBattlefield("White Knight") shouldBe false
            game.isInGraveyard(2, "White Knight") shouldBe true
            game.isInGraveyard(1, "Animate Dead") shouldBe true
        }
        test("blinking the returned creature leaves its new object alive when the Aura dies") {
            val game = board(); cast(game); game.resolveStack()
            val body = game.findPermanent("Craw Wurm")!!
            game.castSpell(1, "Momentary Blink", body).error shouldBe null; game.resolveStack()
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.isInGraveyard(1, "Animate Dead") shouldBe true
            game.state.projectedState.getPower(body) shouldBe 6
        }
        test("a noncast Aura chooses a graveyard card and survives its entry continuation") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, returnAura.name).withCardInGraveyard(1, "Animate Dead")
                .withCardInGraveyard(2, "Deadly Insect")
                .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val body = game.state.getGraveyard(game.player2Id).single()
            game.castSpellTargetingGraveyardCard(1, returnAura.name, 1, "Animate Dead").error shouldBe null
            game.resolveStack()
            (game.getPendingDecision() is ChooseTargetsDecision) shouldBe true
            game.selectTargets(listOf(body)).error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Animate Dead") shouldBe true
            game.isOnBattlefield("Deadly Insect") shouldBe true
            game.state.projectedState.getController(body) shouldBe game.player1Id
        }
        test("a graveyard entry prohibition prevents return and leaves the Aura unattached") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Animate Dead").withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(2, "Soulless Jailer").withCardInGraveyard(2, "Craw Wurm")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            cast(game); game.resolveStack()
            game.isOnBattlefield("Craw Wurm") shouldBe false
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isInGraveyard(1, "Animate Dead") shouldBe true
        }
        test("a graveyard host that leaves and returns is a new object and the Aura cannot follow it") {
            val game = board(); cast(game)
            game.passPriority().error shouldBe null; game.passPriority().error shouldBe null
            game.isOnBattlefield("Animate Dead") shouldBe true
            game.castSpellTargetingGraveyardCard(1, cycleGraveyard.name, 2, "Craw Wurm").error shouldBe null
            game.resolveStack()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe false
            game.isInGraveyard(1, "Animate Dead") shouldBe true
        }
        test("a creature's as-enters copy choice resumes the pipeline before reattachment") {
            val game = board("Clone")
            val clone = game.state.getGraveyard(game.player2Id).single()
            val bear = game.findPermanent("Grizzly Bears")!!
            cast(game); game.resolveStack()
            (game.getPendingDecision() is SelectCardsDecision) shouldBe true
            game.selectCards(listOf(bear)).error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Animate Dead") shouldBe true
            game.state.getBattlefield().contains(clone) shouldBe true
            game.state.projectedState.getPower(clone) shouldBe 1
            game.state.getEntity(game.findPermanent("Animate Dead")!!)!!
                .get<com.wingedsheep.engine.state.components.battlefield.AttachedToComponent>()!!.targetId shouldBe clone
        }
    }
}
