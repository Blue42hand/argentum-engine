import { test, expect } from '../../fixtures/scenarioFixture'
import { OPPONENT_BATTLEFIELD, PLAYER_BATTLEFIELD, cardByName } from '../../helpers/selectors'

test.setTimeout(60_000)

test('Abrade payment window lets caster use Springleaf Drum and keeps opponent view clear', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Caster',
    player2Name: 'Opponent',
    player1: {
      hand: ['Abrade'],
      battlefield: [
        { name: 'Mountain' },
        { name: 'Springleaf Drum' },
        { name: 'Grizzly Bears', summoningSickness: false },
      ],
      library: ['Forest', 'Forest'],
    },
    player2: {
      battlefield: [{ name: 'Phyrexian Altar' }],
      library: ['Island', 'Island'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage

  await p1.clickCard('Abrade')
  await p1.selectAction('Destroy target artifact')
  await p1.page.locator(OPPONENT_BATTLEFIELD).locator(cardByName('Phyrexian Altar')).click()
  await p1.confirmTargets()

  await expect(p1.page.getByText('Pay cost?')).toBeVisible()
  await expect(p1.page.getByText('You can also click any permanent to use its mana ability.')).toBeVisible()
  await expect(p2.page.getByText('Pay cost?')).toHaveCount(0)
  await p1.screenshot('Abrade payment window')
  await p2.screenshot('Opponent view during cast payment')

  await p1.page.locator(PLAYER_BATTLEFIELD).locator(cardByName('Springleaf Drum')).click()
  await p1.selectAction('Add one mana of any color')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p1.selectManaColor('Red')
  await expect(p1.page.getByText('Pay cost?')).toBeVisible()
  await p1.page.getByRole('button', { name: /Pay \(1\)/ }).click()

  await p2.resolveStack('Abrade')
  await p1.expectNotInHand('Abrade')
  await p1.expectNotOnBattlefield('Phyrexian Altar')
  await p2.expectNotOnBattlefield('Phyrexian Altar')
})
