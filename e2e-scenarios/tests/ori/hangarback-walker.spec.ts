import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD } from '../../helpers/selectors'

test('Hangarback Walker casts for X=2 and leaves two Thopters when destroyed', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Walker pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Hangarback Walker', 'Murder'],
      battlefield: Array.from({ length: 7 }, () => ({ name: 'Swamp' })),
      library: ['Swamp', 'Swamp'],
    },
    player2: { battlefield: [{ name: 'Hill Giant' }], library: ['Swamp', 'Swamp'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Hangarback Walker')
  await p1.selectAction('Cast Hangarback Walker')
  await p1.selectXValue(2)
  await p1.expectOnBattlefield('Hangarback Walker')
  await p1.expectStats('Hangarback Walker', '2/2')

  await p1.clickCard('Murder')
  await p1.selectAction('Cast Murder')
  await p1.selectTarget('Hangarback Walker')
  await p1.confirmTargets()
  await p2.resolveStack('Murder')
  await p2.resolveStack('Hangarback Walker trigger')

  await p1.expectNotOnBattlefield('Hangarback Walker')
  await expect(player1.page.locator(BATTLEFIELD).locator('img[alt*="Thopter"]')).toHaveCount(2)
})
