package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class RakdosCharmScenarioTest : FunSpec({
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all)
        initMirrorMatch(deck = Deck.of("Swamp" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.cast(mode: Int, targets: List<ChosenTarget> = emptyList(), resolve: Boolean = true) {
        val charm = putCardInHand(player1, "Rakdos Charm")
        giveMana(player1, Color.BLACK, 1)
        giveMana(player1, Color.RED, 1)
        submitSuccess(CastSpell(
            playerId = player1,
            cardId = charm,
            targets = targets,
            paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = listOf(mode),
            modeTargetsOrdered = listOf(targets),
        ))
        if (resolve) bothPass()
    }

    test("graveyard mode can target either player and leaves the other graveyard intact") {
        for (targetOwn in listOf(true, false)) {
            val d = game()
            val own = d.putCardInGraveyard(d.player1, "Grizzly Bears")
            val other = d.putCardInGraveyard(d.player2, "Hill Giant")
            val target = if (targetOwn) d.player1 else d.player2
            d.cast(0, listOf(ChosenTarget.Player(target)))
            d.getExile(target) shouldContain if (targetOwn) own else other
            d.getGraveyard(if (targetOwn) d.player2 else d.player1) shouldContain if (targetOwn) other else own
            d.getLifeTotal(d.player1) shouldBe 20
            d.getLifeTotal(d.player2) shouldBe 20
        }
    }

    test("an empty graveyard is a legal player target") {
        val d = game()
        d.cast(0, listOf(ChosenTarget.Player(d.player2)))
        d.stackSize shouldBe 0
        d.getExile(d.player2).size shouldBe 0
    }

    test("artifact mode destroys only its selected artifact") {
        val d = game()
        val selected = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spared = d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        d.cast(1, listOf(ChosenTarget.Permanent(selected)))
        d.getGraveyard(d.player2) shouldContain selected
        d.findPermanent(d.player1, "Sol Ring") shouldBe spared
        d.getLifeTotal(d.player2) shouldBe 20
    }

    test("artifact mode fizzles when its only target leaves before resolution") {
        val d = game()
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.cast(1, listOf(ChosenTarget.Permanent(artifact)), resolve = false)
        d.replaceState(d.zones.moveToZone(d.state, artifact, Zone.EXILE).state)
        d.bothPass()
        d.getExile(d.player2) shouldContain artifact
        d.getGraveyard(d.player2).size shouldBe 0
        d.stackSize shouldBe 0
    }

    test("damage mode includes the two tokens created by a resolved spell") {
        val d = game()
        val alarm = d.putCardInHand(d.player1, "Raise the Alarm")
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.WHITE, 1)
        d.submitSuccess(CastSpell(d.player1, alarm, paymentStrategy = PaymentStrategy.FromPool))
        d.bothPass()
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 18
        d.getLifeTotal(d.player2) shouldBe 20
        val damage = d.events.filterIsInstance<DamageDealtEvent>().filter { it.targetIsPlayer }
        damage.size shouldBe 2
        damage.map { it.sourceId }.toSet().size shouldBe 2
    }

    test("artifact mode rejects a nonartifact creature target") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val charm = d.putCardInHand(d.player1, "Rakdos Charm")
        d.giveMana(d.player1, Color.BLACK, 1)
        d.giveMana(d.player1, Color.RED, 1)
        val targets = listOf(ChosenTarget.Permanent(creature))
        d.submit(CastSpell(d.player1, charm, targets, paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = listOf(1), modeTargetsOrdered = listOf(targets))).error?.isNotBlank() shouldBe true
        d.stackSize shouldBe 0
    }

    test("damage mode uses each creature as its source and each projected controller as recipient") {
        val d = game()
        val stolen = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putPermanentOnBattlefield(d.player1, "Control Magic")
        d.addComponent(aura, AttachedToComponent(stolen))
        val ours = d.putCreatureOnBattlefield(d.player1, "Hill Giant")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 18
        d.getLifeTotal(d.player2) shouldBe 19
        val damage = d.events.filterIsInstance<DamageDealtEvent>().filter { it.targetIsPlayer }
        damage.map { it.sourceId to it.targetId }.toSet() shouldBe setOf(
            stolen to d.player1, ours to d.player1, theirs to d.player2,
        )
        damage.all { it.amount == 1 && !it.isCombatDamage } shouldBe true
    }

    test("damage mode needs no target even when a creature's controller has hexproof") {
        val d = game()
        d.putCreatureOnBattlefield(d.player2, "Crystal Barricade")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 20
        d.getLifeTotal(d.player2) shouldBe 18
    }

    test("creature-source prevention stops this noncombat damage") {
        val d = game()
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val chant = d.putCardInHand(d.player1, "Chant of Vitu-Ghazi")
        d.giveColorlessMana(d.player1, 6)
        d.giveMana(d.player1, Color.WHITE, 2)
        d.submitSuccess(CastSpell(d.player1, chant, paymentStrategy = PaymentStrategy.FromPool))
        d.bothPass()
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 22
        d.getLifeTotal(d.player2) shouldBe 20
    }

    test("lifelink restores a briefly zero life total before state-based actions") {
        val d = game()
        d.putCreatureOnBattlefield(d.player1, "Vampire Nighthawk")
        d.setLifeTotal(d.player1, 1)
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 1
        d.state.gameOver shouldBe false
    }

    test("both players at one life lose together after every creature deals damage") {
        val d = game()
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        d.setLifeTotal(d.player1, 1)
        d.setLifeTotal(d.player2, 1)
        d.cast(2)
        d.getLifeTotal(d.player1) shouldBe 0
        d.getLifeTotal(d.player2) shouldBe 0
        d.state.gameOver shouldBe true
        d.state.winnerId shouldBe null
    }
})
