package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Regression coverage for the service-level single-threaded-per-environment contract. */
class ConcurrentEnvAccessTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun config(): EnvConfig {
        val deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))
        return EnvConfig(
            players = listOf(
                PlayerSpec("Alice", deck),
                PlayerSpec("Bob", deck)
            ),
            skipMulligans = true,
            startingPlayerIndex = 0
        )
    }

    test("operations on the same env are serialized at the service boundary") {
        val service = MultiEnvService(registry())
        val executor = Executors.newSingleThreadExecutor()

        try {
            val envId = service.create(config()).envId

            // Hold the exact monitor MultiEnvService uses. Reflection is deliberate here: the test
            // needs a deterministic way to prove a second call waits, rather than relying on a
            // scheduler race that could make the old unsafe behavior pass intermittently.
            val envsField = MultiEnvService::class.java.getDeclaredField("envs").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val envs = envsField.get(service) as ConcurrentHashMap<EnvId, Any>
            val envMonitor = envs.getValue(envId)

            val started = CountDownLatch(1)
            val finished = CountDownLatch(1)

            synchronized(envMonitor) {
                executor.submit {
                    started.countDown()
                    service.observe(envId)
                    finished.countDown()
                }

                started.await(1, TimeUnit.SECONDS).shouldBeTrue()
                finished.await(100, TimeUnit.MILLISECONDS).shouldBeFalse()
            }

            finished.await(2, TimeUnit.SECONDS).shouldBeTrue()
        } finally {
            executor.shutdownNow()
            service.workerPool.close()
        }
    }
})
