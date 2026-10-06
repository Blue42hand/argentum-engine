package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EverflowingChaliceScenarioTest : ScenarioTestBase() {
    init {
        fun board(lands: Int) = scenario().withPlayers()
            .withCardInHand(1, "Everflowing Chalice")
            .withLandsOnBattlefield(1, "Forest", lands)
            .withCardInLibrary(1, "Forest")
            .withCardInLibrary(2, "Forest")
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

        test("unkicked Chalice enters empty and its mana ability produces zero") {
            val game = board(0).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()

            game.execute(CastSpell(game.player1Id, card)).error shouldBe null
            game.resolveStack()
            val chalice = game.findPermanent("Everflowing Chalice")!!
            (game.state.getEntity(chalice)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) ?: 0) shouldBe 0

            val ability = cardRegistry.requireCard("Everflowing Chalice").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, chalice, ability)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 0
        }

        test("one kick buys one entry counter and one colorless mana") {
            val game = board(2).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 1)).error shouldBe null
            game.resolveStack()
            val chalice = game.findPermanent("Everflowing Chalice")!!
            game.state.getEntity(chalice)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 1

            val ability = cardRegistry.requireCard("Everflowing Chalice").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, chalice, ability)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 1
        }

        test("two kicks buy two counters and two colorless mana") {
            val game = board(4).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 2)).error shouldBe null
            game.resolveStack()
            val chalice = game.findPermanent("Everflowing Chalice")!!
            game.state.getEntity(chalice)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 2

            val ability = cardRegistry.requireCard("Everflowing Chalice").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, chalice, ability)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 2
        }

        test("two kicks are unavailable with only three mana") {
            val game = board(3).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 2)).error shouldNotBe null
            game.findPermanent("Everflowing Chalice") shouldBe null
        }

        test("eleven kicks are offered and paid when twenty two mana is available") {
            val game = board(22).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()
            val offered = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id)
                .mapNotNull { (it.action as? CastSpell)?.takeIf { cast -> cast.cardId == card &&
                    cast.declaredCostSlot == ChoiceSlot.KICKED }?.declaredCostTimes }
            offered shouldBe (1..11).toList()

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 11)).error shouldBe null
            game.resolveStack()
            val chalice = game.findPermanent("Everflowing Chalice")!!
            game.state.getEntity(chalice)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 11
        }

        test("large affordable counts use one compact offer and still pay the selected count") {
            val game = board(66).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()
            val offers = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id)
                .filter { (it.action as? CastSpell)?.cardId == card &&
                    (it.action as CastSpell).declaredCostSlot == ChoiceSlot.KICKED }
            offers.filter { it.maxOptionalCostTimes == null }
                .map { (it.action as CastSpell).declaredCostTimes } shouldBe (1..32).toList()
            val compact = offers.single { it.maxOptionalCostTimes != null }
            compact.maxOptionalCostTimes shouldBe 33
            compact.optionalManaCostString shouldBe "{2}"
            compact.manaCostString shouldBe "{2}"

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 33)).error shouldBe null
            game.resolveStack()
            val chalice = game.findPermanent("Everflowing Chalice")!!
            game.state.getEntity(chalice)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 33
        }

        test("malformed and unaffordable kick counts are rejected before cost expansion") {
            val game = board(2).build()
            val card = game.findCardsInHand(1, "Everflowing Chalice").single()
            game.state = game.state.withLifeTotal(game.player1Id, Int.MAX_VALUE)
            for (count in listOf(-1, 0, 2, 1_000_000, Int.MAX_VALUE)) {
                game.execute(CastSpell(game.player1Id, card,
                    declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = count)).error shouldNotBe null
            }
            game.findPermanent("Everflowing Chalice") shouldBe null
        }

        test("exile permission offers a compact count above thirty two") {
            val game = scenario().withPlayers()
                .withCardInExile(1, "Everflowing Chalice")
                .withLandsOnBattlefield(1, "Forest", 66)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val card = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE)).single()
            game.state = game.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(card), controllerId = game.player1Id,
                timestamp = game.state.timestamp
            ))

            val offered = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id)
                .filter { (it.action as? CastSpell)?.cardId == card &&
                    (it.action as CastSpell).declaredCostSlot == ChoiceSlot.KICKED }
            offered.filter { it.maxOptionalCostTimes == null }
                .map { (it.action as CastSpell).declaredCostTimes } shouldBe (1..32).toList()
            offered.single { it.maxOptionalCostTimes != null }.maxOptionalCostTimes shouldBe 33
        }

        test("a free cast still owes every multikicker payment") {
            val game = scenario().withPlayers()
                .withCardInExile(1, "Everflowing Chalice")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val card = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE)).single()
            game.state = game.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(card), controllerId = game.player1Id,
                timestamp = game.state.timestamp
            )).updateEntity(card) { it.with(PlayWithoutPayingCostComponent(game.player1Id)) }

            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 1)).error shouldNotBe null
            game.state = game.state.updateEntity(game.player1Id) { player ->
                val pool = player.get<ManaPoolComponent>() ?: ManaPoolComponent()
                player.with(pool.copy(colorless = 1))
            }
            game.execute(CastSpell(game.player1Id, card,
                declaredCostSlot = ChoiceSlot.KICKED, declaredCostTimes = 1)).error shouldBe null
        }
    }
}
