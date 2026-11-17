package com.wingedsheep.engine.mana

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.event.GrantedActivatedAbility
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.matchers.shouldBe

/** Printed mana is absent face down; later grants and intrinsic land types still apply. */
class FaceDownManaSourcesTest : ScenarioTestBase() {
    private fun solver() = ManaSolver(cardRegistry, services.predicateEvaluator)
    private fun faceDown(game: TestGame, name: String): EntityId {
        val id = game.findPermanent(name)!!
        game.state = game.state.updateEntity(id) { it.with(FaceDownComponent) }
        return id
    }

    init {
        test("face-down artifact creature and land do not supply printed mana or fallback colorless") {
            for (name in listOf("Talisman of Unity", "Llanowar Elves", "Forest")) {
                val g = scenario().withPlayers().withCardOnBattlefield(1, name, summoningSickness = false).build()
                faceDown(g, name)
                val s = solver()
                s.findAvailableManaSources(g.state, g.player1Id).isEmpty() shouldBe true
                s.getAvailableManaCount(g.state, g.player1Id) shouldBe 0
                s.canPay(g.state, g.player1Id, ManaCost.parse("{1}")) shouldBe false
            }
        }
        test("turning the permanent face up restores its printed mana") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false).build()
            val id = faceDown(g, "Talisman of Unity")
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe false
            g.state = g.state.updateEntity(id) { it.without<FaceDownComponent>() }
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe true
        }
        test("a manifested Talisman plus a colorless land cannot auto-pay Seal Away") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false)
                .withCardOnBattlefield(1, "Wastes").withCardInHand(1, "Seal Away")
                .withCardOnBattlefield(2, "Grizzly Bears", tapped = true).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = faceDown(g, "Talisman of Unity")
            val beforeLife = g.getLifeTotal(1)
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{1}{W}")) shouldBe false
            g.getLegalActions(1).any { it.action is CastSpell } shouldBe false
            (g.castSpell(1, "Seal Away").outcome is Outcome.Rejected) shouldBe true
            g.state.getEntity(id)!!.has<TappedComponent>() shouldBe false
            g.getLifeTotal(1) shouldBe beforeLife
        }
        test("runtime granted white mana works without borrowing the hidden Talisman pain effect") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false)
                .withCardInHand(1, "Authority of the Consuls").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = faceDown(g, "Talisman of Unity")
            val ability = ActivatedAbility(AbilityId("granted_white"), AbilityCost.Tap, Effects.AddMana(Color.WHITE), isManaAbility = true)
            g.state = g.state.copy(grantedActivatedAbilities = listOf(GrantedActivatedAbility(id, ability, Duration.EndOfTurn)))
            val source = solver().findAvailableManaSources(g.state, g.player1Id).single()
            source.producesColors shouldBe setOf(Color.WHITE)
            source.producesColorless shouldBe false
            val beforeLife = g.getLifeTotal(1)
            (g.castSpell(1, "Authority of the Consuls").outcome is Outcome.Rejected) shouldBe false
            g.state.getEntity(id)!!.has<TappedComponent>() shouldBe true
            g.getLifeTotal(1) shouldBe beforeLife
        }
        test("Cryptolith Rite grants usable mana to a face-down creature without printed pain") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false)
                .withCardOnBattlefield(1, "Cryptolith Rite").withCardInHand(1, "Authority of the Consuls")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = faceDown(g, "Talisman of Unity")
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe true
            val beforeLife = g.getLifeTotal(1)
            (g.castSpell(1, "Authority of the Consuls").outcome is Outcome.Rejected) shouldBe false
            g.state.getEntity(id)!!.has<TappedComponent>() shouldBe true
            g.getLifeTotal(1) shouldBe beforeLife
        }
        test("face-down creatures can gain intrinsic Forest mana through Ashaya") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false)
                .withCardOnBattlefield(1, "Ashaya, Soul of the Wild", summoningSickness = true).build()
            val id = faceDown(g, "Talisman of Unity")
            val source = solver().findAvailableManaSources(g.state, g.player1Id).single { it.entityId == id }
            source.producesColors shouldBe setOf(Color.GREEN)
            source.producesColorless shouldBe false
        }
        test("a later mana grant does not bypass summoning sickness") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = true)
                .withCardOnBattlefield(1, "Cryptolith Rite").build()
            faceDown(g, "Talisman of Unity")
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe false
        }
        test("face-down printed sacrifice and tap-permanent abilities do not create bonus affordability") {
            for (name in listOf("Treasure", "Springleaf Drum", "Birchlore Rangers")) {
                val g = scenario().withPlayers().withCardOnBattlefield(1, name, summoningSickness = false)
                    .withCardOnBattlefield(1, "Elvish Warrior").withCardOnBattlefield(1, "Elvish Warrior")
                    .withCardOnBattlefield(1, "Elvish Warrior").build()
                faceDown(g, name)
                solver().getAvailableManaCount(g.state, g.player1Id) shouldBe 0
                solver().canPay(g.state, g.player1Id, ManaCost.parse("{G}")) shouldBe false
            }
        }
        test("a later explicit-only sacrifice mana grant remains available") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Talisman of Unity", summoningSickness = false).build()
            val id = faceDown(g, "Talisman of Unity")
            val ability = ActivatedAbility(AbilityId("granted_sacrifice"), AbilityCost.SacrificeSelf, Effects.AddMana(Color.WHITE), isManaAbility = true)
            g.state = g.state.copy(grantedActivatedAbilities = listOf(GrantedActivatedAbility(id, ability, Duration.EndOfTurn)))
            solver().canPay(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe true
            solver().solve(g.state, g.player1Id, ManaCost.parse("{W}")) shouldBe null
        }
    }
}
