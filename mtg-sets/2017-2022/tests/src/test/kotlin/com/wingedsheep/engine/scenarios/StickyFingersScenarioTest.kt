package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Sticky Fingers grants the enchanted creature menace and a combat-damage Treasure trigger.
 * The Treasure belongs to that creature's controller; the Aura's death-trigger draw belongs
 * to the Aura's controller, even when the enchanted creature is controlled by the opponent.
 */
class StickyFingersScenarioTest : ScenarioTestBase() {
    private fun TestGame.treasures(playerNumber: Int): Int {
        val controller = if (playerNumber == 1) player1Id else player2Id
        return state.getBattlefield().count { id ->
            val entity = state.getEntity(id) ?: return@count false
            entity.get<CardComponent>()?.name == "Treasure" &&
                entity.get<ControllerComponent>()?.playerId == controller
        }
    }

    init {
        test("combat damage by an opponent's enchanted creature gives that opponent the Treasure") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardAttachedTo(1, "Sticky Fingers", "Grizzly Bears")
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state.projectedState.hasKeyword(bears, Keyword.MENACE) shouldBe true

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Grizzly Bears" to 1)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.declareNoBlockers().error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
            game.passPriority().error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 18
            withClue("the combat trigger is granted to the creature, not controlled by the Aura") {
                game.treasures(2) shouldBe 1
                game.treasures(1) shouldBe 0
            }
        }

        test("one blocker cannot block the enchanted creature but two can") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Sticky Fingers", "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardOnBattlefield(2, "Centaur Courser")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

            withClue("menace forbids a single blocker") {
                game.declareBlockers(mapOf("Hill Giant" to listOf("Grizzly Bears")))
                    .error shouldNotBe null
            }
            withClue("two blockers satisfy menace") {
                game.declareBlockers(
                    mapOf(
                        "Hill Giant" to listOf("Grizzly Bears"),
                        "Centaur Courser" to listOf("Grizzly Bears")
                    )
                ).error shouldBe null
            }
        }

        test("when an opponent's enchanted creature dies, the Aura controller draws") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardAttachedTo(1, "Sticky Fingers", "Grizzly Bears")
                .withCardInHand(1, "Murder")
                .withCardInLibrary(1, "Plains")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!

            game.castSpell(1, "Murder", bears).error shouldBe null
            game.resolveStack()
            withClue("the creature and its now-unattached Aura both go to their owners' graveyards") {
                game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                game.isInGraveyard(1, "Sticky Fingers") shouldBe true
            }
            withClue("the Aura's controller draws using the enchanted creature's last-known state") {
                game.isInHand(1, "Plains") shouldBe true
                game.handSize(1) shouldBe 1
                game.handSize(2) shouldBe 0
            }
        }

        test("the Aura controller draws when an enchanted creature token dies") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(2, "Grizzly Bears", isToken = true)
                .withCardAttachedTo(1, "Sticky Fingers", "Grizzly Bears")
                .withCardInHand(1, "Shock")
                .withCardInLibrary(1, "Plains")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val token = game.findPermanent("Grizzly Bears")!!
            game.state.getEntity(token)?.has<TokenComponent>() shouldBe true

            game.castSpell(1, "Shock", token).error shouldBe null
            game.resolveStack()
            withClue("the token ceases to exist, while its former Aura reaches the graveyard") {
                game.findPermanent("Grizzly Bears") shouldBe null
                game.isInGraveyard(1, "Sticky Fingers") shouldBe true
            }
            withClue("the token's death still causes the Aura controller to draw") {
                game.isInHand(1, "Plains") shouldBe true
                game.handSize(1) shouldBe 1
            }
        }

        test("destroying the Aura before combat removes menace and the Treasure trigger") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Sticky Fingers", "Grizzly Bears")
                .withCardInHand(1, "Naturalize")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            val aura = game.findPermanent("Sticky Fingers")!!
            game.state.projectedState.hasKeyword(bears, Keyword.MENACE) shouldBe true

            game.castSpell(1, "Naturalize", aura).error shouldBe null
            game.resolveStack()
            game.isInGraveyard(1, "Sticky Fingers") shouldBe true
            game.state.projectedState.hasKeyword(bears, Keyword.MENACE) shouldBe false

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.declareNoBlockers().error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
            game.passPriority().error shouldBe null
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 18
            game.treasures(1) shouldBe 0
        }

        test("noncombat damage by the enchanted creature does not create a Treasure") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Prodigal Pyromancer")
                .withCardAttachedTo(1, "Sticky Fingers", "Prodigal Pyromancer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val pyromancer = game.findPermanent("Prodigal Pyromancer")!!
            val ability = cardRegistry.requireCard("Prodigal Pyromancer").activatedAbilities.single().id

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = pyromancer,
                    abilityId = ability,
                    targets = listOf(ChosenTarget.Player(game.player2Id))
                )
            ).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 19
            game.treasures(1) shouldBe 0
        }
    }
}
