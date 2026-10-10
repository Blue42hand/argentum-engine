package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.RedirectZoneChange
import io.kotest.matchers.shouldBe

class AsmodeusTheArchfiendScenarioTest : ScenarioTestBase() {
    private val source = "Asmodeus the Archfiend"
    private val library = listOf("Ancestral Recall", "Dark Ritual", "Lightning Bolt", "Forest", "Mountain", "Plains", "Swamp")

    private fun board(count: Int = 7, active: Int = 1): ScenarioBuilder {
        val builder = scenario().withPlayers("Pilot", "Opponent")
            .withCardOnBattlefield(1, source)
            .withCardInHand(1, "Unsummon").withCardOnBattlefield(1, "Island")
            .withCardInHand(2, "Control Magic")
            .withActivePlayer(active).withPriorityPlayer(active)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(16) { builder.withCardOnBattlefield(1, "Swamp") }
        repeat(8) { builder.withCardOnBattlefield(2, "Swamp") }
        repeat(4) { builder.withCardOnBattlefield(2, "Island") }
        repeat(count) { builder.withCardInLibrary(1, library[it % library.size]) }
        library.forEach { builder.withCardInLibrary(2, it) }
        return builder
    }

    private fun pay(game: TestGame) {
        if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay().error shouldBe null
    }

    private fun activate(game: TestGame, index: Int, player: Int = 1, entity: EntityId = game.findPermanent(source)!!) {
        val who = if (player == 1) game.player1Id else game.player2Id
        if (game.state.priorityPlayerId != who) game.passPriority().error shouldBe null
        val ability = cardRegistry.getCard(source)!!.activatedAbilities[index].id
        game.execute(ActivateAbility(who, entity, ability)).error shouldBe null
        pay(game)
    }

    private fun resolve(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
        game.getPendingDecision() shouldBe null
        game.state.stack.size shouldBe 0
    }

    private fun bounce(game: TestGame) {
        game.castSpell(1, "Unsummon", game.findPermanent(source)!!).error shouldBe null
        pay(game)
        resolve(game)
    }

    init {
        // Test-only redirect exercises actual-return accounting; it is not corpus content.
        cardRegistry.register(listOf(card("Return redirect fixture") {
            typeLine = "Artifact"
            replacementEffect(RedirectZoneChange(Zone.GRAVEYARD,
                EventPattern.ZoneChangeEvent(from = Zone.EXILE, to = Zone.HAND)))
        }))

        test("seven separate draws become linked face-down exile hidden from both seats and do not trigger draw abilities") {
            val game = board().withCardOnBattlefield(1, "Teferi, Temporal Pilgrim").build()
            val hand = game.state.getHand(game.player1Id).toList()
            activate(game, 0)
            resolve(game)
            val pile = game.state.getExile(game.player1Id)
            pile.size shouldBe 7
            game.state.getHand(game.player1Id) shouldBe hand
            game.state.getLibrary(game.player1Id).size shouldBe 0
            game.getLifeTotal(2) shouldBe 20
            game.state.getEntity(game.findPermanent("Teferi, Temporal Pilgrim")!!)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY) shouldBe 4
            game.state.getEntity(game.findPermanent(source)!!)!!.get<LinkedExileComponent>()!!.exiledIds.toSet() shouldBe pile.toSet()
            val visibility = Visibility(cardRegistry, conditionEvaluator = services.conditionEvaluator)
            pile.forEach { id ->
                game.state.getEntity(id)!!.has<FaceDownComponent>() shouldBe true
                listOf(game.player1Id, game.player2Id).forEach { viewer ->
                    visibility.isCardIdentityVisibleTo(game.state, ZoneKey(game.player1Id, Zone.EXILE), id, viewer) shouldBe false
                }
                game.getLegalActions(1).any { it.description.contains("Ancestral Recall") } shouldBe false
            }
        }

        test("returning the linked seven cards loses seven life and a repeated activation returns nothing") {
            val game = board().build()
            activate(game, 0)
            resolve(game)
            val pile = game.state.getExile(game.player1Id).toList()
            activate(game, 1)
            resolve(game)
            pile.all { it in game.state.getHand(game.player1Id) } shouldBe true
            pile.forEach { game.state.getEntity(it)!!.has<FaceDownComponent>() shouldBe false }
            game.state.getExile(game.player1Id).size shouldBe 0
            game.getLifeTotal(1) shouldBe 13
            activate(game, 1)
            resolve(game)
            game.getLifeTotal(1) shouldBe 13
        }

