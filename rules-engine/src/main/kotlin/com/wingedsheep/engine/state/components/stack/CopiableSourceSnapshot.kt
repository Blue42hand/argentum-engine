package com.wingedsheep.engine.state.components.stack

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.engine.state.components.identity.NumericKeywordValuesComponent
import com.wingedsheep.engine.state.components.identity.ToxicComponent
import com.wingedsheep.engine.state.components.identity.copiableCardComponent
import kotlinx.serialization.Serializable

/** The copiable values of an ability's own cost-exiled card at its departure from exile. */
@Serializable
data class CopiableSourceSnapshot(
    val objectRef: ObjectRef,
    val card: CardComponent,
    val numericKeywords: NumericKeywordValuesComponent? = null,
    val toxic: ToxicComponent? = null,
    val doubleFaced: DoubleFacedComponent? = null,
) {
    fun asContainer(): ComponentContainer {
        var container = ComponentContainer.of(card)
        numericKeywords?.let { container = container.with(it) }
        toxic?.let { container = container.with(it) }
        doubleFaced?.let { container = container.with(it) }
        return container
    }

    companion object {
        fun capture(objectRef: ObjectRef, container: ComponentContainer): CopiableSourceSnapshot? =
            container.copiableCardComponent()?.let { card ->
                CopiableSourceSnapshot(
                    objectRef = objectRef,
                    card = card,
                    numericKeywords = container.get<NumericKeywordValuesComponent>(),
                    toxic = container.get<ToxicComponent>(),
                    doubleFaced = container.get<DoubleFacedComponent>(),
                )
            }
    }
}
