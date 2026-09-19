package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dst.cards.Skullclamp
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Skullclamp (DST #140) — "Equipped creature gets +1/-1. Whenever equipped creature dies,
 * draw two cards. Equip {1}."
 *
 * The load-bearing interaction is the attached-object leave binding: when the equipped creature
 * leaves the battlefield, Skullclamp must still recognize that it was attached to that creature
 * and queue the draw trigger. The final test also covers the characteristic Skullclamp case where
 * its own -1 toughness modifier is what causes a 1-toughness creature to die.
 */
class SkullclampScenarioTest : FunSpec({

    fun GameTestDriver.putEquipmentAttached(
        playerId: EntityId,
        host: EntityId
    ): EntityId {
        val equipmentId = putPermanentOnBattlefield(playerId, "Skullclamp")
        var newState = state.updateEntity(equipmentId) { c -> c.with(AttachedToComponent(host)) }
        val existing = newState.getEntity(host)?.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
        newState = newState.updateEntity(host) { c -> c.with(AttachmentsComponent(existing + equipmentId)) }
        replaceState(newState)
        return equipmentId
    }

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(Skullclamp))
        d.initMirrorMatch(
            deck = Deck.of("Swamp" to 20, "Grizzly Bears" to 20),
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("equipped creature gets +1/-1") {
        val d = driver()
        val me = d.activePlayer!!
        val host = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.putEquipmentAttached(me, host)

        d.state.projectedState.getPower(host) shouldBe 3
        d.state.projectedState.getToughness(host) shouldBe 1
    }

    test("when the equipped creature dies Skullclamp draws two cards") {
        val d = driver()
        val me = d.activePlayer!!
        val host = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.putEquipmentAttached(me, host)
        val handBefore = d.getHand(me).size

        d.giveMana(me, Color.BLACK, 2)
        val doomBlade = d.putCardInHand(me, "Doom Blade")
        d.castSpellWithTargets(me, doomBlade, listOf(ChosenTarget.Permanent(host))).isSuccess shouldBe true
        d.bothPass() // resolve Doom Blade; Skullclamp trigger is queued
        d.bothPass() // resolve Skullclamp draw trigger

        d.getGraveyard(me) shouldContain host
        d.getHand(me).size shouldBe handBefore + 2
    }

    test("the -1 toughness can kill the equipped creature and still trigger the draw") {
        val d = driver()
        val me = d.activePlayer!!
        val host = d.putCreatureOnBattlefield(me, "Savannah Lions") // 2/1
        val handBefore = d.getHand(me).size
        d.putEquipmentAttached(me, host)

        d.state.projectedState.getPower(host) shouldBe 3
        d.state.projectedState.getToughness(host) shouldBe 0

        // The next engine action runs state-based actions, moving the 0-toughness creature to the
        // graveyard and queueing Skullclamp's leaves-the-battlefield trigger.
        d.passPriority(d.priorityPlayer!!).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()

        d.getGraveyard(me) shouldContain host
        d.getHand(me).size shouldBe handBefore + 2
    }
})
