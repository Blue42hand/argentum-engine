package com.wingedsheep.gameserver.persistence

import com.wingedsheep.gameserver.config.RedisProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/** Separate, credential-free QA variant. Never enables the production Redis path implicitly. */
@Component
class QaRedisPersistence(
    @Value("\${native.qa.redis-persistence-enabled:false}") val enabled: Boolean = false,
    private val redis: RedisProperties = RedisProperties(),
    @Value("\${native.lifecycle.enabled:false}") lifecycleEnabled: Boolean = false,
    @Value("\${native.qa.callback-gate-enabled:false}") callbackGateEnabled: Boolean = false,
    @Value("\${native.qa.callback-initially-paused:false}") initiallyPaused: Boolean = false,
) {
    private val failed = AtomicBoolean(false)
    @Volatile private var recovered = false
    init {
        if (enabled) {
            require(lifecycleEnabled && callbackGateEnabled && initiallyPaused) { "QA Redis requires private lifecycle and initially paused callbacks" }
            require(redis.enabled && redis.host == "127.0.0.1" && redis.port == 26379 && redis.ttlMinutes == 30L &&
                redis.keyPrefix.matches(Regex("qa:[a-f0-9]{32}:"))) { "Unsupported QA Redis configuration" }
        }
    }
    private var recoveryBytes = 0L
    private var recoveryKeys = 0
    @Synchronized fun recoveryKeys(count: Int) {
        if (!enabled) return
        recoveryKeys += count
        require(count >= 0 && recoveryKeys <= 64) { "QA Redis recovery key bound" }
    }
    @Synchronized fun recoveryValue(value: String) {
        if (!enabled) return
        val bytes = value.toByteArray(Charsets.UTF_8).size
        recoveryBytes += bytes
        require(bytes <= 8 * 1024 * 1024 && recoveryBytes <= 16 * 1024 * 1024) { "QA Redis recovery byte bound" }
    }
    fun fail() { if (enabled) failed.set(true) }
    fun recovered() { check(!failed.get()); recovered = true }
    fun healthy(): Boolean = enabled && recovered && !failed.get()
}
