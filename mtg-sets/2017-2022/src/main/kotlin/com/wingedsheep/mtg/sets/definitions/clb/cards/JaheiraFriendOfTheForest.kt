package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

// Choose a Background modifies pregame commander designation. The current deck model
// supports a single commander (see CommanderEligibility); Jaheira is playable in the 99.
val JaheiraFriendOfTheForest = card("Jaheira, Friend of the Forest") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Human Elf Druid"
    oracleText = "Tokens you control have \"{T}: Add {G}.\"\nChoose a Background (You can have a Background as a second commander.)"
    power = 2
    toughness = 3

    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbility(
                id = AbilityId.next(),
                cost = Costs.Tap,
                effect = Effects.AddMana(Color.GREEN),
                isManaAbility = true,
                timing = TimingRule.ManaAbility
            ),
            filter = GroupFilter(GameObjectFilter.Token.youControl())
        )
    }

    metadata {
        collectorNumber = "237"
        artist = "Mila Pesic"
        imageUri = "https://cards.scryfall.io/normal/front/f/e/feb7ad1b-4466-48f7-b46d-cc83d4e22b51.jpg?1783922710"
        rarity = Rarity.RARE
        ruling("2022-06-10", "Jaheira's ability gives the mana ability to all tokens you control, not just creature tokens.")
        ruling("2022-06-10", "You won't be able to activate the mana abilities of creature tokens you control on the turn they're created unless they have haste. Conversely, you will be able to activate the mana abilities of noncreature tokens you control on the turn they're created (provided they're untapped).")
        ruling("2022-06-10", "Choose a Background is a variant of the partner ability. You may have two commanders if one of them is a legendary creature with the choose a background ability and the other is a legendary Background enchantment. Backgrounds and cards with choose a Background do not interact with cards which have any other partner ability.")
        ruling("2022-06-10", "If your Commander deck has two commanders, you can include only cards whose own color identities are also found in your commanders’ combined color identities.")
        ruling("2022-06-10", "Both commanders start in the command zone, and the remaining 98 cards (or 58 cards in a Commander Draft game) of your deck are shuffled to become your library.")
        ruling("2022-06-10", "If your commander loses the choose a Background ability or stops being a Background during the game, as appropriate, it is still your commander.")
        ruling("2022-06-10", "Once the game begins, your two commanders are tracked separately. If you cast one, you won’t have to pay an additional {2} the first time you cast the other. A player loses the game after having been dealt 21 combat damage from any one of them, not from both of them combined (although your Background won’t usually be a creature anyway).")
        ruling("2022-06-10", "If something refers to your commander while you have two commanders, it refers to one of them of your choice. If you are instructed to perform an action on your commander (e.g. put it from the command zone into your hand due to Command Beacon), you choose one of your commanders at the time the effect happens.")
        ruling("2022-06-10", "An effect that checks whether you control your commander is satisfied if you control one or both of your two commanders.")
        ruling("2022-06-10", "You can choose two commanders that are the same color or colors.")
        ruling("2022-06-10", "If a card refers to a commander creature you own, a Background won't usually be counted or included for that effect. If another spell or ability causes your Background to become a creature, however, it will be included. Any effect that refers to your commander or a commander you own or control without specifying creature will apply to a Background that is your commander, as appropriate.")
        ruling("2022-06-10", "If you control a Background that grants an ability to commander creatures you own, and you own more than one commander creature, each of them will have that ability.")
    }
}
