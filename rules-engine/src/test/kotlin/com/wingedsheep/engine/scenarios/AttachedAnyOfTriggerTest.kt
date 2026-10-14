package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import io.kotest.matchers.shouldBe

class AttachedAnyOfTriggerTest : ScenarioTestBase() {
    private val aura = card("Overlapping Attached Trigger Test Aura") {
        manaCost = "{B}"
        typeLine = "Enchantment — Aura"
        auraTarget = TargetObject(filter = TargetFilter.Creature)
        triggeredAbility {
            trigger = Triggers.or(Triggers.attached.dies(), Triggers.attached.leaves())
            effect = Effects.DrawCards(1)
        }
    }
    init {
        cardRegistry.register(aura)
        test("an attached departure matching both disjuncts creates only one trigger") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, aura.name, "Grizzly Bears")
                .withCardInHand(2, "Doom Blade").withLandsOnBattlefield(2, "Swamp", 2)
                .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Island")
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(2, "Doom Blade", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            game.resolveStack()
            game.state.getHand(game.player1Id).size shouldBe 1
        }
    }
}
