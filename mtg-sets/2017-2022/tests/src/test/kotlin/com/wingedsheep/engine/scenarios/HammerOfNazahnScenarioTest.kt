package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.BatchYesNoDecision
import com.wingedsheep.engine.core.BatchYesNoResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OrderObjectsDecision
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class HammerOfNazahnScenarioTest : ScenarioTestBase() {
    private val source = "Hammer of Nazahn"
    private val steal = card("Test Instant Creature Control") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.GainControl(creature, Duration.EndOfTurn)
        }
    }

    private fun board() = scenario().withPlayers()
        .withCardInHand(1, source)
        .withCardInHand(1, "Bonesplitter")
        .withCardInHand(1, "Sol Ring")
        .withCardInHand(1, "Boomerang")
        .withCardInHand(1, "Lightning Bolt")
        .withCardInHand(1, "Shatterstorm")
        .withCardInHand(1, "Second Sunrise")
        .withCardInHand(2, "Bonesplitter")
        .withCardInHand(2, "Test Instant Creature Control")
        .withLandsOnBattlefield(1, "Plains", 8)
        .withLandsOnBattlefield(1, "Island", 2)
        .withLandsOnBattlefield(1, "Mountain", 4)
        .withLandsOnBattlefield(2, "Island", 4)
        .withCardInLibrary(1, "Forest")
        .withCardInLibrary(1, "Mountain")
        .withCardInLibrary(2, "Forest")
        .withCardInLibrary(2, "Mountain")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun creatures() = board()
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withCardOnBattlefield(1, "Centaur Courser")

    private fun settle(game: TestGame, accept: Boolean = true, creature: String = "Grizzly Bears") {
        var guard = 0
        while ((game.state.stack.isNotEmpty() || game.hasPendingDecision()) && guard++ < 80) {
            when (val d = game.state.pendingDecision) {
                null -> game.resolveStack().forEach { it.error shouldBe null }
                is YesNoDecision -> game.answerYesNo(accept).error shouldBe null
                is BatchYesNoDecision -> game.submitDecision(BatchYesNoResponse(d.id, accept, true)).error shouldBe null
                is ChooseTargetsDecision -> game.selectTargets(listOf(game.findPermanent(creature)!!)).error shouldBe null
                is OrderObjectsDecision -> game.submitDecision(OrderedResponse(d.id, d.objects)).error shouldBe null
                else -> error("Unexpected decision: $d")
            }
        }
        game.hasPendingDecision() shouldBe false
        game.state.stack.size shouldBe 0
    }

    private fun attachment(game: TestGame, name: String) =
        game.state.getEntity(game.findPermanent(name)!!)!!.get<AttachedToComponent>()?.targetId

    private fun cast(game: TestGame, name: String, player: Int = 1) {
        game.castSpell(player, name).error shouldBe null
    }

    private fun leaveTrigger(game: TestGame, name: String = source) {
        cast(game, name)
        game.resolveStack().forEach { it.error shouldBe null }
        game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        game.answerYesNo(true).error shouldBe null
        val targets = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        targets.targetRequirements.single().minTargets shouldBe 1
        targets.legalTargets[0]!!.contains(game.findPermanent("Centaur Courser")) shouldBe true
        game.selectTargets(listOf(game.findPermanent("Grizzly Bears")!!)).error shouldBe null
        game.state.stack.size shouldBe 1
    }

    init {
        cardRegistry.register(steal)

        test("Hammer entering has required creature targeting and attaches with its stats and indestructible") {
            val game = creatures().build()
            leaveTrigger(game)
            settle(game)
            val bear = game.findPermanent("Grizzly Bears")!!
            attachment(game, source) shouldBe bear
            game.state.projectedState.getPower(bear) shouldBe 4
            game.state.projectedState.getToughness(bear) shouldBe 2
            game.state.projectedState.hasKeyword(bear, Keyword.INDESTRUCTIBLE) shouldBe true
            game.state.projectedState.hasKeyword(game.findPermanent(source)!!, Keyword.INDESTRUCTIBLE) shouldBe false
        }

        test("declining the may leaves Hammer unattached without changing creature stats") {
            val game = creatures().build()
            cast(game, source)
            settle(game, accept = false)
            attachment(game, source) shouldBe null
            game.state.projectedState.getPower(game.findPermanent("Grizzly Bears")!!) shouldBe 2
        }

        test("entry with no controlled creatures finishes without an attachment") {
            val game = board().build()
            cast(game, source)
            settle(game)
            attachment(game, source) shouldBe null
        }

        test("another Equipment enters and that Equipment attaches rather than Hammer") {
            val game = creatures().withCardOnBattlefield(1, source).build()
            leaveTrigger(game, "Bonesplitter")
            settle(game)
            attachment(game, "Bonesplitter") shouldBe game.findPermanent("Grizzly Bears")
            attachment(game, source) shouldBe null
            game.state.projectedState.getPower(game.findPermanent("Grizzly Bears")!!) shouldBe 4
            game.state.projectedState.hasKeyword(game.findPermanent("Grizzly Bears")!!, Keyword.INDESTRUCTIBLE) shouldBe false
        }

        test("opponent Equipment and controlled non-Equipment artifacts do not trigger") {
            val opponentGame = creatures().withCardOnBattlefield(1, source).withActivePlayer(2).build()
            cast(opponentGame, "Bonesplitter", 2)
            opponentGame.resolveStack().forEach { it.error shouldBe null }
            opponentGame.hasPendingDecision() shouldBe false
            opponentGame.state.stack.size shouldBe 0
            attachment(opponentGame, "Bonesplitter") shouldBe null
            val game = creatures().withCardOnBattlefield(1, source).build()
            cast(game, "Sol Ring")
            game.resolveStack().forEach { it.error shouldBe null }
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("Hammer returning simultaneously with two other Equipment triggers and attaches each one") {
            val game = creatures().withCardOnBattlefield(1, source)
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Short Sword").build()
            cast(game, "Shatterstorm")
            settle(game)
            game.findPermanent(source) shouldBe null
            game.findPermanent("Bonesplitter") shouldBe null
            game.findPermanent("Short Sword") shouldBe null
            cast(game, "Second Sunrise")
            settle(game)
            val bear = game.findPermanent("Grizzly Bears")!!
            for (name in listOf(source, "Bonesplitter", "Short Sword")) attachment(game, name) shouldBe bear
            game.state.projectedState.getPower(bear) shouldBe 7
            game.state.projectedState.getToughness(bear) shouldBe 3
            game.state.projectedState.hasKeyword(bear, Keyword.INDESTRUCTIBLE) shouldBe true
        }

        test("the creature leaving in response makes the attachment trigger fizzle") {
            val game = creatures().build()
            leaveTrigger(game)
            game.castSpell(1, "Boomerang", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            settle(game)
            attachment(game, source) shouldBe null
            game.findPermanent("Grizzly Bears") shouldBe null
        }

        test("the entering Equipment leaving in response cannot attach from hand") {
            val game = creatures().withCardOnBattlefield(1, source).build()
            leaveTrigger(game, "Bonesplitter")
            game.castSpell(1, "Boomerang", game.findPermanent("Bonesplitter")!!).error shouldBe null
            settle(game)
            game.findPermanent("Bonesplitter") shouldBe null
            attachment(game, source) shouldBe null
            game.state.projectedState.getPower(game.findPermanent("Grizzly Bears")!!) shouldBe 2
        }

        test("Hammer leaving in response does not stop another Equipment's already triggered attachment") {
            val game = creatures().withCardOnBattlefield(1, source).build()
            leaveTrigger(game, "Bonesplitter")
            game.castSpell(1, "Boomerang", game.findPermanent(source)!!).error shouldBe null
            settle(game)
            game.findPermanent(source) shouldBe null
            attachment(game, "Bonesplitter") shouldBe game.findPermanent("Grizzly Bears")
        }

        test("the targeted creature becoming opponent-controlled makes the trigger fizzle") {
            val game = creatures().build()
            leaveTrigger(game)
            val bear = game.findPermanent("Grizzly Bears")!!
            game.passPriority().error shouldBe null
            game.castSpell(2, steal.name, bear).error shouldBe null
            settle(game)
            game.state.projectedState.getController(bear) shouldBe game.player2Id
            attachment(game, source) shouldBe null
        }

        test("moving Hammer by paying equip removes protection and marked damage kills its former host") {
            val game = creatures().withCardAttachedTo(1, source, "Grizzly Bears").build()
            val bear = game.findPermanent("Grizzly Bears")!!
            val courser = game.findPermanent("Centaur Courser")!!
            game.castSpell(1, "Lightning Bolt", bear).error shouldBe null
            settle(game)
            game.findPermanent("Grizzly Bears") shouldNotBe null
            val hammer = game.findPermanent(source)!!
            val equip = cardRegistry.requireCard(source).activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, hammer, equip, listOf(ChosenTarget.Permanent(courser)))).error shouldBe null
            settle(game)
            attachment(game, source) shouldBe courser
            game.findPermanent("Grizzly Bears") shouldBe null
            game.state.getGraveyard(game.player1Id).contains(bear) shouldBe true
            game.state.projectedState.getPower(courser) shouldBe 5
            game.state.projectedState.hasKeyword(courser, Keyword.INDESTRUCTIBLE) shouldBe true
        }
    }
}
