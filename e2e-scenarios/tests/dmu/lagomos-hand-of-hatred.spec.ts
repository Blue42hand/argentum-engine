import { expect } from '@playwright/test'
import { test } from '../../fixtures/scenarioFixture'

const source = 'Lagomos, Hand of Hatred'

test('five deaths enable a mandatory private library search on both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: ['Pyroclasm'],
      battlefield: [{ name: source }, ...Array.from({ length: 4 }, () => ({ name: 'Mountain' })),
        { name: 'Grizzly Bears' }, { name: 'Silvercoat Lion' }],
      library: ['Forest', 'Plains', 'Swamp'],
    },
    player2: {
      hand: ['Island'],
      battlefield: [{ name: 'Runeclaw Bear' }, { name: 'Goblin Piker' }, { name: 'Elite Vanguard' }],
      library: ['Island', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await expect(p1.page.getByRole('button', { name: /Search your library/ })).toHaveCount(0)
  await p1.page.mouse.click(5, 5)
  await p1.clickCard('Pyroclasm')
  await p1.selectAction('Cast Pyroclasm')
  await p2.resolveStack('Pyroclasm')
  await p1.page.getByRole('button', { name: 'Confirm Order' }).click()
  await p2.page.getByRole('button', { name: 'Confirm Order' }).click()
  await p1.expectOnBattlefield(source)
  await p2.expectOnBattlefield(source)
  await p1.clickCard(source)
  await p1.selectAction('Search your library for a card')
  await p2.resolveStack(`${source} ability`)
  await p1.page.getByRole('heading', { level: 2 }).first().waitFor({ state: 'visible' })
  await expect(p1.page.getByRole('button', { name: 'Fail to Find' })).toHaveCount(0)
  await expect(p2.page.getByRole('heading', { name: /Search your library/ })).toHaveCount(0)
  await expect(p2.page.locator('img[alt="Forest"]')).toHaveCount(0)
  await expect(p2.page.locator('img[alt="Plains"]')).toHaveCount(0)
  await expect(p2.page.locator('img[alt="Swamp"]')).toHaveCount(0)
  await p1.screenshot('Mandatory private Lagomos search with no fail-to-find')
  await p2.screenshot('Opponent sees waiting state without private library choices')
  await p1.selectCardInZoneOverlay('Forest')
  await p1.page.getByRole('button', { name: /Confirm Selection|Confirm|Put.*Hand/ }).first().click()
  await p1.expectInHand('Forest')
  await expect(p2.page.locator('img[alt="Forest"]')).toHaveCount(0)
  await p2.expectOnBattlefield(source)
  await p1.screenshot('Selected Forest moved to hand')
  await p2.screenshot('Opponent sees resolved Lagomos ability')
})

test('own-combat trigger creates a visible hasty trampling Elemental and sacrifices it next end step', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { battlefield: [{ name: source }], library: ['Forest', 'Plains'] },
    player2: { library: ['Island', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
    player1StopAtSteps: ['PRECOMBAT_MAIN', 'BEGIN_COMBAT', 'POSTCOMBAT_MAIN', 'END'],
    player2OpponentStopAtSteps: ['BEGIN_COMBAT', 'END'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.pass()
  await p2.resolveStack(`${source} trigger`)
  await p1.expectStats('Elemental Token', '2/1')
  await p2.expectStats('Elemental Token', '2/1')
  await p1.screenshot('Own-combat Elemental visible with haste and trample')
  await p2.screenshot('Opponent sees the same combat token')
  await p1.pass()
  await p2.pass()
  await p1.skipAttacking()
  await p1.pass()
  await p2.resolveStack(`${source} trigger`)
  await p1.expectNotOnBattlefield('Elemental Token')
  await p2.expectNotOnBattlefield('Elemental Token')
  await p1.screenshot('Delayed end-step sacrifice resolved')
  await p2.screenshot('Opponent sees token removed at the same end step')
})
