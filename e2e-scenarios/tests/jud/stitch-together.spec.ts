import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, OPPONENT_HAND, cardByName, exile } from '../../helpers/selectors'

const spell = 'Stitch Together'
const creature = 'Grizzly Bears'

for (const graveyardCount of [6, 7]) {
  test(`${graveyardCount} graveyard cards return the creature to ${graveyardCount === 7 ? 'battlefield' : 'hand'}`, async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1: {
        hand: [spell, 'Dark Ritual'], graveyard: [creature, ...Array(graveyardCount - 1).fill('Swamp')],
        battlefield: [{ name: 'Swamp' }, { name: 'Swamp' }],
        library: ['Forest', 'Plains', 'Mountain'],
      },
      player2: {
        hand: ['Coffin Purge'], battlefield: [{ name: 'Swamp' }],
        graveyard: ['Hill Giant'], library: ['Island', 'Forest', 'Mountain'],
      },
      phase: 'PRECOMBAT_MAIN', activePlayer: 1,
    })
    const p1 = player1.gamePage
    const p2 = player2.gamePage
    await p1.expectInHand('Dark Ritual')
    await expect(p2.page.locator(cardByName('Dark Ritual'))).toHaveCount(0)
    await p1.clickCard(spell)
    await p1.selectAction(`Cast ${spell}`)
    const overlay = p1.page.locator('div')
      .filter({ has: p1.page.getByRole('heading', { level: 2 }) })
      .filter({ has: p1.page.getByRole('button', { name: /Confirm|View Battlefield/ }) }).last()
    await expect(overlay.locator(cardByName(creature))).toBeVisible()
    await expect(overlay.locator(cardByName('Swamp'))).toHaveCount(0)
    await expect(overlay.locator(cardByName('Hill Giant'))).toHaveCount(0)
    await p1.selectCardInZoneOverlay(creature)
    await p1.confirmTargets()
    await p2.resolveStack(spell)
    if (graveyardCount === 7) {
      for (const page of [p1, p2]) {
        await page.expectOnBattlefield(creature)
        const returned = page.page.locator(BATTLEFIELD).locator(cardByName(creature))
          .locator('xpath=ancestor::*[@data-card-id][1]')
        await expect(returned).not.toHaveAttribute('data-tapped', 'true')
      }
      await p1.expectNotInHand(creature)
    } else {
      await p1.expectInHand(creature)
      for (const page of [p1, p2]) await page.expectNotOnBattlefield(creature)
      // The existing public-return tracker keeps this formerly public card known in hand.
      await expect(p2.page.locator(OPPONENT_HAND).locator(cardByName(creature)).first()).toBeVisible()
    }
    await expect(p2.page.locator(cardByName('Dark Ritual'))).toHaveCount(0)
    await p1.screenshot('Caster sees the resolved threshold destination')
    await p2.screenshot('Responder sees only the public result')
  })
}

test('exiling the chosen creature in response makes Stitch Together fail', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [spell], graveyard: [creature, ...Array(6).fill('Swamp')],
      battlefield: [{ name: 'Swamp' }, { name: 'Swamp' }],
      library: ['Forest', 'Plains', 'Mountain'],
    },
    player2: {
      hand: ['Coffin Purge'], battlefield: [{ name: 'Swamp' }],
      library: ['Island', 'Forest', 'Mountain'],
    },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(spell)
  await p1.selectAction(`Cast ${spell}`)
  await p1.selectCardInZoneOverlay(creature)
  await p1.confirmTargets()
  await p2.page.getByText(spell, { exact: true }).first().waitFor({ state: 'visible' })
  await p2.clickCard('Coffin Purge')
  await p2.selectAction('Cast Coffin Purge')
  await p2.selectCardInZoneOverlay(creature)
  await p2.confirmTargets()
  await p1.resolveStack('Coffin Purge')
  await p2.resolveStack(spell)
  for (const page of [p1, p2]) {
    await expect(page.page.locator(exile(player1.playerId)).locator(cardByName(creature))).toBeVisible()
  }
  await p1.expectGraveyardSize(player1.playerId, 7)
  await p2.expectGraveyardSize(player2.playerId, 1)
  await p1.expectNotInHand(creature)
  for (const page of [p1, p2]) await page.expectNotOnBattlefield(creature)
  await p1.screenshot('Exiled target is not recovered by Stitch Together')
  await p2.screenshot('Responder sees the failed recovery')
})
