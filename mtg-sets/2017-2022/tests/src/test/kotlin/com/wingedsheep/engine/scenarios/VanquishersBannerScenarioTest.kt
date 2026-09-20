package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.xln.cards.VanquishersBanner
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class VanquishersBannerScenarioTest : FunSpec({
    test("chooses a creature type, buffs it, and draws for matching creature spells") {
        VanquishersBanner.script.replacementEffects.single().shouldBeInstanceOf<EntersWithChoice>()
        VanquishersBanner.script.staticAbilities.single().shouldBeInstanceOf<ModifyStats>()
        val trigger = VanquishersBanner.script.triggeredAbilities.single()
        trigger.trigger.shouldBeInstanceOf<EventPattern.SpellCastEvent>()
        trigger.effect.shouldBeInstanceOf<DrawCardsEffect>()
    }
})
