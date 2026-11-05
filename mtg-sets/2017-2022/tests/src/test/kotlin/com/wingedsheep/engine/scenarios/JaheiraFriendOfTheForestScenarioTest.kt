package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.clb.cards.JaheiraFriendOfTheForest
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class JaheiraFriendOfTheForestScenarioTest : FunSpec({
    val token = card("Test Jaheira Artifact") { typeLine = "Artifact" }
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(JaheiraFriendOfTheForest, token))
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.activations(player: EntityId, source: EntityId) =
        legalActions(player).filter { it.affordable }.map { it.action }.filterIsInstance<ActivateAbility>().filter { it.sourceId == source }

    test("a new noncreature token taps for mana immediately and does not use the stack") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val artifact = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.addComponent(artifact, TokenComponent)
        val action = d.activations(player, artifact).single()
        d.submit(action).error shouldBe null
        d.isTapped(artifact) shouldBe true
        d.state.stack.size shouldBe 0
        d.state.getEntity(player)!!.get<ManaPoolComponent>()!!.green shouldBe 1
    }
    test("a creature token must lose summoning sickness before it can tap for mana") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.addComponent(creature, TokenComponent)
        d.activations(player, creature).isEmpty() shouldBe true
        val disabled = d.legalActions(player).map { it.action }.filterIsInstance<ActivateAbility>()
            .single { it.sourceId == creature }
        d.submit(disabled).error shouldNotBe null
        d.removeSummoningSickness(creature)
        d.activations(player, creature).size shouldBe 1
    }
    test("ordinary permanents do not receive the ability and opposing tokens are excluded") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(d.getOpponent(player), "Jaheira, Friend of the Forest")
        val artifact = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.addComponent(artifact, TokenComponent)
        d.activations(player, artifact).isEmpty() shouldBe true
        d.putCreatureOnBattlefield(player, "Jaheira, Friend of the Forest")
        val nontoken = d.putPermanentOnBattlefield(player, "Test Jaheira Artifact")
        d.activations(player, nontoken).isEmpty() shouldBe true
        d.activations(player, artifact).size shouldBe 1
    }
})

/**
 * Jaheira, Friend of the Forest (CLB #237).
 *
 * "Tokens you control have '{T}: Add {G}.'" — every token, not just creature tokens (ruling). A
 * noncreature token can use it the turn it arrives; a creature token can't without haste; a nontoken
 * permanent never has it.
 */
class JaheiraFriendOfTheForestUpstreamScenarioTest : ScenarioTestBase() {

    private val grantedId by lazy {
        cardRegistry.requireCard("Jaheira, Friend of the Forest").staticAbilities
            .filterIsInstance<GrantActivatedAbility>().single().ability.id
    }

    private fun TestGame.greenInPool(): Int =
        state.getEntity(player1Id)?.get<ManaPoolComponent>()?.getAmount(Color.GREEN) ?: 0

    init {
        test("a Treasure token that just arrived taps for {G} without being sacrificed") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Jaheira, Friend of the Forest")
                .withCardOnBattlefield(1, "Treasure", summoningSickness = true, isToken = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val treasure = game.findPermanent("Treasure")!!

            game.execute(ActivateAbility(game.player1Id, treasure, grantedId)).error shouldBe null

            game.greenInPool() shouldBe 1
            withClue("tapping it is not the Treasure's own sacrifice ability") {
                game.findPermanent("Treasure") shouldBe treasure
            }
        }

        test("a creature token taps for {G} once it has been under your control since your turn began") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Jaheira, Friend of the Forest")
                .withCardOnBattlefield(1, "Grizzly Bears", isToken = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Grizzly Bears")!!, grantedId))
                .error shouldBe null
            game.greenInPool() shouldBe 1
        }

        test("a summoning-sick creature token can't use it") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Jaheira, Friend of the Forest")
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = true, isToken = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Grizzly Bears")!!, grantedId))
                .error shouldNotBe null
            game.greenInPool() shouldBe 0
        }

        test("a nontoken creature doesn't have the ability") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Jaheira, Friend of the Forest")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Grizzly Bears")!!, grantedId))
                .error shouldNotBe null
            game.greenInPool() shouldBe 0
        }
    }
}
