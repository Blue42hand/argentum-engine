package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FrozenAttachmentDepartureFlagsTest : FunSpec({
    val spirit = card("Frozen Attachment Bestow Spirit") {
        manaCost = "{W}"
        typeLine = "Enchantment Creature — Spirit"
        power = 2
        toughness = 3
        keywordAbility(KeywordAbility.bestow("{0}"))
    }

    for (ownAura in listOf(true, false)) {
        for (auraLeavesFirst in listOf(true, false)) {
            test("batch departure preserves enchanted and controller-sensitive modified flags: ownAura=$ownAura auraFirst=$auraLeavesFirst") {
                val game = GameTestDriver()
                game.registerCards(TestCards.all + spirit)
                game.initMirrorMatch(deck = Deck.of("Plains" to 40))
                game.passPriorityUntil(Step.PRECOMBAT_MAIN)
                val player = game.activePlayer!!
                val opponent = game.state.turnOrder.single { it != player }
                val hostController = if (ownAura) player else opponent
                val host = game.putCreatureOnBattlefield(hostController, "Grizzly Bears")
                val bestowed = game.putCardInHand(player, spirit.name)
                game.submit(CastSpell(
                    playerId = player,
                    cardId = bestowed,
                    targets = listOf(ChosenTarget.Permanent(host)),
                    useAlternativeCost = true,
                    alternativeCostType = AlternativeCostType.BESTOW,
                    paymentStrategy = PaymentStrategy.FromPool
                )).error shouldBe null
                game.bothPass()
                game.state.getEntity(bestowed)?.get<AttachedToComponent>()?.targetId shouldBe host
                game.state.projectedState.hasSubtype(bestowed, "Aura") shouldBe true

                // Exercise the real simultaneous departure API: it freezes both objects before
                // moving either, even though the per-object cleanup runs in list order.
                val departures = if (auraLeavesFirst) listOf(bestowed, host) else listOf(host, bestowed)
                val result = game.zones.moveToZoneBatch(game.state, departures, Zone.GRAVEYARD)
                val snapshot = result.events.filterIsInstance<ZoneChangeEvent>()
                    .single { it.entityId == host }.lastKnown.shouldNotBeNull()
                snapshot.wasEnchanted shouldBe true
                snapshot.wasEquipped shouldBe false
                snapshot.wasModified shouldBe ownAura
                snapshot.controllerId shouldBe hostController
                snapshot.attachmentIds shouldBe listOf(bestowed)
                snapshot.attachments.single().hasSubtype(Subtype.AURA) shouldBe true
                snapshot.attachments.single().controllerId shouldBe player
                // The live card has reverted; the departure flags must not consult it.
                result.state.getEntity(bestowed)?.get<CardComponent>()?.typeLine?.isAura shouldBe false
                result.state.getEntity(bestowed)?.get<AttachedToComponent>() shouldBe null
            }
        }
    }
})
