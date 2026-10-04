package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.permanent.stats.ModifyStatsExecutor
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** CR 208.3a: a stat modification can be created before a Vehicle becomes a creature. */
class ModifyStatsVehicleScenarioTest : FunSpec({
    val vehicle = card("Stat Test Vehicle") {
        manaCost = "{4}"
        typeLine = "Artifact — Vehicle"
        power = 6
        toughness = 6
        keywordAbility(KeywordAbility.crew(2))
    }

    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + vehicle)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("self stat modification created while uncrewed applies after crew") {
        val d = driver()
        val mech = d.putPermanentOnBattlefield(d.player1, vehicle.name)
        val pilot = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val result = ModifyStatsExecutor(d.services.predicateEvaluator.amounts).execute(
            d.state,
            ModifyStatsEffect(1, 0, EffectTarget.Self),
            EffectContext(sourceId = mech, controllerId = d.player1),
        )
        result.error shouldBe null
        d.replaceState(result.state)

        d.submitSuccess(CrewVehicle(d.player1, mech, listOf(pilot)))
        d.bothPass()
        d.state.projectedState.isCreature(mech) shouldBe true
        d.state.projectedState.getPower(mech) shouldBe 7
        d.state.projectedState.getToughness(mech) shouldBe 6
    }

    test("a targeted creature stat modification still rejects an uncrewed Vehicle") {
        val d = driver()
        val mech = d.putPermanentOnBattlefield(d.player1, vehicle.name)
        val caster = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val result = ModifyStatsExecutor(d.services.predicateEvaluator.amounts).execute(
            d.state,
            ModifyStatsEffect(1, 0, EffectTarget.ContextTarget(0)),
            EffectContext(
                sourceId = caster,
                controllerId = d.player1,
                targets = listOf(ChosenTarget.Permanent(mech)),
            ),
        )
        result.error shouldBe "Target is not a creature"
        result.state.floatingEffects.size shouldBe d.state.floatingEffects.size
    }
})
