import { test, expect } from '../../fixtures/scenarioFixture'
import { createScenario } from '../../helpers/scenarioApi'

// Both seats retain a real native mana action, so the responder's window holds the
// scenario still while the first seat inspects and cancels its temporary yield.
const position = {
  player1Name: 'Priority Tester',
  player2Name: 'Response Tester',
  player1: { battlefield: [{ name: 'Island' }], library: ['Island', 'Island'] },
  player2: { battlefield: [{ name: 'Mountain' }], library: ['Mountain', 'Mountain'] },
  phase: 'PRECOMBAT_MAIN' as const,
  step: 'PRECOMBAT_MAIN',
  activePlayer: 1,
}

test.describe('Native human priority controls', () => {
  test('defaults to Forced Only and acknowledges temporary yield activation and cancellation', async ({ createGame }, testInfo) => {
    const { player1, player2 } = await createGame(position)
    const page = player1.page
    await expect(page.getByRole('button', { name: 'Forced Only', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Pass until end of turn', exact: true })).toBeEnabled()
    const nextTurn = page.getByRole('button', { name: 'Pass until your next turn', exact: true })
    await expect(nextTurn).toBeEnabled()
    await testInfo.attach('priority-controls-default', { body: await page.screenshot(), contentType: 'image/png' })

    await nextTurn.click()
    await expect(page.getByText('Passing until your next turn', { exact: true })).toBeVisible()
    // The other seat can still act; the first seat's yield never dismisses that window.
    await expect(player2.page.getByRole('button', { name: 'Pass until end of turn', exact: true })).toBeEnabled()
    await testInfo.attach('priority-controls-active', { body: await page.screenshot(), contentType: 'image/png' })
    await page.getByRole('button', { name: 'Cancel', exact: true }).click()
    await expect(page.getByText('Passing until your next turn', { exact: true })).toHaveCount(0)
    await expect(nextTurn).toBeVisible()
    await expect(player2.page.getByRole('button', { name: 'Pass until end of turn', exact: true })).toBeEnabled()
  })

  test('keeps temporary passing disabled in Full Control', async ({ createGame }) => {
    const { player1 } = await createGame(position)
    const page = player1.page
    await page.getByRole('button', { name: 'Forced Only', exact: true }).click()
    await page.getByRole('button', { name: 'Stops', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Full Control', exact: true })).toBeVisible()
    for (const name of ['Pass until end of turn', 'Pass until your next turn']) {
      const button = page.getByRole('button', { name, exact: true })
      await expect(button).toBeDisabled()
      await expect(button).toHaveAttribute('title', 'Switch from Full Control to another priority mode to use temporary passing.')
    }
  })

  test('does not carry active yield or send a stale intent into a replacement game', async ({ createGame }) => {
    const { player1 } = await createGame(position)
    const page = player1.page
    await page.getByRole('button', { name: 'Pass until your next turn', exact: true }).click()
    await expect(page.getByText('Passing until your next turn', { exact: true })).toBeVisible()

    const replacement = await createScenario(position)
    const sent: string[] = []
    page.on('websocket', socket => socket.on('framesent', frame => sent.push(String(frame.payload))))
    // Re-enter with a fresh native session using the same browser storage. Temporary
    // yields are session state, unlike the intentionally persisted phase stops.
    await page.evaluate(({ token, name }) => {
      localStorage.setItem('argentum-token', token)
      localStorage.setItem('argentum-player-name', name)
    }, replacement.player1)
    await page.reload()
    await expect(page.getByRole('button', { name: 'Forced Only', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Pass until your next turn', exact: true })).toBeEnabled()
    await expect(page.getByText('Passing until your next turn', { exact: true })).toHaveCount(0)
    expect(sent.some(frame => JSON.parse(frame).type === 'setPriorityYield')).toBe(false)
  })
})
