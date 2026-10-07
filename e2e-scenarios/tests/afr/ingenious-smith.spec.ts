import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName } from '../../helpers/selectors'

test('Ingenious Smith reveals an artifact and grows only once for two artifact entries', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Smith pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Ingenious Smith', 'Memnite'],
      battlefield: Array.from({ length: 3 }, () => ({ name: 'Plains' })),
      library: ['Sol Ring', 'Forest', 'Mountain', 'Swamp', 'Island'],
    },
    player2: { library: ['Swamp'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Ingenious Smith')
  await p1.selectAction('Cast Ingenious Smith')
  await p2.resolveStack('Ingenious Smith trigger')
  await expect(player2.page.locator(cardByName('Sol Ring'))).toHaveCount(0)
  await p1.selectCardInDecision('Sol Ring')
  await p1.confirmSelection()
  const reveal = player2.page.locator('div')
    .filter({ has: player2.page.getByRole('heading', { level: 2 }) })
    .filter({ has: player2.page.getByRole('button', { name: 'OK', exact: true }) })
    .last()
  await expect(reveal.locator(cardByName('Sol Ring'))).toBeVisible()
  await p2.dismissRevealedCards()
  await p1.dismissRevealedCards()
  await p1.expectInHand('Sol Ring')
  await p1.clickCard('Sol Ring')
  await p1.selectAction('Cast Sol Ring')
  await p2.resolveStack('Ingenious Smith trigger')
  await p1.expectStats('Ingenious Smith', '2/2')
  await p2.expectStats('Ingenious Smith', '2/2')
  await p1.clickCard('Memnite')
  await p1.selectAction('Cast Memnite')
  await p1.expectOnBattlefield('Memnite')
  await p2.expectOnBattlefield('Memnite')
  await expect(player1.page.getByText('Ingenious Smith trigger', { exact: true })).toHaveCount(0)
  await p1.expectStats('Ingenious Smith', '2/2')
  await p2.expectStats('Ingenious Smith', '2/2')
})
