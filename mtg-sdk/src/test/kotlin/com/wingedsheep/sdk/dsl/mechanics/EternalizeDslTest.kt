package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** The reusable eternalize authoring surface and its serialized activated-ability contract. */
class EternalizeDslTest : FunSpec({
    val card = card("SDK Eternalize Probe") {
        manaCost = "{2}{G}{G}"
        typeLine = "Creature — Human Shaman"
        power = 2
        toughness = 1
        eternalize("{5}{G}{G}")
    }

    test("eternalize composes a graveyard sorcery ability with mana and immediate self-exile costs") {
        card.keywords shouldContain Keyword.ETERNALIZE
        val ability = card.activatedAbilities.single()
        ability.activateFromZone shouldBe Zone.GRAVEYARD
        ability.timing shouldBe TimingRule.SorcerySpeed
        val cost = ability.cost.shouldBeInstanceOf<AbilityCost.Composite>()
        cost.costs shouldBe listOf(
            AbilityCost.Atom(CostAtom.Mana(ManaCost.parse("{5}{G}{G}"))),
            AbilityCost.ExileSelf,
        )
    }

    test("token-copy exceptions are copiable black, 4/4, Zombie-addition, and no-mana-cost values") {
        val effect = card.activatedAbilities.single().effect.shouldBeInstanceOf<CreateTokenCopyOfTargetEffect>()
        effect.target shouldBe EffectTarget.Self
        effect.copyExceptions.overrideColors shouldBe setOf(Color.BLACK)
        effect.copyExceptions.powerOverride shouldBe 4
        effect.copyExceptions.toughnessOverride shouldBe 4
        effect.copyExceptions.addedSubtypes shouldContain Subtype.ZOMBIE
        effect.copyExceptions.noManaCost shouldBe true
    }

    test("eternalize ability and copy exceptions survive serialization") {
        val ability = card.activatedAbilities.single()
        val encoded = Json.encodeToString(ActivatedAbility.serializer(), ability)
        val decoded = Json.decodeFromString(ActivatedAbility.serializer(), encoded)
        decoded shouldBe ability
    }
})
