import { expect } from '@playwright/test'
import { test } from '../../fixtures/scenarioFixture'

const source = 'Metalwork Colossus'

test('tapped noncreature artifacts permit a zero-mana Colossus cast visible from both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source],
      battlefield: [{ name: 'Dreamstone Hedron', tapped: true }, { name: 'Gilded Lotus', tapped: true }],
      library: ['Forest', 'Plains'],
    },
    player2: { hand: ['Counterspell'], battlefield: [{ name: 'Island' }, { name: 'Island' }], library: ['Mountain', 'Swamp'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p2.resolveStack(source)
  await p1.expectStats(source, '10/10')
  await p2.expectStats(source, '10/10')
  await p1.expectHandSize(0)
  await p1.screenshot('Zero-mana Colossus with tapped mana-value contributors')
  await p2.screenshot('Opponent sees resolved 10/10 Colossus')
})

test('graveyard ability requires two battlefield sacrifices and returns a real hand card', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      graveyard: [source],
      battlefield: [{ name: 'Sol Ring' }, { name: 'Mind Stone' }, { name: 'Ornithopter' }],
      library: ['Forest', 'Plains'],
    },
    player2: { hand: ['Stifle'], battlefield: [{ name: 'Island' }], library: ['Mountain', 'Swamp'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.expectGhostCardInHand(source)
  await p1.page.locator('[data-ghost="true"]').getByAltText(source, { exact: true }).click()
  await p1.selectAction('Return this card from your graveyard to your hand')
  await p1.selectCardInZoneOverlay('Sol Ring')
  await expect(p1.page.getByRole('button').filter({ hasText: /^Confirm/ }).first()).toBeDisabled()
  await p1.selectCardInZoneOverlay('Mind Stone')
  await p1.screenshot('Both distinct artifacts selected as sacrifice costs')
  await p2.screenshot('Opponent sees the battlefield during cost selection')
  await p1.confirmTargets()
  await p1.page.getByRole('button', { name: 'Confirm Order', exact: true }).click()
  await p1.expectNotOnBattlefield('Sol Ring')
  await p1.expectNotOnBattlefield('Mind Stone')
  await p2.resolveStack(`${source} ability`)
  await p1.expectInHand(source)
  await p1.expectHandSize(1)
  await p1.expectNoGhostCardInHand(source)
  await p1.expectOnBattlefield('Ornithopter')
  await p2.expectNotOnBattlefield('Sol Ring')
  await p2.expectNotOnBattlefield('Mind Stone')
  await p1.screenshot('Colossus returned to hand after two paid sacrifices')
  await p2.screenshot('Opponent sees public sacrifices and resolved graveyard ability')
})
