package com.wingedsheep.gameserver.recording

import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater

/** Transport only: exact logical UTF-8 records and their existing hash chains are unchanged. */
internal object PrivateRecordCodec {
    const val MAX_RECORD_BYTES = 64 * 1024 * 1024
    private const val COMPRESS_THRESHOLD = 4096

    fun encode(logical: ByteArray): ByteArray {
        require(logical.size <= MAX_RECORD_BYTES && logical.lastOrNull() == '\n'.code.toByte()) {
            "record_size_or_framing_limit"
        }
        if (logical.size < COMPRESS_THRESHOLD) return logical
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(logical) }
        }.toByteArray()
        val frame = (buildJsonObject {
            put("recordCodec", 1)
            put("encoding", "gzip-base64")
            put("decodedBytes", logical.size)
            put("data", Base64.getEncoder().encodeToString(compressed))
        }.toString() + "\n").toByteArray(Charsets.UTF_8)
        return if (frame.size < logical.size) frame else logical
    }

    fun decode(physical: ByteArray): ByteArray {
        require(physical.size <= MAX_RECORD_BYTES && physical.lastOrNull() == '\n'.code.toByte()) {
            "record_size_or_framing_limit"
        }
        val frame = Json.parseToJsonElement(utf8(physical)) as? JsonObject ?: return physical
        if ("recordCodec" !in frame) return physical
        require(frame.keys == setOf("recordCodec", "encoding", "decodedBytes", "data") &&
            frame["recordCodec"] == JsonPrimitive(1) && frame["encoding"] == JsonPrimitive("gzip-base64")) {
            "unsupported_record_frame"
        }
        val length = frame.getValue("decodedBytes").jsonPrimitive
        require(!length.isString && length.content.matches(Regex("[1-9][0-9]*"))) { "invalid_record_length" }
        val expected = length.int
        require(expected in 1..MAX_RECORD_BYTES) { "record_size_or_framing_limit" }
        val encoded = frame.getValue("data").jsonPrimitive
        require(encoded.isString) { "invalid_record_frame" }
        val compressed = Base64.getDecoder().decode(encoded.content)
        require(Base64.getEncoder().encodeToString(compressed) == encoded.content && compressed.size >= 18 &&
            compressed[0] == 0x1f.toByte() && compressed[1] == 0x8b.toByte() &&
            compressed[2] == 8.toByte() && compressed[3] == 0.toByte()) { "invalid_record_frame" }
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream(minOf(expected, 65536))
        try {
            inflater.setInput(compressed, 10, compressed.size - 18)
            val buffer = ByteArray(65536)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                require(output.size().toLong() + count <= expected) { "record_decode_length_or_checksum" }
                require(count > 0 || inflater.finished()) { "invalid_record_frame" }
                output.write(buffer, 0, count)
            }
            require(inflater.remaining == 0 && output.size() == expected) { "record_decode_length_or_checksum" }
        } finally { inflater.end() }
        val logical = output.toByteArray()
        val trailer = ByteBuffer.wrap(compressed, compressed.size - 8, 8).order(ByteOrder.LITTLE_ENDIAN)
        require((trailer.int.toLong() and 0xffffffffL) == CRC32().also { it.update(logical) }.value &&
            (trailer.int.toLong() and 0xffffffffL) == logical.size.toLong() &&
            logical.lastOrNull() == '\n'.code.toByte()) { "record_decode_length_or_checksum" }
        utf8(logical)
        return logical
    }

    fun readLine(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (output.size() == 0) null else output.toByteArray()
            require(output.size() < MAX_RECORD_BYTES) { "record_size_or_framing_limit" }
            output.write(value)
            if (value == '\n'.code) return output.toByteArray()
        }
    }

    private fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
