package com.wingedsheep.engine.mana

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.PreventActivatedAbilities
import com.wingedsheep.sdk.scripting.GameObjectFilter
import io.kotest.matchers.shouldBe

/** Manual activation prohibitions also govern automatic and explicit-only payment inventories. */
class ManaActivationProhibitionTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private fun solver() = ManaSolver(cardRegistry, services.predicateEvaluator)

    init {
        cardRegistry.register(card("Artifact Activation Lock") {
            typeLine = "Enchantment"
            staticAbility { ability = PreventActivatedAbilities(GameObjectFilter.Artifact) }
        })
        test("Arrest blocks Cryptolith Rite mana and cannot fund a cast") {
            val g = scenario().withPlayers()
                .withCardOnBattlefield(1, "Lathril, Blade of the Elves", summoningSickness = false)
                .withCardOnBattlefield(1, "Cryptolith Rite")
                .withCardAttachedTo(2, "Arrest", "Lathril, Blade of the Elves")
                .withCardInHand(1, "Llanowar Elves").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val s = solver()
            s.findAvailableManaSources(g.state, p1).isEmpty() shouldBe true
            s.canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe false
            s.getAvailableManaCount(g.state, p1) shouldBe 0
            g.getLegalActions(1).any { it.action is CastSpell } shouldBe false
            (g.castSpell(1, "Llanowar Elves").outcome is Outcome.Rejected) shouldBe true
            g.state.getEntity(g.findPermanent("Lathril, Blade of the Elves")!!)!!.has<TappedComponent>() shouldBe false
        }
        test("AutoPay spends an available land and preserves the arrested creature") {
            val g = scenario().withPlayers()
                .withCardOnBattlefield(1, "Lathril, Blade of the Elves", summoningSickness = false)
                .withCardOnBattlefield(1, "Cryptolith Rite")
                .withCardAttachedTo(2, "Arrest", "Lathril, Blade of the Elves")
                .withCardOnBattlefield(1, "Forest").withCardInHand(1, "Llanowar Elves")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val lathril = g.findPermanent("Lathril, Blade of the Elves")!!
            solver().findAvailableManaSources(g.state, p1).map { it.entityId } shouldBe listOf(g.findPermanent("Forest")!!)
            (g.castSpell(1, "Llanowar Elves").outcome is Outcome.Rejected) shouldBe false
            g.state.getEntity(lathril)!!.has<TappedComponent>() shouldBe false
            g.state.getEntity(g.findPermanent("Forest")!!)!!.has<TappedComponent>() shouldBe true
        }
        test("printed creature mana is locked too") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardAttachedTo(2, "Arrest", "Llanowar Elves").build()
            solver().canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe false
        }
        test("removing the lock restores mana availability") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardOnBattlefield(2, "Cursed Totem").build()
            val s = solver()
            s.canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe false
            val lock = g.findPermanent("Cursed Totem")!!
            // Remove the permanent through the native zone transition service.
            g.state = zones.moveToZone(g.state, lock, com.wingedsheep.sdk.core.Zone.GRAVEYARD).state
            s.canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe true
        }
        test("a global creature lock preserves artifact mana") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardOnBattlefield(1, "Sol Ring").withCardOnBattlefield(2, "Cursed Totem").build()
            solver().findAvailableManaSources(g.state, p1).map { it.entityId } shouldBe listOf(g.findPermanent("Sol Ring")!!)
            solver().getAvailableManaCount(g.state, p1) shouldBe 2
        }
        test("non-mana-only locks keep mana usable") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardOnBattlefield(1, "Sol Ring").withCardOnBattlefield(2, "Damping Matrix").build()
            solver().getAvailableManaCount(g.state, p1) shouldBe 3
        }
        test("player and turn scoped prohibitions match manual activation") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardOnBattlefield(2, "Grand Abolisher").withActivePlayer(2).build()
            val s = solver()
            s.canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe false
            s.canPay(g.state.copy(activePlayerId = p1, priorityPlayerId = p1), p1, ManaCost.parse("{G}")) shouldBe true
        }
        test("explicit-only creature mana is not counted through a global lock") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Llanowar Elves").withCardOnBattlefield(2, "Cursed Totem").build()
            solver().calculateTapPermanentsBonusMana(g.state, p1).totalMana shouldBe 0
            solver().canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe false
        }
        test("artifact lock excludes sacrifice, composite tap and explicit-only inventories") {
            for (source in listOf("Treasure", "Springleaf Drum", "Ashnod's Altar")) {
                val g = scenario().withPlayers().withCardOnBattlefield(1, source)
                    .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Artifact Activation Lock").build()
                val s = solver()
                s.getAvailableManaCount(g.state, p1) shouldBe 0
                s.canPay(g.state, p1, ManaCost.parse("{1}")) shouldBe false
            }
        }
        test("an arrested creature may pay the tap cost of another source") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(2, "Arrest", "Grizzly Bears")
                .withCardOnBattlefield(1, "Springleaf Drum").build()
            solver().canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe true
        }
        test("mana already floating remains spendable under a lock") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
                .withCardOnBattlefield(2, "Cursed Totem").build()
            g.state = g.state.withEntity(p1, g.state.getEntity(p1)!!.with(ManaPoolComponent(green = 1)))
            solver().canPay(g.state, p1, ManaCost.parse("{G}")) shouldBe true
        }
    }
}
