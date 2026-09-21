package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.provenance.SemanticFingerprint
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class SemanticIdentityTest : FunSpec({
    val player = EntityId("player-1")

    test("legal action identity is structured and ignores presentation description") {
        val first = LegalAction(
            action = PassPriority(player),
            actionType = "PassPriority",
            description = "Pass priority"
        )
        val renamed = first.copy(description = "Yield")

        SemanticIdentity.forLegalAction(first) shouldBe SemanticIdentity.forLegalAction(renamed)
        SemanticIdentity.forLegalAction(first) shouldStartWith "argentum-action-v1:"
    }

    test("different engine actions have different semantic identities") {
        val first = LegalAction(
            action = PassPriority(player),
            actionType = "PassPriority",
            description = "Pass priority"
        )
        val second = first.copy(action = PassPriority(EntityId("player-2")))

        SemanticIdentity.forLegalAction(first) shouldNotBe SemanticIdentity.forLegalAction(second)
    }

    test("pending decision identity ignores routing and presentation metadata") {
        val first = YesNoDecision(
            id = "routing-a",
            playerId = player,
            prompt = "Do the thing?",
            context = DecisionContext(
                sourceId = EntityId("source-1"),
                sourceName = "Displayed source",
                effectHint = "Displayed hint",
                inlineOnTrigger = true
            ),
            yesText = "Yes",
            noText = "No",
            hint = "Reminder text"
        )
        val presentationChanged = first.copy(
            id = "routing-b",
            prompt = "A rewritten prompt",
            context = first.context.copy(
                sourceName = "Renamed display source",
                effectHint = "Rewritten display hint",
                inlineOnTrigger = false
            ),
            yesText = "Proceed",
            noText = "Decline",
            hint = "Different reminder text"
        )

        SemanticIdentity.forPendingDecision(first) shouldBe
            SemanticIdentity.forPendingDecision(presentationChanged)
        SemanticIdentity.forPendingDecision(first) shouldStartWith "argentum-decision-v1:"
    }

    test("pending decision identity changes with authoritative decision semantics") {
        val first = YesNoDecision(
            id = "routing-a",
            playerId = player,
            prompt = "Do the thing?",
            context = DecisionContext(sourceId = EntityId("source-1"))
        )
        val differentSource = first.copy(
            id = "routing-b",
            context = first.context.copy(sourceId = EntityId("source-2"))
        )

        SemanticIdentity.forPendingDecision(first) shouldNotBe
            SemanticIdentity.forPendingDecision(differentSource)
    }

    test("response identity ignores decision routing id but preserves the choice") {
        val yesA = YesNoResponse(decisionId = "routing-a", choice = true)
        val yesB = YesNoResponse(decisionId = "routing-b", choice = true)
        val no = YesNoResponse(decisionId = "routing-a", choice = false)

        SemanticIdentity.forDecisionResponse(yesA) shouldBe
            SemanticIdentity.forDecisionResponse(yesB)
        SemanticIdentity.forDecisionResponse(yesA) shouldNotBe
            SemanticIdentity.forDecisionResponse(no)
        SemanticIdentity.forDecisionResponse(yesA) shouldStartWith
            "argentum-decision-response-v1:"
    }

    test("Gym delegates byte-for-byte to the shared engine primitive with a surface schema scope") {
        val action = LegalAction(
            action = PassPriority(player),
            actionType = "PassPriority",
            description = "Pass priority"
        )
        val decision = YesNoDecision(
            id = "routing-a",
            playerId = player,
            prompt = "Do the thing?",
            context = DecisionContext(sourceId = EntityId("source-1"))
        )
        val response = YesNoResponse(decisionId = "routing-a", choice = true)

        SemanticIdentity.forLegalAction(action) shouldBe
            SemanticFingerprint.forLegalAction(action, SchemaHash.CURRENT)
        SemanticFingerprint.forGameAction(
            action.actionType,
            action.action,
            SchemaHash.CURRENT,
        ) shouldBe SemanticIdentity.forLegalAction(action)
        SemanticIdentity.forPendingDecision(decision) shouldBe
            SemanticFingerprint.forPendingDecision(decision, SchemaHash.CURRENT)
        SemanticIdentity.forDecisionResponse(response) shouldBe
            SemanticFingerprint.forDecisionResponse(response, SchemaHash.CURRENT)

        // game-server or another Argentum surface can use the same semantic primitive while
        // supplying its own compatibility scope instead of depending on the Gym contract.
        SemanticFingerprint.forLegalAction(action, "argentum-game-server@v1") shouldNotBe
            SemanticIdentity.forLegalAction(action)
        SemanticFingerprint.forPendingDecision(decision, "argentum-game-server@v1") shouldNotBe
            SemanticIdentity.forPendingDecision(decision)
        SemanticFingerprint.forDecisionResponse(response, "argentum-game-server@v1") shouldNotBe
            SemanticIdentity.forDecisionResponse(response)
    }
})
