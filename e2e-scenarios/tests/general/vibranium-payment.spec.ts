import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, cardByName } from '../../helpers/selectors'

test('Vibranium is visible to both seats and its mana can cast an artifact', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [{ name: 'Vibranium' }],
      hand: ['Sol Ring'],
    },
    player2: { library: ['Forest'] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
    priorityPlayer: 1,
  })

  const token = player1.page.locator(BATTLEFIELD).locator(cardByName('Vibranium')).first()
  await expect(token).toBeVisible()
  await expect(player2.page.locator(BATTLEFIELD).locator(cardByName('Vibranium')).first()).toBeVisible()

  await player1.gamePage.clickCard('Vibranium')
  await player1.page.getByRole('button', { name: /This mana can't be spent to cast a nonartifact spell/ }).click()
  await expect(player1.page.getByTitle("This mana can't be spent to cast a nonartifact spell")).toBeVisible()
  await player1.gamePage.selectCardInHand('Sol Ring')
  await player1.gamePage.selectAction('Cast Sol Ring')
  await player1.gamePage.expectOnBattlefield('Sol Ring')
  await player2.gamePage.expectOnBattlefield('Sol Ring')
})

test('Vibranium can be selected to pay a visible attack tax', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [
        { name: 'Vibranium' },
        { name: 'Grizzly Bears', summoningSickness: false },
      ],
    },
    player2: { battlefield: [{ name: 'Baird, Steward of Argive' }] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
    priorityPlayer: 1,
  })

  await player1.gamePage.pass()
  await player1.gamePage.attackAll()
  await expect(player1.page.getByText('Pay cost')).toBeVisible()
  await expect(player1.page.getByRole('button', { name: 'Pay (1)' })).toBeEnabled()
  await player1.page.getByRole('button', { name: 'Pay (1)' }).click()
  await expect(player2.page.locator(BATTLEFIELD).locator(cardByName('Grizzly Bears')).first()).toBeVisible()
  await expect(player1.page.locator('[data-card-id][data-tapped="true"]').filter({
    has: player1.page.locator(cardByName('Vibranium')),
  })).toBeVisible()
})

test('Vibranium mana floated before ward enables the Pay button', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [{ name: 'Vibranium' }, { name: 'Mountain' }],
      hand: ['Shock'],
    },
    player2: { battlefield: [{ name: 'Pippin, Guard of the Citadel' }] },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
    priorityPlayer: 1,
  })

  await player1.gamePage.clickCard('Vibranium')
  await player1.page.getByRole('button', { name: /This mana can't be spent to cast a nonartifact spell/ }).click()
  await expect(player1.page.getByTitle("This mana can't be spent to cast a nonartifact spell")).toBeVisible()

  await player1.gamePage.clickCard('Shock')
  await player1.gamePage.selectAction('Cast Shock')
  await player1.gamePage.selectTarget('Pippin, Guard of the Citadel')
  await player1.gamePage.confirmTargets()

  await player1.gamePage.pass()
  await expect(player1.page.getByText('Pay cost')).toBeVisible()
  await expect(player1.page.getByRole('button', { name: 'Pay', exact: true })).toBeEnabled()
  await player1.page.getByRole('button', { name: 'Pay', exact: true }).click()
  await player2.gamePage.pass()
  await player2.gamePage.expectNotOnBattlefield('Pippin, Guard of the Citadel')
})
