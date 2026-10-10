import { test, expect } from '../../fixtures/scenarioFixture'
import { BATTLEFIELD, cardByName } from '../../helpers/selectors'
import type { Page } from '@playwright/test'

const source = 'Reconnaissance'
const action = 'Remove attacking creature from combat and untap it'
const attacker = {
  battlefield: [{ name: source }, { name: 'Grizzly Bears', summoningSickness: false }],
  library: ['Forest', 'Plains'],
}
const defender = {
  hand: ['Stifle'], battlefield: [{ name: 'Island' }], library: ['Mountain', 'Swamp'],
}

// GamePage.expectUntapped only checks visibility; assert the actual card rotation here.
async function expectUntapped(page: Page) {
  const card = page.locator(BATTLEFIELD).locator(cardByName('Grizzly Bears')).first()
  await expect(card).toBeVisible()
  await expect.poll(async () => card.evaluate(node => {
    const battlefield = node.closest('[data-zone="player-battlefield"], [data-zone="opponent-battlefield"]')
    let current: Element | null = node
    while (current && current !== battlefield) {
      if (getComputedStyle(current).transform !== 'none') return false
      current = current.parentElement
    }
    return true
  })).toBe(true)
}

test('withdraw a blocked attacker before damage using the free battlefield-targeted ability', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: attacker,
    player2: { ...defender, battlefield: [...defender.battlefield, { name: 'Hill Giant' }] },
    phase: 'COMBAT', step: 'DECLARE_ATTACKERS', activePlayer: 1,
    player1StopAtSteps: ['DECLARE_BLOCKERS', 'END_COMBAT'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.attackWith('Grizzly Bears')
  await p2.declareBlocker('Hill Giant', 'Grizzly Bears')
  await p2.confirmBlockers()
  await p1.expectTapped('Grizzly Bears')
  await p1.clickCard(source)
  await p1.selectAction(action)
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack(`${source} ability`)
  await expectUntapped(player1.page)
  await expectUntapped(player2.page)
  await p1.pass()
  for (const p of [p1, p2]) {
    await p.expectOnBattlefield('Grizzly Bears')
    await p.expectOnBattlefield('Hill Giant')
    await p.expectLifeTotal(player1.playerId, 20)
    await p.expectLifeTotal(player2.playerId, 20)
  }
  await p1.screenshot('Attacker removed before damage; both creatures survive')
  await p2.screenshot('Defender sees untapped attacker and surviving blocker')
})

test('untap at end of combat after the unblocked attacker has dealt damage', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1: attacker, player2: defender,
    phase: 'COMBAT', step: 'DECLARE_ATTACKERS', activePlayer: 1,
    player1StopAtSteps: ['DECLARE_BLOCKERS', 'END_COMBAT'],
  })
  const p1 = player1.gamePage
  const p2 = player2.gamePage
  await p1.attackWith('Grizzly Bears')
  await p1.expectLifeTotal(player2.playerId, 20)
  await p1.pass()
  await p1.expectLifeTotal(player2.playerId, 18)
  await p2.expectLifeTotal(player2.playerId, 18)
  await p1.expectTapped('Grizzly Bears')
  await p1.screenshot('Combat damage dealt before the end-of-combat activation')
  await p1.clickCard(source)
  await p1.selectAction(action)
  await p1.selectTarget('Grizzly Bears')
  await p1.confirmTargets()
  await p2.resolveStack(`${source} ability`)
  await expectUntapped(player1.page)
  await expectUntapped(player2.page)
  await p1.expectLifeTotal(player2.playerId, 18)
  await p2.expectLifeTotal(player2.playerId, 18)
  await p1.screenshot('Attacker untapped after damage; defender remains at 18')
  await p2.screenshot('Defender sees post-damage untap and unchanged life total')
})
