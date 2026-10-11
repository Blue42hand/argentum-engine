package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.GameStateSerializer
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.embalm
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** A departed source may supply printed copy values; stale action targets must still fail closed. */
class TokenCopyDepartedSourceTest : FunSpec({
    val embalmCreature = card("Test Embalm Cleric") {
        manaCost = "{2}{W}"
        typeLine = "Creature — Human Cleric"
        power = 2
        toughness = 3
        embalm("{3}{W}")
    }
    val selfAction = card("Test Exiled Self Action") {
        manaCost = "{1}{B}"
        typeLine = "Creature — Spirit"
        power = 2
        toughness = 2
        activatedAbility {
            cost = Costs.ExileSelf
            effect = Effects.Move(EffectTarget.Self, Zone.HAND)
            activateFromZone = Zone.GRAVEYARD
        }
    }
    val copyOther = card("Test Copy Other") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            val chosen = target(TargetFilter(GameObjectFilter.Creature))
            effect = Effects.CreateTokenCopyOfTarget(chosen)
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(embalmCreature, selfAction, copyOther))
        initMirrorMatch(deck = Deck.of("Plains" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("embalm copies its printed card after the exiled source moves to the graveyard") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, embalmCreature.name)
        d.giveMana(d.player1, Color.WHITE, 4)
        d.submit(ActivateAbility(d.player1, source, embalmCreature.activatedAbilities.single().id))
            .outcome shouldBe Outcome.Done
        d.getExile(d.player1) shouldContain source

        d.replaceState(d.zones.moveToZone(d.state, source, Zone.GRAVEYARD).state)
        d.bothPass()

        val token = d.findPermanent(d.player1, embalmCreature.name).shouldNotBeNull()
        val card = d.state.getEntity(token)!!.get<CardComponent>().shouldNotBeNull()
        d.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
        card.colors shouldBe setOf(Color.WHITE)
        card.manaCost shouldBe ManaCost.ZERO
        card.typeLine.subtypes shouldContain Subtype.ZOMBIE
        card.typeLine.subtypes shouldContain Subtype("Human")
        card.typeLine.subtypes shouldContain Subtype("Cleric")
        d.state.projectedState.getPower(token) shouldBe 2
        d.state.projectedState.getToughness(token) shouldBe 3
        d.getGraveyardCardNames(d.player1) shouldContain embalmCreature.name
    }

    test("embalm ignores new-object values after the exiled source reenters the battlefield") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, embalmCreature.name)
        d.giveMana(d.player1, Color.WHITE, 4)
        d.submit(ActivateAbility(d.player1, source, embalmCreature.activatedAbilities.single().id))
            .outcome shouldBe Outcome.Done

        d.replaceState(d.zones.moveToZone(d.state, source, Zone.BATTLEFIELD).state)
        // Simulate the new battlefield object's distinct copiable values. The pending activation
        // must read its captured printed card, not this later object with the same entity ID.
        val later = d.state.getEntity(source)!!.get<CardComponent>().shouldNotBeNull()
        d.replaceState(d.state.updateEntity(source) { container ->
            container.with(later.copy(name = "New Object", baseStats = CreatureStats(9, 9)))
        })
        d.bothPass()

        val token = d.findPermanent(d.player1, embalmCreature.name).shouldNotBeNull()
        val card = d.state.getEntity(token)!!.get<CardComponent>().shouldNotBeNull()
        card.name shouldBe embalmCreature.name
        card.colors shouldBe setOf(Color.WHITE)
        card.manaCost shouldBe ManaCost.ZERO
        d.state.projectedState.getPower(token) shouldBe 2
        d.state.projectedState.getToughness(token) shouldBe 3
    }

    test("restored game preserves the departed embalm source's printed copy values") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, embalmCreature.name)
        d.giveMana(d.player1, Color.WHITE, 4)
        d.submit(ActivateAbility(d.player1, source, embalmCreature.activatedAbilities.single().id))
            .outcome shouldBe Outcome.Done
        d.replaceState(d.zones.moveToZone(d.state, source, Zone.BATTLEFIELD).state)
        val later = d.state.getEntity(source)!!.get<CardComponent>().shouldNotBeNull()
        d.replaceState(d.state.updateEntity(source) { container ->
            container.with(later.copy(name = "Restored New Object", baseStats = CreatureStats(9, 9)))
        })

        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val saved = json.encodeToString(GameState.serializer(), d.state)
        d.replaceState(json.decodeFromString(GameStateSerializer, saved))
        d.bothPass()

        val token = d.findPermanent(d.player1, embalmCreature.name).shouldNotBeNull()
        val card = d.state.getEntity(token)!!.get<CardComponent>().shouldNotBeNull()
        d.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
        card.name shouldBe embalmCreature.name
        card.colors shouldBe setOf(Color.WHITE)
        card.manaCost shouldBe ManaCost.ZERO
        card.typeLine.subtypes shouldContain Subtype.ZOMBIE
        d.state.projectedState.getPower(token) shouldBe 2
        d.state.projectedState.getToughness(token) shouldBe 3
    }

    test("copying a different target does nothing when that target leaves before resolution") {
        val d = driver()
        val bears = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val copy = d.putCardInHand(d.player1, copyOther.name)
        val bolt = d.putCardInHand(d.player2, "Lightning Bolt")
        d.giveMana(d.player1, Color.BLUE, 1)
        d.giveMana(d.player2, Color.RED, 1)

        d.castSpell(d.player1, copy, listOf(bears)).outcome shouldBe Outcome.Done
        d.passPriority(d.player1)
        d.castSpell(d.player2, bolt, listOf(bears)).outcome shouldBe Outcome.Done
        d.bothPass()
        d.findPermanent(d.player1, "Grizzly Bears") shouldBe null
        d.bothPass()

        d.state.getBattlefield().count { d.state.getEntity(it)?.has<TokenComponent>() == true } shouldBe 0
    }

    test("a stale Self action does not move the source's new graveyard object to hand") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, selfAction.name)
        d.submit(ActivateAbility(d.player1, source, selfAction.activatedAbilities.single().id))
            .outcome shouldBe Outcome.Done
        d.getExile(d.player1) shouldContain source

        d.replaceState(d.zones.moveToZone(d.state, source, Zone.GRAVEYARD).state)
        d.bothPass()

        d.getGraveyardCardNames(d.player1) shouldContain selfAction.name
        d.getHand(d.player1).contains(source) shouldBe false
    }
})
