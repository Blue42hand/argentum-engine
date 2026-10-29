package com.wingedsheep.gym

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.scg.cards.WirewoodSymbiote
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class NativeBounceObservationTest : FunSpec({
    test("seat-visible Gym observation round-trips exact native bounce candidates and required count") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + WirewoodSymbiote)
        d.initMirrorMatch(Deck.of("Forest" to 40))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val p = d.activePlayer!!
        val opponent = d.getOpponent(p)
        val source = d.putCreatureOnBattlefield(p, "Wirewood Symbiote")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val theirs = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val ownHand = d.putCardInHand(p, "Llanowar Elves")
        val hidden = d.putCardInHand(opponent, "Llanowar Elves")
        val offers = d.legalActions(p)
        val observation = ObservationBuilder(d.cardRegistry).build(d.state, p, offers).observation as TrainingObservation
        val roundTrip = Json.decodeFromString<TrainingObservation>(Json.encodeToString(observation))
        roundTrip shouldBe observation
        val action = roundTrip.legalActions.single { (offers[it.actionId].action as? ActivateAbility)?.sourceId == source }
        val cost = action.costChoices!!
        cost.validBounceTargets shouldBe listOf(elf)
        cost.bounceCount shouldBe 1
        action.parameterSpec.allowedFields["bouncedPermanents"] shouldBe ActionParameterFieldKind.ENTITY_ID_ARRAY
        cost.validBounceTargets.none { it in listOf(theirs, ownHand, hidden, source) } shouldBe true
        Json.encodeToString(observation).contains(hidden.value) shouldBe false
        val completed = ActionParameterizer.apply(offers[action.actionId],
            ActionParams(targets = listOf(source), bouncedPermanents = cost.validBounceTargets), d.state) as ActivateAbility
        completed.costPayment!!.bouncedPermanents shouldBe listOf(elf)
        d.submit(completed).error shouldBe null
        (elf in d.getHand(p)) shouldBe true
    }
})
