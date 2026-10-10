import { test } from '../../fixtures/scenarioFixture'

const source = 'Rakdos Charm'
const lands = [{ name: 'Swamp' }, { name: 'Mountain' }]
const response = {
  hand: ['Counterspell'], battlefield: [{ name: 'Island' }, { name: 'Island' }],
  library: ['Mountain', 'Swamp'],
}

test('graveyard mode targets a player and exiles only that graveyard from both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: [source], battlefield: lands, graveyard: ['Grizzly Bears'], library: ['Forest', 'Plains'] },
    player2: { ...response, graveyard: ['Hill Giant', 'Llanowar Elves'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction("Exile target player's graveyard")
  await p1.selectPlayer(player2.playerId)
  await p1.confirmTargets()
  await p1.screenshot('Caster selects opponent graveyard by player target')
  await p2.resolveStack(source)
  await p1.expectGraveyardSize(player2.playerId, 0)
  await p2.expectGraveyardSize(player2.playerId, 0)
  // The resolving instant goes to the caster's graveyard after leaving its existing card alone.
  await p1.expectGraveyardSize(player1.playerId, 2)
  await p1.screenshot('Target graveyard empty; caster graveyard preserved')
  await p2.screenshot('Opponent sees its graveyard exiled')
})

test('artifact mode offers battlefield targeting and destroys the selected artifact', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: [source], battlefield: [...lands, { name: 'Mind Stone' }], library: ['Forest', 'Plains'] },
    player2: { ...response, battlefield: [...response.battlefield, { name: 'Sol Ring' }] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction('Destroy target artifact')
  await p1.selectTarget('Sol Ring')
  await p1.confirmTargets()
  await p2.resolveStack(source)
  await p1.expectNotOnBattlefield('Sol Ring')
  await p2.expectNotOnBattlefield('Sol Ring')
  await p1.expectOnBattlefield('Mind Stone')
  await p2.expectGraveyardSize(player2.playerId, 1)
  await p1.screenshot('Selected artifact destroyed; unselected artifact preserved')
  await p2.screenshot('Opponent sees its artifact in the graveyard')
})

test('damage mode needs no targets and counts creatures separately for each controller', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: [source], battlefield: [...lands, { name: 'Grizzly Bears' }], library: ['Forest', 'Plains'] },
    player2: { ...response, battlefield: [...response.battlefield, { name: 'Hill Giant' }, { name: 'Llanowar Elves' }] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction('Each creature deals 1 damage to its controller')
  await p2.resolveStack(source)
  await p1.expectLifeTotal(player1.playerId, 19)
  await p1.expectLifeTotal(player2.playerId, 18)
  await p2.expectLifeTotal(player1.playerId, 19)
  await p2.expectLifeTotal(player2.playerId, 18)
  await p1.expectOnBattlefield('Grizzly Bears')
  await p2.expectOnBattlefield('Hill Giant')
  await p1.screenshot('Untargeted mode deals creature-source damage to both controllers')
  await p2.screenshot('Both life totals and surviving creatures visible to opponent')
})
