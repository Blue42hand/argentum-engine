package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c16.cards.CrystallineCrawler
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CrystallineCrawlerScenarioTest : FunSpec({
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + CrystallineCrawler)
        it.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun counters(game: GameTestDriver, crawler: EntityId) =
        game.state.getEntity(crawler)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    test("converge counts distinct colors spent on the generic cost, not colorless mana") {
        val game = driver()
        val you = game.player1
        val crawler = game.putCardInHand(you, "Crystalline Crawler")
        game.giveMana(you, Color.WHITE, 2)
        game.giveMana(you, Color.BLUE, 1)
        game.giveColorlessMana(you, 1)

        game.castSpell(you, crawler).error shouldBe null
        game.bothPass()

        counters(game, crawler) shouldBe 2
    }

    test("removing a counter is a mana cost and adds the chosen color without using the stack") {
        val game = driver()
        val you = game.player1
        val crawler = game.putCardInHand(you, "Crystalline Crawler")
        game.giveMana(you, Color.WHITE, 2)
        game.giveMana(you, Color.BLUE, 2)
        game.castSpell(you, crawler).error shouldBe null
        game.bothPass()
        counters(game, crawler) shouldBe 2

        game.submit(
            ActivateAbility(you, crawler, CrystallineCrawler.activatedAbilities[0].id, manaColorChoice = Color.RED)
        ).error shouldBe null
        counters(game, crawler) shouldBe 1
        game.state.getEntity(you)?.get<ManaPoolComponent>()?.red shouldBe 1
        game.stackSize shouldBe 0
    }

    test("zero-color entry has no counter to spend until the tap ability resolves") {
        val game = driver()
        val you = game.player1
        val crawler = game.putCardInHand(you, "Crystalline Crawler")
        game.giveColorlessMana(you, 4)
        game.castSpell(you, crawler).error shouldBe null
        game.bothPass()
        counters(game, crawler) shouldBe 0

        game.submit(
            ActivateAbility(you, crawler, CrystallineCrawler.activatedAbilities[0].id, manaColorChoice = Color.GREEN)
        ).error shouldNotBe null

        // The creature is summoning sick this turn. Test its tap ability after it has stayed under
        // our control through a full turn cycle.
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)

        game.submit(ActivateAbility(you, crawler, CrystallineCrawler.activatedAbilities[1].id)).error shouldBe null
        game.bothPass()
        counters(game, crawler) shouldBe 1
    }
})
