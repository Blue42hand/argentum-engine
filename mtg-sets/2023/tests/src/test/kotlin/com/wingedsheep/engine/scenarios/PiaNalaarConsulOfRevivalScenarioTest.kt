package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mat.MarchOfTheMachineAftermathSet
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.GrantMayPlayFromExileEffect
import com.wingedsheep.sdk.scripting.effects.MayPlayExpiry
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PiaNalaarConsulOfRevivalScenarioTest : FunSpec({

    val impulse = card("Pia Test Impulse") {
        manaCost = "{R}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1)),
                    storeAs = "piaTestExiled",
                ),
                MoveCollectionEffect(
                    from = "piaTestExiled",
                    destination = CardDestination.ToZone(Zone.EXILE),
                ),
                GrantMayPlayFromExileEffect(
                    from = "piaTestExiled",
                    expiry = MayPlayExpiry.EndOfTurn,
                ),
            )
        }
    }

    val testThopter = card("Pia Test Thopter") {
        manaCost = "{1}"
        typeLine = "Artifact Creature — Thopter"
        power = 1
        toughness = 1
    }

    fun newDriver() = GameTestDriver().apply {
        registerCards(TestCards.all + MarchOfTheMachineAftermathSet.cards)
        registerCard(impulse)
        registerCard(testThopter)
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

    fun exileTopWithPermission(
        driver: GameTestDriver,
        player: EntityId,
        cardName: String,
    ): EntityId {
        val exiled = driver.putCardOnTopOfLibrary(player, cardName)
        val impulseCard = driver.putCardInHand(player, "Pia Test Impulse")
        driver.giveMana(player, Color.RED, 1)
        driver.castSpell(player, impulseCard).isSuccess shouldBe true
        resolveStack(driver)
        driver.getExile(player) shouldContain exiled
        return exiled
    }

    test("casting a spell from exile creates one flying hasty Thopter") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        driver.putPermanentOnBattlefield(player, "Pia Nalaar, Consul of Revival")
        val exiledBear = exileTopWithPermission(driver, player, "Grizzly Bears")

        driver.findPermanent(player, "Thopter Token") shouldBe null
        driver.giveMana(player, Color.GREEN, 2)
        driver.castSpell(player, exiledBear).isSuccess shouldBe true
        resolveStack(driver)

        val thopter = driver.findPermanent(player, "Thopter Token")
            ?: error("Pia should create a Thopter token")
        driver.state.projectedState.hasKeyword(thopter, Keyword.FLYING) shouldBe true
        driver.state.projectedState.hasKeyword(thopter, Keyword.HASTE) shouldBe true
        (driver.findPermanent(player, "Grizzly Bears") != null) shouldBe true
    }

    test("playing a land from exile creates one Thopter while a hand cast does not trigger") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        driver.putPermanentOnBattlefield(player, "Pia Nalaar, Consul of Revival")
        val exiledForest = exileTopWithPermission(driver, player, "Forest")

        driver.findPermanent(player, "Thopter Token") shouldBe null
        driver.submit(PlayLand(player, exiledForest)).isSuccess shouldBe true
        resolveStack(driver)

        (driver.findPermanent(player, "Thopter Token") != null) shouldBe true
    }

    test("Pia grants haste only to Thopters you control") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.putPermanentOnBattlefield(player, "Pia Nalaar, Consul of Revival")
        val yours = driver.putCreatureOnBattlefield(player, "Pia Test Thopter")
        val theirs = driver.putCreatureOnBattlefield(opponent, "Pia Test Thopter")
        val bear = driver.putCreatureOnBattlefield(player, "Grizzly Bears")

        driver.state.projectedState.hasKeyword(yours, Keyword.HASTE) shouldBe true
        driver.state.projectedState.hasKeyword(theirs, Keyword.HASTE) shouldBe false
        driver.state.projectedState.hasKeyword(bear, Keyword.HASTE) shouldBe false
    }
})
