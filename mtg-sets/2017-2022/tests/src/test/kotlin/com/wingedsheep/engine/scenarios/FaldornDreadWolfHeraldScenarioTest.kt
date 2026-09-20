package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.clb.cards.FaldornDreadWolfHerald
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class FaldornDreadWolfHeraldScenarioTest : FunSpec({

    val putExiledLandOntoBattlefield = card("Faldorn Test Exile Ramp") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(
                    source = CardSource.FromZone(
                        zone = Zone.EXILE,
                        filter = GameObjectFilter.Land,
                    ),
                    storeAs = "exiledLands",
                ),
                MoveCollectionEffect(
                    from = "exiledLands",
                    destination = CardDestination.ToZone(Zone.BATTLEFIELD),
                ),
            )
        }
    }

    val putGraveyardLandOntoBattlefield = card("Faldorn Test Graveyard Ramp") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(
                    source = CardSource.FromZone(
                        zone = Zone.GRAVEYARD,
                        filter = GameObjectFilter.Land,
                    ),
                    storeAs = "graveyardLands",
                ),
                MoveCollectionEffect(
                    from = "graveyardLands",
                    destination = CardDestination.ToZone(Zone.BATTLEFIELD),
                ),
            )
        }
    }

    fun newDriver() = GameTestDriver().apply {
        registerCards(
            TestCards.all + listOf(
                FaldornDreadWolfHerald,
                putExiledLandOntoBattlefield,
                putGraveyardLandOntoBattlefield,
            )
        )
        initMirrorMatch(deck = Deck.of("Mountain" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(driver: GameTestDriver) {
        repeat(30) {
            when {
                driver.state.pendingDecision != null -> driver.autoResolveDecision()
                driver.state.stack.isNotEmpty() -> driver.bothPass()
                else -> return
            }
        }
        error("Stack did not settle")
    }

    fun wolfCount(driver: GameTestDriver, player: EntityId): Int =
        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).count { entityId ->
            driver.state.getEntity(entityId)?.get<CardComponent>()?.name == "Wolf Token"
        }

    test("the activated ability pays every cost and its exiled spell creates a Wolf") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val faldorn = driver.putCreatureOnBattlefield(player, "Faldorn, Dread Wolf Herald")
        driver.removeSummoningSickness(faldorn)
        val discarded = driver.putCardInHand(player, "Hill Giant")
        val exiledBear = driver.putCardOnTopOfLibrary(player, "Grizzly Bears")
        driver.giveMana(player, Color.GREEN, 1)

        val activation = driver.submit(
            ActivateAbility(
                playerId = player,
                sourceId = faldorn,
                abilityId = FaldornDreadWolfHerald.activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(discardedCards = listOf(discarded)),
            )
        )
        withClue("Faldorn's activation should accept mana, tap, and discard payment") {
            activation.error shouldBe null
        }
        resolveStack(driver)

        driver.isTapped(faldorn) shouldBe true
        driver.state.getZone(ZoneKey(player, Zone.GRAVEYARD)) shouldContain discarded
        driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain exiledBear

        driver.giveMana(player, Color.GREEN, 2)
        driver.castSpell(player, exiledBear).error shouldBe null
        resolveStack(driver)

        wolfCount(driver, player) shouldBe 1
        (driver.findPermanent(player, "Grizzly Bears") != null) shouldBe true
    }

    test("a land put onto the battlefield from exile creates a Wolf") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        driver.putCreatureOnBattlefield(player, "Faldorn, Dread Wolf Herald")
        val forest = driver.putCardInExile(player, "Forest")
        val ramp = driver.putCardInHand(player, "Faldorn Test Exile Ramp")

        driver.castSpell(player, ramp).error shouldBe null
        resolveStack(driver)

        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)) shouldContain forest
        withClue("the spell was cast from hand, so only the exile-to-battlefield land event fires") {
            wolfCount(driver, player) shouldBe 1
        }
    }

    test("ordinary hand casts and lands entering from other zones do not create Wolves") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        driver.putCreatureOnBattlefield(player, "Faldorn, Dread Wolf Herald")
        val bear = driver.putCardInHand(player, "Grizzly Bears")
        val forest = driver.putCardInGraveyard(player, "Forest")
        val ramp = driver.putCardInHand(player, "Faldorn Test Graveyard Ramp")
        driver.giveMana(player, Color.GREEN, 2)

        driver.castSpell(player, bear).error shouldBe null
        resolveStack(driver)
        driver.castSpell(player, ramp).error shouldBe null
        resolveStack(driver)

        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)) shouldContain forest
        wolfCount(driver, player) shouldBe 0
    }
})
