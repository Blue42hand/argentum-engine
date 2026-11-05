package com.wingedsheep.mtg.sets.definitions.znr.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Malakir Rebirth {B} // Malakir Mire — Zendikar Rising #111
 * Instant // Land · Modal double-faced card
 *
 * Front — Malakir Rebirth: "Choose target creature. You lose 2 life. Until end of turn, that
 * creature gains 'When this creature dies, return it to the battlefield tapped under its owner's
 * control.'"
 * Back — Malakir Mire: "This land enters tapped. {T}: Add {B}."
 *
 * The front is the Infuse with Vitality shape: an end-of-turn granted self dies-trigger that moves
 * the card graveyard → battlefield tapped under its owner's control (`fromZone = GRAVEYARD` makes it
 * a no-op if the card already left the graveyard). The life loss is part of the targeted spell, so
 * when the target is illegal on resolution the spell doesn't resolve and no life is lost (ruling).
 */
private val MalakirRebirthFront = card("Malakir Rebirth") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Choose target creature. You lose 2 life. Until end of turn, that creature gains " +
        "\"When this creature dies, return it to the battlefield tapped under its owner's control.\""

    spell {
        val creature = target(TargetFilter.Creature)
        effect = Effects.LoseLife(2, EffectTarget.Controller) then
            Effects.GrantTriggeredAbility(
                ability = TriggeredAbility.create(
                    trigger = Triggers.self.dies(),
                    effect = Effects.Move(
                        target = EffectTarget.Self,
                        destination = Zone.BATTLEFIELD,
                        placement = ZonePlacement.Tapped,
                        fromZone = Zone.GRAVEYARD
                    ),
                    descriptionOverride = "When this creature dies, return it to the battlefield " +
                        "tapped under its owner's control."
                ),
                target = creature,
                duration = Duration.EndOfTurn
            )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "111"
        artist = "Marta Nael"
        imageUri = "https://cards.scryfall.io/normal/front/6/0/609d3ecf-f88d-4268-a8d3-4bf2bcf5df60.jpg?1783929378"
        flavorText = "\"Those who fail to escape Malakir become part of the city's defense.\"\n—Doros, expedition guide"
        ruling("2020-09-25", "If the target creature is an illegal target by the time Malakir Rebirth tries to resolve, the spell doesn't resolve. You don't lose 2 life.")
        ruling("2020-09-25", "Malakir Rebirth's effect works only once. If the targeted creature dies and is then returned to the battlefield, it's considered to be a new creature. If that new creature dies, it won't come back.")
        ruling("2020-09-25", "To determine whether it is legal to play a modal double-faced card, consider only the characteristics of the face you're playing and ignore the other face's characteristics.")
        ruling("2020-09-25", "If an effect allows you to play a specific modal double-faced card, you may cast it as a spell or play it as a land, as determined by which face you choose to play. If an effect allows you to cast (rather than \"play\") a specific modal double-faced card, you can't play it as a land.")
        ruling("2020-09-25", "If an effect allows you to play a land or cast a spell from among a group of cards, you may play or cast a modal double-faced card with any face that fits the criteria of that effect.")
        ruling("2020-09-25", "The mana value of a modal double-faced card is based on the characteristics of the face that's being considered. On the stack and battlefield, consider whichever face is up. In all other zones, consider only the front face. This is different than how the mana value of a transforming double-faced card is determined.")
        ruling("2020-09-25", "A modal double-faced card can't be transformed or be put onto the battlefield transformed. Ignore any instruction to transform a modal double-faced card or to put one onto the battlefield transformed.")
        ruling("2020-09-25", "If an effect puts a double-faced card onto the battlefield, it enters with its front face up. If that front face can't be put onto the battlefield, it doesn't enter the battlefield.")
        ruling("2020-09-25", "If an effect instructs a player to choose a card name, the name of either face may be chosen. If that effect or a linked ability refers to a spell with the chosen name being cast and/or a land with the chosen name being played, it considers only the chosen name, not the other face's name.")
        ruling("2020-09-25", "In the Commander variant, a double-faced card's color identity is determined by the mana costs and mana symbols in the rules text of both faces combined. If either face has a color indicator or basic land type, those are also considered.")
    }
}
private val MalakirMireBack = card("Malakir Mire") {
    typeLine = "Land"
    colorIdentity = "B"
    oracleText = "This land enters tapped.\n{T}: Add {B}."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "111"
        artist = "Marta Nael"
        imageUri = "https://cards.scryfall.io/normal/back/6/0/609d3ecf-f88d-4268-a8d3-4bf2bcf5df60.jpg?1783929378"
        flavorText = "\"Getting into Malakir isn't difficult. It's getting out that's the hard part.\"\n—Doros, expedition guide"
        ruling("2020-09-25", "If the target creature is an illegal target by the time Malakir Rebirth tries to resolve, the spell doesn't resolve. You don't lose 2 life.")
        ruling("2020-09-25", "Malakir Rebirth's effect works only once. If the targeted creature dies and is then returned to the battlefield, it's considered to be a new creature. If that new creature dies, it won't come back.")
        ruling("2020-09-25", "To determine whether it is legal to play a modal double-faced card, consider only the characteristics of the face you're playing and ignore the other face's characteristics.")
        ruling("2020-09-25", "If an effect allows you to play a specific modal double-faced card, you may cast it as a spell or play it as a land, as determined by which face you choose to play. If an effect allows you to cast (rather than \"play\") a specific modal double-faced card, you can't play it as a land.")
        ruling("2020-09-25", "If an effect allows you to play a land or cast a spell from among a group of cards, you may play or cast a modal double-faced card with any face that fits the criteria of that effect.")
        ruling("2020-09-25", "The mana value of a modal double-faced card is based on the characteristics of the face that's being considered. On the stack and battlefield, consider whichever face is up. In all other zones, consider only the front face. This is different than how the mana value of a transforming double-faced card is determined.")
        ruling("2020-09-25", "A modal double-faced card can't be transformed or be put onto the battlefield transformed. Ignore any instruction to transform a modal double-faced card or to put one onto the battlefield transformed.")
        ruling("2020-09-25", "If an effect puts a double-faced card onto the battlefield, it enters with its front face up. If that front face can't be put onto the battlefield, it doesn't enter the battlefield.")
        ruling("2020-09-25", "If an effect instructs a player to choose a card name, the name of either face may be chosen. If that effect or a linked ability refers to a spell with the chosen name being cast and/or a land with the chosen name being played, it considers only the chosen name, not the other face's name.")
        ruling("2020-09-25", "In the Commander variant, a double-faced card's color identity is determined by the mana costs and mana symbols in the rules text of both faces combined. If either face has a color indicator or basic land type, those are also considered.")
    }
}
val MalakirRebirth: CardDefinition = CardDefinition.modalDoubleFacedLand(MalakirRebirthFront, MalakirMireBack)
