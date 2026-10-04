package com.wingedsheep.engine.handlers.mana

import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [ManaRestriction.CannotCastSpellsOtherThan] — "This mana can't be spent to cast nonartifact
 * spells" (Vibranium, Hydraulic Helper). This is a negative restriction: only a spell cast of a
 * disallowed type fails. Ability activations and other non-cast payments remain legal.
 */
class ManaSpendRestrictionCannotCastNonArtifactSpellsTest : FunSpec({

    val cost = ManaCost.parse("{1}")
    val restriction = ManaRestriction.CannotCastSpellsOtherThan(setOf(CardType.ARTIFACT))

    fun freshPool() = ManaPool().addRestricted(null, 1, restriction)

    test("accepts an artifact spell") {
        freshPool().canPay(
            cost,
            SpellPaymentContext(cardTypes = setOf(CardType.ARTIFACT)),
        ) shouldBe true
    }

    test("rejects a nonartifact spell") {
        freshPool().canPay(
            cost,
            SpellPaymentContext(
                isCreature = true,
                cardTypes = setOf(CardType.CREATURE),
            ),
        ) shouldBe false
    }

    test("accepts an ability activation") {
        freshPool().canPay(
            cost,
            SpellPaymentContext(
                isAbilityActivation = true,
                abilitySourceCardTypes = setOf(CardType.CREATURE),
            ),
        ) shouldBe true
    }

    test("accepts a non-cast payment such as a tax or ward cost") {
        freshPool().canPay(cost, SpellPaymentContext()) shouldBe true
    }

    test("accepts a turn-face-up special action") {
        freshPool().canPay(cost, SpellPaymentContext(isTurnFaceUpAction = true)) shouldBe true
    }
})
