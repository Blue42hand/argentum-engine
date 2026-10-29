package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The built-in AI paying a cast with mana that only a **sacrifice** can make — Treasure, Gold,
 * Eldrazi Spawn and Scion tokens.
 *
 * The enumerator counts those sources as affordable (`ManaSolver.canPay`), but auto-pay never
 * sacrifices a permanent on its own, so the AI's AutoPay cast came back illegal and was dropped.
 * An MH3 deck held Devourer of Destiny for four turns because its second {C} had to come from a
 * Spawn. The AI now activates the cheapest sacrifice source first, then casts with the floating
 * mana on its next decision.
 */
class SacrificeManaPaymentAiTest : FunSpec({

    val ogre = card("Sacrifice Test Ogre") {
        manaCost = "{2}{R}"
        typeLine = "Creature — Ogre"
        power = 3
        toughness = 3
    }

    val eldrazi = card("Sacrifice Test Eldrazi") {
        manaCost = "{2}{C}"
        colorIdentity = ""
        typeLine = "Creature — Eldrazi"
        power = 3
        toughness = 3
    }

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(PredefinedTokens.Treasure, PredefinedTokens.EldraziSpawn, ogre, eldrazi))
        initMirrorMatch(Deck.of("Mountain" to 20))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    /** Let the AI act until [spell] leaves the hand, at most [maxDecisions] times. */
    fun GameTestDriver.aiPlaysUntilCast(player: EntityId, spell: EntityId, maxDecisions: Int = 4): List<GameAction> {
        val enumerator = LegalActionEnumerator.create(cardRegistry)
        val ai = AIPlayer.create(cardRegistry, player)
        val submitted = mutableListOf<GameAction>()
        repeat(maxDecisions) {
            if (spell !in getHand(player)) return submitted
            // The opening hand is all Mountains; keep the land drop out so the only play is the cast.
            val legal = enumerator.enumerate(state, player).filterNot { it.action is PlayLand }
            val chosen = ai.chooseFrom(state, legal).action
            submitted += chosen
            submit(chosen).outcome shouldBe Outcome.Done
        }
        return submitted
    }

    test("sacrifices a Treasure to cast a three-drop off two lands") {
        val driver = driver()
        val player = driver.activePlayer!!
        repeat(2) { driver.putLandOnBattlefield(player, "Mountain") }
        val treasure = driver.putPermanentOnBattlefield(player, "Treasure")
        val spell = driver.putCardInHand(player, "Sacrifice Test Ogre")

        val legal = LegalActionEnumerator.create(driver.cardRegistry).enumerate(driver.state, player)
            .single { (it.action as? CastSpell)?.cardId == spell }
        legal.affordable shouldBe true

        val submitted = driver.aiPlaysUntilCast(player, spell)

        (submitted.first() as ActivateAbility).sourceId shouldBe treasure
        submitted.last().shouldBeInstanceOf<CastSpell>()
        driver.bothPass()
        driver.findPermanent(player, "Sacrifice Test Ogre") shouldBe spell
        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(treasure) shouldBe false
    }

    test("sacrifices an Eldrazi Spawn for the {C} no land can make") {
        val driver = driver()
        val player = driver.activePlayer!!
        repeat(3) { driver.putLandOnBattlefield(player, "Mountain") }
        val spawn = driver.putCreatureOnBattlefield(player, "Eldrazi Spawn")
        val spell = driver.putCardInHand(player, "Sacrifice Test Eldrazi")

        val submitted = driver.aiPlaysUntilCast(player, spell)

        (submitted.first() as ActivateAbility).sourceId shouldBe spawn
        submitted.last().shouldBeInstanceOf<CastSpell>()
        driver.bothPass()
        driver.findPermanent(player, "Sacrifice Test Eldrazi") shouldBe spell
        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(spawn) shouldBe false
    }
})
