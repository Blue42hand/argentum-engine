package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val DetentionSphere = card("Detention Sphere") {
    manaCost = "{1}{W}{U}"
    colorIdentity = "UW"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, you may exile target nonland permanent not named Detention Sphere and all other permanents with the same name as that permanent.\nWhen this enchantment leaves the battlefield, return the exiled cards to the battlefield under their owner's control."

    triggeredAbility {
        trigger = Triggers.self.enters()
        optional = true
        val permanent = target(TargetFilter(GameObjectFilter.Nonland.notNamed("Detention Sphere")))
        effect = Effects.Pipeline {
            val matching = gather(GameObjectFilter.Permanent.sharingNameWith(permanent))
            move(matching, CardDestination.ToZone(Zone.EXILE), linkToSource = true)
        }
    }
    triggeredAbility {
        trigger = Triggers.self.leaves()
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "155"
        artist = "Kev Walker"
        imageUri = "https://cards.scryfall.io/normal/front/a/f/afee5464-83b7-4d7a-b407-9ee7de21535b.jpg?1783940341"
        ruling("2012-10-01", "Although the target of the \"enters\" ability must not be a land, lands with the same name as that permanent will be exiled.")
        ruling("2012-10-01", "The \"enters\" ability has only one target. The other permanents with that name aren't targeted. For example, a permanent with protection from white will be exiled if it has the same name as the target nonland permanent.")
        ruling("2012-10-01", "If the target nonland permanent is an illegal target when the \"enters\" ability tries to resolve, it won't resolve and none of its effects will happen. No permanents will be exiled, including those with the same name as the target.")
        ruling("2012-10-01", "If Detention Sphere leaves the battlefield before its \"enters\" ability has resolved, its leaves-the-battlefield ability will trigger and do nothing. Then the \"enters\" ability will resolve and exile the targeted nonland permanent and other permanents with that name indefinitely.")
    }
}
