package com.wingedsheep.gym

import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.PendingDecisionKind
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.ktk.cards.EmptyThePits
import com.wingedsheep.mtg.sets.definitions.ktk.cards.TreasureCruise
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ForcedPlayDelveContractTest : FunSpec({
    fun forcedCast(cardName: String, mana: Color, manaCount: Int): Triple<GameGymEnv, GameEnvironment, List<EntityId>> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(EmptyThePits, TreasureCruise))
        driver.initMirrorMatch(Deck.of("Swamp" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val card = driver.putCardInHand(player, cardName)
        repeat(manaCount) { driver.giveMana(player, mana, 1) }
        val graveyard = (1..7).map { driver.putCardInGraveyard(player, "Grizzly Bears") }

        val environment = GameEnvironment.create(driver.cardRegistry)
        environment.restore(driver.state, listOf(driver.player1, driver.player2))
        val forced = driver.services.effectExecutorRegistry.execute(
            environment.state,
            Effects.ForcePlay("chosen"),
            EffectContext(
                sourceId = null,
                controllerId = player,
                pipeline = PipelineState(storedCollections = mapOf("chosen" to listOf(card))),
            ),
        )
        environment.restore(forced.state, environment.playerIds)
        return Triple(GameGymEnv(environment, environment.playerIds.indexOf(player), true), environment, graveyard)
    }

    test("forced X delve cast rejects a hidden delve parameter before changing state") {
        val (gym, environment, graveyard) = forcedCast("Empty the Pits", Color.BLACK, 6)
        val observation = gym.observe().observation as TrainingObservation
        observation.pendingDecision!!.kind shouldBe PendingDecisionKind.PLAY_CARD
        val cast = observation.legalActions.first { it.kind == "CastSpell" }
        ("delvedCards" in cast.parameterSpec.allowedFields) shouldBe false
        val before = environment.state

        shouldThrow<IllegalArgumentException> {
            gym.step(cast.actionId, ActionParams(xValue = 0, delvedCards = listOf(graveyard.first())))
        }
        environment.state shouldBe before
    }

    test("forced non-X delve cast enforces offered candidates and accepts a valid selection") {
        val (gym, environment, graveyard) = forcedCast("Treasure Cruise", Color.BLUE, 3)
        val observation = gym.observe().observation as TrainingObservation
        val cast = observation.legalActions.first { it.kind == "CastSpell" }
        ("delvedCards" in cast.parameterSpec.allowedFields) shouldBe true
        val offered = environment.legalActions().first { it.action is CastSpell }
        cast.validDelveCards.toSet() shouldBe graveyard.toSet()
        val before = environment.state

        val unoffered = (offered.action as CastSpell).cardId
        shouldThrow<IllegalArgumentException> {
            gym.step(cast.actionId, ActionParams(delvedCards = listOf(unoffered)))
        }
        environment.state shouldBe before

        gym.step(cast.actionId, ActionParams(delvedCards = graveyard.take(5)))
    }
})
