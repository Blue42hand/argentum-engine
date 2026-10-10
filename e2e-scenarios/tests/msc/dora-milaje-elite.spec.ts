import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, cardByName } from '../../helpers/selectors'

const source = 'Dora Milaje Elite'
const protect = 'Legendary permanents you control gain indestructible until end of turn'

test('Dora enters with a visible catch-up trigger and one tapped Vibranium on both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: [source, 'Forest'], battlefield: [{ name: 'Plains' }, { name: 'Plains' }], library: ['Island', 'Mountain'] },
    player2: { hand: ['Unsummon'], battlefield: [{ name: 'Island' }, { name: 'Forest' }, { name: 'Forest' }], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p2.resolveStack(`${source} trigger`)
  await p1.expectOnBattlefield(source)
  await p2.expectOnBattlefield(source)
  for (const page of [player1.page, player2.page]) {
    await expect(page.locator(BATTLEFIELD).locator(cardByName('Vibranium'))).toHaveCount(1)
    await expect(page.getByText(`${source} trigger`, { exact: true })).toHaveCount(0)
  }
  await p1.expectTapped('Vibranium')
  await p2.expectTapped('Vibranium')
  await p1.expectStats(source, '2/2')
  await p1.screenshot('Dora and tapped catch-up Vibranium')
  await p2.screenshot('Opponent sees the same resolved token')
})

test('Dora sacrifices in response to Wrath and protects legendary permanents without protecting other creatures', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Forest'],
      battlefield: [{ name: source }, { name: 'Krenko, Mob Boss' }, { name: 'Mox Opal' }, { name: "Inventors' Fair" }, { name: 'Grizzly Bears' }],
      library: ['Forest', 'Mountain'],
    },
    player2: {
      hand: ['Wrath of God'],
      battlefield: [...Array.from({ length: 4 }, () => ({ name: 'Plains' })), { name: 'Grizzly Bears' }],
      library: ['Island', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 2,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p2.clickCard('Wrath of God')
  await p2.selectAction('Cast Wrath of God')
  await expect(player1.page.getByText('Wrath of God', { exact: true })).toBeVisible()
  await p1.clickCard(source)
  await p1.selectAction(protect)
  await p1.expectNotOnBattlefield(source)
  await p2.expectNotOnBattlefield(source)
  await p2.resolveStack(`${source} ability`)
  await p1.screenshot('Legendary permanents protected while Wrath remains on the stack')
  await p1.resolveStack('Wrath of God')
  await p1.expectOnBattlefield('Krenko, Mob Boss')
  await p2.expectOnBattlefield('Krenko, Mob Boss')
  await p1.expectOnBattlefield('Mox Opal')
  await p1.expectOnBattlefield("Inventors' Fair")
  for (const page of [player1.page, player2.page]) {
    await expect(page.locator(BATTLEFIELD).locator(cardByName('Grizzly Bears'))).toHaveCount(0)
    await expect(page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
    await expect(page.getByText('Wrath of God', { exact: true })).toHaveCount(0)
  }
  await p1.screenshot('Krenko survives while both nonlegendary Bears are destroyed')
  await p2.screenshot('Opponent sees sacrifice and selective survival')
})
