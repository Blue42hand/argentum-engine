package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.legalactions.LegalAction
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Argentum-owned durable semantic identity for Gym decisions and actions.
 *
 * Execution handles such as the per-observation integer `actionId` and
 * [PendingDecision.id] remain the authoritative way to submit a response to the
 * live environment. These fingerprints are deliberately separate: they give
 * replay, training, evaluation, and external pilots a stable provenance key
 * without requiring those consumers to invent a second action ontology.
 *
 * The hashes are schema-scoped. A contract bump therefore cannot silently make
 * observations produced under two different schemas compare as equivalent.
 */
object SemanticIdentity {
    const val ACTION_VERSION: String = "argentum-action-v1"
    const val DECISION_VERSION: String = "argentum-decision-v1"
    const val RESPONSE_VERSION: String = "argentum-decision-response-v1"

    private val json = Json {
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * Stable identity of one engine-authored legal action template.
     *
     * This intentionally hashes the structured [GameAction], not the human
     * description. Current-state affordances such as the set of valid targets
     * remain observation provenance rather than part of the action's identity;
     * [TrainingObservation.stateDigest] binds a recorded choice to that exact
     * information set.
     */
    fun forLegalAction(action: LegalAction): String {
        val payload = buildJsonObject {
            put("schemaHash", SchemaHash.CURRENT)
            put("actionType", action.actionType)
            put("action", json.encodeToJsonElement(GameAction.serializer(), action.action))
        }
        return "$ACTION_VERSION:${sha256(canonical(payload))}"
    }

    /**
     * Stable identity of a pending engine decision, excluding its routing nonce
     * and fields whose only purpose is presentation.
     *
     * Choice-bearing structure (options, target requirements, ranges, source
     * entity, ability identity, etc.) remains in the payload. This means a
     * semantically different choice gets a different fingerprint even if the UI
     * prompt happens to be the same.
     */
    fun forPendingDecision(decision: PendingDecision): String {
        val raw = json.encodeToJsonElement(PendingDecision.serializer(), decision)
        val semantic = stripKeys(raw, DECISION_PRESENTATION_KEYS)
        val payload = buildJsonObject {
            put("schemaHash", SchemaHash.CURRENT)
            put("decision", semantic)
        }
        return "$DECISION_VERSION:${sha256(canonical(payload))}"
    }

    /**
     * Stable identity of a concrete response choice, independent of the live
     * decision routing id to which it is currently bound.
     */
    fun forDecisionResponse(response: DecisionResponse): String {
        val raw = json.encodeToJsonElement(DecisionResponse.serializer(), response)
        val semantic = stripKeys(raw, setOf("decisionId"))
        val payload = buildJsonObject {
            put("schemaHash", SchemaHash.CURRENT)
            put("response", semantic)
        }
        return "$RESPONSE_VERSION:${sha256(canonical(payload))}"
    }

    /**
     * These fields are explicitly presentation/routing metadata in the engine
     * contract. They must not make an otherwise identical decision look novel
     * to a replay or training consumer.
     *
     * We intentionally do *not* strip every text field: some decision types
     * (for example ChooseOption and modal decisions) currently carry part of
     * their semantic option identity as strings. Until the rules contract gives
     * those options a stronger structured identity, retaining them is safer than
     * collapsing distinct choices.
     */
    private val DECISION_PRESENTATION_KEYS = setOf(
        "id",
        "prompt",
        "sourceName",
        "effectHint",
        "inlineOnTrigger",
        "hint",
        "yesText",
        "noText",
        "useTargetingUI",
        "selectedLabel",
        "remainderLabel"
    )

    private fun stripKeys(element: JsonElement, ignored: Set<String>): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .filterNot { (key, _) -> key in ignored }
                .associate { (key, value) -> key to stripKeys(value, ignored) }
        )
        is JsonArray -> JsonArray(element.map { stripKeys(it, ignored) })
        else -> element
    }

    /** Canonical JSON: object keys sorted; array order retained as semantic. */
    private fun canonical(element: JsonElement): String = when (element) {
        JsonNull -> "null"
        is JsonPrimitive -> element.toString()
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonical(it)
        }
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                "${JsonPrimitive(key)}:${canonical(value)}"
            }
    }

    private fun sha256(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
