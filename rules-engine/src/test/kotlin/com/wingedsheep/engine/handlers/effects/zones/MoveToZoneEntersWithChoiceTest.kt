package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MoveToZoneEntersWithChoiceTest : FunSpec({
    val restrictedTypes = listOf(CardType.ARTIFACT, CardType.CREATURE, CardType.SORCERY)
    val TypeChooser = card("Moved Type Chooser") {
        manaCost = "{0}"
        typeLine = "Artifact"
        oracleText = "As this artifact enters, choose artifact, creature, or sorcery."
        replacementEffect(
            EntersWithChoice(
                choiceType = ChoiceType.CARD_TYPE,
                allowedCardTypes = restrictedTypes,
            )
        )
    }

    test("generic move to battlefield pauses for as-enters choice before exposing the entry event") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TypeChooser)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val player = driver.activePlayer!!
        val cardId = driver.putCardInGraveyard(player, "Moved Type Chooser")

        val executor = MoveToZoneEffectExecutor(
            driver.cardRegistry,
            effectExecutor = { _, _, _ -> error("no OnEnterRunEffect expected in this test") },
        )
        val result = executor.execute(
            driver.state,
            MoveToZoneEffect(
                target = EffectTarget.Self,
                destination = Zone.BATTLEFIELD,
                fromZone = Zone.GRAVEYARD,
            ),
            EffectContext(sourceId = cardId, controllerId = player),
        )

        result.isSuccess shouldBe true
        result.state.getBattlefield() shouldContain cardId
        val decision = result.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        decision.prompt shouldBe "Choose a card type"
        decision.options shouldContainExactly listOf("Artifact", "Creature", "Sorcery")

        // The choice resumer owns the entry event/ETB trigger pass. Carrying the original move's
        // ZoneChangeEvent into the pause would expose the entry early and fire ETB triggers twice.
        result.events.filterIsInstance<ZoneChangeEvent>()
            .filter { it.entityId == cardId }
            .size shouldBe 0
    }
})
