package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class CommanderEnvConfigTest : FunSpec({

    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    // Synthetic runtime fixture only: #170 owns production deck legality and
    // exact-deck qualification. Here we only need already-supported cards to
    // prove that :gym can express the engine's Commander runtime mode.
    fun commanderConfig(missingCommanderSeat: Int? = null): EnvConfig = EnvConfig(
        players = (0 until 4).map { seat ->
            PlayerSpec(
                name = "Seat ${seat + 1}",
                deck = DeckSpec.Explicit(mapOf("Mountain" to 99)),
                commanderCardName = if (seat == missingCommanderSeat) null else "Raging Goblin"
            )
        },
        skipMulligans = true,
        startingPlayerIndex = 0,
        format = Format.Commander(),
        perspectivePlayerIndex = 0,
        revealAll = false
    )

    test("gym service creates a masked four-player Commander environment") {
        val service = MultiEnvService(registry())
        val created = service.create(commanderConfig())
        val observation = created.observation.observation as TrainingObservation

        observation.players shouldHaveSize 4
        observation.players.forEach { it.lifeTotal shouldBe 40 }
        observation.terminated.shouldBeFalse()
        observation.legalActions.isNotEmpty().shouldBeTrue()

        val commandZones = observation.zones.filter { it.zoneType == Zone.COMMAND }
        commandZones shouldHaveSize 4
        commandZones.forEach { zone ->
            zone.hidden.shouldBeFalse()
            zone.size shouldBe 1
            zone.cards.single().name shouldBe "Raging Goblin"
        }

        val perspective = observation.perspectivePlayerId
        val opponentHand = observation.zones.first {
            it.ownerId != perspective && it.zoneType == Zone.HAND
        }
        opponentHand.hidden.shouldBeTrue()
        opponentHand.cards shouldHaveSize 0
        opponentHand.size shouldBe 7
    }

    test("Commander gym configuration fails closed when a seat has no commander") {
        val service = MultiEnvService(registry())

        shouldThrow<IllegalArgumentException> {
            service.create(commanderConfig(missingCommanderSeat = 2))
        }
    }
})
