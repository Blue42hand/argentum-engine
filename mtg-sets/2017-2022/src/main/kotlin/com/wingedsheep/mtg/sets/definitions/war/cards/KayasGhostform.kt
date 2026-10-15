package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val KayasGhostform = card("Kaya's Ghostform") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature or planeswalker you control\nWhen enchanted permanent dies or is put into exile, return that card to the battlefield under your control."

    auraTarget = TargetObject(filter = TargetFilter((GameObjectFilter.Creature or GameObjectFilter.Planeswalker).youControl()))
    triggeredAbility {
        trigger = Triggers.or(Triggers.attached.dies(), Triggers.attached.leaves(to = Zone.EXILE))
        effect = Effects.PutOntoBattlefieldUnderYourControl(EffectTarget.TriggeringEntity)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "94"
        artist = "Johan Grenier"
        imageUri = "https://cards.scryfall.io/normal/front/9/8/98f3be88-24e2-433f-9138-f0cb59d09a0f.jpg?1783933444"
        flavorText = "The Eternals found some sparks to be quite slippery."
        ruling("2019-05-03", "Kaya’s Ghostform can enchant a token, but its last ability won’t return the token to the battlefield.")
        ruling("2019-05-03", "Kaya’s Ghostform is put into your graveyard once the enchanted permanent leaves the battlefield. It doesn’t return to the battlefield with that permanent.")
        ruling("2019-05-03", "If an effect exiles the enchanted permanent and immediately returns it to the battlefield, the last ability of Kaya’s Ghostform triggers but will have no effect. If an effect exiles the enchanted permanent and would return it to the battlefield at a later time, Kaya’s Ghostform will return that card to the battlefield and it won’t be returned later.")
        ruling("2019-05-03", "If another player gains control of the enchanted permanent, Kaya’s Ghostform will be put into your graveyard.")
        ruling("2019-05-03", "If the enchanted permanent is put into a graveyard or exile but leaves that zone before the last ability of Kaya’s Ghostform resolves, that card stays in its new zone, even if that zone is also a graveyard or exile. You don’t return it to the battlefield.")
        ruling("2019-05-03", "If Kaya’s Ghostform and the enchanted permanent are both put into graveyards and/or exiled at the same time, the enchanted permanent will be returned to the battlefield.")
        ruling("2019-05-03", "If Kaya’s Ghostform enchants a permanent you control but don’t own, that permanent will return to the battlefield under your control when it dies or is exiled. In a multiplayer game, if a player leaves the game, all cards that player owns leave as well. If you leave the game, any creatures you control from Kaya’s Ghostform’s effect are exiled.")
    }
}
