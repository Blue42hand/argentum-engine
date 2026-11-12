package com.wingedsheep.engine.core

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.ConvokeCreatureInfo
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import com.wingedsheep.sdk.scripting.ConvokePayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConvokeActionParamsTest : FunSpec({
    val spellDefinition = card("Convoke Parameter Draw") {
        manaCost = "{X}{G}{G}{G}"
        typeLine = "Instant"
        keywords(Keyword.CONVOKE)
        spell { effect = Effects.DrawCards(DynamicAmounts.xValue()) }
    }
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + spellDefinition)
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    test("typed convoke parameters round trip and execute the enumerated X payment") {
        val d = driver()
        val bodies = (1..4).map { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        val spell = d.putCardInHand(d.player1, spellDefinition.name)
        val offered = d.legalActions(d.player1).first { (it.action as? CastSpell)?.cardId == spell }
        offered.maxAffordableX shouldBe 1
        ActionParameterizer.spec(offered).allowedFields["convokedCreatures"] shouldBe ActionParameterFieldKind.CONVOKE_PAYMENT_MAP
        val payments = bodies.mapIndexed { n, id -> id to ConvokePayment(if (n < 3) Color.GREEN else null) }.toMap()
        val params = ActionParams(xValue = 1, convokedCreatures = payments)
        val decoded = Json.decodeFromString<ActionParams>(Json.encodeToString(params))
        decoded shouldBe params
        val action = ActionParameterizer.apply(offered, decoded, d.state) as CastSpell
        action.alternativePayment?.convokedCreatures shouldBe payments
        val info = LegalActionInfo(actionType = offered.actionType, description = offered.description,
            action = offered.action, hasConvoke = true,
            validConvokeCreatures = offered.convokeCreatures!!.map { ConvokeCreatureInfo(it.entityId, it.name, it.colors) },
            parameterSpec = ActionParameterizer.spec(offered))
        ActionParameterizer.apply(info, decoded, d.state) shouldBe action
        val handBefore = d.getHand(d.player1).size
        d.submit(action).outcome shouldBe Outcome.Done
        bodies.forEach { d.isTapped(it) shouldBe true }
        d.bothPass()
        d.getHand(d.player1).size shouldBe handBefore
    }
    test("unoffered creatures and mismatched colored payments fail before execution") {
        val d = driver()
        val body = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        repeat(2) { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        val spell = d.putCardInHand(d.player1, spellDefinition.name)
        val offered = d.legalActions(d.player1).first { (it.action as? CastSpell)?.cardId == spell }
        for (payments in listOf(mapOf(EntityId("unoffered") to ConvokePayment()), mapOf(body to ConvokePayment(Color.RED)))) {
            shouldThrow<IllegalArgumentException> {
                ActionParameterizer.apply(offered, ActionParams(xValue = 0, convokedCreatures = payments), d.state)
            }
        }
        val ordinary = offered.copy(hasConvoke = false, convokeCreatures = null)
        ("convokedCreatures" in ActionParameterizer.spec(ordinary).allowedFields) shouldBe false
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(ordinary, ActionParams(convokedCreatures = mapOf(body to ConvokePayment())), d.state)
        }
    }
    test("zero X uses only selected creatures and insufficient payment remains rejected") {
        val d = driver()
        val bodies = (1..4).map { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        val spell = d.putCardInHand(d.player1, spellDefinition.name)
        val offered = d.legalActions(d.player1).first { (it.action as? CastSpell)?.cardId == spell }
        val payments = bodies.take(3).associateWith { ConvokePayment(Color.GREEN) }
        val underpaid = ActionParameterizer.apply(offered, ActionParams(xValue = 1, convokedCreatures = payments), d.state)
        (d.submit(underpaid).outcome is Outcome.Rejected) shouldBe true
        bodies.forEach { d.isTapped(it) shouldBe false }
        val action = ActionParameterizer.apply(offered, ActionParams(xValue = 0, convokedCreatures = payments), d.state)
        d.submit(action).outcome shouldBe Outcome.Done
        bodies.take(3).forEach { d.isTapped(it) shouldBe true }
        d.isTapped(bodies.last()) shouldBe false
    }
    test("completing convoke keeps the template's other alternative choices") {
        val d = driver()
        val existing = AlternativePaymentChoice(delvedCards = listOf(EntityId("existing-delve")))
        val template = CastSpell(d.player1, EntityId("spell"), alternativePayment = existing)
        val payments = mapOf(EntityId("body") to ConvokePayment(Color.GREEN))
        val action = ActionParameterizer.apply(template, ActionParams(convokedCreatures = payments), d.state) as CastSpell
        action.alternativePayment shouldBe existing.copy(convokedCreatures = payments)
    }
})
