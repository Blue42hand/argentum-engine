package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CantHaveKeywordsSerializationTest : FunSpec({
    test("keyword prohibitions retain their target and duration through card-data serialization") {
        val effect = Effects.CantHaveKeywords(setOf(Keyword.HEXPROOF, Keyword.SHROUD),
            EffectTarget.ContextTarget(0), Duration.UntilYourNextTurn)
        Json.decodeFromString<Effect>(Json.encodeToString<Effect>(effect)) shouldBe effect
    }
})
