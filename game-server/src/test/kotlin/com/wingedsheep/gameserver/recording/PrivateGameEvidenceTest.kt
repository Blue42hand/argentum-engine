package com.wingedsheep.gameserver.recording

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class PrivateGameEvidenceTest : FunSpec({
    fun root() = Files.createTempDirectory("synthetic-game-evidence-").also {
        Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwx------"))
    }
    test("private source is ordered hashed durable and separates seat rows") {
        val root = root()
        val writer = PrivateGameEvidence.create(root, "synthetic-game", "a".repeat(40))
        writer.append("initialization", buildJsonObject { put("seed", 42) }, null)
        writer.append("seat_observation", buildJsonObject { put("ownHand", "synthetic") }, "seat-a")
        writer.append("terminal", buildJsonObject { put("winnerId", "seat-a") }, null)
        val path = root.resolve("synthetic-game/native-000000.ndjson")
        Files.getPosixFilePermissions(path) shouldBe PosixFilePermissions.fromString("rw-------")
        var prior = "0".repeat(64)
        Files.readAllLines(path).forEachIndexed { index, line ->
            val wrapper = Json.parseToJsonElement(line).jsonObject
            val body = wrapper.getValue("body").jsonPrimitive.content
            val digest = PrivateGameEvidence.sha256(body.toByteArray(Charsets.UTF_8))
            wrapper.getValue("sha256").jsonPrimitive.content shouldBe digest
            val row = Json.parseToJsonElement(body).jsonObject
            row.getValue("sequence").jsonPrimitive.long shouldBe index.toLong() + 1
            row.getValue("previousSha256").jsonPrimitive.content shouldBe prior
            row.getValue("visibility").jsonPrimitive.content shouldBe if (index == 1) "seat" else "admin"
            prior = digest
        }
        shouldThrow<IllegalArgumentException> {
            PrivateGameEvidence.create(root, "synthetic-game", "a".repeat(40))
        }
    }
    test("storage exhaustion preserves prefix and freezes terminal publication") {
        val root = root()
        val writer = PrivateGameEvidence.create(root, "bounded-game", "a".repeat(40), maxBytes = 1)
        writer.append("initialization", JsonObject(emptyMap()), null)
        writer.append("terminal", JsonObject(emptyMap()), null)
        Files.size(root.resolve("bounded-game/native-000000.ndjson")) shouldBe 0L
    }
    test("public root and traversal identity are rejected without permission changes") {
        val root = root()
        shouldThrow<IllegalArgumentException> { PrivateGameEvidence.create(root, "../other", "a".repeat(40)) }
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"))
        shouldThrow<IllegalArgumentException> { PrivateGameEvidence.create(root, "safe", "a".repeat(40)) }
        Files.getPosixFilePermissions(root) shouldBe PosixFilePermissions.fromString("rwxr-xr-x")
    }
    test("a recovered game resumes an intact prefix with an explicit new clock epoch") {
        val root = root()
        val first = PrivateGameEvidence.create(root, "recovered", "a".repeat(40))
        first.append("initialization", buildJsonObject { put("seed", 42) }, null)
        first.close() // synthetic process exit, no invented terminal
        val next = PrivateGameEvidence.create(root, "recovered", "a".repeat(40))
        next.append("terminal", buildJsonObject { put("winnerId", "synthetic-seat") }, null)
        val rows = Files.readAllLines(root.resolve("recovered/native-000000.ndjson")).map {
            Json.parseToJsonElement(Json.parseToJsonElement(it).jsonObject.getValue("body").jsonPrimitive.content).jsonObject
        }
        rows.map { it.getValue("sequence").jsonPrimitive.long } shouldBe listOf(1L, 2L, 3L)
        rows[1].getValue("kind").jsonPrimitive.content shouldBe "resume"
        (rows[0].getValue("clockEpoch") != rows[1].getValue("clockEpoch")) shouldBe true
    }
    test("a partial source is preserved and cannot block optional game recovery") {
        val root = root()
        val first = PrivateGameEvidence.create(root, "partial", "a".repeat(40))
        first.append("initialization", JsonObject(emptyMap()), null)
        first.close()
        val file = root.resolve("partial/native-000000.ndjson")
        Files.writeString(file, "partial", java.nio.file.StandardOpenOption.APPEND)
        val before = Files.readAllBytes(file)
        System.setProperty("game.recording.root", root.toString())
        System.setProperty("game.recording.engine-revision", "a".repeat(40))
        try {
            PrivateGameEvidence.fromSystemProperties("partial") shouldBe null
            Files.readAllBytes(file).toList() shouldBe before.toList()
        } finally {
            System.clearProperty("game.recording.root")
            System.clearProperty("game.recording.engine-revision")
        }
    }
})
