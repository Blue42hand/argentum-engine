package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class VibraniumTokenDefinitionTest : FunSpec({
    test("Vibranium is a registered indestructible artifact with restricted colorless mana") {
        val token = PredefinedTokens.Vibranium
        token.typeLine.isArtifact shouldBe true
        token.keywords shouldContain Keyword.INDESTRUCTIBLE
        val mana = token.activatedAbilities.single().effect.shouldBeInstanceOf<AddColorlessManaEffect>()
        mana.restriction shouldBe ManaRestriction.CannotCastSpellsOtherThan(setOf(CardType.ARTIFACT))
    }

    test("CreateVibranium uses the predefined-token path and preserves tapped entry") {
        val effect = Effects.CreateVibranium(tapped = true).shouldBeInstanceOf<CreatePredefinedTokenEffect>()
        effect.tokenType shouldBe "Vibranium"
        effect.count shouldBe 1
        effect.tapped shouldBe true
    }
})
