import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName } from '../../helpers/selectors'

test('Mindless Automaton pays discard before growing and draws privately after removing counters', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Automaton pilot',
    player2Name: 'Opponent',
    player1: {
      hand: ['Mindless Automaton', 'Forest', 'Plains'],
      battlefield: Array.from({ length: 5 }, () => ({ name: 'Mountain' })),
      library: ['Island', 'Swamp'],
    },
    player2: { library: ['Swamp', 'Plains'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Mindless Automaton')
  await p1.selectAction('Cast Mindless Automaton')
  await p1.expectStats('Mindless Automaton', '2/2')
  await p2.expectStats('Mindless Automaton', '2/2')
  await expect(player2.page.locator(cardByName('Forest'))).toHaveCount(0)
  await p1.clickCard('Mindless Automaton')
  await p1.selectAction('Discard a card: Put a +1/+1 counter')
  await p1.selectCardInHand('Forest')
  await p1.confirmTargets()
  await p1.expectGraveyardSize(player1.playerId, 1)
  await p2.resolveStack('Mindless Automaton ability')
  await p1.expectStats('Mindless Automaton', '3/3')
  await p2.expectStats('Mindless Automaton', '3/3')
  await p1.clickCard('Mindless Automaton')
  await p1.selectAction('Remove two +1/+1 counters: Draw a card')
  await p2.resolveStack('Mindless Automaton ability')
  await p1.expectStats('Mindless Automaton', '1/1')
  await p2.expectStats('Mindless Automaton', '1/1')
  await p1.expectHandSize(2)
  await expect(player2.page.locator(cardByName('Island'))).toHaveCount(0)
  await expect(player2.page.locator(cardByName('Swamp'))).toHaveCount(0)
})

test('the last counter payment kills Mindless Automaton while its draw still resolves', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Mindless Automaton'],
      battlefield: Array.from({ length: 4 }, () => ({ name: 'Mountain' })),
      library: ['Island', 'Swamp'],
    },
    player2: { library: ['Plains', 'Swamp'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Mindless Automaton')
  await p1.selectAction('Cast Mindless Automaton')
  await p1.expectStats('Mindless Automaton', '2/2')
  await p1.clickCard('Mindless Automaton')
  await p1.selectAction('Remove two +1/+1 counters: Draw a card')
  await p1.expectNotOnBattlefield('Mindless Automaton')
  await p2.expectNotOnBattlefield('Mindless Automaton')
  await p1.expectGraveyardSize(player1.playerId, 1)
  await p2.resolveStack('Mindless Automaton ability')
  await p1.expectHandSize(1)
  await expect(player2.page.locator(cardByName('Island'))).toHaveCount(0)
  await expect(player2.page.locator(cardByName('Swamp'))).toHaveCount(0)
})
