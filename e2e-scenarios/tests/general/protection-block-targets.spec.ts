import { test, expect } from '../../fixtures/scenarioFixture'

/** The browser must use engine-offered blocker pairs, including protection. */
test.describe('Protection block targets', () => {
  test('blue Drake cannot block Piledriver but can block another attacker', async ({ createGame }) => {
    const { player1, player2 } = await createGame({
      player1Name: 'Attacker',
      player2Name: 'Defender',
      player1: {
        battlefield: [
          { name: 'Goblin Piledriver', tapped: false, summoningSickness: false },
          { name: 'Grizzly Bears', tapped: false, summoningSickness: false },
        ],
        library: ['Mountain'],
      },
      player2: {
        battlefield: [{ name: 'Azure Drake', tapped: false }],
        library: ['Island'],
      },
      phase: 'PRECOMBAT_MAIN',
      activePlayer: 1,
    })

    const p1 = player1.gamePage
    const p2 = player2.gamePage
    await p1.pass()
    await p1.attackAll()
    await p2.resolveStack('Goblin Piledriver trigger')

    await p2.declareBlocker('Azure Drake', 'Goblin Piledriver')
    await expect(player2.page.getByRole('button', { name: 'No Blocks' })).toBeVisible()
    await expect(player2.page.getByRole('button', { name: 'Confirm Blocks' })).toHaveCount(0)

    await p2.declareBlocker('Azure Drake', 'Grizzly Bears')
    await expect(player2.page.getByRole('button', { name: 'Confirm Blocks' })).toBeVisible()
    await p2.confirmBlockers()

    // The legal block applies on both seats: Bear dies to the Drake, while the
    // unblocked Piledriver deals one damage to the defending player.
    await p1.expectLifeTotal(player2.playerId, 19)
    await p2.expectLifeTotal(player2.playerId, 19)
    await p1.expectNotOnBattlefield('Grizzly Bears')
    await p2.expectOnBattlefield('Azure Drake')
  })
})
