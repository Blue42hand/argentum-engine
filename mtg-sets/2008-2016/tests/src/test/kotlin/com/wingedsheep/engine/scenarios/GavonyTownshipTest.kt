package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.isd.cards.GavonyTownship
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GavonyTownshipTest : FunSpec({
    val counterAbility = GavonyTownship.activatedAbilities[1]

    fun counters(driver: GameTestDriver, permanent: EntityId): Int =
        driver.state.getEntity(permanent)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    test("puts a +1/+1 counter on each creature you control and no opposing creatures") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + GavonyTownship)
        driver.initMirrorMatch(Deck.of("Forest" to 20, "Plains" to 20), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = driver.activePlayer!!
        val opponent = driver.getOpponent(me)
        val township = driver.putPermanentOnBattlefield(me, "Gavony Township")
        val bears = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        val giant = driver.putCreatureOnBattlefield(me, "Hill Giant")
        val opposingBears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        driver.giveColorlessMana(me, 2)
        driver.giveMana(me, Color.GREEN, 1)
        driver.giveMana(me, Color.WHITE, 1)
        driver.submit(
            ActivateAbility(playerId = me, sourceId = township, abilityId = counterAbility.id)
        ).isSuccess shouldBe true
        driver.bothPass()

        counters(driver, bears) shouldBe 1
        counters(driver, giant) shouldBe 1
        counters(driver, opposingBears) shouldBe 0
    }
})
