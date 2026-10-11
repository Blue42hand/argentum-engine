package com.wingedsheep.engine.state.components.stack

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.NumericKeywordValuesComponent
import com.wingedsheep.engine.state.components.identity.ToxicComponent
import com.wingedsheep.engine.state.components.identity.copiableCardComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class CopiableSourceSnapshotSerializationTest : FunSpec({
    test("a departing copy source survives stack serialization with its exact object generation") {
        val sourceId = EntityId.of("copy-source")
        val ownerId = EntityId.of("owner")
        val card = CardComponent(
            cardDefinitionId = "Snapshot Creature",
            name = "Snapshot Creature",
            manaCost = ManaCost.parse("{2}{G}"),
            typeLine = TypeLine(cardTypes = setOf(CardType.CREATURE)),
            ownerId = ownerId,
        )
        val source = ComponentContainer.of(
            card, NumericKeywordValuesComponent(mapOf(Keyword.BUSHIDO to 2)), ToxicComponent(3)
        )
        val snapshot = CopiableSourceSnapshot.capture(ObjectRef(sourceId, 9), source)!!
        val stacked = ActivatedAbilityOnStackComponent(
            sourceId = sourceId,
            sourceName = card.name,
            controllerId = ownerId,
            effect = Effects.DrawCards(1),
            departedCopiableSource = snapshot,
        )
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val encoded = json.encodeToString(ActivatedAbilityOnStackComponent.serializer(), stacked)
        val decoded = json.decodeFromString(ActivatedAbilityOnStackComponent.serializer(), encoded)

        decoded.departedCopiableSource shouldBe snapshot
        decoded.departedCopiableSource!!.asContainer().copiableCardComponent() shouldBe card
        decoded.departedCopiableSource!!.asContainer().get<NumericKeywordValuesComponent>() shouldBe
            NumericKeywordValuesComponent(mapOf(Keyword.BUSHIDO to 2))
        decoded.departedCopiableSource!!.asContainer().get<ToxicComponent>() shouldBe ToxicComponent(3)
    }
})
