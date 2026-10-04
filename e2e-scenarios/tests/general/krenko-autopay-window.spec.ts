import { test, expect } from '../../fixtures/scenarioFixture'
import { PLAYER_BATTLEFIELD, cardByName } from '../../helpers/selectors'

test.setTimeout(90_000)

test('Krenko payment window enables Auto Pay only after the remaining Treasure is activated', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Caster',
    player2Name: 'Opponent',
    player1: {
      hand: ['Krenko, Mob Boss'],
      battlefield: [
        { name: 'Mountain' },
        { name: 'Island' },
        { name: 'Forest' },
        { name: 'Treasure' },
      ],
      library: ['Forest', 'Forest'],
    },
    player2: {
      hand: ['Island'],
      library: ['Island', 'Island'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage

  const activateLand = async (name: string) => {
    await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName(name)).click()
    await p1.selectAction('Add')
  }
  const activateTreasure = async () => {
    await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Treasure')).click()
    await p1.selectAction('Add one mana of any color')
    await p1.selectManaColor('Red')
  }

  await activateLand('Island')
  await activateLand('Forest')

  await p1.clickCard('Krenko, Mob Boss')
  await p1.selectAction('Cast')

  await expect(p1.page.getByText('Pay cost?')).toBeVisible()
  await expect(p1.page.getByText('Activate a mana ability or select enough sources to pay.')).toBeVisible()
  await expect(p1.page.getByRole('button', { name: 'Auto Pay' })).toBeDisabled()
  await expect(p2.page.getByText('Pay cost?')).toHaveCount(0)
  await expect(p2.page.getByRole('button', { name: 'Auto Pay' })).toHaveCount(0)
  await p1.screenshot('Auto Pay disabled before mana ability')
  await p2.screenshot('Opponent has no private payment prompt')

  await activateTreasure()
  await expect(p1.page.getByRole('button', { name: 'Auto Pay' })).toBeEnabled()
  await p1.screenshot('Auto Pay enabled after Treasure')
  await p1.page.getByRole('button', { name: 'Auto Pay' }).click()

  await p1.expectOnBattlefield('Krenko, Mob Boss')
  await p2.expectOnBattlefield('Krenko, Mob Boss')
})
