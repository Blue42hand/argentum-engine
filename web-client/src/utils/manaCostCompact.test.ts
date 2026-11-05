import { describe, expect, it } from 'vitest'
import { applyManaPoolToCost } from './manaCost'

describe('compact generic pool payment preview', () => {
  it('subtracts a large floating pool in one numeric-symbol step', () => {
    expect(applyManaPoolToCost(['1999999998', 'U'], { white: 0, blue: 0, black: 0, red: 0, green: 0, colorless: 1999999997 })).toEqual(['1', 'U'])
  })
  it('applies the same pool across separate generic symbols and leaves colored requirements', () => {
    expect(applyManaPoolToCost(['3', '4', 'U'], { white: 0, blue: 0, black: 0, red: 0, green: 0, colorless: 5 })).toEqual(['2', 'U'])
    expect(applyManaPoolToCost(['0', '4'], { white: 0, blue: 0, black: 0, red: 0, green: 0, colorless: 3 })).toEqual(['1'])
  })
})
