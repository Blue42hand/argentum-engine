import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, OPPONENT_HAND, cardByName, exile } from '../../helpers/selectors'

const source = 'Timeless Witness'
const returned = 'Mind Stone'

test('mandatory enters trigger returns any own graveyard card to hand for both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source, 'Dark Ritual'], graveyard: [returned],
      battlefield: Array.from({ length: 4 }, () => ({ name: 'Forest' })),
      library: ['Plains', 'Island', 'Mountain'],
    },
    player2: {
      hand: ['Stifle'], graveyard: ['Grizzly Bears'], battlefield: [{ name: 'Island' }],
      library: ['Swamp', 'Plains', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  // Mind Stone is a noncreature card, but is still a legal target; the opposing graveyard is not.
  const overlay = p1.page.locator('div')
    .filter({ has: p1.page.getByRole('heading', { level: 2 }) })
    .filter({ has: p1.page.getByRole('button', { name: /Confirm|View Battlefield/ }) }).last()
  await expect(overlay.locator(cardByName(returned))).toBeVisible()
  await expect(overlay.locator(cardByName('Grizzly Bears'))).toHaveCount(0)
  await expect(overlay.getByRole('button', { name: /^(Skip|Select None|Decline)$/ })).toHaveCount(0)
  await p1.selectCardInZoneOverlay(returned)
  await p1.confirmTargets()
  await p2.resolveStack(`${source} trigger`)
  for (const page of [p1, p2]) await page.expectOnBattlefield(source)
  await p1.expectInHand(returned)
  await p1.expectInHand('Dark Ritual')
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName(returned)).first()).toBeVisible()
  await expect(p2.page.locator(cardByName('Dark Ritual'))).toHaveCount(0)
  await p1.expectGraveyardSize(player1.playerId, 0)
  await p2.expectGraveyardSize(player2.playerId, 1)
  await p1.screenshot('Mandatory Witness return to caster hand')
  await p2.screenshot('Opponent sees public return without hidden hand card')
})

test('eternalize exiles the source as a cost and its token copies the enters trigger', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      graveyard: [source, returned],
      battlefield: Array.from({ length: 7 }, () => ({ name: 'Forest' })),
      library: ['Plains', 'Island', 'Mountain'],
    },
    player2: {
      hand: ['Stifle'], battlefield: [{ name: 'Island' }],
      graveyard: ['Grizzly Bears'], library: ['Swamp', 'Plains', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.expectGhostCardInHand(source)
  await p1.page.locator('[data-ghost="true"]').locator(cardByName(source)).click()
  await p1.selectAction('Eternalize')
  // Costs are paid before the opponent receives priority on the ability.
  for (const page of [p1, p2]) {
    await expect(page.page.locator(exile(player1.playerId)).locator(cardByName(source))).toBeVisible()
    await page.expectNotOnBattlefield(source)
  }
  await p1.expectNoGhostCardInHand(source)
  await p1.expectGraveyardSize(player1.playerId, 1)
  // Identical lands are grouped; expand the stack before counting all paid mana sources.
  await p1.page.getByTitle('Ungroup these 7 cards', { exact: true }).click()
  await expect(p1.page.locator(BATTLEFIELD).locator('[data-card-id][data-tapped="true"]')
    .filter({ has: p1.page.locator(cardByName('Forest')) })).toHaveCount(7)
  await p1.screenshot('Eternalize cost paid before opponent priority')
  await p2.resolveStack(`${source} ability`)
  await p1.selectCardInZoneOverlay(returned)
  await p1.confirmTargets()
  await p2.resolveStack(`${source} trigger`)
  for (const page of [p1, p2]) {
    await page.expectOnBattlefield(source)
    await page.expectStats(source, '4/4')
    const token = page.page.locator(BATTLEFIELD).locator('[data-card-id]')
      .filter({ has: page.page.locator(cardByName(source)) }).first()
    await expect(token.locator('[aria-label="Token"]')).toBeVisible()
  }
  await p1.expectInHand(returned)
  await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName(returned)).first()).toBeVisible()
  await p1.expectGraveyardSize(player1.playerId, 0)
  await p1.screenshot('Eternalized 4/4 token returned a card')
  await p2.screenshot('Opponent sees the token and copied trigger result')
})

test('entering with no legal graveyard target completes without a return decision', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source], battlefield: Array.from({ length: 4 }, () => ({ name: 'Forest' })),
      library: ['Plains', 'Island', 'Mountain'],
    },
    player2: {
      hand: ['Stifle'], battlefield: [{ name: 'Island' }],
      graveyard: ['Grizzly Bears'], library: ['Swamp', 'Plains', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  // Stifle cannot target a creature spell; with no ETB targets the server auto-passes to completion.
  for (const page of [p1, p2]) await page.expectOnBattlefield(source)
  await p1.expectHandSize(0)
  await p1.expectGraveyardSize(player1.playerId, 0)
  await p2.expectGraveyardSize(player2.playerId, 1)
  await p1.screenshot('Witness entered with no legal target')
  await p2.screenshot('Opponent sees completed empty-graveyard entry')
})
