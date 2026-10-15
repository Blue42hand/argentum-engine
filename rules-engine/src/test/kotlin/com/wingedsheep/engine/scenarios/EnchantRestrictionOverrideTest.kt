package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe

class EnchantRestrictionOverrideTest : ScenarioTestBase() {
    private val spell = card("Test Change Enchant Restriction") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            val aura = target(TargetFilter(GameObjectFilter.Enchantment.withSubtype(Subtype("Aura"))))
            val host = target(TargetFilter.Permanent)
            effect = Effects.GrantStaticAbility(
                EnchantRestrictionOverride(TargetObject(filter = TargetFilter.Permanent)), aura, Duration.EndOfTurn
            ) then Effects.AttachToPermanent(aura, host)
        }
    }
    init {
        cardRegistry.register(spell)
        test("a duration-scoped override permits a noncreature host and expires back to the printed restriction") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withLandsOnBattlefield(1, "Plains", 1).withCardInHand(1, spell.name)
                .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val aura = game.findPermanent("Holy Strength")!!; val land = game.findPermanent("Plains")!!
            game.execute(CastSpell(game.player1Id, game.state.getHand(game.player1Id).single(),
                listOf(ChosenTarget.Permanent(aura), ChosenTarget.Permanent(land)))).error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Holy Strength") shouldBe true
            game.state.getEntity(aura)!!.get<com.wingedsheep.engine.state.components.battlefield.AttachedToComponent>()!!.targetId shouldBe land
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.isInGraveyard(1, "Holy Strength") shouldBe true
        }
    }
}
