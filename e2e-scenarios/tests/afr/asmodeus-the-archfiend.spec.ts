import { test, expect } from '../../fixtures/scenarioFixture'
import { HAND, cardByName, exile } from '../../helpers/selectors'

const source = 'Asmodeus the Archfiend'
const secrets = ['Ancestral Recall', 'Dark Ritual', 'Lightning Bolt', 'Giant Growth', 'Counterspell', 'Doom Blade', 'Opt']
const draw = 'Draw seven cards'
const retrieve = "Return all cards exiled with Asmodeus to their owner's hand and you lose that much life"

test('Asmodeus hides all seven replaced draws from both seats, then privately returns them with visible life loss', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: ['Forest'], battlefield: [{ name: source }, ...Array.from({ length: 8 }, () => ({ name: 'Swamp' }))], library: [...secrets, 'Mountain'] },
    player2: { hand: ['Island'], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(draw)
  await player1.page.getByRole('button', { name: 'Activate', exact: true }).click()
  await p2.resolveStack(`${source} ability`)
  await p1.expectHandSize(1)
  await expect(player1.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
  await expect(player2.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
  for (const page of [player1.page, player2.page]) {
    await expect(page.locator(exile(player1.playerId))).toHaveText('7')
    await expect(page.locator(exile(player1.playerId)).locator(cardByName('Face-down card'))).toHaveCount(1)
  }
  for (const name of secrets) {
    await expect(player1.page.locator(cardByName(name))).toHaveCount(0)
    await expect(player2.page.locator(cardByName(name))).toHaveCount(0)
  }
  await p1.screenshot('Face-down linked exile hidden even from its owner')
  await p2.screenshot('Opponent sees no exiled identities')
  await p1.clickCard(source)
  await p1.selectAction(retrieve)
  await player1.page.getByRole('button', { name: 'Activate', exact: true }).click()
  await p2.resolveStack(`${source} ability`)
  await p1.expectHandSize(8)
  await p1.expectLifeTotal(player1.playerId, 13)
  await p2.expectLifeTotal(player1.playerId, 13)
  for (const name of secrets) {
    await expect(player1.page.locator(HAND).locator(cardByName(name))).toHaveCount(1)
    await expect(player2.page.locator(cardByName(name))).toHaveCount(0)
  }
  await expect(player1.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
  await p1.screenshot('Seven privately returned cards and seven life lost')
})

test('Asmodeus return ability survives its source being bounced in response', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: { hand: ['Forest'], battlefield: [{ name: source }, ...Array.from({ length: 8 }, () => ({ name: 'Swamp' }))], library: [...secrets, 'Mountain'] },
    player2: { hand: ['Unsummon', 'Island'], battlefield: [{ name: 'Island' }], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(draw)
  await player1.page.getByRole('button', { name: 'Activate', exact: true }).click()
  await p2.resolveStack(`${source} ability`)
  await expect(player1.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
  await p1.clickCard(source)
  await p1.selectAction(retrieve)
  await player1.page.getByRole('button', { name: 'Activate', exact: true }).click()
  await expect(player2.page.getByText(`${source} ability`, { exact: true })).toBeVisible()
  await p2.clickCard('Unsummon')
  await p2.selectAction('Cast Unsummon')
  await p2.selectTarget(source)
  await p2.confirmTargets()
  await p1.resolveStack('Unsummon')
  await p1.expectNotOnBattlefield(source)
  await p2.expectNotOnBattlefield(source)
  await p2.resolveStack(`${source} ability`)
  await p1.expectHandSize(9)
  await p1.expectLifeTotal(player1.playerId, 13)
  await p2.expectLifeTotal(player1.playerId, 13)
  for (const name of secrets) await expect(player2.page.locator(cardByName(name))).toHaveCount(0)
  await expect(player1.page.getByText(`${source} ability`, { exact: true })).toHaveCount(0)
  await p1.screenshot('Departed source still returns the original hidden pile')
})
