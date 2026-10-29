package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.mechanics.dungeon.DungeonService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dungeons.Dungeons
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The engine AI answers both questions venturing into the dungeon asks (CR 701.49): which dungeon to
 * enter and which room to take at a fork. Both are plain [ChooseOptionDecision]s, so the AI's
 * option simulation handles them; this pins that it answers legally and the game moves on.
 */
class VentureDecisionsTest : FunSpec({

    val venture = card("Test AI Venture") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.VentureIntoTheDungeon() }
    }
    val registry = CardRegistry().apply { register(TestCards.all + venture) }

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + venture)
        initGame(Deck.of("Plains" to 40), Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.ventureUntilDecision(): ChooseOptionDecision {
        val p1 = player1
        castSpell(p1, putCardInHand(p1, "Test AI Venture"))
        repeat(6) {
            (state.pendingDecision as? ChooseOptionDecision)?.let { return it }
            bothPass()
        }
        return state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
    }

    test("the AI chooses a dungeon to enter") {
        val d = driver()
        val decision = d.ventureUntilDecision()
        decision.options.size shouldBe 3

        val response = AIPlayer.create(registry, d.player1, AiProfile.PRODUCTION).respondToDecision(d.state, decision)
        val chosen = response.shouldBeInstanceOf<OptionChosenResponse>()
        chosen.optionIndex shouldBeIn decision.options.indices.toList()
        d.submit(SubmitDecision(d.player1, chosen)).error shouldBe null

        DungeonService.marker(d.state, d.player1)!!.dungeon shouldBe decision.options[chosen.optionIndex]
    }

    test("the AI chooses a room at a fork") {
        val d = driver()
        var s = DungeonService.enter(d.state, d.player1, Dungeons.TombOfAnnihilation, "setup").first
        s = DungeonService.moveTo(s, d.player1, 0, "setup").first
        d.replaceState(s)

        val decision = d.ventureUntilDecision()
        decision.options shouldBe listOf("Veils of Fear", "Oubliette")

        val response = AIPlayer.create(registry, d.player1, AiProfile.PRODUCTION).respondToDecision(d.state, decision)
        val chosen = response.shouldBeInstanceOf<OptionChosenResponse>()
        d.submit(SubmitDecision(d.player1, chosen)).error shouldBe null

        val room = DungeonService.marker(d.state, d.player1)!!.room
        room shouldNotBe 0
        room shouldBeIn listOf(1, 3)
    }
})
