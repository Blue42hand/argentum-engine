import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseDeckText } from '../deckPasteText'
import { commanderSelectionForSave } from '../deckPickerSave'
import { commandersOf, mergeCommanderIntoCards, useDeckLibrary } from '@/store/deckLibrary'
import { savedDeckToShared } from '@/store/useSaveDeck'

const first = 'Tymna the Weaver'
const second = 'Thrasios, Triton Hero'

afterEach(() => {
  vi.unstubAllGlobals()
  useDeckLibrary.setState({ decks: [], hydrated: false })
})

describe('DeckPicker commander save boundary', () => {
  it('preserves a pasted pair through saving, library reload, and the cloud payload', () => {
    const memory = new Map<string, string>()
    vi.stubGlobal('window', { localStorage: {
      getItem: (key: string) => memory.get(key) ?? null,
      setItem: (key: string, value: string) => { memory.set(key, value) },
    } })
    const pasted = parseDeckText(`Commander\n1 ${first}\n1 ${second}\n\nDeck\n98 Forest`)
    const input = {
      name: 'Partner regression', format: 'COMMANDER',
      ...commanderSelectionForSave(pasted.cards, pasted.commanders[0] ?? null, pasted.commanders[1] ?? null),
    }
    const saved = useDeckLibrary.getState().saveDeck(input)
    expect(saved.cards).toEqual({ Forest: 98 })
    expect(commandersOf(saved)).toEqual([first, second])
    useDeckLibrary.setState({ decks: [], hydrated: false })
    useDeckLibrary.getState().hydrate()
    const reloaded = useDeckLibrary.getState().getDeck(saved.id)!
    expect(commandersOf(reloaded)).toEqual([first, second])
    expect(reloaded.cards).toEqual({ Forest: 98 })
    expect(mergeCommanderIntoCards(reloaded.cards, ...commandersOf(reloaded)))
      .toEqual({ Forest: 98, [first]: 1, [second]: 1 })
    expect(savedDeckToShared(input)).toMatchObject({
      cards: { Forest: 98 }, commander: first, secondCommander: second,
    })
  })

  it('keeps one commander and a noncommander deck unchanged', () => {
    expect(commanderSelectionForSave({ Forest: 99, [first]: 1 }, first, null))
      .toEqual({ cards: { Forest: 99 }, commander: first })
    expect(commanderSelectionForSave({ Forest: 60 }, null, null))
      .toEqual({ cards: { Forest: 60 } })
  })
})
