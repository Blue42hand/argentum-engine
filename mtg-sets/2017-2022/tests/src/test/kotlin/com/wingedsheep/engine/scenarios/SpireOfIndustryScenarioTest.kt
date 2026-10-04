package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.aer.cards.SpireOfIndustry
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SpireOfIndustryScenarioTest : FunSpec({
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + SpireOfIndustry)
        it.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("colorless mana works without an artifact and costs no life") {
        val game = driver()
        val you = game.player1
        val spire = game.putPermanentOnBattlefield(you, "Spire of Industry")

        game.submit(ActivateAbility(you, spire, SpireOfIndustry.activatedAbilities[0].id)).error shouldBe null
        game.state.getEntity(you)?.get<ManaPoolComponent>()?.colorless shouldBe 1
        game.getLifeTotal(you) shouldBe 20
    }

    test("colored mana requires your artifact and pays one life as a cost") {
        val game = driver()
        val you = game.player1
        val opponent = game.getOpponent(you)
        val spire = game.putPermanentOnBattlefield(you, "Spire of Industry")
        val coloredAbility = SpireOfIndustry.activatedAbilities[1]

        game.submit(ActivateAbility(you, spire, coloredAbility.id, manaColorChoice = Color.BLUE)).error shouldNotBe null
        game.getLifeTotal(you) shouldBe 20

        game.putPermanentOnBattlefield(opponent, "Ornithopter")
        game.submit(ActivateAbility(you, spire, coloredAbility.id, manaColorChoice = Color.BLUE)).error shouldNotBe null
        game.getLifeTotal(you) shouldBe 20

        game.putPermanentOnBattlefield(you, "Ornithopter")
        game.submit(ActivateAbility(you, spire, coloredAbility.id, manaColorChoice = Color.BLUE)).error shouldBe null
        game.getLifeTotal(you) shouldBe 19
        game.state.getEntity(you)?.get<ManaPoolComponent>()?.blue shouldBe 1
    }
})
