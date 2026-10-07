import { test } from '../../fixtures/scenarioFixture'

test('Return of the Wildspeaker pumps non-Humans visibly to both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Wildspeaker pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Return of the Wildspeaker'],
      battlefield: [
        { name: 'Myrsmith' },
        { name: 'Grizzly Bears' },
        ...Array.from({ length: 5 }, () => ({ name: 'Forest' })),
      ],
      library: ['Forest'],
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
  await p1.clickCard('Return of the Wildspeaker')
  await p1.selectAction('Non-Human creatures you control get +3/+3')
  await p2.resolveStack('Return of the Wildspeaker')

  await p1.expectStats('Grizzly Bears', '5/5')
  await p2.expectStats('Grizzly Bears', '5/5')
  await p1.expectStats('Myrsmith', '2/1')
  await p2.expectStats('Hill Giant', '3/3')
})
