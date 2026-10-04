package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ReanimateScenarioTest : ScenarioTestBase() {
    init {
        cardRegistry.register(card("Reanimate X Creature") {
            manaCost = "{X}{G}"
            colorIdentity = "G"
            typeLine = "Creature — Hydra"
            power = 1
            toughness = 1
        })

        test("returns an opponent's creature under your control and loses its graveyard mana value") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInGraveyard(2, "Centaur Courser")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val courser = game.state.getGraveyard(game.player2Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Centaur Courser"
            }
            val cast = game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, courser)),
            ))
            withClue("Reanimate should target the opponent's graveyard: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            game.isOnBattlefield("Centaur Courser") shouldBe true
            game.state.getEntity(game.findPermanent("Centaur Courser")!!)
                ?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
            game.getLifeTotal(1) shouldBe 17
            game.state.getGraveyard(game.player2Id).contains(courser) shouldBe false
        }

        test("X in the targeted graveyard card's mana cost counts as zero") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInGraveyard(1, "Reanimate X Creature")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val creature = game.state.getGraveyard(game.player1Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Reanimate X Creature"
            }
            game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, creature)),
            )).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Reanimate X Creature") shouldBe true
            game.getLifeTotal(1) shouldBe 19 // {X}{G} has mana value 1 outside the stack.
        }

        test("you lose life before triggered enters abilities resolve") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reanimate")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Soul Warden")
                .withCardInGraveyard(2, "Centaur Courser")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.findCardsInHand(1, "Reanimate").single()
            val courser = game.state.getGraveyard(game.player2Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Centaur Courser"
            }
            game.execute(CastSpell(
                game.player1Id, spell,
                listOf(entityIdToChosenTarget(game.state, courser)),
            )).error shouldBe null

            game.passPriority()
            game.passPriority() // Reanimate resolves; Soul Warden's trigger remains on the stack.
            game.getLifeTotal(1) shouldBe 17
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 18
        }
    }
}
