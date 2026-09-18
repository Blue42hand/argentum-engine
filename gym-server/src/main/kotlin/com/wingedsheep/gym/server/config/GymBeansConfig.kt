package com.wingedsheep.gym.server.config

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.MtgSet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires a default [CardRegistry] and [MultiEnvService] as Spring singletons.
 *
 * The catalogue is [MtgSetCatalog.all] — every set the engine knows about — so a
 * constructed deck can name any implemented card and a [DeckSpec.RandomSealed] can
 * draw from any sealed-supported set. This mirrors `game-server`'s `GameBeansConfig`;
 * `MtgSetCatalog` is the single registration point, so newly-added sets show up here
 * automatically with no edit to this file.
 */
@Configuration
class GymBeansConfig {

    @Bean
    fun cardRegistry(): CardRegistry = createGymCardRegistry()

    @Bean
    fun printingRegistry(cardRegistry: CardRegistry): PrintingRegistry = PrintingRegistry().apply {
        // One synthesised printing per registered card (its canonical printing)...
        for (name in cardRegistry.allCardNames()) {
            cardRegistry.getCardsByName(name).forEach(::registerSynthesizedDefault)
        }
        // ...then explicit reprint rows, which overwrite synthesised entries sharing a key.
        for (set in MtgSetCatalog.all) {
            register(set.printings)
        }
    }

    @Bean
    fun boosterGenerator(): BoosterGenerator = BoosterGenerator(
        MtgSetCatalog.all
            .filter { it.sealedSupported }
            .associate { it.code to it.toBoosterSetConfig() }
    )

    @Bean
    fun multiEnvService(
        cardRegistry: CardRegistry,
        boosterGenerator: BoosterGenerator
    ): MultiEnvService = MultiEnvService(cardRegistry, boosterGenerator)
}

private fun MtgSet.toBoosterSetConfig(): BoosterGenerator.SetConfig =
    BoosterGenerator.SetConfig(
        setCode = code,
        setName = displayName,
        cards = cards,
        basicLands = (basicLandsFallback ?: this).basicLands,
        incomplete = incomplete,
        block = block,
        boosterStrategy = boosterStrategy,
    )
