package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.mh1.cards.EtchingsOfTheChosen
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EtchingsOfTheChosenScenarioTest : FunSpec({
    test("buffs the chosen type and sacrifices one to protect a controlled creature") {
        EtchingsOfTheChosen.script.replacementEffects.single().shouldBeInstanceOf<EntersWithChoice>()
        EtchingsOfTheChosen.script.staticAbilities.single().shouldBeInstanceOf<ModifyStats>()
        val ability = EtchingsOfTheChosen.script.activatedAbilities.single()
        val costs = ability.cost.shouldBeInstanceOf<AbilityCost.Composite>().costs
        costs.last() shouldBe AbilityCost.SacrificeChosenCreatureType
        ability.effect.shouldBeInstanceOf<GrantKeywordEffect>()
    }
})
