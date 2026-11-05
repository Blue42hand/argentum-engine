package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.nem.cards.StrongholdMachinist
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private data class MachinistBoard(
    val driver: GameTestDriver,
    val player: EntityId,
    val machinist: EntityId,
    val creatureSpell: EntityId,
    val noncreatureSpell: EntityId,
    val discard: EntityId,
)

class StrongholdMachinistScenarioTest : FunSpec({
    val creature = card("Machinist Test Artifact Creature") {
        manaCost = "{0}"
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 1
    }
    val instant = card("Machinist Test Instant") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.DrawCards(0) }
    }

    fun board(): MachinistBoard {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(StrongholdMachinist, creature, instant))
        d.initMirrorMatch(deck = Deck.of("Island" to 40), startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = d.activePlayer!!
        val machinist = d.putCreatureOnBattlefield(player, "Stronghold Machinist")
        d.removeSummoningSickness(machinist)
        val creatureSpell = d.putCardInHand(player, creature.name)
        val noncreatureSpell = d.putCardInHand(player, instant.name)
        val discard = d.putCardInHand(player, "Island")
        d.castSpell(player, creatureSpell).error shouldBe null
        d.castSpell(player, noncreatureSpell).error shouldBe null
        d.giveMana(player, Color.BLUE, 2)
        return MachinistBoard(d, player, machinist, creatureSpell, noncreatureSpell, discard)
    }

    fun activation(b: MachinistBoard, target: EntityId) = ActivateAbility(
        playerId = b.player,
        sourceId = b.machinist,
        abilityId = StrongholdMachinist.activatedAbilities.single().id,
        targets = listOf(ChosenTarget.Spell(target)),
        costPayment = AdditionalCostPayment(discardedCards = listOf(b.discard)),
    )

    test("enumeration offers the noncreature spell and excludes an artifact creature spell") {
        val b = board()
        val offered = b.driver.legalActions(b.player).filter {
            (it.action as? ActivateAbility)?.sourceId == b.machinist
        }
        offered.any { it.affordable } shouldBe true
        offered.flatMap { it.validTargets.orEmpty() }.distinct() shouldContainExactlyInAnyOrder
            listOf(b.noncreatureSpell)
    }

    test("a submitted creature target is rejected before paying tap discard or mana costs") {
        val b = board()
        val before = b.driver.state
        val result = b.driver.submit(activation(b, b.creatureSpell))
        result.error shouldNotBe null
        result.newState shouldBe before
    }

    test("a noncreature spell is countered while the creature spell still resolves") {
        val b = board()
        b.driver.submit(activation(b, b.noncreatureSpell)).error shouldBe null
        while (!b.driver.isPaused && b.driver.state.stack.isNotEmpty()) b.driver.bothPass()
        b.driver.isPaused shouldBe false
        b.driver.getGraveyard(b.player) shouldContain b.noncreatureSpell
        b.driver.getGraveyard(b.player) shouldContain b.discard
        b.driver.getPermanents(b.player) shouldContain b.creatureSpell
    }
})
