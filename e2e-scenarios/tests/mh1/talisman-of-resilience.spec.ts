import { test, expect } from '../../fixtures/scenarioFixture'

const source = 'Talisman of Resilience'

for (const { color, life } of [
  { color: 'C', life: 20 },
  { color: 'B', life: 19 },
  { color: 'G', life: 19 },
] as const) {
  test(`tap for {${color}} adds mana immediately and leaves controller at ${life} life`, async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Talisman controller', player2Name: 'Observer',
      player1: {
        lifeTotal: 20, battlefield: [{ name: source }], library: ['Forest', 'Swamp', 'Island'],
      },
      player2: { lifeTotal: 20, library: ['Mountain', 'Plains', 'Island'] },
      phase: 'PRECOMBAT_MAIN', activePlayer: 1,
    })
    const p1 = player1.gamePage
    const p2 = player2.gamePage

    await p1.clickCard(source)
    for (const offered of ['C', 'B', 'G']) {
      await expect(p1.page.getByRole('button', { name: new RegExp(`Add \\{${offered}\\}`) })).toBeVisible()
    }
    await p1.page.getByRole('button', { name: new RegExp(`Add \\{${color}\\}`) }).click()

    for (const page of [p1, p2]) {
      await page.expectTapped(source)
      await page.expectLifeTotal(player1.playerId, life)
      await page.expectLifeTotal(player2.playerId, 20)
      const pool = page.page.getByText('Mana:', { exact: true }).locator('xpath=..')
      await expect(pool.locator(`img[alt="{${color}}"]`)).toHaveCount(1)
      await expect(pool.getByText('(1)', { exact: true })).toBeVisible()
      await expect(page.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
    }
    await p1.screenshot(`Talisman produced ${color} with controller life ${life}`)
    await p2.screenshot(`Observer sees tapped Talisman and ${color} in mana pool`)
  })
}
