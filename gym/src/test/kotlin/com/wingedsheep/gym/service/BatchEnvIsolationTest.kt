package com.wingedsheep.gym.service

import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.registry.CardRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

class BatchEnvIsolationTest : FunSpec({
    test("stepBatch rejects duplicate env IDs before scheduling work") {
        val service = MultiEnvService(CardRegistry())
        val envId = EnvId("duplicate-env")

        val error = shouldThrow<IllegalArgumentException> {
            service.stepBatch(
                listOf(
                    StepRequest(envId, actionId = 0),
                    StepRequest(envId, actionId = 1)
                )
            )
        }

        error.message.shouldNotBeNull() shouldContain "step batch contains duplicate envId"
    }

    test("submitDecisionBatch rejects duplicate env IDs before scheduling work") {
        val service = MultiEnvService(CardRegistry())
        val envId = EnvId("duplicate-env")

        val error = shouldThrow<IllegalArgumentException> {
            service.submitDecisionBatch(
                listOf(
                    DecisionRequest(
                        envId,
                        NumberChosenResponse(decisionId = "decision-a", number = 1)
                    ),
                    DecisionRequest(
                        envId,
                        NumberChosenResponse(decisionId = "decision-b", number = 2)
                    )
                )
            )
        }

        error.message.shouldNotBeNull() shouldContain "decision batch contains duplicate envId"
    }
})