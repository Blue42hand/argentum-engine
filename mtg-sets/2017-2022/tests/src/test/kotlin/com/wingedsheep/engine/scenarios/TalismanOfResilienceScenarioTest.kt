package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh1.cards.TalismanOfResilience
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Talisman of Resilience — the colored mana ability deals preventable damage from this artifact. */
class TalismanOfResilienceScenarioTest : FunSpec({
    fun newGame(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.registerCard(TalismanOfResilience)
        it.initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    listOf("colorless", "black", "green").forEachIndexed { index, color ->
        test("$color mana is immediate, taps once, and ${if (index == 0) "does not hurt" else "deals one damage to"} the controller") {
            val d = newGame()
            val talisman = d.putPermanentOnBattlefield(d.player1, "Talisman of Resilience")
            val first = d.submit(ActivateAbility(d.player1, talisman, TalismanOfResilience.activatedAbilities[index].id))
            first.error shouldBe null

            val pool = d.state.getEntity(d.player1)?.get<ManaPoolComponent>()!!
            when (index) {
                0 -> pool.colorless shouldBe 1
                1 -> pool.black shouldBe 1
                else -> pool.green shouldBe 1
            }
            d.getLifeTotal(d.player1) shouldBe if (index == 0) 20 else 19
            d.isTapped(talisman) shouldBe true
            d.state.stack.isEmpty() shouldBe true

            // A second activation cannot create mana or repeat the damage while tapped.
            d.submit(ActivateAbility(d.player1, talisman, TalismanOfResilience.activatedAbilities[index].id)).error shouldNotBe null
            d.getLifeTotal(d.player1) shouldBe if (index == 0) 20 else 19
        }
    }

    test("a changed controller receives the mana and takes the damage, while the owner does not") {
        val d = newGame()
        val talisman = d.putPermanentOnBattlefield(d.player2, "Talisman of Resilience")
        d.replaceState(d.state.updateEntity(talisman) { it.with(ControllerComponent(d.player1)) })

        d.submit(ActivateAbility(d.player1, talisman, TalismanOfResilience.activatedAbilities[1].id)).error shouldBe null

        d.state.getEntity(d.player1)?.get<ManaPoolComponent>()?.black shouldBe 1
        d.state.getEntity(d.player2)?.get<ManaPoolComponent>()?.black shouldBe 0
        d.getLifeTotal(d.player1) shouldBe 19
        d.getLifeTotal(d.player2) shouldBe 20
    }

    test("preventing the artifact's damage still yields colored mana") {
        val d = newGame()
        val talisman = d.putPermanentOnBattlefield(d.player1, "Talisman of Resilience")
        d.putPermanentOnBattlefield(d.player1, "Spirit of Resistance")
        // Its static prevention is active only while its controller has every color represented.
        listOf("Savannah Lions", "Phantom Warrior", "Black Creature", "Goblin Guide", "Llanowar Elves")
            .forEach { d.putCreatureOnBattlefield(d.player1, it) }

        d.submit(ActivateAbility(d.player1, talisman, TalismanOfResilience.activatedAbilities[2].id)).error shouldBe null

        d.state.getEntity(d.player1)?.get<ManaPoolComponent>()?.green shouldBe 1
        d.getLifeTotal(d.player1) shouldBe 20
        d.isTapped(talisman) shouldBe true
    }

    test("colored activation attributes the damage to Talisman of Resilience") {
        val d = newGame()
        val talisman = d.putPermanentOnBattlefield(d.player1, "Talisman of Resilience")

        val result = d.submit(ActivateAbility(d.player1, talisman, TalismanOfResilience.activatedAbilities[2].id))
        result.error shouldBe null

        result.events.filterIsInstance<DamageDealtEvent>().single().let { damage ->
            damage.sourceId shouldBe talisman
            damage.targetId shouldBe d.player1
            damage.amount shouldBe 1
            damage.isCombatDamage shouldBe false
        }
    }

    test("automatic payment of a green spell taps the Talisman and deals its damage") {
        val d = newGame()
        val talisman = d.putPermanentOnBattlefield(d.player1, "Talisman of Resilience")
        val elves = d.putCardInHand(d.player1, "Llanowar Elves")

        d.castSpell(d.player1, elves).error shouldBe null

        d.isTapped(talisman) shouldBe true
        d.getLifeTotal(d.player1) shouldBe 19
        d.state.getEntity(d.player1)?.get<ManaPoolComponent>()?.green shouldBe 0
    }
})
