package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Additional and alternative costs are priced before reductions, including modal choices. */
class SpellCostOrderingTest : ScenarioTestBase() {
    private val modal = card("Cost Ordering Modal") {
        manaCost = "{3}{R}"
        typeLine = "Sorcery"
        spell {
            modal(chooseCount = 2, minChooseCount = 1, additionalManaCostPerExtraMode = "{2}{R}") {
                mode("Gain 1 life", Effects.GainLife(1))
                mode("Gain 2 life", Effects.GainLife(2))
            }
        }
    }
    private val perMode = card("Cost Ordering Per Mode") {
        manaCost = "{3}{R}"
        typeLine = "Sorcery"
        spell {
            modal {
                mode("Gain 1 life") {
                    effect = Effects.GainLife(1)
                    additionalManaCost = "{2}{R}"
                }
                mode("Gain 2 life", Effects.GainLife(2))
            }
        }
    }
    private val overloaded = card("Cost Ordering Overload") {
        manaCost = "{R}"
        typeLine = "Sorcery"
        keywordAbility(KeywordAbility.overload("{4}{R}"))
        spell {
            effect = Effects.GainLife(1)
            overloadEffect = Effects.GainLife(2)
        }
    }
    private val kicked = card("Cost Ordering Kicker") {
        manaCost = "{3}{R}"
        typeLine = "Sorcery"
        keywordAbility(KeywordAbility.kicker("{2}{R}"))
        spell { effect = Effects.GainLife(1) }
    }

    private val graveyardEnabler = card("Cost Ordering Graveyard Enabler") {
        typeLine = "Enchantment"
        staticAbility {
            ability = com.wingedsheep.sdk.scripting.MayCastFromGraveyard(
                com.wingedsheep.sdk.scripting.GameObjectFilter.InstantOrSorcery
            )
        }
    }
    private val mandatoryWaterbend = card("Cost Ordering Mandatory Waterbend") {
        manaCost = "{R}"
        typeLine = "Sorcery"
        waterbendCost(amount = 2)
        spell { effect = Effects.GainLife(1) }
    }
    private val optionalWaterbend = card("Cost Ordering Optional Waterbend") {
        manaCost = "{R}"
        typeLine = "Sorcery"
        waterbendCost(amount = 2, optional = true)
        spell { effect = Effects.GainLife(1) }
    }

    private fun board(name: String, mana: Int, reducers: Int, tax: Boolean = false): TestGame {
        var setup = scenario().withPlayers("Caster", "Opponent")
            .withCardInHand(1, name)
            .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
            .withLandsOnBattlefield(1, "Mountain", mana)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(reducers) { setup = setup.withCardOnBattlefield(1, "Ruby Medallion") }
        if (tax) setup = setup.withCardOnBattlefield(2, "Thalia, Guardian of Thraben")
        return setup.build()
    }

    private fun action(game: TestGame, name: String) =
        CastSpell(game.player1Id, game.findCardsInHand(1, name).single())

    init {
        listOf(modal, perMode, overloaded, kicked, graveyardEnabler, mandatoryWaterbend, optionalWaterbend).forEach(cardRegistry::register)

        test("extra-mode cost receives reductions beyond the printed generic cost") {
            val game = board(modal.name, mana = 3, reducers = 4)
            game.execute(action(game, modal.name).copy(chosenModes = listOf(0, 1))).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 23
        }

        test("interactive selection offers the same affordable second mode") {
            val game = board(modal.name, mana = 3, reducers = 4)
            game.execute(action(game, modal.name)).error shouldBe null
            val first = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            game.submitDecision(OptionChosenResponse(first.id, first.options.indexOf("Gain 1 life"))).error shouldBe null
            val second = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            second.options shouldContain "Gain 2 life"
            game.submitDecision(OptionChosenResponse(second.id, second.options.indexOf("Gain 2 life"))).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 23
        }

        test("per-mode cost enumeration and payment agree after reductions") {
            val game = board(perMode.name, mana = 3, reducers = 4)
            val spell = action(game, perMode.name)
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.let { a -> a.cardId == spell.cardId && a.chosenModes == listOf(0) } == true
            }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{1}{R}{R}")
            game.execute(spell.copy(chosenModes = listOf(0))).error shouldBe null
        }

        test("overload enumeration and payment include battlefield reductions") {
            val game = board(overloaded.name, mana = 4, reducers = 1)
            val spell = action(game, overloaded.name)
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.let { a -> a.cardId == spell.cardId && a.alternativeCostType == AlternativeCostType.OVERLOAD } == true
            }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{3}{R}")
            game.execute(spell.copy(useAlternativeCost = true, alternativeCostType = AlternativeCostType.OVERLOAD)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 22
        }