        test("an empty library replaces all seven draws without a failed-draw loss") {
            val game = board(count = 0).build()
            activate(game, 0)
            resolve(game)
            game.state.gameOver shouldBe false
            game.state.getExile(game.player1Id).size shouldBe 0
            activate(game, 1)
            resolve(game)
            game.getLifeTotal(1) shouldBe 20
        }

        test("a short library exiles only its two cards and the return loses only two life") {
            val game = board(count = 2).build()
            activate(game, 0)
            resolve(game)
            game.state.getExile(game.player1Id).size shouldBe 2
            game.state.gameOver shouldBe false
            activate(game, 1)
            resolve(game)
            game.getLifeTotal(1) shouldBe 18
        }

        test("a return ability activated before its source leaves still retrieves the original linked pile") {
            val game = board().build()
            activate(game, 0)
            resolve(game)
            val pile = game.state.getExile(game.player1Id).toList()
            activate(game, 1)
            bounce(game)
            pile.all { it in game.state.getHand(game.player1Id) } shouldBe true
            game.isInHand(1, source) shouldBe true
            game.getLifeTotal(1) shouldBe 13
        }

        test("leaving before the draw ability resolves removes the replacement so the seven cards are drawn normally") {
            val game = board().build()
            activate(game, 0)
            bounce(game)
            game.state.getExile(game.player1Id).size shouldBe 0
            library.all { game.isInHand(1, it) } shouldBe true
            game.getLifeTotal(1) shouldBe 20
        }

        test("a returned and recast source cannot retrieve its previous battlefield visit's face-down cards") {
            val game = board().build()
            activate(game, 0)
            resolve(game)
            val oldPile = game.state.getExile(game.player1Id).toList()
            bounce(game)
            game.castSpell(1, source).error shouldBe null
            pay(game)
            resolve(game)
            activate(game, 1)
            resolve(game)
            game.state.getExile(game.player1Id) shouldBe oldPile
            game.getLifeTotal(1) shouldBe 20
        }

        test("a control change keeps the same pile but returns each owner's cards to their hand and charges the new controller") {
            val game = board(active = 2).build()
            activate(game, 0)
            resolve(game)
            val ownedByFirst = game.state.getExile(game.player1Id).toList()
            if (game.state.priorityPlayerId != game.player2Id) game.passPriority().error shouldBe null
            game.castSpell(2, "Control Magic", game.findPermanent(source)!!).error shouldBe null
            pay(game)
            resolve(game)
            activate(game, 0, player = 2)
            resolve(game)
            val ownedBySecond = game.state.getExile(game.player2Id).toList()
            ownedBySecond.size shouldBe 7
            activate(game, 1, player = 2)
            resolve(game)
            ownedByFirst.all { it in game.state.getHand(game.player1Id) } shouldBe true
            ownedBySecond.all { it in game.state.getHand(game.player2Id) } shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 6
        }

        test("two players' copies keep separate linked piles and only replace their controller's draws") {
            val game = board(active = 2).withCardOnBattlefield(2, source).build()
            val first = game.findAllPermanents(source).single { game.state.projectedState.getController(it) == game.player1Id }
            val second = game.findAllPermanents(source).single { game.state.projectedState.getController(it) == game.player2Id }
            activate(game, 0, entity = first)
            resolve(game)
            activate(game, 0, player = 2, entity = second)
            resolve(game)
            activate(game, 1, entity = first)
            resolve(game)
            game.state.getExile(game.player1Id).size shouldBe 0
            game.state.getExile(game.player2Id).size shouldBe 7
            game.getLifeTotal(1) shouldBe 13
            game.getLifeTotal(2) shouldBe 20
        }

        test("cards redirected away from hand by a replacement do not count toward return life loss") {
            val game = board().withCardOnBattlefield(1, "Return redirect fixture").build()
            activate(game, 0)
            resolve(game)
            val pile = game.state.getExile(game.player1Id).toList()
            activate(game, 1)
            resolve(game)
            pile.all { it in game.state.getGraveyard(game.player1Id) } shouldBe true
            pile.none { it in game.state.getHand(game.player1Id) } shouldBe true
            game.getLifeTotal(1) shouldBe 20
        }
    }
}
