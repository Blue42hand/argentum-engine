package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.mechanics.mana.IntrinsicManaAbilities
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lea.cards.UndergroundSea
import com.wingedsheep.mtg.sets.definitions.uds.cards.BubblingMuck
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BubblingMuckScenarioTest : FunSpec({
    val projector = StateProjector()

    fun createDriver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCard(BubblingMuck)
        registerCard(UndergroundSea)
        initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun castMuck(driver: GameTestDriver, player: EntityId) {
        driver.giveMana(player, Color.BLACK, 1)
        driver.castSpell(player, driver.putCardInHand(player, "Bubbling Muck"))
        driver.bothPass()
    }

    fun tapForMana(driver: GameTestDriver, player: EntityId, land: EntityId) {
        val ability = IntrinsicManaAbilities.forEntity(driver.state, projector.project(driver.state), land).first()
        driver.submitSuccess(ActivateAbility(playerId = player, sourceId = land, abilityId = ability.id))
    }

    fun pool(driver: GameTestDriver, player: EntityId): ManaPoolComponent? =
        driver.state.getEntity(player)?.get<ManaPoolComponent>()

    test("a Swamp that arrives after resolution adds an extra black mana") {
        val driver = createDriver()
        val alice = driver.activePlayer!!
        castMuck(driver, alice)

        val swamp = driver.putLandOnBattlefield(alice, "Swamp")
        tapForMana(driver, alice, swamp)
        pool(driver, alice)?.black shouldBe 2
    }

    test("a non-Swamp does not add black mana") {
        val driver = createDriver()
        val alice = driver.activePlayer!!
        castMuck(driver, alice)

        val forest = driver.putLandOnBattlefield(alice, "Forest")
        tapForMana(driver, alice, forest)
        pool(driver, alice)?.green shouldBe 1
        pool(driver, alice)?.black shouldBe 0
    }

    test("a nonbasic land with the Swamp subtype also adds black mana") {
        val driver = createDriver()
        val alice = driver.activePlayer!!
        castMuck(driver, alice)

        val sea = driver.putLandOnBattlefield(alice, "Underground Sea")
        tapForMana(driver, alice, sea)
        val mana = pool(driver, alice)!!
        (mana.blue + mana.black) shouldBe 2
        (mana.black >= 1) shouldBe true
    }

    test("the opponent also receives extra black mana from a Swamp") {
        val driver = createDriver()
        val alice = driver.activePlayer!!
        val bob = driver.getOpponent(alice)
        castMuck(driver, alice)

        val swamp = driver.putLandOnBattlefield(bob, "Swamp")
        driver.passPriority(alice)
        tapForMana(driver, bob, swamp)
        pool(driver, bob)?.black shouldBe 2
    }
})
