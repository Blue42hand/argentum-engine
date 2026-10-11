import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName, OPPONENT_HAND, PLAYER_BATTLEFIELD, OPPONENT_BATTLEFIELD } from '../../helpers/selectors'

const source = 'Yahenni, Undying Partisan'
const ability = 'Sacrifice another creature: Yahenni gains indestructible until end of turn.'

test('newly cast Yahenni can attack immediately without revealing the remaining hand', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source, 'Dark Ritual'],
      battlefield: Array.from({ length: 3 }, () => ({ name: 'Swamp' })),
      library: ['Forest', 'Island', 'Mountain'],
    },
    player2: { library: ['Plains', 'Mountain', 'Island'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1, player1StopAtSteps: ['POSTCOMBAT_MAIN'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  for (const page of [p1, p2]) await page.expectStats(source, '2/2')
  await p1.expectInHand('Dark Ritual')
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName('Dark Ritual'))).toHaveCount(0)
  await p1.pass()
  await p1.attackWith(source)
  for (const page of [p1, p2]) await page.expectLifeTotal(player2.playerId, 18)
  await p1.screenshot('Haste allowed a same-turn attack')
  await p2.screenshot('Defender sees public combat result and no private hand')
})

test('an opposing creature death visibly grows Yahenni in both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Murder', 'Dark Ritual'],
      battlefield: [{ name: source }, ...Array.from({ length: 3 }, () => ({ name: 'Swamp' }))],
      library: ['Forest', 'Island', 'Mountain'],
    },
    player2: {
      hand: ['Dark Ritual'], battlefield: [{ name: 'Hill Giant' }, { name: 'Swamp' }],
      library: ['Plains', 'Mountain', 'Island'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard('Murder')
  await p1.selectAction('Cast Murder')
  await p1.selectTarget('Hill Giant')
  await p1.confirmTargets()
  await p2.resolveStack('Murder')
  await p2.resolveStack(`${source} trigger`)
  for (const page of [p1, p2]) {
    await page.expectNotOnBattlefield('Hill Giant')
    await page.expectStats(source, '3/3')
    await page.expectGraveyardSize(player2.playerId, 1)
  }
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName('Dark Ritual'))).toHaveCount(0)
  await p1.screenshot('Opponent death produced a visible counter')
  await p2.screenshot('Both seats see Yahenni grow')
})

test('sacrifice excludes Yahenni, lands and opponents and indestructible prevents destruction', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Murder', 'Dark Ritual'],
      battlefield: [{ name: source }, { name: 'Grizzly Bears' }, ...Array.from({ length: 3 }, () => ({ name: 'Swamp' }))],
      library: ['Forest', 'Island', 'Mountain'],
    },
    player2: {
      hand: ['Dark Ritual'], battlefield: [{ name: 'Hill Giant' }, { name: 'Swamp' }],
      library: ['Plains', 'Mountain', 'Island'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(ability)
  for (const invalid of [source, 'Swamp', 'Hill Giant']) {
    await p1.selectTarget(invalid)
    await expect(p1.page.getByText('Sacrifice another creature (0/1)', { exact: true })).toBeVisible()
    await p1.expectGraveyardSize(player1.playerId, 0)
    await p1.expectStats('Grizzly Bears', '2/2')
  }
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack(`${source} ability`)
  await expect(p1.page.locator(PLAYER_BATTLEFIELD).locator('[title="INDESTRUCTIBLE"]')).toBeVisible()
  await expect(p2.page.locator(OPPONENT_BATTLEFIELD).locator('[title="INDESTRUCTIBLE"]')).toBeVisible()
  for (const page of [p1, p2]) {
    await page.expectNotOnBattlefield('Grizzly Bears')
    await page.expectStats(source, '2/2')
    await page.expectGraveyardSize(player1.playerId, 1)
  }
  await p1.clickCard('Murder')
  await p1.selectAction('Cast Murder')
  await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName(source)).click({ position: { x: 10, y: 10 } })
  await p1.screenshot('Select protected Yahenni as Murder target')
  await p1.confirmTargets()
  await p2.resolveStack('Murder')
  for (const page of [p1, p2]) {
    await page.expectStats(source, '2/2')
    await page.expectGraveyardSize(player1.playerId, 2)
  }
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName('Dark Ritual'))).toHaveCount(0)
  await p1.screenshot('Paid sacrifice and indestructible survive Murder')
  await p2.screenshot('Public sacrifice and survival match across seats')
})
