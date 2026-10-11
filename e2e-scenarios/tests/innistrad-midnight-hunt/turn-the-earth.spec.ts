import { test, expect } from '../../fixtures/scenarioFixture'
import type { Page } from '@playwright/test'

const spell = 'Turn the Earth'

async function expectOwnedLibrary(page: Page, owner: string, count: number) {
  const pile = page.locator(`[data-zone$="-library"][data-zone-owner="${owner}"]`)
  await expect(pile).toHaveCount(1)
  await expect(pile).toHaveText(String(count))
}

test('the graveyard flashback offer resolves for life and exiles the spell for both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      graveyard: [spell, 'Grizzly Bears'], battlefield: [{ name: 'Forest' }, { name: 'Forest' }],
      library: ['Island', 'Plains', 'Mountain'],
    },
    player2: { library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  const offer = p1.page.locator('[data-ghost="true"]').filter({ has: p1.page.getByAltText(spell, { exact: true }) })
  await expect(offer).toBeVisible()
  await offer.getByAltText(spell, { exact: true }).click()
  await p1.selectAction(`Cast ${spell}`)
  await p1.page.getByRole('button', { name: 'Skip', exact: true }).click()
  await expect(p2.page.getByText(spell, { exact: true })).toBeVisible()
  await p2.pass()
  for (const page of [p1, p2]) {
    await page.expectLifeTotal(player1.playerId, 22)
    await page.expectGraveyardSize(player1.playerId, 1)
    await expect(page.page.locator(`[data-exile-id="${player1.playerId}"]`).getByAltText(spell, { exact: true })).toBeVisible()
    await expectOwnedLibrary(page.page, player1.playerId, 3)
    await expectOwnedLibrary(page.page, player2.playerId, 2)
  }
  await expect(offer).toHaveCount(0)
  await p1.screenshot('Flashback resolves and moves the spell to exile')
  await p2.screenshot('Opponent sees the public flashback outcome')
})

test('choosing no targets gains two life without a shuffle', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [spell], graveyard: ['Grizzly Bears'], battlefield: [{ name: 'Forest' }],
      library: ['Island', 'Plains', 'Mountain'],
    },
    player2: { graveyard: ['Hill Giant'], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.page.getByRole('button', { name: /^Log \(/ }).click()
  const ownShuffles = p1.page.getByText('You shuffled your library', { exact: true })
  const otherShuffles = p1.page.getByText('Opponent shuffled their library', { exact: true })
  const ownBefore = await ownShuffles.count()
  const otherBefore = await otherShuffles.count()

  await p1.clickCard(spell)
  await p1.selectAction(`Cast ${spell}`)
  // The cast target picker offers a dedicated Skip action for zero targets.
  await expect(p1.page.getByRole('button', { name: 'Skip', exact: true })).toBeVisible()
  await p1.page.getByRole('button', { name: 'Skip', exact: true }).click()
  await expect(p2.page.getByText(spell, { exact: true })).toBeVisible()
  await p2.pass()

  for (const page of [p1, p2]) {
    await page.expectLifeTotal(player1.playerId, 22)
    await page.expectGraveyardSize(player1.playerId, 2) // original creature plus the resolved spell
    await page.expectGraveyardSize(player2.playerId, 1)
    await expectOwnedLibrary(page.page, player1.playerId, 3)
    await expectOwnedLibrary(page.page, player2.playerId, 2)
  }
  await expect(ownShuffles).toHaveCount(ownBefore)
  await expect(otherShuffles).toHaveCount(otherBefore)
  await p1.screenshot('Zero targets gain life without shuffling')
  await p2.screenshot('Opponent sees life gain and unchanged graveyards')
})

test('three cards across both graveyards return to their owners libraries and shuffle once each', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Earth caster', player2Name: 'Other owner',
    player1: {
      hand: [spell], graveyard: ['Grizzly Bears', 'Swamp'],
      battlefield: [{ name: 'Forest' }], library: ['Island', 'Plains', 'Mountain'],
    },
    player2: { graveyard: ['Hill Giant', 'Mountain'], library: ['Forest', 'Island'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.page.getByRole('button', { name: /^Log \(/ }).click()
  await p2.page.getByRole('button', { name: /^Log \(/ }).click()
  const ownShuffles = p1.page.getByText('You shuffled your library', { exact: true })
  const otherShuffles = p1.page.getByText('Opponent shuffled their library', { exact: true })
  const ownBefore = await ownShuffles.count()
  const otherBefore = await otherShuffles.count()

  await p1.clickCard(spell)
  await p1.selectAction(`Cast ${spell}`)
  await p1.selectCardInZoneOverlay('Grizzly Bears')
  await p1.selectCardInZoneOverlay('Swamp')
  await p1.page.getByRole('button', { name: /Other owner's Graveyard/ }).click()
  await p1.selectCardInZoneOverlay('Hill Giant')
  await p1.page.getByRole('button', { name: 'Confirm (3)', exact: true }).click()
  await expect(p2.page.getByText(spell, { exact: true })).toBeVisible()
  await p2.pass()

  for (const page of [p1, p2]) {
    await page.expectLifeTotal(player1.playerId, 22)
    await page.expectGraveyardSize(player1.playerId, 1) // spell itself
    await page.expectGraveyardSize(player2.playerId, 1) // unchosen Mountain
    await expectOwnedLibrary(page.page, player1.playerId, 5)
    await expectOwnedLibrary(page.page, player2.playerId, 3)
  }
  await expect(ownShuffles).toHaveCount(ownBefore + 1)
  await expect(otherShuffles).toHaveCount(otherBefore + 1)
  await p1.screenshot('Three chosen cards moved by owner with one shuffle each')
  await p2.screenshot('Other owner sees their library and graveyard updated')
})
