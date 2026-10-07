import { describe, expect, it } from 'vitest'
import { lobbyKindFor } from '../lobby/modeMatrix'
import { practiceRecipe } from './practice'

describe('practiceRecipe', () => {
  it('warms up a Limited search with a random deck against the AI, started at once', () => {
    const recipe = practiceRecipe(null)
    expect(recipe.selection.roster).toBe('SOLO')
    expect(recipe.deck).toEqual({ kind: 'RANDOM' })
    expect(recipe.autoStart).toBe(true)
    expect(lobbyKindFor(recipe.selection)).toBe('QUICK')
  })

  it("warms up a constructed search under that format's legality", () => {
    const recipe = practiceRecipe('PAUPER')
    expect(recipe.selection.roster).toBe('SOLO')
    expect(recipe.settings.deckFormat).toBe('PAUPER')
    expect(lobbyKindFor(recipe.selection)).toBe('QUICK')
  })

  it('falls back to a random deck for commander-shaped formats, which have no 1v1 quick lobby', () => {
    expect(practiceRecipe('COMMANDER').deck).toEqual({ kind: 'RANDOM' })
  })
})
