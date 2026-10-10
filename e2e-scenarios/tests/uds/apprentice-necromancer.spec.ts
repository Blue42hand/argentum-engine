import { test, expect } from '../../fixtures/scenarioFixture'

const ability = 'Return a creature from your graveyard with haste; sacrifice it at the next end step'

test('Apprentice Necromancer visibly targets a graveyard creature, pays sacrifice and reanimates in both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Forest'],
      battlefield: [{ name: 'Apprentice Necromancer', summoningSickness: false }, { name: 'Swamp' }],
      graveyard: ['Grizzly Bears', 'Mountain'], library: ['Forest', 'Swamp'],
    },
    player2: { hand: ['Island'], library: ['Island', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
    player1StopAtSteps: ['END'], player2OpponentStopAtSteps: ['END'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Apprentice Necromancer')
  await p1.selectAction(ability)
  await p1.selectTargetInStep('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack('Apprentice Necromancer ability')
  await p1.expectNotOnBattlefield('Apprentice Necromancer')
  await p2.expectNotOnBattlefield('Apprentice Necromancer')
  await p1.expectStats('Grizzly Bears', '2/2')
  await p2.expectStats('Grizzly Bears', '2/2')
  await expect(player2.page.locator('[data-zone="opponent-hand"] img[alt="Forest"]')).toHaveCount(0)
  await p1.screenshot('Returned creature and paid sacrifice')
  await p2.screenshot('Opponent public result')
})

test('Apprentice Necromancer next-end sacrifice remains visible after its source has left', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [{ name: 'Apprentice Necromancer', summoningSickness: false }, { name: 'Swamp' }],
      graveyard: ['Grizzly Bears'], library: ['Forest', 'Swamp'],
    },
    player2: { library: ['Island', 'Mountain'] },
    phase: 'POSTCOMBAT_MAIN', activePlayer: 1,
    player1StopAtSteps: ['END'], player2OpponentStopAtSteps: ['END'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Apprentice Necromancer')
  await p1.selectAction(ability)
  await p1.selectTargetInStep('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack('Apprentice Necromancer ability')
  await p1.expectOnBattlefield('Grizzly Bears')
  await p1.pass()
  await p2.resolveStack('Apprentice Necromancer trigger')
  await p1.expectNotOnBattlefield('Grizzly Bears')
  await p2.expectNotOnBattlefield('Grizzly Bears')
  await p1.expectGraveyardSize(player1.playerId, 2)
  await p2.expectGraveyardSize(player1.playerId, 2)
})
