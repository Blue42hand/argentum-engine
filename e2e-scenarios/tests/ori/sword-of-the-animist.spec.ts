import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName } from '../../helpers/selectors'

const source = 'Sword of the Animist'

test('cast, equip and attack search remain private and put a basic land onto the battlefield tapped', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Sword controller', player2Name: 'Defender',
    player1: {
      hand: [source],
      battlefield: [{ name: 'Grizzly Bears' }, ...Array.from({ length: 4 }, () => ({ name: 'Mountain' }))],
      library: ['Forest', 'Plains', 'Lightning Bolt'],
    },
    player2: { library: ['Island', 'Swamp', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1, player1StopAtSteps: ['POSTCOMBAT_MAIN'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p1.expectOnBattlefield(source)
  await p1.clickCard(source)
  await p1.selectAction('Equip')
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack(`${source} ability`)
  for (const page of [p1, p2]) await page.expectStats('Grizzly Bears', '3/3')
  await p1.screenshot('Sword equipped and bonus visible')
  await p2.screenshot('Defender sees the same equipped creature')
  await p1.pass()
  await p1.attackWith('Grizzly Bears')
  await p2.resolveStack(`${source} trigger`)
  await p1.answerYes()
  const search = p1.page.locator('div')
    .filter({ has: p1.page.getByRole('heading', { name: 'Choose up to 1 card', exact: true }) })
    .filter({ has: p1.page.getByRole('button', { name: 'Select None', exact: true }) })
    .last()
  await expect(search.locator(cardByName('Forest'))).toBeVisible()
  await expect(search.locator(cardByName('Plains'))).toBeVisible()
  await expect(search.locator(cardByName('Lightning Bolt'))).toHaveCount(0)
  await expect(p2.page.locator(cardByName('Forest'))).toHaveCount(0)
  await expect(p2.page.locator(cardByName('Plains'))).toHaveCount(0)
  await expect(p2.page.getByRole('button', { name: 'Confirm Selection', exact: true })).toHaveCount(0)
  await p1.screenshot('Only the searching player sees eligible basic lands')
  await p2.screenshot('Defender sees no private search choices')
  await p1.selectCardInDecision('Forest')
  await p1.confirmSelection()
  for (const page of [p1, p2]) {
    await page.expectOnBattlefield('Forest')
    await page.expectTapped('Forest')
    await page.expectStats('Grizzly Bears', '3/3')
  }
  for (const page of [p1, p2]) await page.expectLifeTotal(player2.playerId, 17)
  await p1.screenshot('Tapped searched Forest and combat damage')
  await p2.screenshot('Defender sees the same searched land and damage')
})

for (const accept of [false, true]) {
  test(accept ? 'accepted attack search may fail to find without exposing the library' : 'attack search may be declined', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1: {
        battlefield: [{ name: 'Grizzly Bears' }, { name: source, attachedTo: 'Grizzly Bears' }],
        library: ['Forest', 'Plains', 'Lightning Bolt'],
      },
      player2: { library: ['Island', 'Swamp', 'Mountain'] },
      phase: 'COMBAT', step: 'DECLARE_ATTACKERS', activePlayer: 1,
      player1StopAtSteps: ['POSTCOMBAT_MAIN'],
    })
    const p1 = player1.gamePage
    const p2 = player2.gamePage
    await p1.attackWith('Grizzly Bears')
    await p2.resolveStack(`${source} trigger`)
    if (accept) {
      await p1.answerYes()
      await expect(p2.page.locator(cardByName('Forest'))).toHaveCount(0)
      await expect(p2.page.locator(cardByName('Plains'))).toHaveCount(0)
      await p1.page.getByRole('button', { name: 'Select None', exact: true }).click()
    } else {
      await p1.answerNo()
    }
    await p1.expectNotOnBattlefield('Forest')
    await p2.expectNotOnBattlefield('Forest')
    for (const page of [p1, p2]) await page.expectLifeTotal(player2.playerId, 17)
    await p1.screenshot(accept ? 'Search failed to find and combat continued' : 'Declined search and combat continued')
    await p2.screenshot('Defender sees combat with no searched land')
  })
}
