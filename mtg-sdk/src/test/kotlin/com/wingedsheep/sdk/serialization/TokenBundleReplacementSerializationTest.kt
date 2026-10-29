package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.scripting.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class TokenBundleReplacementSerializationTest : FunSpec({
    test("multi-kind replacement round trips with its controller and token filter") {
        val effect: ReplacementEffect = ReplaceTokenCreationWithTokens(listOf("Clue", "Food", "Treasure"),
            EventPattern.TokenCreationEvent(tokenFilter = GameObjectFilter.Any.withAnySubtype("Clue", "Food", "Treasure")))
        val json = CardSerialization.json
        json.decodeFromString<ReplacementEffect>(json.encodeToString<ReplacementEffect>(effect)) shouldBe effect
    }
    test("empty output and blank registry keys are rejected") {
        shouldThrow<IllegalArgumentException> { ReplaceTokenCreationWithTokens(emptyList()) }
        shouldThrow<IllegalArgumentException> { ReplaceTokenCreationWithTokens(listOf(" ")) }
    }
})
