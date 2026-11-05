import { stripCommanderFromCards, type SavedDeck } from '@/store/deckLibrary'

/** Keep the saved library and both command-zone designations consistent. */
export function commanderSelectionForSave(
  cards: Record<string, number>,
  commander: string | null,
  secondCommander: string | null,
): Pick<SavedDeck, 'cards' | 'commander' | 'secondCommander'> {
  return {
    cards: stripCommanderFromCards(cards, commander, commander ? secondCommander : null),
    ...(commander ? { commander } : {}),
    ...(commander && secondCommander ? { secondCommander } : {}),
  }
}
