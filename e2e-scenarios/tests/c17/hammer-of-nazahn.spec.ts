import { test } from '../../fixtures/scenarioFixture'
import { GamePage } from '../../helpers/gamePage'

const source = 'Hammer of Nazahn'
const board = Array.from({ length: 10 }, () => ({ name: 'Plains' }))

async function acceptAttachment(p1: GamePage, p2: GamePage, entering: string, host: string) {
  await p2.resolveStack(entering)
  await p1.answerYes()
  await p1.page.getByText('Choose Target', { exact: true }).waitFor({ state: 'visible', timeout: 10_000 })
  await p1.selectTarget(host)
  await p1.confirmTargets()
  await p2.resolveStack(`${source} trigger`)
}

test('Hammer attaches itself, attaches the other entering Equipment, and can move with paid equip on both seats', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source, 'Bonesplitter', 'Forest'],
      battlefield: [...board, { name: 'Grizzly Bears' }, { name: 'Centaur Courser' }],
      library: ['Island', 'Mountain'],
    },
    player2: { hand: ['Unsummon'], battlefield: [{ name: 'Island' }], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.clickCard(source)
  await p1.selectAction(`Cast ${source}`)
  await acceptAttachment(p1, p2, source, 'Grizzly Bears')
  await p1.expectStats('Grizzly Bears', '4/2')
  await p2.expectStats('Grizzly Bears', '4/2')
  await p1.screenshot('Hammer visibly attached with its bonus')
  await p2.screenshot('Opponent sees the same Hammer attachment')
  await p1.clickCard('Bonesplitter')
  await p1.selectAction('Cast Bonesplitter')
  await acceptAttachment(p1, p2, 'Bonesplitter', 'Centaur Courser')
  await p1.expectStats('Grizzly Bears', '4/2')
  await p1.expectStats('Centaur Courser', '5/3')
  await p2.expectStats('Centaur Courser', '5/3')
  // Equipment peeks above its host; click that visible strip rather than the covered center.
  await p1.page.locator(`img[alt="${source}"]`).first().click({ position: { x: 20, y: 5 } })
  await p1.selectAction('Equip')
  await p1.selectTarget('Centaur Courser')
  await p1.confirmTargets()
  await p2.resolveStack(`${source} ability`)
  for (const page of [p1, p2]) {
    await page.expectStats('Grizzly Bears', '2/2')
    await page.expectStats('Centaur Courser', '7/3')
  }
  await p1.screenshot('Paid equip moved Hammer to the other equipped creature')
  await p2.screenshot('Opponent sees the same moved attachment and stats')
})

test('declining both self-entry and other-Equipment attachment leaves creatures unchanged', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: {
      hand: [source, 'Bonesplitter', 'Forest'],
      battlefield: [...board, { name: 'Grizzly Bears' }, { name: 'Centaur Courser' }],
      library: ['Island', 'Mountain'],
    },
    player2: { hand: ['Unsummon'], battlefield: [{ name: 'Island' }], library: ['Forest', 'Mountain'] },
    phase: 'PRECOMBAT_MAIN', activePlayer: 1,
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  for (const entering of [source, 'Bonesplitter']) {
    await p1.clickCard(entering)
    await p1.selectAction(`Cast ${entering}`)
    await p2.resolveStack(entering)
    await p1.answerNo()
    await p1.expectOnBattlefield(entering)
    await p2.expectOnBattlefield(entering)
  }
  for (const page of [p1, p2]) {
    await page.expectStats('Grizzly Bears', '2/2')
    await page.expectStats('Centaur Courser', '3/3')
  }
  await p1.screenshot('Both optional attachments declined')
  await p2.screenshot('Opponent sees unattached Equipment and unchanged creatures')
})
