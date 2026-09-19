package com.wingedsheep.gameserver

import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.provenance.EngineSemanticIdentity
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

/** Proves game-server can consume the shared engine provenance primitive without depending on :gym. */
class SemanticIdentityReuseTest : FunSpec({
    test("game-server can scope engine semantic identity to its own contract") {
        val action = LegalAction(
            action = PassPriority(EntityId("player-1")),
            actionType = "PassPriority",
            description = "Pass priority"
        )

        val gameServerId = EngineSemanticIdentity.forLegalAction(action, "argentum-game-server@v1")
        val differentSurfaceId = EngineSemanticIdentity.forLegalAction(action, "argentum-gym-contract@v1")

        gameServerId shouldStartWith EngineSemanticIdentity.ACTION_VERSION
        gameServerId shouldNotBe differentSurfaceId
    }
})
