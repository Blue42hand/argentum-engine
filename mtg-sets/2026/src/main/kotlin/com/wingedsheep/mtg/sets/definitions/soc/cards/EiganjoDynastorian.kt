package com.wingedsheep.mtg.sets.definitions.soc.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val EiganjoDynastorian = card("Eiganjo Dynastorian") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Fox Advisor"
    oracleText = "Vigilance\nWhenever you attack with two or more creatures, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)"
    power = 2
    toughness = 3

    keywords(Keyword.VIGILANCE)
    triggeredAbility {
        trigger = Triggers.you.attacks(minAttackers = 2)
        effect = Effects.BecomePrepared()
    }
    prepare("Replenish") {
        manaCost = "{3}{W}"
        typeLine = "Sorcery"
        oracleText = "Return all enchantment cards from your graveyard to the battlefield."
        spell {
            effect = Effects.Pipeline {
                val enchantments = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Enchantment))
                move(enchantments, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "13"
        artist = "Aldo Domínguez"
        imageUri = "https://cards.scryfall.io/normal/front/0/9/09d1f048-f411-4677-a0dc-dbddd06fe694.jpg?1783903863"
        ruling("2026-03-20", "Once Eiganjo Dynastorian's last ability triggers, it doesn't matter what happens to the attacking creatures in response.")
        ruling("2026-03-20", "While copies of spells in zones other than the stack cease to exist when state-based actions are checked, this does not apply to copies of prepare spells in exile that were created as a creature became prepared.")
        ruling("2026-03-20", "A preparation card is a creature card in every zone. For example, while it's in your graveyard, Emeritus of Ideation is a blue creature whose mana value is 5. It can't be the target of Zealous Lorecaster's triggered ability (\"When this creature enters, return target instant or sorcery card from your graveyard to your hand.\").")
        ruling("2026-03-20", "Only the current controller of a prepared creature can cast the copy of its prepare spell in exile. It doesn't matter who owns that creature or who controlled it when it became prepared.")
        ruling("2026-03-20", "If a prepared creature stops being a creature, it will still be prepared, and the copy of its prepare spell will remain in exile. That permanent's controller will still be able to cast it. The same is true if a prepared creature becomes a copy of something else, even if it's then a permanent without a prepare spell.")
        ruling("2026-03-20", "Casting a copy of a prepare spell from exile isn't casting it for an alternative cost. Effects that allow you to cast a spell for an alternative cost or without paying its mana cost may allow you to apply those to a copy of a prepare spell cast from exile.")
        ruling("2026-03-20", "Preparation cards can only be cast with their base characteristics.")
        ruling("2026-03-20", "A creature without a prepare spell can't become prepared.")
        ruling("2026-03-20", "Creating a copy of a creature's prepare spell in exile ignores copy exceptions applied to that creature that would affect the copiable values of that spell. For example, say a player casts Croaking Counterpart (a sorcery that says \"Create a token that's a copy of target non-Frog creature, except it's a 1/1 green Frog.\") targeting Goblin Glasswright. The token copy of Goblin Glasswright will enter prepared as a 1/1 green Frog creature, but the copy of Craft with Pride in exile will still be a red sorcery. The copy of Craft with Pride won't be green, a creature, or a Frog, and it won't have power or toughness.")
        ruling("2026-03-20", "A creature with a prepare spell can't become prepared more than once at the same time. For example, Emeritus of Ideation is on the battlefield and is prepared. When it attacks, its last ability triggers. When that ability resolves, its controller may still exile eight cards from their graveyard, but doing so won't cause Emeritus of Ideation to become prepared a second time or create a second copy of Ancestral Recall in exile.")
        ruling("2026-03-20", "If a prepare spell with one or more targets has no legal targets when it tries to resolve, it won't resolve and none of its effects will happen. Since the spell was cast, the associated permanent will still not be prepared.")
        ruling("2026-03-20", "If a prepared creature loses all abilities, it won't stop being prepared, and nothing will happen to its alternative characteristics or to the copy of its prepare spell in exile.")
        ruling("2026-03-20", "If an effect instructs you to choose a card name, you may choose the alternative prepare spell's name. Consider only the alternative characteristics to determine whether that is an appropriate name to choose.")
        ruling("2026-03-20", "An Aura put onto the battlefield this way can't enchant an enchantment that's also being put onto the battlefield with Replenish.")
        ruling("2026-03-20", "As an effect causes a creature with a prepare spell to become prepared (including effects that state that a creature \"enters prepared\"), that creature's controller creates a copy of that creature's prepare spell in exile. That copy remains in exile for as long as that permanent is on the battlefield and is prepared. That creature's controller may cast that copy as long as it remains in exile. As they cast it, that creature stops being prepared.")
        ruling("2026-03-20", "Being prepared isn't a copiable value. If a permanent becomes a copy of a prepared creature, it won't be prepared. That permanent will have the alternative characteristics, though, and some other effect could cause it to become prepared later.")
        ruling("2026-03-20", "If an effect causes a creature to become unprepared, a prepared creature stops being prepared and the associated copy of its prepare spell in exile ceases to exist. If it wasn't prepared at that time, nothing happens.")
        ruling("2026-03-20", "If an Aura is put onto the battlefield without being cast, the Aura's controller-to-be chooses what it will enchant as it enters the battlefield. An Aura put onto the battlefield this way doesn't target anything (so it could be attached to an opponent's permanent with hexproof, for example), but the Aura's enchant ability restricts what it can be attached to. If the Aura can't legally be attached to anything, it remains in its current zone.")
    }
}
