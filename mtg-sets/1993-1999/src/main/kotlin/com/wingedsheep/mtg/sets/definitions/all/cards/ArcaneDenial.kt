package com.wingedsheep.mtg.sets.definitions.all.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CounterEffect
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect
import com.wingedsheep.sdk.scripting.effects.DelayedTriggerTiming
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.effects.DrawUpToEffect

/**
 * Arcane Denial — Alliances #22a (canonical printing)
 *
 * Snapshot the targeted spell's controller before attempting the counter. The delayed
 * beneficiary trigger is then created while the pipeline is rebound to that captured
 * controller, so the identity survives the spell leaving the stack without adding a
 * card-specific delayed-target primitive. The 0/1/2 choice stays in DrawUpToEffect and
 * is therefore made only when the next-upkeep delayed trigger resolves. Existing generic
 * pipeline and delayed-trigger vocabulary is sufficient; no new engine primitive is needed.
 */
val ArcaneDenial = card("Arcane Denial") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell. Its controller may draw up to two cards at the beginning of the next turn's upkeep.\n" +
        "You draw a card at the beginning of the next turn's upkeep."

    spell {
        target("target spell", Targets.Spell)
        effect = Effects.Pipeline {
            val spell = gather(CardSource.ChosenTargets, name = "deniedSpell")
            val controllers = captureControllers(spell, name = "deniedControllers")

            run(CounterEffect())

            // "Its controller may draw up to two cards ..." The controller snapshot is
            // intentionally taken before CounterEffect, so this remains well-defined even
            // if the counter instruction does not move the spell.
            forEachCaptured(spell, spell, controllers) {
                run(
                    CreateDelayedTriggerEffect(
                        step = Step.UPKEEP,
                        effect = DrawUpToEffect(2),
                        timing = DelayedTriggerTiming.NEXT_TURN
                    )
                )
            }

            // This trigger remains under Arcane Denial's controller rather than the
            // captured target-spell controller.
            run(
                CreateDelayedTriggerEffect(
                    step = Step.UPKEEP,
                    effect = DrawCardsEffect(1),
                    timing = DelayedTriggerTiming.NEXT_TURN
                )
            )
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "22a"
        artist = "Richard Kane Ferguson"
        imageUri = "https://cards.scryfall.io/normal/front/b/0/b0c5728e-43e7-417a-ba18-5038345cec67.jpg?1783947197"
        ruling(
            "2007-09-16",
            "The controller of the countered spell doesn't choose how many cards to draw until the relevant ability resolves. The player may draw 0, 1, or 2 cards. They choose the number before drawing any cards."
        )
    }
}
