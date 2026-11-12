package com.wingedsheep.gym

import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.ActionParameterFieldKind
import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.rav.cards.ChordOfCalling
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ConvokePayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NativeConvokeObservationTest : FunSpec({
    test("Gym advertises the candidates and typed allocation needed to execute an affordable Chord") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + ChordOfCalling)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val bodies = (1..4).map { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        val chord = d.putCardInHand(d.player1, "Chord of Calling")
        val offered = d.legalActions(d.player1)
        val view = (ObservationBuilder(d.cardRegistry).build(d.state, d.player1, offered).observation as TrainingObservation)
            .legalActions.first { (offered[it.actionId].action as? CastSpell)?.cardId == chord }
        view.hasConvoke shouldBe true
        view.maxAffordableX shouldBe 1
        view.parameterSpec.allowedFields["convokedCreatures"] shouldBe ActionParameterFieldKind.CONVOKE_PAYMENT_MAP
        view.validConvokeCreatures.map { it.entityId }.toSet() shouldBe bodies.toSet()
        view.validConvokeCreatures.all { it.colors == setOf(Color.GREEN) } shouldBe true
        val params = ActionParams(xValue = 1, convokedCreatures = bodies.mapIndexed { n, id ->
            id to ConvokePayment(if (n < 3) Color.GREEN else null)
        }.toMap())
        val action = ActionParameterizer.apply(offered[view.actionId], params, d.state)
        d.submit(action).outcome shouldBe Outcome.Done
        bodies.forEach { d.isTapped(it) shouldBe true }
    }
})
