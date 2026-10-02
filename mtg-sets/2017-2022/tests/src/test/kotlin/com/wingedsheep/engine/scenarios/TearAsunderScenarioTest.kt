package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dmu.cards.TearAsunder
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kicker changes Tear Asunder's legal target when it is cast. */
class TearAsunderScenarioTest : FunSpec({
    fun game() = GameTestDriver().also {
        it.registerCards(TestCards.all + TearAsunder)
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun cast(game: GameTestDriver, you: EntityId, card: EntityId, target: EntityId, kicked: Boolean) =
        game.submit(
            CastSpell(
                playerId = you,
                cardId = card,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = if (kicked) ChoiceSlot.KICKED else null,
                paymentStrategy = PaymentStrategy.AutoPay,
            )
        )

    test("unkicked cast exiles an enchantment but cannot target an ordinary creature") {
        val game = game()
        val you = game.activePlayer!!
        val opponent = game.getOpponent(you)
        val creature = game.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val enchantment = game.putPermanentOnBattlefield(opponent, "Test Enchantment")
        game.giveMana(you, Color.GREEN, 2)
        val card = game.putCardInHand(you, "Tear Asunder")

        cast(game, you, card, creature, kicked = false).isSuccess shouldBe false
        cast(game, you, card, enchantment, kicked = false).isSuccess shouldBe true
        game.bothPass()

        game.state.getExile(opponent).contains(enchantment) shouldBe true
        game.state.getBattlefield().contains(creature) shouldBe true
    }

    test("kicked cast exiles an ordinary creature but cannot target a land") {
        val game = game()
        val you = game.activePlayer!!
        val opponent = game.getOpponent(you)
        val creature = game.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val land = game.putLandOnBattlefield(opponent, "Forest")
        game.giveMana(you, Color.GREEN, 2)
        game.giveMana(you, Color.BLACK, 2)
        val card = game.putCardInHand(you, "Tear Asunder")

        cast(game, you, card, land, kicked = true).isSuccess shouldBe false
        cast(game, you, card, creature, kicked = true).isSuccess shouldBe true
        game.bothPass()

        game.state.getExile(opponent).contains(creature) shouldBe true
        game.state.getBattlefield().contains(land) shouldBe true
    }
})
