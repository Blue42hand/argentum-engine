package com.wingedsheep.gameserver.recording

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.util.Base64
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class PrivateRecordCodecTest : FunSpec({
    val fixture = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/record-codec-v1.json")).readText()).jsonObject
    fun bytes(key: String) = Base64.getDecoder().decode(fixture.getValue(key).jsonPrimitive.content)
    test("JDK and Python frames preserve exact UTF8 logical bytes and hashes") {
        val logical = bytes("logicalUtf8Base64")
        for (key in listOf("pythonFrameBase64", "jvmFrameBase64")) {
            PrivateRecordCodec.decode(bytes(key)).contentEquals(logical) shouldBe true
        }
        PrivateRecordCodec.decode(PrivateRecordCodec.encode(logical)).contentEquals(logical) shouldBe true
        (PrivateRecordCodec.encode(logical).size < logical.size / 10) shouldBe true
        val small = "{\"synthetic\":1}\n".toByteArray()
        PrivateRecordCodec.encode(small).contentEquals(small) shouldBe true
    }
    test("version length checksum truncation and concatenated members fail closed") {
        val frame = Json.parseToJsonElement(bytes("pythonFrameBase64").toString(Charsets.UTF_8)).jsonObject
        fun rejected(changes: Map<String, JsonElement>) {
            shouldThrowAny { PrivateRecordCodec.decode((JsonObject(frame + changes).toString() + "\n").toByteArray()) }
        }
        rejected(mapOf("recordCodec" to JsonPrimitive(true)))
        rejected(mapOf("recordCodec" to JsonPrimitive(2)))
        rejected(mapOf("decodedBytes" to JsonPrimitive("100")))
        rejected(mapOf("decodedBytes" to JsonPrimitive(64)))
        rejected(mapOf("decodedBytes" to JsonPrimitive(PrivateRecordCodec.MAX_RECORD_BYTES + 1)))
        rejected(mapOf("extra" to JsonPrimitive(1)))
        rejected(mapOf("encoding" to JsonPrimitive("unknown")))
        val gzip = Base64.getDecoder().decode(frame.getValue("data").jsonPrimitive.content)
        val damaged = gzip.copyOf().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() }
        for (data in listOf(damaged, gzip.copyOf(gzip.size - 1), gzip + gzip, gzip + byteArrayOf(1))) {
            rejected(mapOf("data" to JsonPrimitive(Base64.getEncoder().encodeToString(data))))
        }
    }
    test("partial physical tail is returned for crash detection but cannot decode") {
        val partial = bytes("jvmFrameBase64").dropLast(4).toByteArray()
        PrivateRecordCodec.readLine(ByteArrayInputStream(partial))!!.contentEquals(partial) shouldBe true
        shouldThrowAny { PrivateRecordCodec.decode(partial) }
    }
    test("measure synchronous native recorder compression and durability latency on synthetic state") {
        val root = Files.createTempDirectory("synthetic-codec-latency-")
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
        val writer = PrivateGameEvidence.create(root, "latency", "a".repeat(40))
        val payload = buildJsonObject { put("own", "synthetic-state-".repeat(70000)) }
        val times = (1..25).map {
            val start = System.nanoTime()
            writer.append("state_checkpoint", payload, null)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        writer.healthy() shouldBe true
        println("RECORD_CODEC_BENCH logicalPayloadBytes=${payload.toString().toByteArray().size} samples=${times.size} medianMs=${times[times.size/2]} p95Ms=${times[23]} physicalBytes=${Files.size(root.resolve("latency/native-000000.ndjson"))}")
        writer.close()
    }
    test("mixed legacy and compressed durable records resume with physical cap and exact chain") {
        val root = Files.createTempDirectory("synthetic-codec-")
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
        val writer = PrivateGameEvidence.create(root, "g", "a".repeat(40), maxBytes = 16000)
        writer.append("initialization", buildJsonObject { put("seed", 42) }, null)
        val source = root.resolve("g/native-000000.ndjson")
        val prefix = Files.readAllBytes(source)
        writer.append("seat_observation", buildJsonObject { put("own", "synthetic-state-".repeat(60000)) }, "a")
        writer.healthy() shouldBe true
        (Files.size(source) < 16000) shouldBe true
        writer.close()
        val resumed = PrivateGameEvidence.create(root, "g", "a".repeat(40), maxBytes = 16000)
        resumed.append("terminal", buildJsonObject { put("winnerId", "a") }, null)
        resumed.healthy() shouldBe true
        Files.readAllBytes(source).take(prefix.size).toByteArray().contentEquals(prefix) shouldBe true
        var sequence = 0L
        var hash = "0".repeat(64)
        Files.newInputStream(source).buffered().use { input ->
            while (true) {
                val physical = PrivateRecordCodec.readLine(input) ?: break
                val wrapper = Json.parseToJsonElement(PrivateRecordCodec.decode(physical).toString(Charsets.UTF_8)).jsonObject
                val bodyText = wrapper.getValue("body").jsonPrimitive.content
                val body = Json.parseToJsonElement(bodyText).jsonObject
                body.getValue("sequence").jsonPrimitive.long shouldBe ++sequence
                body.getValue("previousSha256").jsonPrimitive.content shouldBe hash
                hash = PrivateGameEvidence.sha256(bodyText.toByteArray())
                wrapper.getValue("sha256").jsonPrimitive.content shouldBe hash
            }
        }
        sequence shouldBe 4L // initialization, observation, resume, terminal
    }
})
