package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Rakdos Charm — Return to Ravnica #184. */
val RakdosCharm = card("Rakdos Charm") {
    manaCost = "{B}{R}"
    colorIdentity = "BR"
    typeLine = "Instant"
    oracleText = "Choose one —\n• Exile target player's graveyard.\n• Destroy target artifact.\n• Each creature deals 1 damage to its controller."

    spell {
        modal {
            mode("Exile target player's graveyard") {
                target(Targets.Player)
                effect = Effects.Pipeline {
                    val graveyard = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.TargetPlayer))
                    move(graveyard, CardDestination.ToZone(Zone.EXILE))
                }
            }
            mode("Destroy target artifact") {
                val artifact = target(TargetFilter.Artifact)
                effect = Effects.Destroy(artifact)
            }
            mode("Each creature deals 1 damage to its controller") {
                effect = Effects.ForEachInGroup(
                    GroupFilter.AllCreatures,
                    Effects.DealDamage(
                        1,
                        target = EffectTarget.PlayerRef(Player.ControllerOfIterationEntity),
                        damageSource = EffectTarget.IterationEntity,
                    ),
                )
            }
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "184"
        artist = "Zoltan Boros"
        imageUri = "https://cards.scryfall.io/normal/front/0/f/0fcd4394-d22d-4eec-ad73-ffaf10ad60de.jpg?1783940335"
    }
}
