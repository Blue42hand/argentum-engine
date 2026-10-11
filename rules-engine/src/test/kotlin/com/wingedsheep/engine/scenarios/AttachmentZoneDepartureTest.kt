package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Attachments match their host's real departure, including a token's, with captured object identity. */
class AttachmentZoneDepartureTest : FunSpec({
    val aura = card("Attachment Departure Probe") {
        typeLine = "Enchantment — Aura"
        triggeredAbility { trigger = Triggers.attached.dies(); effect = Effects.GainLife(3) }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + aura)
        initMirrorMatch(Deck.of("Mountain" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun fixture(tokenHost: Boolean = false): Triple<GameTestDriver, EntityId, EntityId> {
        val d = driver()
        val host = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        if (tokenHost) d.addComponent(host, TokenComponent)
        val source = d.putPermanentOnBattlefield(d.player1, aura.name)
        d.addComponent(source, AttachedToComponent(host))
        d.addComponent(host, AttachmentsComponent(listOf(source)))
        return Triple(d, host, source)
    }
    fun triggers(d: GameTestDriver, events: List<GameEvent>) =
        d.services.triggerDetector.detectTriggers(d.state, events)
            .filter { it.sourceName == aura.name }
    fun checkTrigger(d: GameTestDriver, host: EntityId, source: EntityId,
                     events: List<GameEvent>) {
        val pending = triggers(d, events).single()
        pending.sourceId shouldBe source
        pending.controllerId shouldBe d.player1
        pending.triggerContext.triggeringEntityId shouldBe host
        val resolved = d.services.effectExecutorRegistry.execute(
            d.state, pending.ability.effect,
            EffectContext(sourceId = source, controllerId = pending.controllerId,
                triggerContext = pending.triggerContext, objectReferences = pending.objectReferences)
        )
        resolved.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 23
        resolved.state.getEntity(d.player2)!!.get<LifeTotalComponent>()!!.life shouldBe 20
    }

    for (tokenHost in listOf(false, true)) {
        test("host ${if (tokenHost) "token" else "card"} death emits one Aura trigger for opposite controller") {
            val (d, host, source) = fixture(tokenHost)
            val originalHost = d.state.objectRef(host)
            val originalAura = d.state.objectRef(source)
            val move = d.zones.moveToZone(d.state, host, Zone.GRAVEYARD)
            d.replaceState(move.state)
            val hostEvent = move.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == host }
            hostEvent.lastKnown!!.attachmentIds shouldBe listOf(source)
            hostEvent.oldObject shouldBe originalHost
            hostEvent.newObject shouldBe d.state.objectRef(host)
            val pending = triggers(d, move.events).single()
            pending.objectReferences.origin shouldBe originalAura
            pending.triggerContext.triggeringOrigin shouldBe originalHost
            pending.objectReferences.triggering shouldBe hostEvent.newObject
            checkTrigger(d, host, source, move.events)
            if (tokenHost) {
                // Token cleanup can erase the host entity; the emitted departure is sufficient.
                hostEvent.lastKnown!!.wasToken shouldBe true
            }
        }
    }

    for (destination in listOf(Zone.HAND, Zone.EXILE)) {
        test("host leaves for $destination without firing attached dies") {
            val (d, host, _) = fixture()
            val move = d.zones.moveToZone(d.state, host, destination)
            d.replaceState(move.state)
            move.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == host }.toZone shouldBe destination
            triggers(d, move.events).size shouldBe 0
        }
    }

    test("Aura removed before host death does not trigger") {
        val (d, host, source) = fixture()
        val auraMove = d.zones.moveToZone(d.state, source, Zone.GRAVEYARD)
        d.replaceState(auraMove.state)
        triggers(d, auraMove.events).size shouldBe 0
        val hostMove = d.zones.moveToZone(d.state, host, Zone.GRAVEYARD)
        d.replaceState(hostMove.state)
        hostMove.events.filterIsInstance<ZoneChangeEvent>().single().lastKnown!!.attachmentIds shouldBe emptyList()
        triggers(d, hostMove.events).size shouldBe 0
        // Sequential instructions can still deliver both real moves in one trigger-detection batch.
        triggers(d, auraMove.events + hostMove.events).size shouldBe 0
    }

    test("Aura that lost all abilities while attached has no host-death trigger") {
        val (d, host, source) = fixture()
        val stripped = d.services.effectExecutorRegistry.execute(
            d.state, Effects.RemoveAllAbilities(EffectTarget.Self, Duration.Permanent),
            EffectContext(sourceId = source, controllerId = d.player1)
        )
        d.replaceState(stripped.state)
        d.state.projectedState.hasLostAllAbilities(source) shouldBe true
        val death = d.zones.moveToZone(d.state, host, Zone.GRAVEYARD)
        d.replaceState(death.state)
        death.events.filterIsInstance<ZoneChangeEvent>().single().lastKnown!!.attachmentIds shouldBe listOf(source)
        triggers(d, death.events).size shouldBe 0
    }

    test("face-down Aura has no host-death trigger before orphan state actions run") {
        val (d, host, source) = fixture()
        val faced = d.services.effectExecutorRegistry.execute(
            d.state, Effects.TurnFaceDown(EffectTarget.Self),
            EffectContext(sourceId = source, controllerId = d.player1)
        )
        faced.error shouldBe null
        d.replaceState(faced.state)
        d.state.getEntity(source)!!.has<FaceDownComponent>() shouldBe true
        val death = d.zones.moveToZone(d.state, host, Zone.GRAVEYARD)
        d.replaceState(death.state)
        death.events.filterIsInstance<ZoneChangeEvent>().single().lastKnown!!.attachmentIds shouldBe listOf(source)
        triggers(d, death.events).size shouldBe 0
    }

    test("simultaneously departing Aura with lost abilities cannot trigger from its last-known attachment") {
        val (d, host, source) = fixture()
        val stripped = d.services.effectExecutorRegistry.execute(
            d.state, Effects.RemoveAllAbilities(EffectTarget.Self, Duration.Permanent),
            EffectContext(sourceId = source, controllerId = d.player1)
        )
        d.replaceState(stripped.state)
        d.state.projectedState.hasLostAllAbilities(source) shouldBe true
        val death = d.zones.moveToZoneBatch(d.state, listOf(source, host), Zone.GRAVEYARD)
        d.replaceState(death.state)
        val exits = death.events.filterIsInstance<ZoneChangeEvent>()
        exits.single { it.entityId == source }.lastKnown!!.lostAllAbilities shouldBe true
        exits.single { it.entityId == host }.lastKnown!!.attachmentIds shouldBe listOf(source)
        triggers(d, death.events).size shouldBe 0
    }

    test("host exile and same-ID battlefield return cannot become an attached dies event") {
        val (d, host, _) = fixture()
        val originalHost = d.state.objectRef(host)
        val exile = d.zones.moveToZone(d.state, host, Zone.EXILE)
        d.replaceState(exile.state)
        val returnToBattlefield = d.zones.moveToZone(d.state, host, Zone.BATTLEFIELD)
        d.replaceState(returnToBattlefield.state)
        val exitsAndEntries = exile.events + returnToBattlefield.events
        val zoneEvents = exitsAndEntries.filterIsInstance<ZoneChangeEvent>()
        zoneEvents.map { it.toZone } shouldBe listOf(Zone.EXILE, Zone.BATTLEFIELD)
        zoneEvents.first().oldObject shouldBe originalHost
        (d.state.objectRef(host) == originalHost) shouldBe false
        triggers(d, exitsAndEntries).size shouldBe 0
    }

    for (auraFirst in listOf(false, true)) {
        test("simultaneous host and Aura death preserves identity when ${if (auraFirst) "Aura" else "host"} moves first") {
            val (d, host, source) = fixture()
            val hostBefore = d.state.objectRef(host)
            val auraBefore = d.state.objectRef(source)
            val ids = if (auraFirst) listOf(source, host) else listOf(host, source)
            val move = d.zones.moveToZoneBatch(d.state, ids, Zone.GRAVEYARD)
            d.replaceState(move.state)
            val exits = move.events.filterIsInstance<ZoneChangeEvent>()
            exits.map { it.entityId }.toSet() shouldBe ids.toSet()
            val hostExit = exits.single { it.entityId == host }
            val auraExit = exits.single { it.entityId == source }
            hostExit.lastKnown!!.attachmentIds shouldBe listOf(source)
            auraExit.lastKnown!!.attachedTo shouldBe host
            hostExit.oldObject shouldBe hostBefore
            auraExit.oldObject shouldBe auraBefore
            val pending = triggers(d, move.events).single()
            pending.objectReferences.origin shouldBe auraBefore
            pending.objectReferences.source shouldBe auraExit.newObject
            pending.triggerContext.triggeringOrigin shouldBe hostBefore
            pending.objectReferences.triggering shouldBe hostExit.newObject
            checkTrigger(d, host, source, move.events)
            (source in d.state.getZone(ZoneKey(d.player1, Zone.GRAVEYARD))) shouldBe true
        }
    }

    test("DestroyAll through ActionProcessor freezes attachment links when Aura exits first") {
        val sweep = card("Attachment Sweep Probe") {
            manaCost = "{R}"
            typeLine = "Sorcery"
            spell { effect = Effects.DestroyAll(Filters.NonlandPermanent) }
        }
        val d = driver()
        d.registerCard(sweep)
        val source = d.putPermanentOnBattlefield(d.player1, aura.name)
        val host = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(source, AttachedToComponent(host))
        d.addComponent(host, AttachmentsComponent(listOf(source)))
        (d.state.getBattlefield().indexOf(source) < d.state.getBattlefield().indexOf(host)) shouldBe true
        val spell = d.putCardInHand(d.player1, sweep.name)
        d.giveMana(d.player1, Color.RED, 1)
        d.castSpell(d.player1, spell).error shouldBe null
        d.bothPass().error shouldBe null
        val exits = d.events.filterIsInstance<ZoneChangeEvent>()
            .filter { it.fromZone == Zone.BATTLEFIELD && it.entityId in setOf(source, host) }
        exits.map { it.entityId } shouldBe listOf(source, host)
        exits.single { it.entityId == source }.lastKnown!!.attachedTo shouldBe host
        exits.single { it.entityId == host }.lastKnown!!.attachmentIds shouldBe listOf(source)
        d.stackSize shouldBe 1
        val queued = d.state.getEntity(d.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!
        queued.sourceId shouldBe source
        queued.controllerId shouldBe d.player1
        d.bothPass().error shouldBe null
        d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 23
        d.state.getEntity(d.player2)!!.get<LifeTotalComponent>()!!.life shouldBe 20
    }

    for (tokenHost in listOf(false, true)) {
        test("lethal spell damage to ${if (tokenHost) "token" else "card"} host runs Aura trigger through ActionProcessor") {
            val (d, host, source) = fixture(tokenHost)
            val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
            d.giveMana(d.player1, Color.RED, 1)
            d.castSpell(d.player1, bolt, listOf(host)).error shouldBe null
            d.bothPass().error shouldBe null
            d.stackSize shouldBe 1
            val hostExits = d.events.filterIsInstance<ZoneChangeEvent>()
                .filter { it.entityId == host && it.fromZone == Zone.BATTLEFIELD }
            hostExits.size shouldBe 1
            hostExits.single().lastKnown!!.attachmentIds shouldBe listOf(source)
            if (tokenHost) {
                (host in d.state.getZone(ZoneKey(d.player2, Zone.BATTLEFIELD))) shouldBe false
                (host in d.state.getZone(ZoneKey(d.player2, Zone.GRAVEYARD))) shouldBe false
            }
            d.bothPass().error shouldBe null
            d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 23
            d.state.getEntity(d.player2)!!.get<LifeTotalComponent>()!!.life shouldBe 20
        }
    }

    val returningAura = card("Attachment Returning Probe") {
        typeLine = "Enchantment — Aura"
        triggeredAbility {
            trigger = Triggers.attached.dies()
            effect = Effects.ReturnToHandFromGraveyard(EffectTarget.Self)
        }
    }
    val destroyHost = card("Attachment Destroy Probe") {
        manaCost = "{B}"
        typeLine = "Instant"
        spell {
            val creature = target(TargetFilter.Creature)
            effect = Effects.Destroy(creature)
        }
    }
    fun queuedReturn(): Triple<GameTestDriver, EntityId, EntityId> {
        val d = driver()
        d.registerCards(listOf(returningAura, destroyHost))
        val host = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val source = d.putPermanentOnBattlefield(d.player1, returningAura.name)
        d.addComponent(source, AttachedToComponent(host))
        d.addComponent(host, AttachmentsComponent(listOf(source)))
        val removal = d.putCardInHand(d.player1, destroyHost.name)
        d.giveMana(d.player1, Color.BLACK, 1)
        d.castSpell(d.player1, removal, listOf(host)).error shouldBe null
        d.bothPass().error shouldBe null
        d.stackSize shouldBe 1
        return Triple(d, host, source)
    }

    test("destroyed host queues one attached trigger and orphan cleanup rebinds its immediate graveyard source") {
        val (d, host, source) = queuedReturn()
        val hostExit = d.events.filterIsInstance<ZoneChangeEvent>()
            .single { it.entityId == host && it.fromZone == Zone.BATTLEFIELD }
        val auraExit = d.events.filterIsInstance<ZoneChangeEvent>()
            .single { it.entityId == source && it.fromZone == Zone.BATTLEFIELD }
        hostExit.lastKnown!!.attachmentIds shouldBe listOf(source)
        auraExit.lastKnown!!.attachedTo shouldBe host
        (source in d.state.getZone(ZoneKey(d.player1, Zone.GRAVEYARD))) shouldBe true
        val queued = d.state.getEntity(d.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!
        queued.objectReferences.origin shouldBe auraExit.oldObject
        queued.objectReferences.source shouldBe auraExit.newObject
        queued.objectReferences.triggering shouldBe hostExit.newObject
        d.bothPass().error shouldBe null
        (source in d.state.getZone(ZoneKey(d.player1, Zone.HAND))) shouldBe true
        d.stackSize shouldBe 0
    }

    test("Aura graveyard round trip cannot satisfy an earlier attached trigger's Self reference") {
        val (d, _, source) = queuedReturn()
        val queued = d.state.getEntity(d.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!
        val firstGraveyardObject = d.state.objectRef(source)
        queued.objectReferences.source shouldBe firstGraveyardObject
        val exile = d.zones.moveToZone(d.state, source, Zone.EXILE)
        d.replaceState(exile.state)
        val returnToGraveyard = d.zones.moveToZone(d.state, source, Zone.GRAVEYARD)
        d.replaceState(returnToGraveyard.state)
        d.state.objectRef(source) shouldBe returnToGraveyard.events.filterIsInstance<ZoneChangeEvent>().single().newObject
        (d.state.objectRef(source) == firstGraveyardObject) shouldBe false
        d.bothPass().error shouldBe null
        (source in d.state.getZone(ZoneKey(d.player1, Zone.GRAVEYARD))) shouldBe true
        (source in d.state.getZone(ZoneKey(d.player1, Zone.HAND))) shouldBe false
    }

    val equipment = card("Attachment Equipment Probe") {
        typeLine = "Artifact — Equipment"
        triggeredAbility { trigger = Triggers.attached.dies(); effect = Effects.GainLife(3) }
    }
    fun equipmentFixture(): Triple<GameTestDriver, EntityId, EntityId> {
        val d = driver()
        d.registerCard(equipment)
        val host = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val source = d.putPermanentOnBattlefield(d.player1, equipment.name)
        d.addComponent(source, AttachedToComponent(host))
        d.addComponent(host, AttachmentsComponent(listOf(source)))
        return Triple(d, host, source)
    }
    fun checkEquipmentGain(d: GameTestDriver, host: EntityId, source: EntityId, events: List<GameEvent>) {
        val pending = d.services.triggerDetector.detectTriggers(d.state, events)
            .filter { it.sourceName == equipment.name }.single()
        pending.sourceId shouldBe source
        pending.controllerId shouldBe d.player1
        pending.triggerContext.triggeringEntityId shouldBe host
        val resolved = d.services.effectExecutorRegistry.execute(
            d.state, pending.ability.effect,
            EffectContext(sourceId = source, controllerId = pending.controllerId,
                triggerContext = pending.triggerContext, objectReferences = pending.objectReferences)
        )
        resolved.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 23
        resolved.state.getEntity(d.player2)!!.get<LifeTotalComponent>()!!.life shouldBe 20
    }

    test("Equipment surviving its host's death triggers once from its live object") {
        val (d, host, source) = equipmentFixture()
        val sourceBefore = d.state.objectRef(source)
        val death = d.zones.moveToZone(d.state, host, Zone.GRAVEYARD)
        d.replaceState(death.state)
        death.events.filterIsInstance<ZoneChangeEvent>().single().lastKnown!!.attachmentIds shouldBe listOf(source)
        val pending = d.services.triggerDetector.detectTriggers(d.state, death.events)
            .filter { it.sourceName == equipment.name }.single()
        pending.objectReferences.origin shouldBe sourceBefore
        pending.objectReferences.source shouldBe sourceBefore
        (source in d.state.getZone(ZoneKey(d.player1, Zone.BATTLEFIELD))) shouldBe true
        checkEquipmentGain(d, host, source, death.events)
    }

    for (equipmentFirst in listOf(false, true)) {
        test("Equipment and host dying together trigger once when ${if (equipmentFirst) "Equipment" else "host"} moves first") {
            val (d, host, source) = equipmentFixture()
            val equipmentBefore = d.state.objectRef(source)
            val ids = if (equipmentFirst) listOf(source, host) else listOf(host, source)
            val death = d.zones.moveToZoneBatch(d.state, ids, Zone.GRAVEYARD)
            d.replaceState(death.state)
            val exits = death.events.filterIsInstance<ZoneChangeEvent>()
            exits.single { it.entityId == host }.lastKnown!!.attachmentIds shouldBe listOf(source)
            exits.single { it.entityId == source }.lastKnown!!.attachedTo shouldBe host
            val pending = d.services.triggerDetector.detectTriggers(d.state, death.events)
                .filter { it.sourceName == equipment.name }.single()
            pending.objectReferences.origin shouldBe equipmentBefore
            pending.objectReferences.source shouldBe equipmentBefore
            checkEquipmentGain(d, host, source, death.events)
        }
    }

    test("departed Equipment cannot attach from its graveyard or a later battlefield visit") {
        val reattaching = card("Attachment Reequip Probe") {
            typeLine = "Artifact — Equipment"
            triggeredAbility {
                trigger = Triggers.attached.dies()
                effect = Effects.AttachEquipment(EffectTarget.ContextTarget(0))
            }
        }
        val d = driver()
        d.registerCard(reattaching)
        val host = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val candidate = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val source = d.putPermanentOnBattlefield(d.player1, reattaching.name)
        d.addComponent(source, AttachedToComponent(host))
        d.addComponent(host, AttachmentsComponent(listOf(source)))
        val sourceBefore = d.state.objectRef(source)
        val death = d.zones.moveToZoneBatch(d.state, listOf(source, host), Zone.GRAVEYARD)
        d.replaceState(death.state)
        val pending = d.services.triggerDetector.detectTriggers(d.state, death.events)
            .filter { it.sourceName == reattaching.name }.single()
        pending.objectReferences.source shouldBe sourceBefore
        val context = EffectContext(sourceId = source, controllerId = d.player1,
            targets = listOf(ChosenTarget.Permanent(candidate)), triggerContext = pending.triggerContext,
            objectReferences = pending.objectReferences)
        val first = d.services.effectExecutorRegistry.execute(d.state, pending.ability.effect, context)
        first.error shouldBe null
        d.replaceState(first.state)
        d.state.getEntity(source)!!.get<AttachedToComponent>() shouldBe null
        d.state.getEntity(candidate)!!.get<AttachmentsComponent>()?.attachedIds.orEmpty() shouldBe emptyList()
        val laterBattlefield = d.zones.moveToZone(d.state, source, Zone.BATTLEFIELD)
        d.replaceState(laterBattlefield.state)
        (source in d.state.getZone(ZoneKey(d.player1, Zone.BATTLEFIELD))) shouldBe true
        (d.state.objectRef(source) == sourceBefore) shouldBe false
        val second = d.services.effectExecutorRegistry.execute(d.state, pending.ability.effect, context)
        second.error shouldBe null
        second.state.getEntity(source)!!.get<AttachedToComponent>() shouldBe null
        second.state.getEntity(candidate)!!.get<AttachmentsComponent>()?.attachedIds.orEmpty() shouldBe emptyList()
    }
})
