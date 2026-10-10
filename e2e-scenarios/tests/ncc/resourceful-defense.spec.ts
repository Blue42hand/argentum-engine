import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName } from '../../helpers/selectors'

const board = [
  { name: 'Grizzly Bears', counters: { PLUS_ONE_PLUS_ONE: 2, CHARGE: 1 } },
  { name: 'Llanowar Elves' },
  ...Array.from({ length: 8 }, () => ({ name: 'Plains' })),
  { name: 'Island' },
]

test('Resourceful Defense preserves a bounced creature’s counters with visible targeting in both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: ['Resourceful Defense', 'Unsummon', 'Forest'], battlefield: board, library: ['Forest', 'Mountain'] },
    player2: { library: ['Swamp', 'Plains'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Resourceful Defense')
  await p1.selectAction('Cast Resourceful Defense')
  await p1.expectOnBattlefield('Resourceful Defense')
  await p1.clickCard('Unsummon')
  await p1.selectAction('Cast Unsummon')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack('Unsummon')
  await expect(player1.page.getByText('Choose Target', { exact: true })).toBeVisible()
  await p1.selectTarget('Llanowar Elves')
  await p1.confirmTargets()
  await p2.resolveStack('Resourceful Defense trigger')
  await p1.expectStats('Llanowar Elves', '3/3')
  await p2.expectStats('Llanowar Elves', '3/3')
  await p2.expectNotOnBattlefield('Grizzly Bears')
  // The engine retains knowledge of a card returned from the public battlefield.
  await expect(player2.page.locator('[data-zone="opponent-hand"] img[alt="Grizzly Bears"]')).toHaveCount(1)
  await expect(player2.page.locator('[data-zone="opponent-hand"] img[alt="Forest"]')).toHaveCount(0)
})

test('Resourceful Defense chooses two distinct targets and per-kind counter counts including zero', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: ['Resourceful Defense'], battlefield: board, library: ['Forest', 'Mountain'] },
    player2: { library: ['Swamp', 'Plains'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Resourceful Defense')
  await p1.selectAction('Cast Resourceful Defense')
  await p1.expectOnBattlefield('Resourceful Defense')
  await p1.clickCard('Resourceful Defense')
  await p1.selectAction('Move any number of counters between two permanents you control')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p1.selectTarget('Llanowar Elves')
  await p1.confirmTargets()
  await p2.resolveStack('Resourceful Defense ability')
  await expect(player1.page.getByRole('heading', { name: /Move how many \+1\/\+1 counters/ })).toBeVisible()
  await player1.page.getByRole('button', { name: '1', exact: true }).click()
  await player1.page.getByRole('button', { name: 'Confirm', exact: true }).click()
  await expect(player1.page.getByRole('heading', { name: /Move how many charge counters/ })).toBeVisible()
  await player1.page.getByRole('button', { name: '0', exact: true }).click()
  await player1.page.getByRole('button', { name: 'Confirm', exact: true }).click()
  await p1.expectStats('Grizzly Bears', '3/3')
  await p2.expectStats('Grizzly Bears', '3/3')
  await p1.expectStats('Llanowar Elves', '2/2')
  await p2.expectStats('Llanowar Elves', '2/2')
  await expect(player2.page.locator(cardByName('Forest'))).toHaveCount(0)
  await p1.screenshot('Counters transferred')
  await p2.screenshot('Public counter result')
})
