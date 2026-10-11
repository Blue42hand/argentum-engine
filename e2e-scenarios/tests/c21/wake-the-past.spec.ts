import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, PLAYER_BATTLEFIELD, OPPONENT_BATTLEFIELD, cardByName } from '../../helpers/selectors'

const source = 'Wake the Past'
const mana = [...Array.from({ length: 6 }, () => ({ name: 'Plains' })), { name: 'Mountain' }]

test('returns only own graveyard artifacts, granting haste to the returned objects', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source], graveyard: ['Steel Overseer', 'Sol Ring', 'Grizzly Bears'],
      battlefield: [{ name: 'Ornithopter' }, ...mana], library: ['Forest', 'Island', 'Swamp'],
    },
    player2: { graveyard: ['Mind Stone'], library: ['Mountain', 'Forest', 'Plains'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p2.resolveStack(source)

  for (const page of [p1, p2]) {
    await page.expectOnBattlefield('Steel Overseer')
    await page.expectOnBattlefield('Sol Ring')
    await page.expectOnBattlefield('Ornithopter')
    await page.expectNotOnBattlefield('Mind Stone')
    await page.expectGraveyardSize(player1.playerId, 2) // nonartifact Bears and Wake itself
    await page.expectGraveyardSize(player2.playerId, 1)
    const returned = page.page.locator(BATTLEFIELD).locator(cardByName('Steel Overseer'))
      .locator('xpath=ancestor::*[@data-card-id][1]')
    await expect(returned.locator('[title="HASTE"]')).toBeVisible()
    const existing = page.page.locator(BATTLEFIELD).locator(cardByName('Ornithopter'))
      .locator('xpath=ancestor::*[@data-card-id][1]')
    await expect(existing.locator('[title="HASTE"]')).toHaveCount(0)
  }
  await p1.screenshot('Own artifacts returned with haste, existing artifact unchanged')
  await p2.screenshot('Other owner sees their artifact remain in the graveyard')
})

test('empty own artifact graveyard leaves an existing artifact without haste', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source], graveyard: ['Grizzly Bears'],
      battlefield: [{ name: 'Steel Overseer' }, ...mana], library: ['Forest', 'Island', 'Swamp'],
    },
    player2: { graveyard: ['Mind Stone'], library: ['Mountain', 'Forest', 'Plains'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p2.resolveStack(source)

  for (const page of [p1, p2]) {
    await page.expectOnBattlefield('Steel Overseer')
    await page.expectNotOnBattlefield('Mind Stone')
    await page.expectGraveyardSize(player1.playerId, 2)
    await page.expectGraveyardSize(player2.playerId, 1)
    const existing = page.page.locator(BATTLEFIELD).locator(cardByName('Steel Overseer'))
      .locator('xpath=ancestor::*[@data-card-id][1]')
    await expect(existing.locator('[title="HASTE"]')).toHaveCount(0)
  }
  await p1.screenshot('Empty artifact return gives no existing artifact haste')
  await p2.screenshot('No opponent artifact was returned')
})

test('returned Cursed Mirror offers its entry copy choice during Wake resolution', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source], graveyard: ['Cursed Mirror'], battlefield: mana,
      library: ['Forest', 'Island', 'Swamp'],
    },
    player2: {
      battlefield: [{ name: 'Hill Giant' }], library: ['Mountain', 'Forest', 'Plains'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p2.resolveStack(source)
  await expect(p1.page.getByText('Choose a creature for Cursed Mirror to copy', { exact: true })).toBeVisible()
  await p1.selectTarget('Hill Giant')
  await p1.confirmSelection()

  await expect(p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Hill Giant'))).toBeVisible()
  await expect(p2.page.locator(OPPONENT_BATTLEFIELD).locator(cardByName('Hill Giant'))).toBeVisible()
  await p1.expectGraveyardSize(player1.playerId, 1) // Wake, not Cursed Mirror
  await p1.screenshot('Returned Cursed Mirror copied the opposing Hill Giant')
  await p2.screenshot('Opponent sees the resulting copied creature')
})
