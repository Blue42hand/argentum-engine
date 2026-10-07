import { describe, expect, it } from 'vitest'
import { activeQueues, formatWait, queueLabel, searchingIn } from './queues'

describe('matchmaking queue helpers', () => {
  const counts = [
    { format: null, ranked: false, searching: 1 },
    { format: 'PAUPER' as const, ranked: true, searching: 3 },
    { format: 'PAUPER' as const, ranked: false, searching: 0 },
  ]

  it('reads one queue, keeping casual and ranked apart', () => {
    expect(searchingIn(counts, 'PAUPER', true)).toBe(3)
    expect(searchingIn(counts, 'PAUPER', false)).toBe(0)
    expect(searchingIn(counts, null, false)).toBe(1)
    expect(searchingIn(null, null, false)).toBe(0)
  })

  it('lists only queues with someone in them, busiest first', () => {
    expect(activeQueues(counts).map((c) => c.searching)).toEqual([3, 1])
  })

  it('names a queue', () => {
    expect(queueLabel(null, false)).toBe('Casual Limited')
    expect(queueLabel('STANDARD_BRAWL', true)).toBe('Ranked Standard Brawl')
  })

  it('formats a wait as m:ss', () => {
    expect(formatWait(7_400)).toBe('0:07')
    expect(formatWait(161_000)).toBe('2:41')
    expect(formatWait(-5)).toBe('0:00')
  })
})
