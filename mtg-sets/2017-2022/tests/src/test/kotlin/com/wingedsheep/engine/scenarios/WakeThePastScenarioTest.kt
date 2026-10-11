package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m11.cards.SteelOverseer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Wake the Past — C21 #75. The card returns one batch and grants haste only to that batch. */
class WakeThePastScenarioTest : FunSpec({
    fun setup(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.castWake() {
        val wake = putCardInHand(player1, "Wake the Past")
        giveMana(player1, Color.RED)
        giveMana(player1, Color.WHITE)
        giveColorlessMana(player1, 5)
        castSpell(player1, wake).error shouldBe null
        bothPass().error shouldBe null
    }

    fun GameTestDriver.counters(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun GameTestDriver.chooseMimicType(type: String) {
        val choice = state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = choice.options.indexOfFirst { it.equals(type, ignoreCase = true) }
        require(index >= 0) { "Creature type $type is absent from the entry choice" }
        submitDecision(player1, OptionChosenResponse(choice.id, index)).error shouldBe null
    }

    test("returns only your artifact cards, including noncreatures, and leaves existing permanents alone") {
        val d = setup()
        val returnedCreature = d.putCardInGraveyard(d.player1, "Steel Overseer")
        val returnedRock = d.putCardInGraveyard(d.player1, "Sol Ring")
        val returnedLand = d.putCardInGraveyard(d.player1, "Great Furnace")
        val returnedEquipment = d.putCardInGraveyard(d.player1, "Bonesplitter")
        val nonartifact = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        val opponentArtifact = d.putCardInGraveyard(d.player2, "Sol Ring")
        val existing = d.putPermanentOnBattlefield(d.player1, "Steel Overseer")

        d.castWake()

        d.state.getBattlefield().containsAll(listOf(
            returnedCreature, returnedRock, returnedLand, returnedEquipment, existing
        )) shouldBe true
        d.state.getGraveyard(d.player1).contains(nonartifact) shouldBe true
        d.state.getGraveyard(d.player2).contains(opponentArtifact) shouldBe true
        d.state.projectedState.hasKeyword(returnedCreature, Keyword.HASTE) shouldBe true
        d.state.projectedState.hasKeyword(returnedRock, Keyword.HASTE) shouldBe true
        d.state.projectedState.hasKeyword(returnedLand, Keyword.HASTE) shouldBe true
        d.state.projectedState.hasKeyword(returnedEquipment, Keyword.HASTE) shouldBe true
        d.state.projectedState.hasKeyword(existing, Keyword.HASTE) shouldBe false
    }

    test("a preexisting Soulless Jailer prevents all graveyard artifact entry") {
        val d = setup()
        val jailer = d.putPermanentOnBattlefield(d.player2, "Soulless Jailer")
        val automaton = d.putCardInGraveyard(d.player1, "Mindless Automaton")
        val ring = d.putCardInGraveyard(d.player1, "Sol Ring")

        d.castWake()

        d.state.getBattlefield().contains(jailer) shouldBe true
        d.state.getBattlefield().contains(automaton) shouldBe false
        d.state.getBattlefield().contains(ring) shouldBe false
        d.state.getGraveyard(d.player1).containsAll(listOf(automaton, ring)) shouldBe true
        d.counters(automaton) shouldBe 0
        d.state.projectedState.hasKeyword(automaton, Keyword.HASTE) shouldBe false
        d.state.projectedState.hasKeyword(ring, Keyword.HASTE) shouldBe false
    }

    listOf(
        listOf("Soulless Jailer", "Sol Ring"),
        listOf("Sol Ring", "Soulless Jailer")
    ).forEach { order ->
        test("Jailer returned simultaneously does not block the other artifact in order $order") {
            val d = setup()
            val returned = order.associateWith { d.putCardInGraveyard(d.player1, it) }

            d.castWake()

            d.state.getBattlefield().containsAll(returned.values) shouldBe true
            d.state.getGraveyard(d.player1).any { it in returned.values } shouldBe false
            returned.values.forEach { d.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true }
        }
    }

    listOf(
        listOf("Steel Overseer", "Golgari Grave-Troll"),
        listOf("Golgari Grave-Troll", "Steel Overseer")
    ).forEach { order ->
        test("Mycosynth makes both graveyard creatures return and Troll counts both in order $order") {
            val d = setup()
            d.putPermanentOnBattlefield(d.player1, "Encroaching Mycosynth")
            val returned = order.associateWith { d.putCardInGraveyard(d.player1, it) }

            d.castWake()

            d.state.getBattlefield().containsAll(returned.values) shouldBe true
            d.counters(returned.getValue("Golgari Grave-Troll")) shouldBe 2
            d.state.projectedState.getToughness(returned.getValue("Golgari Grave-Troll")) shouldBe 2
            returned.values.forEach { d.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true }
        }
    }

    test("an empty artifact graveyard resolves without granting haste to an existing artifact") {
        val d = setup()
        val existing = d.putPermanentOnBattlefield(d.player1, "Steel Overseer")
        d.putCardInGraveyard(d.player1, "Grizzly Bears")

        d.castWake()

        d.state.getBattlefield().contains(existing) shouldBe true
        d.state.projectedState.hasKeyword(existing, Keyword.HASTE) shouldBe false
        d.state.getGraveyard(d.player1).count {
            d.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears"
        } shouldBe 1
    }

    test("returned creature can use its tap ability immediately and haste expires at end of turn") {
        val d = setup()
        val returned = d.putCardInGraveyard(d.player1, "Steel Overseer")
        d.castWake()

        d.state.projectedState.hasKeyword(returned, Keyword.HASTE) shouldBe true
        d.submit(ActivateAbility(d.player1, returned, SteelOverseer.activatedAbilities.single().id)).error shouldBe null
        d.bothPass().error shouldBe null
        d.isTapped(returned) shouldBe true
        d.counters(returned) shouldBe 1

        d.passPriorityUntil(Step.UPKEEP)
        d.state.projectedState.hasKeyword(returned, Keyword.HASTE) shouldBe false
    }

    test("Cursed Mirror returned from the graveyard receives its entry copy choice") {
        val d = setup()
        val giant = d.putPermanentOnBattlefield(d.player2, "Hill Giant")
        val mirror = d.putCardInGraveyard(d.player1, "Cursed Mirror")

        d.castWake()
        d.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        d.submitCardSelection(d.player1, listOf(giant)).error shouldBe null

        d.state.getBattlefield().contains(mirror) shouldBe true
        d.state.getEntity(mirror)?.get<CardComponent>()?.name shouldBe "Hill Giant"
        d.state.projectedState.hasKeyword(mirror, Keyword.HASTE) shouldBe true
    }

    test("Mirror copying Grave-Troll counts the Bear but not its former noncreature self") {
        val d = setup()
        val opposingTroll = d.putPermanentOnBattlefield(d.player2, "Golgari Grave-Troll")
        d.replaceState(d.state.updateEntity(opposingTroll) {
            it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        })
        d.state.projectedState.getToughness(opposingTroll) shouldBe 2
        val mirror = d.putCardInGraveyard(d.player1, "Cursed Mirror")
        val bear = d.putCardInGraveyard(d.player1, "Grizzly Bears")

        d.castWake()
        d.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        d.submitCardSelection(d.player1, listOf(opposingTroll)).error shouldBe null

        d.state.getBattlefield().contains(mirror) shouldBe true
        d.state.getGraveyard(d.player1).contains(bear) shouldBe true
        d.state.getEntity(mirror)?.get<CardComponent>()?.name shouldBe "Golgari Grave-Troll"
        d.counters(mirror) shouldBe 1
        d.counters(opposingTroll) shouldBe 2
        d.state.projectedState.hasKeyword(mirror, Keyword.HASTE) shouldBe true
    }

    listOf(
        listOf("Metallic Mimic", "Steel Overseer"),
        listOf("Steel Overseer", "Metallic Mimic")
    ).forEach { order ->
        test("simultaneous Mimic and Overseer entry has no extra counters in order $order") {
            val d = setup()
            val returned = order.associateWith { d.putCardInGraveyard(d.player1, it) }

            d.castWake()
            d.chooseMimicType("Construct")

            d.state.getBattlefield().containsAll(returned.values) shouldBe true
            d.counters(returned.getValue("Metallic Mimic")) shouldBe 0
            d.counters(returned.getValue("Steel Overseer")) shouldBe 0
            returned.values.forEach { d.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true }
        }
    }

    test("a preexisting Metallic Mimic grants a counter to the returned Construct") {
        val d = setup()
        val mimic = d.putCardInHand(d.player1, "Metallic Mimic")
        d.giveColorlessMana(d.player1, 2)
        d.castSpell(d.player1, mimic).error shouldBe null
        d.bothPass().error shouldBe null
        d.chooseMimicType("Construct")
        val overseer = d.putCardInGraveyard(d.player1, "Steel Overseer")

        d.castWake()

        d.state.getBattlefield().contains(mimic) shouldBe true
        d.counters(overseer) shouldBe 1
        d.state.projectedState.hasKeyword(overseer, Keyword.HASTE) shouldBe true
    }

    test("Mindless Automaton retains its own two entry counters after mass return") {
        val d = setup()
        val automaton = d.putCardInGraveyard(d.player1, "Mindless Automaton")
        val overseer = d.putCardInGraveyard(d.player1, "Steel Overseer")

        d.castWake()

        d.state.getBattlefield().containsAll(listOf(automaton, overseer)) shouldBe true
        d.counters(automaton) shouldBe 2
        d.state.projectedState.getToughness(automaton) shouldBe 2
        d.state.projectedState.hasKeyword(automaton, Keyword.HASTE) shouldBe true
    }
})
