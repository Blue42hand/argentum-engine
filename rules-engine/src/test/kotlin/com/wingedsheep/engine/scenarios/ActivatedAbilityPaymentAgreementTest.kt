package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ManaSourcesSelectedResponse
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.mtg.sets.definitions.ltr.cards.StingTheGlintingDagger
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** AutoPay offers for activated abilities must match sources it can actually use. */
class ActivatedAbilityPaymentAgreementTest : FunSpec({
    test("AutoPay chooses Phyrexian life instead of a manual Drum color") {
        val abilityCard = card("Manual Color Phyrexian Activation") {
            typeLine = "Artifact"
            activatedAbility {
                cost = Costs.Mana("{4}{R/P}")
                effect = Effects.GainLife(3)
            }
        }
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SpringleafDrum, abilityCard))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val player = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val source = game.putPermanentOnBattlefield(player, "Manual Color Phyrexian Activation")
        val drum = game.putPermanentOnBattlefield(player, "Springleaf Drum")
        game.putCreatureOnBattlefield(player, "Grizzly Bears")
        repeat(4) { game.putLandOnBattlefield(player, "Forest") }

        game.legalActions(player).single {
            (it.action as? ActivateAbility)?.sourceId == source
        }.affordable shouldBe true
        val announced = game.submit(ActivateAbility(player, source, abilityCard.activatedAbilities.single().id))
        announced.error shouldBe null
        game.pendingDecision shouldBe null
        game.getLifeTotal(player) shouldBe 18
        (game.state.stack.isNotEmpty()) shouldBe true
        game.state.getEntity(drum)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe false
    }

    test("manual Drum mana and Phyrexian life complete the same activation") {
        val abilityCard = card("Manual Phyrexian Activation") {
            typeLine = "Artifact"
            activatedAbility {
                cost = Costs.Mana("{1}{R/P}")
                effect = Effects.GainLife(3)
            }
        }
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SpringleafDrum, abilityCard))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val player = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val source = game.putPermanentOnBattlefield(player, "Manual Phyrexian Activation")
        val drum = game.putPermanentOnBattlefield(player, "Springleaf Drum")
        val creature = game.putCreatureOnBattlefield(player, "Grizzly Bears")

        val announced = game.submit(ActivateAbility(player, source, abilityCard.activatedAbilities.single().id))
        (announced.outcome is Outcome.Paused) shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        game.submit(ActivateAbility(player, drum, SpringleafDrum.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
            manaColorChoice = Color.RED)).error shouldBe null
        val resumed = game.submitDecision(player, ManaSourcesSelectedResponse(window.id))
        resumed.error shouldBe null
        game.getLifeTotal(player) shouldBe 18
        (game.state.stack.isNotEmpty()) shouldBe true
    }

    test("equip offered using Drum mana opens a manual payment window") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + listOf(SpringleafDrum, StingTheGlintingDagger))
        game.initMirrorMatch(Deck.of("Forest" to 40))
        val player = game.activePlayer!!
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val sting = game.putPermanentOnBattlefield(player, "Sting, the Glinting Dagger")
        val drum = game.putPermanentOnBattlefield(player, "Springleaf Drum")
        val creature = game.putCreatureOnBattlefield(player, "Grizzly Bears")
        val equipId = StingTheGlintingDagger.activatedAbilities.single { it.isEquipAbility }.id
        game.giveMana(player, Color.RED)

        fun offeredEquip() = game.legalActions(player).single {
            (it.action as? ActivateAbility)?.let { action ->
                action.sourceId == sting && action.abilityId == equipId
            } == true
        }

        offeredEquip().affordable shouldBe true
        val announced = game.submit(ActivateAbility(player, sting, equipId,
            targets = listOf(ChosenTarget.Permanent(creature))))
        (announced.outcome is Outcome.Paused) shouldBe true
        val window = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        window.requiredCost shouldBe "{2}"
        game.state.getEntity(player)!!.get<ManaPoolComponent>()!!.red shouldBe 1

        game.submit(ActivateAbility(player, drum, SpringleafDrum.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(creature)),
            manaColorChoice = Color.RED)).error shouldBe null
        val refreshed = game.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        refreshed.id shouldBe window.id
        game.submitDecision(player, ManaSourcesSelectedResponse(refreshed.id)).error shouldBe null
        (game.state.stack.isNotEmpty()) shouldBe true
    }
})
