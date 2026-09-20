package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.pending.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Victimize (Urza's Saga #166) — {2}{B} Sorcery.
 *
 * "Choose two target creature cards in your graveyard. Sacrifice a creature. If you do, return the
 * chosen cards to the battlefield tapped."
 *
 * These scenarios pin the two easy-to-get-wrong boundaries from the 2020-11-10 rulings: two targets
 * are mandatory at cast time, while the sacrifice is made during resolution and gates the return.
 */
class VictimizeScenarioTest : ScenarioTestBase() {

    init {
        context("Victimize") {

            test("sacrifices a creature during resolution and returns both targets tapped") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Victimize")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(1, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Victimize"
                }
                val hillGiant = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Hill Giant"
                }
                val courser = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Centaur Courser"
                }
                val bears = game.findPermanent("Grizzly Bears")!!

                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        spellId,
                        listOf(
                            entityIdToChosenTarget(game.state, hillGiant),
                            entityIdToChosenTarget(game.state, courser),
                        ),
                    )
                )
                withClue("Victimize should cast with exactly two creature-card targets") {
                    cast.error shouldBe null
                }

                game.resolveStack()

                val sacrifice = game.state.pendingDecision as? SelectCardsDecision
                withClue("Victimize should require a creature sacrifice during resolution") {
                    sacrifice.shouldNotBeNull()
                }
                game.selectCards(listOf(bears))
                game.resolveStack()

                withClue("the chosen battlefield creature should be sacrificed") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }

                val giantPermanent = game.findPermanent("Hill Giant")
                val courserPermanent = game.findPermanent("Centaur Courser")
                withClue("both targeted creature cards should return") {
                    giantPermanent.shouldNotBeNull()
                    courserPermanent.shouldNotBeNull()
                }
                withClue("both returned creatures should enter tapped") {
                    game.state.getEntity(giantPermanent!!)?.get<TappedComponent>() shouldBe TappedComponent
                    game.state.getEntity(courserPermanent!!)?.get<TappedComponent>() shouldBe TappedComponent
                }
            }

            test("returns nothing when no creature can be sacrificed") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Victimize")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(1, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Victimize"
                }
                val targets = game.state.getGraveyard(game.player1Id).map { id ->
                    entityIdToChosenTarget(game.state, id)
                }

                game.execute(CastSpell(game.player1Id, spellId, targets)).error shouldBe null
                game.resolveStack()

                withClue("without a successful sacrifice, the if-you-do rider must not return either target") {
                    game.isInGraveyard(1, "Hill Giant") shouldBe true
                    game.isInGraveyard(1, "Centaur Courser") shouldBe true
                    game.findPermanent("Hill Giant") shouldBe null
                    game.findPermanent("Centaur Courser") shouldBe null
                }
            }

            test("cannot be cast with only one target") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Victimize")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Victimize"
                }
                val hillGiant = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Hill Giant"
                }

                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        spellId,
                        listOf(entityIdToChosenTarget(game.state, hillGiant)),
                    )
                )

                withClue("Victimize requires exactly two creature-card targets") {
                    (cast.error != null) shouldBe true
                }
            }
        }
    }
}
