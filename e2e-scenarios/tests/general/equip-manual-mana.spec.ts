import { test, expect } from '../../fixtures/scenarioFixture'
import { PLAYER_BATTLEFIELD, cardByName } from '../../helpers/selectors'

test.setTimeout(90_000)

test('Equip can use Drum mana without exposing its payment choice to the opponent', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Equipper',
    player2Name: 'Opponent',
    player1: {
      battlefield: [
        { name: 'Sting, the Glinting Dagger' },
        { name: 'Springleaf Drum' },
        { name: 'Grizzly Bears', summoningSickness: false },
        { name: 'Mountain' },
      ],
      library: ['Forest', 'Forest'],
    },
    player2: { library: ['Island', 'Island'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage

  await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Mountain')).click()
  await p1.selectAction('Add')
  await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Sting, the Glinting Dagger')).click()
  await p1.selectAction('Equip')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()

  await expect(p1.page.getByText('Pay cost?')).toBeVisible()
  await expect(p2.page.getByText('Pay cost?')).toHaveCount(0)
  await expect(p2.page.getByRole('button', { name: 'Auto Pay' })).toHaveCount(0)
  await p1.screenshot('Equip awaits manual mana')
  await p2.screenshot('Opponent sees no Equip payment choice')

  await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Springleaf Drum')).click()
  await p1.selectAction('Add one mana of any color')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p1.selectManaColor('Red')
  await expect(p1.page.getByRole('button', { name: 'Auto Pay' })).toBeEnabled()
  await p1.page.getByRole('button', { name: 'Auto Pay' }).click()

  await p2.pass()
  await p1.expectStats('Grizzly Bears', '3/3')
  await p2.expectStats('Grizzly Bears', '3/3')
})
