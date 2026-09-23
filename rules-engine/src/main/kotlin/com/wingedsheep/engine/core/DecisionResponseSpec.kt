package com.wingedsheep.engine.core

import kotlinx.serialization.Serializable

/**
 * Machine-readable native response contract for a PendingDecision.
 *
 * External player/controller adapters should consume this instead of maintaining their own
 * PendingDecision -> DecisionResponse ontology. It describes the JSON response shape only;
 * DecisionValidators remains authoritative for whether the chosen values are legal in state.
 */
@Serializable
data class DecisionResponseSpec(
    val responseType: String,
    val requiredFields: Map<String, DecisionResponseFieldKind>,
    val cancelAllowed: Boolean = false,
)

@Serializable
enum class DecisionResponseFieldKind {
    BOOLEAN,
    INTEGER,
    STRING,
    ENTITY_ID_ARRAY,
    INTEGER_ARRAY,
    ENTITY_ID_ARRAY_ARRAY,
    MAP,
    DAMAGE_EDGE_AMOUNT_ARRAY,
}

/**
 * Return the native DecisionResponse wire shape expected for this decision.
 *
 * The exhaustive sealed-interface branch is intentional: adding a new PendingDecision subtype
 * forces this contract to be updated at compile time rather than leaving external controllers to
 * discover the mismatch during a live game.
 */
fun PendingDecision.responseSpec(): DecisionResponseSpec = when (this) {
    is ChooseTargetsDecision -> DecisionResponseSpec(
        responseType = "TargetsResponse",
        requiredFields = mapOf("selectedTargets" to DecisionResponseFieldKind.MAP),
        cancelAllowed = canCancel,
    )
    is SelectCardsDecision -> DecisionResponseSpec(
        responseType = "CardsSelectedResponse",
        requiredFields = mapOf("selectedCards" to DecisionResponseFieldKind.ENTITY_ID_ARRAY),
    )
    is YesNoDecision -> DecisionResponseSpec(
        responseType = "YesNoResponse",
        requiredFields = mapOf("choice" to DecisionResponseFieldKind.BOOLEAN),
    )
    is BatchYesNoDecision -> DecisionResponseSpec(
        responseType = "BatchYesNoResponse",
        requiredFields = mapOf(
            "choice" to DecisionResponseFieldKind.BOOLEAN,
            "applyToAll" to DecisionResponseFieldKind.BOOLEAN,
        ),
    )
    is ChooseModeDecision -> DecisionResponseSpec(
        responseType = "ModesChosenResponse",
        requiredFields = mapOf("selectedModes" to DecisionResponseFieldKind.INTEGER_ARRAY),
    )
    is ChooseColorDecision -> DecisionResponseSpec(
        responseType = "ColorChosenResponse",
        requiredFields = mapOf("color" to DecisionResponseFieldKind.STRING),
    )
    is ChooseNumberDecision -> DecisionResponseSpec(
        responseType = "NumberChosenResponse",
        requiredFields = mapOf("number" to DecisionResponseFieldKind.INTEGER),
    )
    is DistributeDecision -> DecisionResponseSpec(
        responseType = "DistributionResponse",
        requiredFields = mapOf("distribution" to DecisionResponseFieldKind.MAP),
    )
    is OrderObjectsDecision -> DecisionResponseSpec(
        responseType = "OrderedResponse",
        requiredFields = mapOf("orderedObjects" to DecisionResponseFieldKind.ENTITY_ID_ARRAY),
    )
    is SplitPilesDecision -> DecisionResponseSpec(
        responseType = "PilesSplitResponse",
        requiredFields = mapOf("piles" to DecisionResponseFieldKind.ENTITY_ID_ARRAY_ARRAY),
    )
    is ChooseOptionDecision -> DecisionResponseSpec(
        responseType = "OptionChosenResponse",
        requiredFields = mapOf("optionIndex" to DecisionResponseFieldKind.INTEGER),
        cancelAllowed = canCancel,
    )
    is ChooseReplacementDecision -> DecisionResponseSpec(
        responseType = "ReplacementChosenResponse",
        requiredFields = mapOf(
            "fromIndex" to DecisionResponseFieldKind.INTEGER,
            "toIndex" to DecisionResponseFieldKind.INTEGER,
        ),
    )
    is BudgetModalDecision -> DecisionResponseSpec(
        responseType = "BudgetModalResponse",
        requiredFields = mapOf("selectedModeIndices" to DecisionResponseFieldKind.INTEGER_ARRAY),
    )
    is AssignDamageDecision -> DecisionResponseSpec(
        responseType = "DamageAssignmentResponse",
        requiredFields = mapOf("assignments" to DecisionResponseFieldKind.MAP),
    )
    is CombatResolutionDecision -> DecisionResponseSpec(
        responseType = "CombatResolutionResponse",
        requiredFields = mapOf("edges" to DecisionResponseFieldKind.DAMAGE_EDGE_AMOUNT_ARRAY),
    )
    is SearchLibraryDecision -> DecisionResponseSpec(
        responseType = "CardsSelectedResponse",
        requiredFields = mapOf("selectedCards" to DecisionResponseFieldKind.ENTITY_ID_ARRAY),
    )
    is ReorderLibraryDecision -> DecisionResponseSpec(
        responseType = "OrderedResponse",
        requiredFields = mapOf("orderedObjects" to DecisionResponseFieldKind.ENTITY_ID_ARRAY),
    )
    is SelectManaSourcesDecision -> DecisionResponseSpec(
        responseType = "ManaSourcesSelectedResponse",
        requiredFields = mapOf(
            "selectedSources" to DecisionResponseFieldKind.ENTITY_ID_ARRAY,
            "autoPay" to DecisionResponseFieldKind.BOOLEAN,
            "waterbendPermanents" to DecisionResponseFieldKind.ENTITY_ID_ARRAY,
            "declined" to DecisionResponseFieldKind.BOOLEAN,
        ),
    )
}