        test("optional additional cost is reduced together with the printed cost") {
            val game = board(kicked.name, mana = 3, reducers = 4)
            val spell = action(game, kicked.name)
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.let { a -> a.cardId == spell.cardId && a.declaredCostSlot == ChoiceSlot.KICKED } == true
            }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{1}{R}{R}")
            game.execute(spell.copy(declaredCostSlot = ChoiceSlot.KICKED)).error shouldBe null
        }

        for (mana in listOf(2, 3)) {
            test("free modal cast keeps additions, tax and reduction with $mana mana") {
                val game = board(modal.name, mana, reducers = 1, tax = true)
                val spell = action(game, modal.name)
                game.state = game.state.updateEntity(spell.cardId) {
                    it.with(PlayWithoutPayingCostComponent(game.player1Id))
                }
                val result = game.execute(spell.copy(chosenModes = listOf(0, 1)))
                if (mana == 3) result.error shouldBe null else result.error shouldNotBe null
            }
        }

        test("fixed alternative base does not erase modal additions") {
            val game = board(modal.name, mana = 3, reducers = 1)
            val spell = action(game, modal.name)
            game.state = game.state.updateEntity(spell.cardId) {
                it.with(PlayWithFixedAlternativeManaCostComponent(game.player1Id, ManaCost.parse("{1}")))
            }
            game.execute(spell.copy(chosenModes = listOf(0, 1))).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 23
        }

        test("without reducers the full combined cost is still required") {
            val short = board(modal.name, mana = 6, reducers = 0)
            short.execute(action(short, modal.name).copy(chosenModes = listOf(0, 1))).error shouldNotBe null
            val exact = board(modal.name, mana = 7, reducers = 0)
            exact.execute(action(exact, modal.name).copy(chosenModes = listOf(0, 1))).error shouldBe null
        }

        test("generic reduction never removes required colored mana") {
            val game = board(modal.name, mana = 1, reducers = 8)
            game.execute(action(game, modal.name).copy(chosenModes = listOf(0, 1))).error shouldNotBe null
        }
        for (mana in listOf(1, 3)) {
            test("Adventure uses the instant face with $mana mana and a creature reducer") {
                val game = scenario().withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Heartflame Duelist")
                    .withCardOnBattlefield(1, "Heartless Summoning")
                    .withLandsOnBattlefield(1, "Mountain", mana)
                    .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val cast = action(game, "Heartflame Duelist").copy(faceIndex = 0,
                    targets = listOf(ChosenTarget.Player(game.player2Id)))
                cardRegistry.getCard("Heartflame Duelist")!!.cardFaces[0].name shouldBe "Heartflame Slash"
                val offer = game.getLegalActions(1).singleOrNull { (it.action as? CastSpell)?.faceIndex == 0 && it.isAffordable }
                if (mana == 1) {
                    offer shouldBe null
                    game.execute(cast).error shouldNotBe null
                } else {
                    offer shouldNotBe null
                    ManaCost.parse(offer!!.manaCostString!!) shouldBe ManaCost.parse("{2}{R}")
                    game.execute(cast).error shouldBe null
                    game.resolveStack()
                    game.getLifeTotal(2) shouldBe 17
                }
            }
        }

        test("flashback quote and payment include source-zone reductions") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInGraveyard(1, "Deep Analysis")
                .withCardOnBattlefield(1, "Doc Aurlock, Grizzled Genius")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.findCardsInGraveyard(1, "Deep Analysis").single()
            val offer = game.getLegalActions(1).single { (it.action as? CastSpell)?.cardId == id }
            offer.isAffordable shouldBe true
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{U}")
            game.execute((offer.action as CastSpell).copy(targets = listOf(ChosenTarget.Player(game.player1Id)))).error shouldBe null
            game.getLifeTotal(1) shouldBe 17
        }

        test("free cast permission does not make a spell tax affordable without mana") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, overloaded.name)
                .withCardOnBattlefield(1, "Omniscience")
                .withCardOnBattlefield(2, "Thalia, Guardian of Thraben")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.getLegalActions(1).any {
                (it.action as? CastSpell)?.useWithoutPayingManaCost == true && it.isAffordable
            } shouldBe false
            game.execute(action(game, overloaded.name).copy(useWithoutPayingManaCost = true)).error shouldNotBe null
        }

        test("splice reductions reach the added mana in enumeration and payment") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reach Through Mists").withCardInHand(1, "Dampen Thought")
                .withCardOnBattlefield(1, "Sapphire Medallion")
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val spliceId = game.findCardsInHand(1, "Dampen Thought").single()
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.splicedCardIds == listOf(spliceId)
            }
            offer.isAffordable shouldBe true
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{U}{U}")
            game.execute((offer.action as CastSpell).copy(targets = listOf(ChosenTarget.Player(game.player2Id)))).error shouldBe null
        }

        test("free mandatory waterbend can be paid with eligible artifact taps") {
            val plain = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, mandatoryWaterbend.name)
                .withCardOnBattlefield(1, "Ornithopter").withCardOnBattlefield(1, "Ornithopter")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val free = action(plain, mandatoryWaterbend.name)
            plain.state = plain.state.updateEntity(free.cardId) {
                it.with(PlayWithoutPayingCostComponent(plain.player1Id))
            }
            val payers = plain.findAllPermanents("Ornithopter").toSet()
            plain.execute(free.copy(alternativePayment = AlternativePaymentChoice(tapForGenericPermanents = payers))).error shouldBe null
            payers.all { plain.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
        }

        test("optional waterbend quote reduces the added cost") {
            val game = board(optionalWaterbend.name, mana = 1, reducers = 2)
            val offer = game.getLegalActions(1).single { (it.action as? CastSpell)?.wasWaterbendPaid == true }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{R}")
            game.execute(offer.action).error shouldBe null
        }

        test("kicker from graveyard prices additions before reductions") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInGraveyard(1, kicked.name)
                .withCardOnBattlefield(1, graveyardEnabler.name)
                .withCardOnBattlefield(1, "Ruby Medallion").withCardOnBattlefield(1, "Ruby Medallion")
                .withCardOnBattlefield(1, "Ruby Medallion").withCardOnBattlefield(1, "Ruby Medallion")
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val offer = game.getLegalActions(1).single { (it.action as? CastSpell)?.declaredCostSlot == ChoiceSlot.KICKED }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{1}{R}{R}")
            game.execute(offer.action).error shouldBe null
        }

        test("free mandatory waterbend quote retains its additional mana") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, mandatoryWaterbend.name)
                .withCardOnBattlefield(1, "Omniscience")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.getLegalActions(1).any {
                (it.action as? CastSpell)?.useWithoutPayingManaCost == true && it.isAffordable
            } shouldBe false
        }

        test("free improvise cast offers and pays a tax using an artifact") {
            val improvised = card("Cost Ordering Improvise") {
                manaCost = "{3}{U}"
                typeLine = "Sorcery"
                keywords(com.wingedsheep.sdk.core.Keyword.IMPROVISE)
                spell { effect = Effects.GainLife(1) }
            }
            cardRegistry.register(improvised)
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, improvised.name)
                .withCardOnBattlefield(1, "Omniscience")
                .withCardOnBattlefield(1, "Ornithopter")
                .withCardOnBattlefield(2, "Thalia, Guardian of Thraben")
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.useWithoutPayingManaCost == true && it.isAffordable
            }
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{1}")
            val payers = game.findAllPermanents("Ornithopter").toSet()
            game.execute((offer.action as CastSpell).copy(
                alternativePayment = AlternativePaymentChoice(tapForGenericPermanents = payers)
            )).error shouldBe null
            payers.all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
        }

        test("free mandatory waterbend offer includes the artifact payment picker") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, mandatoryWaterbend.name)
                .withCardOnBattlefield(1, "Omniscience")
                .withCardOnBattlefield(1, "Ornithopter").withCardOnBattlefield(1, "Ornithopter")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.useWithoutPayingManaCost == true && it.isAffordable
            }
            offer.hasTapForGeneric shouldBe true
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{2}")
            game.execute((offer.action as CastSpell).copy(alternativePayment = AlternativePaymentChoice(
                tapForGenericPermanents = game.findAllPermanents("Ornithopter").toSet()
            ))).error shouldBe null
        }

        test("exile blight offer honors the chosen leg and any-color permission") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInExile(1, "Bogslither's Embrace")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Swamp")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getExile(game.player1Id).single()
            game.state = game.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(id), controllerId = game.player1Id,
                timestamp = game.state.timestamp, withAnyManaType = true
            ))
            val offer = game.getLegalActions(1).single {
                (it.action as? CastSpell)?.cardId == id && it.additionalCostInfo?.costType == "Blight"
            }
            offer.isAffordable shouldBe true
            ManaCost.parse(offer.manaCostString!!) shouldBe ManaCost.parse("{1}{B}")
            val bears = game.findAllPermanents("Grizzly Bears")
            game.execute((offer.action as CastSpell).copy(
                targets = listOf(ChosenTarget.Permanent(bears.last())),
                additionalCostPayment = AdditionalCostPayment(blightTargets = listOf(bears.first()))
            )).error shouldBe null
        }

    }
}
