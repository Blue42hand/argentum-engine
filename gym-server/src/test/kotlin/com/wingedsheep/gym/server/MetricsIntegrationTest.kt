package com.wingedsheep.gym.server

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsIntegrationTest : FunSpec() {

    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newBuilder().build()

    init {
        extension(SpringExtension())

        fun get(path: String): HttpResponse<String> = client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        test("records completed Gym HTTP requests with standard server tags") {
            get("/health").statusCode() shouldBe 200

            val metrics = get("/actuator/metrics/http.server.requests")
            metrics.statusCode() shouldBe 200
            metrics.body() shouldContain "\"name\":\"http.server.requests\""
            metrics.body() shouldContain "\"tag\":\"method\""
            metrics.body() shouldContain "\"tag\":\"status\""
            metrics.body() shouldContain "\"tag\":\"uri\""
            metrics.body() shouldContain "/health"
        }
    }
}
