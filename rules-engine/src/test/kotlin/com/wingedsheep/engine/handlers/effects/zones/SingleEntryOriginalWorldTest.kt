package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.copy.EntryCopyChoice
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** A single reanimation must retain the original graveyard card's characteristics too. */
class SingleEntryOriginalWorldTest : FunSpec({
    val copyEntry = EntersAsCopy(
        additionalKeywords = listOf(Keyword.HASTE), duration = Duration.EndOfTurn,
    )
    val mirror = card("Single Entry Mirror") {
        manaCost = "{3}"
        typeLine = "Artifact"
        replacementEffect(copyEntry)
    }
    val troll = card("Single Entry Graveyard Counter") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Troll"
        power = 0
        toughness = 0
        replacementEffect(EntersWithDynamicCounters(
            count = DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature),
        ))
    }

    test("a noncreature returned as a creature copy does not count its prepared self in the old graveyard") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(mirror, troll))
        driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = driver.player1
        val target = driver.putPermanentOnBattlefield(player, troll.name)
        val entrant = driver.putCardInGraveyard(player, mirror.name)
        driver.putCardInGraveyard(player, "Grizzly Bears")
        val copiedCard = driver.state.getEntity(target)!!.get<CardComponent>()!!
        val executor = MoveToZoneEffectExecutor(
            driver.services.zones, driver.cardRegistry, driver.services.targetFinder,
            effectExecutor = { _, _, _ -> error("no OnEnterRun expected") },
        )
        // The as-enters decision has already selected the existing battlefield creature.
        val result = executor.execute(driver.state,
            MoveToZoneEffect(EffectTarget.Self, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD),
            EffectContext(sourceId = entrant, controllerId = player,
                entryCopies = mapOf(entrant to EntryCopyChoice(copyEntry, copiedCard, target))),
        )
        (entrant in result.state.getBattlefield()) shouldBe true
        result.state.getEntity(entrant)!!.get<CountersComponent>()!!
            .getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        result.state.projectedState.hasKeyword(entrant, Keyword.HASTE) shouldBe true
        result.state.projectedState.getPower(entrant) shouldBe 1
    }
})
