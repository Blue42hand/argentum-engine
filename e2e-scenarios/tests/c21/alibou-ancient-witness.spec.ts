import { test, expect } from '../../fixtures/scenarioFixture'
import { cardByName } from '../../helpers/selectors'

const source = 'Alibou, Ancient Witness'
const scryCards = ['Ancestral Recall', 'Dark Ritual', 'Lightning Bolt']

test('Alibou grants haste to other artifacts and batches visible damage with private scry choices', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source, 'Forest'],
      battlefield: [
        { name: 'Memnite', summoningSickness: true }, { name: 'Ornithopter', summoningSickness: true },
        { name: 'Sol Ring', tapped: true },
        ...Array.from({ length: 3 }, () => ({ name: 'Plains' })),
        ...Array.from({ length: 2 }, () => ({ name: 'Mountain' })),
      ],
      library: [...scryCards, 'Swamp'],
    },
    player2: { hand: ['Island'], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1, player1StopAtSteps: ['DECLARE_ATTACKERS'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await p1.expectOnBattlefield(source)
  await p1.pass()
  await p1.attackAll()
  await p1.selectPlayer(player2.playerId)
  await p2.resolveStack(`${source} trigger`)
  await p1.expectLifeTotal(player2.playerId, 17)
  await p2.expectLifeTotal(player2.playerId, 17)
  const scrySelection = player1.page.locator('div')
    .filter({ has: player1.page.getByRole('heading', { name: 'Choose up to 3 cards', exact: true }) })
    .filter({ has: player1.page.getByRole('button', { name: /Select None|Confirm Selection/ }) })
    .last()
  for (const name of scryCards) {
    await expect(scrySelection.locator(cardByName(name))).toHaveCount(1)
    await expect(player2.page.locator(cardByName(name))).toHaveCount(0)
  }
  await expect(player2.page.locator('[data-zone="opponent-hand"] img[alt="Forest"]')).toHaveCount(0)
  await p1.screenshot('Three private scry cards after one attack trigger')
  await p2.screenshot('Public damage without private scry identities')
  for (const name of scryCards) await scrySelection.locator(cardByName(name)).click()
  await player1.page.getByRole('button', { name: 'Confirm Selection', exact: true }).click()
  await expect(player1.page.getByRole('button', { name: 'Confirm Order', exact: true })).toHaveCount(0)
  await expect(player1.page.getByRole('button', { name: 'Confirm Selection', exact: true })).toHaveCount(0)
  await expect(player1.page.getByRole('heading', { name: 'Choose up to 3 cards', exact: true })).toHaveCount(0)
})

test('Alibou does not scry after its sole damage target leaves in response', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      battlefield: [{ name: source, summoningSickness: true }, { name: 'Ornithopter', summoningSickness: true }, { name: 'Sol Ring', tapped: true }],
      library: scryCards,
    },
    player2: {
      hand: ['Unsummon', 'Island'], battlefield: [{ name: 'Grizzly Bears' }, { name: 'Island' }],
      library: ['Forest', 'Mountain'],
    },
    phase: 'COMBAT', step: 'DECLARE_ATTACKERS', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.attackAll()
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await expect(player2.page.getByText(`${source} trigger`, { exact: true })).toBeVisible()
  await p2.clickCard('Unsummon')
  await p2.selectAction('Cast Unsummon')
  await p2.selectTarget('Grizzly Bears')
  await p2.confirmTargets()
  await p1.resolveStack('Unsummon')
  await p1.expectNotOnBattlefield('Grizzly Bears')
  await p2.expectNotOnBattlefield('Grizzly Bears')
  await p2.resolveStack(`${source} trigger`)
  await expect(player1.page.getByText(`${source} trigger`, { exact: true })).toHaveCount(0)
  await expect(player2.page.getByText(`${source} trigger`, { exact: true })).toHaveCount(0)
  await p1.expectLifeTotal(player2.playerId, 20)
  for (const name of scryCards) await expect(player1.page.locator(cardByName(name))).toHaveCount(0)
  await p1.screenshot('Illegal target prevents scry')
})
