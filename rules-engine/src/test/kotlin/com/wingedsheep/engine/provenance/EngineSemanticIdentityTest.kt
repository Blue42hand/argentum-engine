package com.wingedsheep.engine.provenance

import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EngineSemanticIdentityTest : FunSpec({
    val player = EntityId("player-1")
    val schema = "consumer-contract@v1"

    test("canonical action identity ignores presentation but is scoped to consumer schema") {
        val action = LegalAction(
            action = PassPriority(player),
            actionType = "PassPriority",
            description = "Pass priority"
        )
        val renamed = action.copy(description = "Yield")

        EngineSemanticIdentity.forLegalAction(action, schema) shouldBe
            EngineSemanticIdentity.forLegalAction(renamed, schema)
        EngineSemanticIdentity.forLegalAction(action, schema) shouldNotBe
            EngineSemanticIdentity.forLegalAction(action, "consumer-contract@v2")
    }

    test("pending decision ignores routing presentation and preserves authoritative semantics") {
        val decision = YesNoDecision(
            id = "routing-a",
            playerId = player,
            prompt = "Do the thing?",
            context = DecisionContext(
                sourceId = EntityId("source-1"),
                sourceName = "Displayed source"
            )
        )
        val presentationChanged = decision.copy(
            id = "routing-b",
            prompt = "Rewritten prompt",
            context = decision.context.copy(sourceName = "Renamed source")
        )
        val semanticChanged = decision.copy(
            id = "routing-c",
            context = decision.context.copy(sourceId = EntityId("source-2"))
        )

        EngineSemanticIdentity.forPendingDecision(decision, schema) shouldBe
            EngineSemanticIdentity.forPendingDecision(presentationChanged, schema)
        EngineSemanticIdentity.forPendingDecision(decision, schema) shouldNotBe
            EngineSemanticIdentity.forPendingDecision(semanticChanged, schema)
    }

    test("decision response ignores live routing id but preserves choice") {
        val yesA = YesNoResponse(decisionId = "routing-a", choice = true)
        val yesB = YesNoResponse(decisionId = "routing-b", choice = true)
        val no = YesNoResponse(decisionId = "routing-a", choice = false)

        EngineSemanticIdentity.forDecisionResponse(yesA, schema) shouldBe
            EngineSemanticIdentity.forDecisionResponse(yesB, schema)
        EngineSemanticIdentity.forDecisionResponse(yesA, schema) shouldNotBe
            EngineSemanticIdentity.forDecisionResponse(no, schema)
    }
})
