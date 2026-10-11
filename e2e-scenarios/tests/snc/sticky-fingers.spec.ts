import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName, OPPONENT_HAND, PLAYER_BATTLEFIELD } from '../../helpers/selectors'

test('Sticky Fingers cast grants menace, makes Treasure on combat damage, and draws when host dies', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Aura caster',
    player2Name: 'Defender',
    player1: {
      hand: ['Sticky Fingers', 'Shock'],
      battlefield: [
        { name: 'Grizzly Bears', tapped: false, summoningSickness: false },
        { name: 'Mountain' },
        { name: 'Mountain' },
      ],
      library: ['Forest', 'Plains', 'Island'],
    },
    player2: {
      battlefield: [{ name: 'Glory Seeker', tapped: false, summoningSickness: false }],
      library: ['Mountain', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
    player1StopAtSteps: ['POSTCOMBAT_MAIN'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage

  await p1.clickCard('Sticky Fingers')
  await p1.selectAction('Cast Sticky Fingers')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack('Sticky Fingers')
  await p1.expectOnBattlefield('Sticky Fingers')
  await p2.expectOnBattlefield('Sticky Fingers')
  await expect(p1.page.locator(PLAYER_BATTLEFIELD).locator('[title="MENACE"]')).toBeVisible()
  await expect(p2.page.locator('[data-zone="opponent-battlefield"] [title="MENACE"]')).toBeVisible()
  await p1.screenshot('Sticky Fingers attached and menace visible')
  await p2.screenshot('Defender sees enchanted attacker and menace')

  await p1.pass()
  await p1.attackWith('Grizzly Bears')
  await p2.noBlocks()
  await p1.pass()
  await p2.resolveStack('Grizzly Bears trigger')
  await p1.expectLifeTotal(player2.playerId, 18)
  await p2.expectLifeTotal(player2.playerId, 18)
  await p1.expectOnBattlefield('Treasure')
  await p2.expectOnBattlefield('Treasure')
  await p1.screenshot('Combat damage created Treasure')
  await p2.screenshot('Defender sees damage and Treasure')

  await p1.clickCard('Shock')
  await p1.selectAction('Cast Shock')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack('Shock')
  await p2.resolveStack('Sticky Fingers trigger')
  await p1.expectNotOnBattlefield('Grizzly Bears')
  await p1.expectNotOnBattlefield('Sticky Fingers')
  await p2.expectNotOnBattlefield('Grizzly Bears')
  await p2.expectNotOnBattlefield('Sticky Fingers')
  await p1.expectInHand('Forest')
  await p1.expectHandSize(1)
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName('Forest'))).toHaveCount(0)
  await p1.expectOnBattlefield('Treasure')
  await p2.expectOnBattlefield('Treasure')
  await p1.screenshot('Aura controller drew after host death')
  await p2.screenshot('Defender sees host and Aura leave')
})
