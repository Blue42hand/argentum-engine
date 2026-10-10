package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ReconnaissanceScenarioTest : FunSpec({
    val attackWitness = card("Reconnaissance attack witness") {
        typeLine = "Creature — Soldier"; power = 2; toughness = 2
        triggeredAbility { trigger = Triggers.self.attacks(); effect = Effects.GainLife(1) }
    }
    val blockWitness = card("Reconnaissance block witness") {
        typeLine = "Creature — Soldier"; power = 3; toughness = 3
        triggeredAbility { trigger = Triggers.self.blocks(); effect = Effects.GainLife(1) }
    }

    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(attackWitness, blockWitness))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        putPermanentOnBattlefield(player1, "Reconnaissance")
    }
    fun attacker(d: GameTestDriver, name: String = "Grizzly Bears", player: EntityId = d.player1) =
        d.putCreatureOnBattlefield(player, name).also(d::removeSummoningSickness)

    fun attack(d: GameTestDriver, attackers: List<EntityId>, player: EntityId = d.player1) {
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.submitSuccess(com.wingedsheep.engine.core.DeclareAttackers(
            player, attackers.associateWith { d.getOpponent(player) },
        ))
    }
    fun ability(d: GameTestDriver, target: EntityId) = ActivateAbility(
        d.player1,
        d.findPermanent(d.player1, "Reconnaissance")!!,
        d.cardRegistry.requireCard("Reconnaissance").activatedAbilities.single().id,
        targets = listOf(ChosenTarget.Permanent(target)),
    )
    fun remove(d: GameTestDriver, target: EntityId) {
        d.submitSuccess(ability(d, target))
        d.bothPass().error shouldBe null
        d.isTapped(target) shouldBe false
        d.state.getEntity(target)!!.has<AttackingComponent>() shouldBe false
    }

    test("zero-cost activation removes a blocked attacker and prevents combat damage in both directions") {
        val d = game()
        val a = attacker(d)
        val b = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        attack(d, listOf(a))
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.declareBlockers(d.player2, mapOf(b to listOf(a))).error shouldBe null
        if (d.priorityPlayer != d.player1) d.passPriority(d.priorityPlayer!!).error shouldBe null
        remove(d, a)
        d.state.getEntity(b)!!.get<BlockingComponent>()!!.blockedAttackerIds shouldBe emptyList()
        d.passPriorityUntil(Step.END_COMBAT)
        d.findPermanent(d.player1, "Grizzly Bears") shouldBe a
        d.findPermanent(d.player2, "Hill Giant") shouldBe b
        d.getLifeTotal(d.player1) shouldBe 20
        d.getLifeTotal(d.player2) shouldBe 20
    }

    test("end-of-combat activation untaps an attacker after its combat damage has already happened") {
        val d = game()
        val a = attacker(d)
        attack(d, listOf(a))
        d.passPriorityUntil(Step.END_COMBAT)
        d.getLifeTotal(d.player2) shouldBe 18
        d.isTapped(a) shouldBe true
        d.state.getEntity(a)!!.has<AttackingComponent>() shouldBe true
        remove(d, a)
        d.getLifeTotal(d.player2) shouldBe 18
    }

    test("distinct attackers can each be removed with separate free activations") {
        val d = game()
        val a = attacker(d)
        val b = attacker(d, "Hill Giant")
        attack(d, listOf(a, b))
        remove(d, a)
        remove(d, b)
        d.stackSize shouldBe 0
    }

    test("a creature that is not attacking is not a legal target") {
        val d = game()
        val a = attacker(d)
        d.submit(ability(d, a)).error shouldNotBe null
        d.stackSize shouldBe 0
    }

    test("an opponent's attacking creature is not a legal target") {
        val d = game()
        val a = attacker(d, player = d.player2)
        d.passPriorityUntil(Step.UPKEEP)
        d.activePlayer shouldBe d.player2
        attack(d, listOf(a), d.player2)
        d.passPriority(d.player2).error shouldBe null
        d.priorityPlayer shouldBe d.player1
        d.submit(ability(d, a)).error shouldNotBe null
        d.isTapped(a) shouldBe true
    }

    test("a target leaving in response makes the ability fizzle") {
        val d = game()
        val a = attacker(d)
        attack(d, listOf(a))
        d.submitSuccess(ability(d, a))
        d.replaceState(d.zones.moveToZone(d.state, a, Zone.EXILE).state)
        d.bothPass().error shouldBe null
        d.getExile(d.player1) shouldContain a
        d.stackSize shouldBe 0
    }

    test("losing control of the target in response does not untap it") {
        val d = game()
        val a = attacker(d)
        attack(d, listOf(a))
        d.submitSuccess(ability(d, a))
        val aura = d.putPermanentOnBattlefield(d.player2, "Control Magic")
        d.addComponent(aura, AttachedToComponent(a))
        d.bothPass().error shouldBe null
        d.state.projectedState.getController(a) shouldBe d.player2
        d.isTapped(a) shouldBe true
        d.stackSize shouldBe 0
    }

    test("the ability resolves after Reconnaissance leaves the battlefield") {
        val d = game()
        val a = attacker(d)
        attack(d, listOf(a))
        val source = d.findPermanent(d.player1, "Reconnaissance")!!
        d.submitSuccess(ability(d, a))
        d.replaceState(d.zones.moveToZone(d.state, source, Zone.GRAVEYARD).state)
        d.bothPass().error shouldBe null
        d.getGraveyard(d.player1) shouldContain source
        d.isTapped(a) shouldBe false
        d.state.getEntity(a)!!.has<AttackingComponent>() shouldBe false
    }

    test("removal does not undo already resolved attack or block triggers") {
        val d = game()
        val a = attacker(d, attackWitness.name)
        val b = d.putCreatureOnBattlefield(d.player2, blockWitness.name)
        attack(d, listOf(a))
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 21
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.declareBlockers(d.player2, mapOf(b to listOf(a))).error shouldBe null
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player2) shouldBe 21
        if (d.priorityPlayer != d.player1) d.passPriority(d.priorityPlayer!!).error shouldBe null
        remove(d, a)
        d.getLifeTotal(d.player1) shouldBe 21
        d.getLifeTotal(d.player2) shouldBe 21
    }
})
