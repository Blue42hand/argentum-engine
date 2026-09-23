package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AiSeatPresetResolutionTest : FunSpec({
    val deckPreset = AiDeckSpec.Fixed(
        deckList = mapOf("Plains" to 60),
        label = "Provider preset",
    )
    val provider = object : AiControllerProvider {
        override val mode: String = "external-test"
        override val profiles: List<AiControllerProfile> = listOf(
            AiControllerProfile(
                id = "profile-a",
                displayName = "Profile A",
                deckSpec = deckPreset,
            )
        )

        override fun create(context: AiControllerContext): AiPlayerController =
            error("controller creation is not part of preset resolution")
    }

    test("provider profile resolves controller and deck as one immutable preset") {
        val registry = AiControllerProviderRegistry(listOf(provider))
        val spec = AiControllerSpec(mode = " EXTERNAL-TEST ", profileId = "profile-a")

        registry.resolveSeatPreset(spec) shouldBe ResolvedAiSeatPreset(
            controllerSpec = spec,
            deckSpec = deckPreset,
        )
    }

    test("unknown explicit provider profile fails closed before any seat mutation") {
        val registry = AiControllerProviderRegistry(listOf(provider))

        shouldThrow<IllegalArgumentException> {
            registry.resolveSeatPreset(AiControllerSpec("external-test", "missing"))
        }
    }

    test("built-in controller modes reject profile ids") {
        val registry = AiControllerProviderRegistry(emptyList())

        shouldThrow<IllegalArgumentException> {
            registry.resolveSeatPreset(AiControllerSpec("engine", "not-supported"))
        }
    }
})
