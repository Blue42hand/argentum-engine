package com.wingedsheep.engine.provenance

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
 * Engine-level durable semantic identity for authoritative Argentum actions and decisions.
 *
 * Live execution/routing handles remain authoritative for submission. These fingerprints are
 * deliberately separate provenance keys that can be reused by multiple surfaces (Gym,
 * game-server, replay tooling) without creating surface-specific action ontologies.
 *
 * [schemaScope] is supplied by the exposing surface. It prevents observations produced under
 * incompatible wire/projection schemas from silently comparing as equivalent while keeping the
 * underlying semantic canonicalization shared at the rules-engine boundary.
 */
object EngineSemanticIdentity {
    const val ACTION_VERSION: String = "argentum-action-v1"
    const val DECISION_VERSION: String = "argentum-decision-v1"
    const val RESPONSE_VERSION: String = "argentum-decision-response-v1"

    private val json = Json {
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun forLegalAction(action: LegalAction, schemaScope: String): String {
        val payload = buildJsonObject {
            put("schemaHash", schemaScope)
            put("actionType", action.actionType)
            put("action", json.encodeToJsonElement(GameAction.serializer(), action.action))
        }
        return "$ACTION_VERSION:${sha256(canonical(payload))}"
    }

    fun forPendingDecision(decision: PendingDecision, schemaScope: String): String {
        val raw = json.encodeToJsonElement(PendingDecision.serializer(), decision)
        val semantic = stripKeys(raw, DECISION_PRESENTATION_KEYS)
        val payload = buildJsonObject {
            put("schemaHash", schemaScope)
            put("decision", semantic)
        }
        return "$DECISION_VERSION:${sha256(canonical(payload))}"
    }

    fun forDecisionResponse(response: DecisionResponse, schemaScope: String): String {
        val raw = json.encodeToJsonElement(DecisionResponse.serializer(), response)
        val semantic = stripKeys(raw, setOf("decisionId"))
        val payload = buildJsonObject {
            put("schemaHash", schemaScope)
            put("response", semantic)
        }
        return "$RESPONSE_VERSION:${sha256(canonical(payload))}"
    }

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
