import { test, expect } from '../../fixtures/scenarioFixture'

const source = 'Relic of Legends'
const tapSource = 'Tap: Add one mana of any color'
const tapCreature = 'Tap a legendary creature: Add one mana of any color'

test('choose mana from Relic, then tap a summoning-sick legendary creature while Relic is tapped', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [
        { name: source }, { name: 'Krenko, Mob Boss', summoningSickness: true },
        { name: 'Talrand, Sky Summoner', tapped: true }, { name: 'Grizzly Bears' },
      ],
      library: ['Forest', 'Plains'],
    },
    player2: { battlefield: [{ name: 'Lathril, Blade of the Elves' }], library: ['Mountain', 'Swamp'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(tapSource)
  await p1.selectManaColor('Blue')
  await p1.expectTapped(source)
  await p2.expectTapped(source)
  await expect(p1.page.getByText('Mana:', { exact: true })).toBeVisible()
  await expect(p2.page.getByText('Mana:', { exact: true })).toBeVisible()
  await p1.screenshot('Relic taps and produces chosen blue mana immediately')
  await p2.screenshot('Opponent sees tapped Relic and floating mana')

  await p1.clickCard(source)
  await p1.selectAction(tapCreature)
  await p1.screenshot('Select untapped legendary creature as cost')
  await p2.screenshot('Opponent sees battlefield during private cost selection')
  await p1.selectTarget('Krenko, Mob Boss')
  await p1.confirmTargets()
  await p1.selectManaColor('Red')
  await p1.expectTapped('Krenko, Mob Boss')
  await p2.expectTapped('Krenko, Mob Boss')
  await expect(p1.page.getByText('(2)', { exact: true })).toBeVisible()
  await expect(p2.page.getByText('(2)', { exact: true })).toBeVisible()
  await p1.screenshot('Tapped Relic makes a second mana using summoning-sick Krenko')
  await p2.screenshot('Opponent sees both paid tap costs and two floating mana')
})
