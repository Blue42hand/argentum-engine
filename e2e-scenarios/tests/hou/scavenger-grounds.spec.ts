import { test } from '../../fixtures/scenarioFixture'

test('Scavenger Grounds selects another Desert as the cost in the client', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Desert pilot',
    player2Name: 'Opponent',
    player1: {
      battlefield: [
        { name: 'Scavenger Grounds' },
        { name: 'Desert of the Mindful' },
        { name: 'Plains' },
        { name: 'Plains' },
      ],
      graveyard: ['Grizzly Bears'],
      library: ['Plains'],
    },
    player2: { graveyard: ['Hill Giant'], library: ['Swamp'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Scavenger Grounds')
  await p1.selectAction('Sacrifice a Desert: Exile all graveyards')
  await p1.selectTarget('Desert of the Mindful')
  await p1.confirmTargets()
  await p2.pass()

  await p1.expectOnBattlefield('Scavenger Grounds')
  await p1.expectNotOnBattlefield('Desert of the Mindful')
  await p1.expectGraveyardSize(player1.playerId, 0)
  await p1.expectGraveyardSize(player2.playerId, 0)
})
