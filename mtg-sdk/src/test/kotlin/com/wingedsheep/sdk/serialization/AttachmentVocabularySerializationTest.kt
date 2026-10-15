package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AttachmentVocabularySerializationTest : FunSpec({
    test("referenced gathers, enchant overrides, and explicit attachments round trip in card data") {
        val effect = Effects.Pipeline {
            val host = gather(CardSource.ReferencedEntity(EffectTarget.EnchantedPermanent))
            run(Effects.GrantStaticAbility(
                EnchantRestrictionOverride(TargetObject(filter = TargetFilter.Creature), "returned"),
                EffectTarget.Self, Duration.Permanent))
            run(Effects.AttachToPermanent(EffectTarget.Self, host.asTarget))
        }
        val encoded = Json.encodeToString(Effect.serializer(), effect)
        Json.decodeFromString(Effect.serializer(), encoded) shouldBe effect
    }
})
