package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.emitter.Emitter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class TokenBundleReplacementEmitterTest : FunSpec({
    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()
    val ir = """{"_OracleCard":"Card","Name":"Test Artifact Bundle","Typeline":{"Supertypes":[],"Cardtypes":["Artifact","Creature"],"Subtypes":["AssemblyWorker"]},"ManaCost":[{"_ManaSymbol":"ManaCostGeneric","args":3}],"Rules":[{"_Rule":"ReplaceAPlayerWouldCreateAToken","args":[{"_ReplacableEventAPlayerWouldCreateAToken":"APlayerWouldCreateAToken","args":[{"_Players":"SinglePlayer","args":{"_Player":"You"}},{"_Permanents":"Or","args":[{"_Permanents":"IsArtifactType","args":"Clue"},{"_Permanents":"IsArtifactType","args":"Food"},{"_Permanents":"IsArtifactType","args":"Treasure"}]}]},[{"_ReplacementActionAPlayerWouldCreateAToken":"CreateTokensInstead","args":[{"_CreatableToken":"ClueToken"},{"_CreatableToken":"FoodToken"},{"_CreatableToken":"TreasureToken"}]}]]}],"CardPT":{"Power":1,"Toughness":3}}"""
    test("multi-kind replacement renders the exact filter and predefined outputs") {
        val rendered = Emitter.renderCard(Json.parseToJsonElement(ir).jsonObject, null, effects, keywords)
        rendered.complete shouldBe true
        rendered.text shouldContain "ReplaceTokenCreationWithTokens"
        rendered.text shouldContain "listOf(\"Clue\", \"Food\", \"Treasure\")"
        rendered.text shouldContain "GameObjectFilter.Any.withAnySubtype(\"Clue\", \"Food\", \"Treasure\")"
        rendered.text shouldContain "import com.wingedsheep.sdk.scripting.ReplaceTokenCreationWithTokens"
    }
    test("unsupported output and controller remain scaffolds") {
        for (changed in listOf(ir.replace("TreasureToken", "UnknownToken"), ir.replace("\"You\"", "\"Opponent\""))) {
            Emitter.renderCard(Json.parseToJsonElement(changed).jsonObject, null, effects, keywords).complete shouldBe false
        }
    }
})
