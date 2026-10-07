import { test, expect } from '../../fixtures/scenarioFixture'
import { OPPONENT_BATTLEFIELD, cardByName } from '../../helpers/selectors'

test('Myrsmith offers payment for an artifact cast and shows the Myr to both seats', async ({ createGame }) => {
  test.setTimeout(60_000)
  const { player1, player2 } = await createGame({
    player1Name: 'Myrsmith pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Memnite'],
      battlefield: [{ name: 'Myrsmith' }, { name: 'Plains' }],
      library: ['Plains'],
    },
    player2: { library: ['Forest'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Memnite')
  await p1.selectAction('Cast')
  await p2.pass()

  await expect(p1.page.getByRole('button', { name: 'Pay {1}' })).toBeVisible()
  await p1.page.getByRole('button', { name: 'Pay {1}' }).click()
  await expect(p1.page.getByRole('button', { name: 'Pay (1)' })).toBeVisible()
  await p1.page.getByRole('button', { name: 'Pay (1)' }).click()
  await p1.expectOnBattlefield('Myr Token')
  await expect(p2.page.locator(OPPONENT_BATTLEFIELD).locator(cardByName('Myr Token'))).toBeVisible()
})
