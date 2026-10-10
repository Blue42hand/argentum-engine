package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ResourcefulDefenseScenarioTest : ScenarioTestBase() {
    private fun game() = scenario()
        .withPlayers("Defense pilot", "Opponent")
        .withCardOnBattlefield(1, "Resourceful Defense")
        .withCardOnBattlefield(1, "Sol Ring")
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withCardOnBattlefield(1, "Forest")
        .withCardOnBattlefield(2, "Centaur Courser")
        .withCardInHand(1, "Disenchant")
        .withCardInHand(1, "Unsummon")
        .withLandsOnBattlefield(1, "Plains", 6)
        .withLandsOnBattlefield(1, "Island", 1)
        .withCardInLibrary(1, "Mountain")
        .withCardInLibrary(2, "Swamp")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun TestGame.setCounters(id: EntityId) {
        state = state.updateEntity(id) { it.with(CountersComponent(mapOf(
            CounterType.PLUS_ONE_PLUS_ONE to 3,
            CounterType.CHARGE to 2
        ))) }
    }

    private fun TestGame.count(id: EntityId, kind: CounterType) =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(kind) ?: 0

    private fun TestGame.activate(from: EntityId, onto: EntityId) = execute(ActivateAbility(
        playerId = player1Id,
        sourceId = findPermanent("Resourceful Defense")!!,
        abilityId = cardRegistry.getCard("Resourceful Defense")!!.script.activatedAbilities[0].id,
        targets = listOf(entityIdToChosenTarget(state, from), entityIdToChosenTarget(state, onto))
    ))

    init {
        test("a bounced creature's last-known counters are put on a controlled land") {
            val game = game()
            val bear = game.findPermanent("Grizzly Bears")!!
            val land = game.findPermanent("Forest")!!
            game.setCounters(bear)
            game.castSpell(1, "Unsummon", bear).error shouldBe null
            game.resolveStack()
            game.findPermanent("Grizzly Bears") shouldBe null
            game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
            game.hasPendingDecision() shouldBe true
            game.selectTargets(listOf(land)).error shouldBe null
            game.resolveStack()
            game.count(land, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.count(land, CounterType.CHARGE) shouldBe 2
        }

        test("the enchantment's own departure still transfers all its counter kinds") {
            val game = game()
            val defense = game.findPermanent("Resourceful Defense")!!
            val ring = game.findPermanent("Sol Ring")!!
            game.setCounters(defense)
            game.castSpell(1, "Disenchant", defense).error shouldBe null
            game.resolveStack()
            game.findPermanent("Resourceful Defense") shouldBe null
            game.hasPendingDecision() shouldBe true
            game.selectTargets(listOf(ring)).error shouldBe null
            game.resolveStack()
            game.count(ring, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.count(ring, CounterType.CHARGE) shouldBe 2
        }

        test("counterless controlled departures and opposing departures do not trigger") {
            for (name in listOf("Grizzly Bears", "Centaur Courser")) {
                val game = game()
                val creature = game.findPermanent(name)!!
                if (name == "Centaur Courser") game.setCounters(creature)
                game.castSpell(1, "Unsummon", creature).error shouldBe null
                game.resolveStack()
                game.hasPendingDecision() shouldBe false
                game.state.stack.size shouldBe 0
            }
        }

        test("activation chooses each kind at resolution and can move only part of the counters") {
            val game = game()
            val ring = game.findPermanent("Sol Ring")!!
            val land = game.findPermanent("Forest")!!
            game.setCounters(ring)
            game.activate(ring, land).error shouldBe null
            game.count(land, CounterType.CHARGE) shouldBe 0
            game.resolveStack()
            repeat(2) {
                val decision = game.getPendingDecision() as ChooseNumberDecision
                decision.minValue shouldBe 0
                val amount = if (decision.prompt.contains("+1/+1")) 1 else 2
                game.chooseNumber(amount).error shouldBe null
            }
            game.count(ring, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            game.count(ring, CounterType.CHARGE) shouldBe 0
            game.count(land, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            game.count(land, CounterType.CHARGE) shouldBe 2
        }

        test("zero counters may move and the two controlled targets must be distinct") {
            val game = game()
            val ring = game.findPermanent("Sol Ring")!!
            val defense = game.findPermanent("Resourceful Defense")!!
            val opponent = game.findPermanent("Centaur Courser")!!
            game.setCounters(ring)
            val before = game.state
            game.activate(ring, ring).error shouldNotBe null
            game.state shouldBe before
            game.activate(ring, opponent).error shouldNotBe null
            game.state shouldBe before
            game.activate(ring, defense).error shouldBe null
            game.resolveStack()
            repeat(2) { game.chooseNumber(0).error shouldBe null }
            game.count(ring, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.count(ring, CounterType.CHARGE) shouldBe 2
            game.count(defense, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            game.count(defense, CounterType.CHARGE) shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a removed transfer source does not supply counters from its new graveyard object") {
            val game = game()
            val ring = game.findPermanent("Sol Ring")!!
            val defense = game.findPermanent("Resourceful Defense")!!
            val land = game.findPermanent("Forest")!!
            game.setCounters(ring)
            game.activate(ring, defense).error shouldBe null
            game.castSpell(1, "Disenchant", ring).error shouldBe null
            game.resolveStack()
            game.selectTargets(listOf(land)).error shouldBe null
            game.resolveStack()
            game.findPermanent("Sol Ring") shouldBe null
            game.count(land, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.count(land, CounterType.CHARGE) shouldBe 2
            game.count(defense, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            game.count(defense, CounterType.CHARGE) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("a removed transfer destination leaves every counter on the first target") {
            val game = game()
            val ring = game.findPermanent("Sol Ring")!!
            val defense = game.findPermanent("Resourceful Defense")!!
            game.setCounters(ring)
            game.activate(ring, defense).error shouldBe null
            game.castSpell(1, "Disenchant", defense).error shouldBe null
            game.resolveStack()
            game.findPermanent("Resourceful Defense") shouldBe null
            game.count(ring, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.count(ring, CounterType.CHARGE) shouldBe 2
            game.count(defense, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("a removed trigger recipient receives no last-known counters") {
            val game = game()
            val ring = game.findPermanent("Sol Ring")!!
            val bear = game.findPermanent("Grizzly Bears")!!
            game.setCounters(ring)
            game.castSpell(1, "Disenchant", ring).error shouldBe null
            game.resolveStack()
            game.selectTargets(listOf(bear)).error shouldBe null
            game.castSpell(1, "Unsummon", bear).error shouldBe null
            game.resolveStack()
            game.findPermanent("Grizzly Bears") shouldBe null
            game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
            game.count(bear, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            game.count(bear, CounterType.CHARGE) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }
    }
}
