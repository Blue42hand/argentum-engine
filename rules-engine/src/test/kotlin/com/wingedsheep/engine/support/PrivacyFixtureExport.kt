package com.wingedsheep.engine.support

import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/** Scratch-only export of exact native DTOs for the private #72 offline privacy audit. */
fun exportPrivacyFixture(
    caseName: String,
    view: ClientGameState,
    trackedIds: List<EntityId> = emptyList(),
    pending: PendingDecision? = null,
) {
    val target = System.getenv("ARGENTUM_PRIVACY_FIXTURE_JSONL") ?: return
    val path = Path.of(target)
    val privateDirectory = Files.getPosixFilePermissions(path.parent).none {
        it in setOf(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE,
        )
    }
    require(privateDirectory) { "privacy fixture directory must be private" }
    if (!Files.exists(path)) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    }
    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        classDiscriminator = "type"
        serializersModule = engineSerializersModule
    }
    val record = buildJsonObject {
        put("case", caseName)
        put("state", json.encodeToJsonElement(ClientGameState.serializer(), view))
        putJsonArray("trackedIds") { trackedIds.forEach { add(JsonPrimitive(it.value)) } }
        if (pending != null) put("pendingDecision", json.encodeToJsonElement(PendingDecision.serializer(), pending))
    }
    Files.writeString(path, record.toString() + "\n", StandardOpenOption.APPEND)
}
