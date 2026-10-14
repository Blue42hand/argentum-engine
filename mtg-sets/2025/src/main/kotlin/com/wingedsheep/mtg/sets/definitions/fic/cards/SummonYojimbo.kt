package com.wingedsheep.mtg.sets.definitions.fic.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val SummonYojimbo = card("Summon: Yojimbo") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment Creature — Saga Samurai"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)\nI — Exile target artifact, enchantment, or tapped creature an opponent controls.\nII, III — Until your next turn, creatures can't attack you unless their controller pays {2} for each of those creatures.\nIV — Create X Treasure tokens, where X is the number of opponents who control a creature with power 4 or greater.\nVigilance"
    power = 5
    toughness = 5

    keywords(Keyword.VIGILANCE)
    sagaChapter(1) {
        val permanent = target(TargetFilter((GameObjectFilter.Artifact or GameObjectFilter.Enchantment or GameObjectFilter.Creature.tapped()).opponentControls()))
        effect = Effects.Exile(permanent)
    }
    for (chapter in 2..3) {
        sagaChapter(chapter) {
            effect = Effects.GrantStaticAbility(AttackTax(DynamicAmounts.fixed(2)),
                EffectTarget.Controller, Duration.UntilYourNextTurn)
        }
    }
    sagaChapter(4) {
        effect = Effects.CreateTreasure(
            DynamicAmounts.countPlayersWith(Player.EachOpponent, Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))),
            imageUri = "https://cards.scryfall.io/normal/front/0/3/03992f88-7a15-4234-9bed-b7617d7ff09c.jpg?1789735245"
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "28"
        artist = "Benjamin Ee"
        imageUri = "https://cards.scryfall.io/normal/front/c/d/cdbc2d28-ea35-47c6-af7e-07924e9d5794.jpg?1783906365"
        ruling("2025-06-06", "On turns where the effect of Summon: Yojimbo's second or third chapter ability is applying, your opponents can choose not to attack with a creature that must attack if able as long as there is no other player, planeswalker, or battle for that creature to attack that wouldn't require a cost.")
        ruling("2025-06-06", "The value of X is determined only once, as Summon: Yojimbo's fourth chapter ability resolves.")
        ruling("2025-06-06", "A chapter ability doesn't trigger if a lore counter is put on a Saga that already had a number of lore counters greater than or equal to that chapter's number. For example, the third lore counter put on a Saga causes the chapter III ability to trigger, but chapters I and II won't trigger again.")
        ruling("2025-06-06", "As a Saga enters, its controller puts a lore counter on it. As your first main phase begins (immediately after your draw step), you put another lore counter on each Saga you control. Putting a lore counter on a Saga in either of these ways doesn't use the stack.")
        ruling("2025-06-06", "If multiple chapter abilities trigger at the same time, their controller puts them on the stack in any order. If any of them require targets, those targets are chosen as you put the abilities on the stack, before any of those abilities resolve.")
        ruling("2025-06-06", "Removing lore counters won't cause a previous chapter ability to trigger. If lore counters are removed from a Saga, the appropriate chapter abilities will trigger again when the Saga receives more lore counters.")
        ruling("2025-06-06", "Due to a rules change that takes effect with this release (see below for details), a Saga that somehow loses all of its chapter abilities will not be sacrificed as a state-based action. It will also not gain a lore counter at the beginning of each of its controller's first main phases.")
        ruling("2025-06-06", "Once a chapter ability has triggered, the ability on the stack won't be affected if the Saga gains or loses counters, or if it leaves the battlefield.")
        ruling("2025-06-06", "Once the number of lore counters on a Saga is greater than or equal to the greatest number among its chapter abilities, the Saga's controller sacrifices it as soon as its chapter ability has left the stack, most likely by resolving or being countered. This state-based action doesn't use the stack.")
        ruling("2025-06-06", "Each symbol on the left of a Saga's text box represents a chapter ability. A chapter ability is a triggered ability that triggers when a lore counter that is put on the Saga causes the number of lore counters on the Saga to become equal to or greater than the ability's chapter number. Chapter abilities are put onto the stack and may be responded to.")
        ruling("2025-06-06", "Saga creatures have two sections to their text boxes. The first section, above the type line, contains their chapter abilities, and the second section, below the type line, contains any other abilities (or italicized flavor text). Any abilities in the latter section aren't chapter abilities and apply no matter how many lore counters are on the creature.")
    }
}
