import { test } from '../../fixtures/scenarioFixture'

test('Chittering Witch creates a Rat and sacrifices it to weaken an opposing creature', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Witch pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Chittering Witch'],
      battlefield: Array.from({ length: 6 }, () => ({ name: 'Swamp' })),
      library: ['Swamp'],
    },
    player2: {
      battlefield: [{ name: 'Hill Giant' }],
      library: ['Mountain'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Chittering Witch')
  await p1.selectAction('Cast Chittering Witch')
  await p2.resolveStack('Chittering Witch trigger')
  await p1.expectOnBattlefield('Chittering Witch')
  await p1.expectOnBattlefield('Rat Token')

  await p1.clickCard('Chittering Witch')
  await p1.selectAction('Sacrifice a creature')
  await p1.selectTarget('Rat Token')
  await p1.confirmTargets()
  await p1.selectTarget('Hill Giant')
  await p1.confirmTargets()
  await p2.pass()

  await p1.expectNotOnBattlefield('Rat Token')
  await p1.expectStats('Hill Giant', '1/1')
  await p2.expectStats('Hill Giant', '1/1')
})
