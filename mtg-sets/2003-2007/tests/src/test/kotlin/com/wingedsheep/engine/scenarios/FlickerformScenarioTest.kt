package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.rav.cards.Flickerform
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Flickerform (RAV #18) — "{2}{W}{W}: Exile enchanted creature and all Auras attached to it. At the
 * beginning of the next end step, return that card to the battlefield under its owner's control. If
 * you do, return the other cards exiled this way to the battlefield under their owners' control
 * attached to that creature."
 *
 * Exercises the two pieces the card needs: `CreateDelayedTriggerEffect.carryCollections` (the end-step
 * trigger remembers which card was the creature and which were the Auras) and
 * `MoveCollectionEffect.attachTo` (each Aura comes back attached to that card, under its owner's
 * control, with no enchant choice).
 */
class FlickerformScenarioTest : ScenarioTestBase() {

    private val abilityId = Flickerform.activatedAbilities.single().id

    init {
        context("Flickerform") {

            test("the creature and every Aura on it are exiled, then come back together at the end step") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, "Flickerform", "Grizzly Bears")
                    .withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                    // Bob's Aura on Alice's creature returns under Bob's control.
                    .withCardAttachedTo(2, "Unholy Strength", "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val flickerform = game.findPermanent("Flickerform")!!
                game.execute(ActivateAbility(playerId = game.player1Id, sourceId = flickerform, abilityId = abilityId))
                    .error shouldBe null
                game.resolveStack()

                withClue("the creature and all three Auras are in exile") {
                    game.isInExile(1, "Grizzly Bears") shouldBe true
                    game.isInExile(1, "Flickerform") shouldBe true
                    game.isInExile(1, "Holy Strength") shouldBe true
                    game.isInExile(2, "Unholy Strength") shouldBe true
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                game.checkStateBasedActions()

                val bears = game.findPermanent("Grizzly Bears")!!
                withClue("the creature returned under its owner's control") {
                    game.state.projectedState.getController(bears) shouldBe game.player1Id
                }
                for (name in listOf("Flickerform", "Holy Strength", "Unholy Strength")) {
                    withClue("$name returned attached to that creature") {
                        val aura = game.findPermanent(name)!!
                        game.state.getEntity(aura)!!.get<AttachedToComponent>()!!.targetId shouldBe bears
                    }
                }
                withClue("each Aura returned under its owner's control") {
                    game.state.projectedState.getController(game.findPermanent("Unholy Strength")!!) shouldBe game.player2Id
                    game.state.projectedState.getController(game.findPermanent("Holy Strength")!!) shouldBe game.player1Id
                }
                withClue("both Auras apply again: 2/2 +1/+2 +2/+1") {
                    game.state.projectedState.getPower(bears) shouldBe 5
                    game.state.projectedState.getToughness(bears) shouldBe 5
                }
            }

            test("the creature doesn't come back until the end step") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, "Flickerform", "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val flickerform = game.findPermanent("Flickerform")!!
                game.execute(ActivateAbility(playerId = game.player1Id, sourceId = flickerform, abilityId = abilityId))
                    .error shouldBe null
                game.resolveStack()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.isOnBattlefield("Grizzly Bears") shouldBe false

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                game.checkStateBasedActions()
                game.isOnBattlefield("Grizzly Bears") shouldBe true
                val aura = game.findPermanent("Flickerform")!!
                game.state.getEntity(aura)!!.get<AttachedToComponent>()!!.targetId shouldBe game.findPermanent("Grizzly Bears")
            }
        }
    }
}
