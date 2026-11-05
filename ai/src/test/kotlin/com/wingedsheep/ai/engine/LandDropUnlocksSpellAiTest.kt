package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lrw.cards.BoggartHarbinger
import com.wingedsheep.mtg.sets.definitions.lrw.cards.BoggartSpriteChaser
import com.wingedsheep.mtg.sets.definitions.lrw.cards.HoardersGreed
import com.wingedsheep.mtg.sets.definitions.lrw.cards.MudbuttonTorchrunner
import com.wingedsheep.mtg.sets.definitions.lrw.cards.Shriekmaw
import com.wingedsheep.mtg.sets.definitions.vow.cards.MindleechGhoul
import com.wingedsheep.mtg.sets.definitions.vow.cards.MischievousCatgeist
import com.wingedsheep.mtg.sets.definitions.vow.cards.RaggedRecluse
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * A land drop is scored by the spell it makes castable — [AiProfile.landDropUnlocksSpells].
 *
 * 2026-10-12 AI-vs-AI logs (VOW vs LRW, game 1 turn 10): on four lands with a Mountain in hand,
 * the AI cast Boggart Sprite-Chaser first, then played the Mountain, then had only enough left to
 * *evoke* Shriekmaw for the kill. Land first makes Shriekmaw's hard cast affordable: the same kill
 * plus a 3/2 fear body. The one-ply search scored the land as a land, so rollout noise decided
 * the order — the reconstruction below went Sprite-Chaser-first in one run of five.
 */
class LandDropUnlocksSpellAiTest : FunSpec({

    val cards = TestCards.all + listOf(
        Shriekmaw, BoggartSpriteChaser, HoardersGreed, BoggartHarbinger, MudbuttonTorchrunner,
        MindleechGhoul, RaggedRecluse, MischievousCatgeist,
    )
    val registry = CardRegistry().apply { register(cards) }

    fun GameTestDriver.nameOf(action: GameAction): String? = (action as? CastSpell)
        ?.let { state.getEntity(it.cardId)?.get<CardComponent>()?.name }

    // Each run deals a fresh shuffle, and the rollouts' seeds follow the position, so several runs
    // cover several rollout draws of the same board.
    for (run in 1..4) {
        test("plays the land first, then hard-casts Shriekmaw rather than evoking it (run $run)") {
            val d = GameTestDriver()
            d.registerCards(cards)
            d.initMirrorMatch(deck = Deck.of("Swamp" to 20, "Mountain" to 20), skipMulligans = true, startingPlayer = 0)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val me = d.player1
            val opp = d.player2
            listOf(me, opp).forEach { p -> d.state.getZone(p, Zone.HAND).forEach { d.moveToGraveyard(it) } }
            listOf("Swamp", "Swamp", "Mountain", "Mountain").forEach { d.putLandOnBattlefield(me, it) }
            listOf("Boggart Harbinger", "Mudbutton Torchrunner")
                .forEach { d.removeSummoningSickness(d.putCreatureOnBattlefield(me, it)) }
            listOf("Mindleech Ghoul", "Ragged Recluse", "Mischievous Catgeist")
                .forEach { d.removeSummoningSickness(d.putCreatureOnBattlefield(opp, it)) }
            repeat(5) { d.putLandOnBattlefield(opp, "Swamp") }
            listOf("Hoarder's Greed", "Shriekmaw", "Mountain", "Mountain", "Boggart Sprite-Chaser")
                .forEach { d.putCardInHand(me, it) }

            val ai = AIPlayer.create(registry, me, AiProfile.LIVE)

            val first = ai.chooseAction(d.state)
            first.shouldBeInstanceOf<PlayLand>()
            d.submit(first)

            val second = ai.chooseAction(d.state)
            d.nameOf(second) shouldBe "Shriekmaw"
            (second as CastSpell).useAlternativeCost shouldBe false
            d.submit(second)
            ai.chooseAction(d.state).shouldBeInstanceOf<PassPriority>()
        }
    }
})
