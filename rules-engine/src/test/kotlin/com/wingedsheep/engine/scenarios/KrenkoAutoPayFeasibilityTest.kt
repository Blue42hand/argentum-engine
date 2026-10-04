package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ManaSourcesSelectedResponse
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.mechanics.mana.ManaPaymentWindow
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m13.cards.KrenkoMobBoss
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class KrenkoAutoPayFeasibilityTest : FunSpec({
    test("empty suggestion still permits AutoPay when floating mana covers the whole cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all)
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.giveMana(caster, Color.RED, 4)

        val window = ManaPaymentWindow.buildDecision(
            game.state, caster, ManaCost.parse("{2}{R}{R}"), "pool-covered",
            "Pay Krenko's mana cost", DecisionContext(), true, game.cardRegistry
        )
        window.autoPaySuggestion shouldBe emptyList()
        window.canAutoPayNow shouldBe true
    }

    test("Krenko payment window distinguishes affordable cast from executable AutoPay") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(KrenkoMobBoss, PredefinedTokens.Treasure))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val caster = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val krenko = game.putCardInHand(caster, "Krenko, Mob Boss")
        val mountain = game.putPermanentOnBattlefield(caster, "Mountain")
        val treasure = game.putPermanentOnBattlefield(caster, "Treasure")
        game.giveMana(caster, Color.RED, 2)

        val cast = game.legalActions(caster).single {
            it.actionType == "CastSpell" && (it.action as? CastSpell)?.cardId == krenko
        }
        cast.affordable shouldBe true
        game.submit(cast.action).isPaused shouldBe true

        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        window.requiredCost shouldBe "{2}{R}{R}"
        window.availableSources.map { it.entityId } shouldBe listOf(mountain)
        window.autoPaySuggestion shouldBe emptyList()
        window.canAutoPayNow shouldBe false

        val invalid = game.submitDecision(caster, ManaSourcesSelectedResponse(
            window.id, selectedSources = listOf(mountain), autoPay = true
        ))
        invalid.error shouldBe "Auto-pay is not available yet; activate a mana ability or select payment sources"
        game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().canAutoPayNow shouldBe false

        game.submit(ActivateAbility(
            caster, treasure, PredefinedTokens.Treasure.activatedAbilities.single().id,
            manaColorChoice = Color.RED
        )).error shouldBe null

        val refreshed = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        refreshed.autoPaySuggestion shouldBe listOf(mountain)
        refreshed.canAutoPayNow shouldBe true
        game.submitDecision(caster, ManaSourcesSelectedResponse(
            refreshed.id, selectedSources = listOf(mountain), autoPay = true
        )).error shouldBe "Auto-pay cannot be combined with selected mana sources"
        game.submitDecision(caster, ManaSourcesSelectedResponse(refreshed.id, autoPay = true)).error shouldBe null
        (krenko in game.state.stack) shouldBe true
    }
})
