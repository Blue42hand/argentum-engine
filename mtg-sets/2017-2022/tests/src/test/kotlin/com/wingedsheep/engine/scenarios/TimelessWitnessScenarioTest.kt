package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh2.cards.TimelessWitness
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Timeless Witness (MH2 #179): mandatory own-graveyard ETB and eternalize {5}{G}{G}. */
class TimelessWitnessScenarioTest : FunSpec({
    val eternalizeId = TimelessWitness.activatedAbilities.single().id

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(TimelessWitness))
        initMirrorMatch(deck = Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun activate(d: GameTestDriver, card: EntityId): Unit {
        d.giveMana(d.player1, Color.GREEN, 7)
        d.submit(ActivateAbility(d.player1, card, eternalizeId)).outcome shouldBe Outcome.Done
    }

    test("cast Witness must target a card in its controller's graveyard and return it") {
        val d = driver()
        val mine = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        val theirs = d.putCardInGraveyard(d.player2, "Centaur Courser")
        val witness = d.putCardInHand(d.player1, "Timeless Witness")
        d.giveMana(d.player1, Color.GREEN, 4)

        d.castSpell(d.player1, witness).outcome shouldBe Outcome.Done
        d.bothPass()

        val decision = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        decision.legalTargets.getValue(0) shouldContain mine
        decision.legalTargets.getValue(0) shouldNotContain theirs
        d.submitTargetSelection(d.player1, listOf(mine)).error shouldBe null
        d.bothPass()

        d.getHand(d.player1) shouldContain mine
        d.getGraveyardCardNames(d.player1) shouldNotContain "Grizzly Bears"
    }

    test("ETB does not ask for a target when only an opponent has graveyard cards") {
        val d = driver()
        d.putCardInGraveyard(d.player2, "Centaur Courser")
        val witness = d.putCardInHand(d.player1, "Timeless Witness")
        d.giveMana(d.player1, Color.GREEN, 4)

        d.castSpell(d.player1, witness).outcome shouldBe Outcome.Done
        d.bothPass()

        d.findPermanent(d.player1, "Timeless Witness") shouldNotBe null
        d.pendingDecision shouldBe null
        d.stackSize shouldBe 0
    }

    test("mandatory ETB can return a noncreature card from your graveyard") {
        val d = driver()
        val ring = d.putCardInGraveyard(d.player1, "Sol Ring")
        val witness = d.putCardInHand(d.player1, "Timeless Witness")
        d.giveMana(d.player1, Color.GREEN, 4)

        d.castSpell(d.player1, witness).outcome shouldBe Outcome.Done
        d.bothPass()
        d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            .legalTargets.getValue(0) shouldContain ring
        d.submitTargetSelection(d.player1, listOf(ring)).error shouldBe null
        d.bothPass()

        d.getHand(d.player1) shouldContain ring
    }

    test("eternalize exiles as a cost, creates a black 4/4 Zombie Human Shaman with no mana cost, and triggers ETB") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        val target = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        activate(d, witness)

        d.getExile(d.player1) shouldContain witness
        d.getGraveyardCardNames(d.player1) shouldNotContain "Timeless Witness"
        d.findPermanent(d.player1, "Timeless Witness") shouldBe null

        d.bothPass()
        val token = d.findPermanent(d.player1, "Timeless Witness").shouldNotBeNull()
        d.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
        val card = d.state.getEntity(token)!!.get<CardComponent>().shouldNotBeNull()
        card.colors shouldBe setOf(Color.BLACK)
        card.manaCost shouldBe ManaCost.ZERO
        card.typeLine.subtypes shouldContain Subtype.ZOMBIE
        card.typeLine.subtypes shouldContain Subtype("Human")
        card.typeLine.subtypes shouldContain Subtype("Shaman")
        d.state.projectedState.getPower(token) shouldBe 4
        d.state.projectedState.getToughness(token) shouldBe 4
        d.state.getEntity(witness)!!.get<CardComponent>()!!.manaCost shouldBe ManaCost.parse("{2}{G}{G}")

        val decision = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        decision.legalTargets.getValue(0) shouldContain target
        d.submitTargetSelection(d.player1, listOf(target)).error shouldBe null
        d.bothPass()
        d.getHand(d.player1) shouldContain target
    }

    test("eternalize requires the full mana cost and an empty stack at sorcery timing") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        d.giveMana(d.player1, Color.GREEN, 6)
        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).error shouldNotBe null
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"

        val spell = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN, 3)
        d.castSpell(d.player1, spell).outcome shouldBe Outcome.Done
        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).error shouldNotBe null
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"
    }

    test("eternalize cannot be activated during the opponent's main phase") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.activePlayer shouldBe d.player2
        d.giveMana(d.player1, Color.GREEN, 7)
        d.passPriority(d.player2)

        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).error shouldNotBe null
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"
    }

    test("eternalize rejects seven colorless mana because two green pips are required") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        d.giveColorlessMana(d.player1, 7)

        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).error shouldNotBe null
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"
    }

    test("eternalize cannot be activated from the battlefield or exile") {
        val d = driver()
        val onBattlefield = d.putCreatureOnBattlefield(d.player1, "Timeless Witness")
        val inExile = d.putCardInExile(d.player1, "Timeless Witness")
        d.giveMana(d.player1, Color.GREEN, 14)

        d.submit(ActivateAbility(d.player1, onBattlefield, eternalizeId)).error shouldNotBe null
        d.submit(ActivateAbility(d.player1, inExile, eternalizeId)).error shouldNotBe null
        d.findPermanent(d.player1, "Timeless Witness") shouldBe onBattlefield
        d.getExile(d.player1) shouldContain inExile
    }

    test("countering eternalize prevents the token but does not undo the exile cost") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        val stifle = d.putCardInHand(d.player2, "Stifle")
        d.giveMana(d.player1, Color.GREEN, 7)
        d.giveMana(d.player2, Color.BLUE, 1)

        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).outcome shouldBe Outcome.Done
        val abilityOnStack = d.getTopOfStack().shouldNotBeNull()
        d.passPriority(d.player1)
        d.castSpellWithTargets(d.player2, stifle, listOf(ChosenTarget.Spell(abilityOnStack))).outcome shouldBe Outcome.Done
        d.bothPass()

        d.getExile(d.player1) shouldContain witness
        d.findPermanent(d.player1, "Timeless Witness") shouldBe null
        d.stackSize shouldBe 0
    }

    test("token copies printed values rather than counters on the prior battlefield object") {
        val d = driver()
        val witness = d.putCreatureOnBattlefield(d.player1, "Timeless Witness")
        d.addComponent(witness, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        d.replaceState(d.zones.moveToZone(d.state, witness, Zone.GRAVEYARD).state)
        activate(d, witness)
        d.bothPass()

        val token = d.findPermanent(d.player1, "Timeless Witness").shouldNotBeNull()
        d.state.projectedState.getPower(token) shouldBe 4
        d.state.projectedState.getToughness(token) shouldBe 4
        (d.state.getEntity(token)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
    }

    test("ability uses the exiled card's printed values even if it moves before resolution") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        activate(d, witness)
        d.replaceState(d.zones.moveToZone(d.state, witness, Zone.HAND).state)
        d.bothPass()

        val token = d.findPermanent(d.player1, "Timeless Witness").shouldNotBeNull()
        d.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
        d.state.getEntity(token)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.BLACK)
        d.state.projectedState.getPower(token) shouldBe 4
        d.state.projectedState.getToughness(token) shouldBe 4
        d.getHand(d.player1) shouldContain witness
    }

    test("eternalize still creates a token after an instant returns its exiled source to the graveyard") {
        // Pull from Eternity is not in the current card registry. This test-only instant has its
        // relevant targeting and zone-change behavior, so the response uses the real spell stack.
        val returnExiledCard = card("Test Return Exiled Card") {
            manaCost = "{W}"
            typeLine = "Instant"
            spell {
                val exiled = target(TargetFilter(GameObjectFilter.Any, zone = Zone.EXILE))
                effect = Effects.Move(exiled, Zone.GRAVEYARD)
            }
        }
        val d = driver()
        d.registerCard(returnExiledCard)
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        val response = d.putCardInHand(d.player2, returnExiledCard.name)
        d.giveMana(d.player1, Color.GREEN, 7)
        d.giveMana(d.player2, Color.WHITE, 1)

        d.submit(ActivateAbility(d.player1, witness, eternalizeId)).outcome shouldBe Outcome.Done
        d.passPriority(d.player1)
        d.castSpellWithTargets(
            d.player2, response, listOf(ChosenTarget.Card(witness, d.player1, Zone.EXILE))
        ).outcome shouldBe Outcome.Done
        d.bothPass()
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"
        d.bothPass()

        val token = d.findPermanent(d.player1, "Timeless Witness").shouldNotBeNull()
        d.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
        d.state.getEntity(token)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.BLACK)
        d.state.projectedState.getPower(token) shouldBe 4
        d.state.projectedState.getToughness(token) shouldBe 4
        d.getGraveyardCardNames(d.player1) shouldContain "Timeless Witness"
    }

    test("Clone sees the eternalized token's black 4/4 Zombie and no-mana-cost copiable values") {
        val d = driver()
        val witness = d.putCardInGraveyard(d.player1, "Timeless Witness")
        activate(d, witness)
        d.bothPass()
        val token = d.findPermanent(d.player1, "Timeless Witness").shouldNotBeNull()

        val clone = d.putCardInHand(d.player1, "Clone")
        d.giveMana(d.player1, Color.BLUE, 4)
        d.castSpell(d.player1, clone).outcome shouldBe Outcome.Done
        d.bothPass()
        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.options shouldContain token
        d.submitCardSelection(d.player1, listOf(token)).error shouldBe null

        val copiedCard = d.state.getEntity(clone)!!.get<CardComponent>().shouldNotBeNull()
        copiedCard.name shouldBe "Timeless Witness"
        copiedCard.colors shouldBe setOf(Color.BLACK)
        copiedCard.manaCost shouldBe ManaCost.ZERO
        copiedCard.manaValue shouldBe 0
        copiedCard.typeLine.subtypes shouldContain Subtype.ZOMBIE
        copiedCard.typeLine.subtypes shouldContain Subtype("Human")
        copiedCard.typeLine.subtypes shouldContain Subtype("Shaman")
        d.state.projectedState.getPower(clone) shouldBe 4
        d.state.projectedState.getToughness(clone) shouldBe 4
    }
})
