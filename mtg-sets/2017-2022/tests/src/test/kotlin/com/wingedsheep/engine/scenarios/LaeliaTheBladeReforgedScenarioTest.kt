package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c21.cards.LaeliaTheBladeReforged
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class LaeliaTheBladeReforgedScenarioTest : FunSpec({

    val exileTwo = card("Laelia Test Exile Two") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Patterns.Exile.impulse(count = 2) }
    }

    val exileGraveyard = card("Laelia Test Exile Graveyard") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(
                    source = CardSource.FromZone(
                        zone = Zone.GRAVEYARD,
                        filter = GameObjectFilter.Any,
                    ),
                    storeAs = "graveyardCards",
                ),
                MoveCollectionEffect(
                    from = "graveyardCards",
                    destination = CardDestination.ToZone(Zone.EXILE),
                ),
            )
        }
    }

    fun newDriver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(LaeliaTheBladeReforged, exileTwo, exileGraveyard))
        initMirrorMatch(deck = Deck.of("Mountain" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(driver: GameTestDriver) {
        repeat(40) {
            when {
                driver.state.pendingDecision != null -> driver.autoResolveDecision()
                driver.state.stack.isNotEmpty() -> driver.bothPass()
                else -> return
            }
        }
        error("Stack did not settle")
    }

    fun plusOneCounters(driver: GameTestDriver, laelia: EntityId): Int =
        driver.state.getEntity(laelia)
            ?.get<CountersComponent>()
            ?.counters
            ?.get(CounterType.PLUS_ONE_PLUS_ONE)
            ?: 0

    test("attacking exiles the top card and gives Laelia one counter") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val laelia = driver.putCreatureOnBattlefield(player, "Laelia, the Blade Reforged")
        val topCard = driver.putCardOnTopOfLibrary(player, "Grizzly Bears")

        withClue("haste allows Laelia to attack immediately") {
            driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
            driver.declareAttackers(player, listOf(laelia), opponent).error shouldBe null
        }
        resolveStack(driver)

        driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain topCard
        plusOneCounters(driver, laelia) shouldBe 1
    }

    test("several cards exiled from the library in one batch give only one counter") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val laelia = driver.putCreatureOnBattlefield(player, "Laelia, the Blade Reforged")
        val first = driver.putCardOnTopOfLibrary(player, "Grizzly Bears")
        val second = driver.putCardOnTopOfLibrary(player, "Hill Giant")
        val spell = driver.putCardInHand(player, "Laelia Test Exile Two")

        driver.castSpell(player, spell).error shouldBe null
        resolveStack(driver)

        driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain first
        driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain second
        plusOneCounters(driver, laelia) shouldBe 1
    }

    test("cards exiled from your graveyard count, but an opponent's cards do not") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val laelia = driver.putCreatureOnBattlefield(player, "Laelia, the Blade Reforged")
        val mine = driver.putCardInGraveyard(player, "Grizzly Bears")
        val theirs = driver.putCardInGraveyard(opponent, "Hill Giant")
        val mySpell = driver.putCardInHand(player, "Laelia Test Exile Graveyard")
        val theirSpell = driver.putCardInHand(opponent, "Laelia Test Exile Graveyard")

        driver.castSpell(player, mySpell).error shouldBe null
        resolveStack(driver)
        driver.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContain mine
        plusOneCounters(driver, laelia) shouldBe 1

        driver.passPriority(player).error shouldBe null
        driver.castSpell(opponent, theirSpell).error shouldBe null
        resolveStack(driver)
        driver.state.getZone(ZoneKey(opponent, Zone.EXILE)) shouldContain theirs
        plusOneCounters(driver, laelia) shouldBe 1
    }
})
