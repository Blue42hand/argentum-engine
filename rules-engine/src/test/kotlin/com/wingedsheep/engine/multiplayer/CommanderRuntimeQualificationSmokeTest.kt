package com.wingedsheep.engine.multiplayer

import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Commander Gym migration qualification smoke for Blue42hand/commander-gym#170.
 *
 * The focused unit suites already pin each Commander rule in isolation. This suite deliberately
 * stays small and proves that the Commander primitives Commander Gym depends on compose through
 * the real action processor in a four-seat pod, without Forge, deck-specific proxies, or a second
 * rules implementation.
 */
class CommanderRuntimeQualificationSmokeTest : FunSpec({

    val commander = CardDefinition.creature(
        name = "Commander Gym Qualification Commander",
        manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Warrior")),
        power = 21,
        toughness = 21,
        supertypes = setOf(Supertype.LEGENDARY),
    )

    fun pod(skipMulligans: Boolean = true): Pair<GameTestDriver, List<com.wingedsheep.sdk.model.EntityId>> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(commander)
        val players = driver.initMultiplayer(
            decks = List(4) { Deck.of("Forest" to 99) },
            format = Format.Commander(),
            commanders = List(4) { commander.name },
            skipMulligans = skipMulligans,
            startingPlayer = 0,
        )
        return driver to players
    }

    fun GameTestDriver.passUntil(
        description: String,
        maxPasses: Int = 32,
        done: () -> Boolean,
    ) {
        repeat(maxPasses) {
            if (done()) return
            if (pendingDecision != null) {
                error("Paused on unexpected decision while waiting for $description: $pendingDecision")
            }
            val priority = state.priorityPlayerId
                ?: error("No priority holder while waiting for $description at ${state.step}")
            val result = passPriority(priority)
            if (!result.isSuccess && !result.isPaused) {
                error("Priority pass failed while waiting for $description: ${result.error}")
            }
        }
        error("Did not reach $description after $maxPasses priority passes")
    }

    test("four-seat Commander lifecycle composes command casting tax zone choice damage elimination and winner") {
        val (driver, players) = pod()
        val caster = players[0]
        val firstVictim = players[1]
        val laterVictim = players[2]
        val finalOpponent = players[3]

        val commanderId = driver.state.getZone(ZoneKey(caster, Zone.COMMAND)).single()

        // Command-zone cast #1 goes through the ordinary cast action and resolves as a permanent.
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(caster, Color.RED)
        driver.castSpell(caster, commanderId).isSuccess.shouldBeTrue()
        driver.passUntil("first commander resolution") {
            commanderId in driver.state.getZone(ZoneKey(caster, Zone.BATTLEFIELD))
        }
        driver.state.getEntity(commanderId)!!
            .get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1

        // Real combat damage from the actual commander entity reaches 21 and eliminates one seat,
        // but a four-player pod continues.
        driver.removeSummoningSickness(commanderId)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(caster, listOf(commanderId), firstVictim).isSuccess.shouldBeTrue()
        driver.passPriorityUntil(Step.COMBAT_DAMAGE)
        if (driver.state.getEntity(firstVictim)!!.get<PlayerLostComponent>() == null) {
            driver.passUntil("commander-damage elimination") {
                driver.state.getEntity(firstVictim)!!.get<PlayerLostComponent>() != null
            }
        }
        driver.state.commanderDamageOf(commanderId, firstVictim) shouldBe 21
        driver.state.getEntity(firstVictim)!!.get<PlayerLostComponent>()!!.reason shouldBe
            LossReason.COMMANDER_DAMAGE
        driver.state.gameOver.shouldBeFalse()

        // Kill the commander through a normal spell. The post-resolution SBA must surface the
        // CR 903.9a owner choice; choosing command zone restores the same commander entity there.
        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        val doomBlade = driver.putCardInHand(caster, "Doom Blade")
        driver.giveMana(caster, Color.BLACK)
        driver.giveColorlessMana(caster, 1)
        driver.castSpell(caster, doomBlade, targets = listOf(commanderId)).isSuccess.shouldBeTrue()
        driver.passUntil("commander zone-choice prompt") {
            driver.pendingDecision is YesNoDecision
        }

        val zoneChoice = driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        zoneChoice.playerId shouldBe caster
        driver.submitYesNo(caster, true)
        commanderId in driver.state.getZone(ZoneKey(caster, Zone.COMMAND)) shouldBe true

        // The first command-zone cast was committed, so the second costs the printed {R} + {2}.
        // Prove the tax at the action boundary: base mana alone fails, then exactly two generic
        // mana more makes the same cast legal.
        driver.giveMana(caster, Color.RED)
        driver.castSpell(caster, commanderId).isSuccess.shouldBeFalse()
        driver.giveColorlessMana(caster, 2)
        driver.castSpell(caster, commanderId).isSuccess.shouldBeTrue()
        driver.passUntil("second commander resolution") {
            commanderId in driver.state.getZone(ZoneKey(caster, Zone.BATTLEFIELD))
        }
        driver.state.getEntity(commanderId)!!
            .get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2

        // A later elimination still leaves two players; eliminating the last opponent terminates
        // the pod and records the sole survivor as winner.
        driver.concede(laterVictim).isSuccess.shouldBeTrue()
        driver.state.gameOver.shouldBeFalse()

        driver.concede(finalOpponent).isSuccess.shouldBeTrue()
        driver.state.gameOver.shouldBeTrue()
        driver.state.winnerId shouldBe caster
    }

    test("four-seat Commander uses the multiplayer free mulligan through the action processor") {
        val (driver, players) = pod(skipMulligans = false)
        val player = players[0]

        driver.state.getEntity(player)!!.get<MulliganStateComponent>()!!.freeMulligan.shouldBeTrue()

        driver.submit(TakeMulligan(player)).isSuccess.shouldBeTrue()
        driver.state.getEntity(player)!!.get<MulliganStateComponent>()!!.apply {
            mulligansTaken shouldBe 1
            cardsToBottom shouldBe 0
        }

        driver.submit(TakeMulligan(player)).isSuccess.shouldBeTrue()
        driver.state.getEntity(player)!!.get<MulliganStateComponent>()!!.apply {
            mulligansTaken shouldBe 2
            cardsToBottom shouldBe 1
        }
    }
})
