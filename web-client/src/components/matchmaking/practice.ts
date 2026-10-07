/**
 * "Play the AI while you wait": the warm-up game a searching player can start without leaving the
 * queue. The server keeps a player in a vs-AI lobby or game searching, and when a match is found and
 * accepted it ends the warm-up quietly and seats them in the matched lobby.
 *
 * The warm-up follows the queue: a Limited search warms up with a random deck (nothing to choose,
 * so it starts at once); a constructed search opens an AI lobby under that format's legality, where
 * the player picks the deck they're about to queue with. Commander-shaped formats have no 1v1 quick
 * lobby to warm up in, so they take the random deck too.
 */
import type { DeckFormat } from '@/types'
import type { LobbyRecipe } from '../lobby/lobbyRecipe'
import { defaultOptions, recipeForOptions } from '../lobby/playModes'

const COMMANDER_SHAPED: ReadonlySet<DeckFormat> = new Set<DeckFormat>(['COMMANDER', 'BRAWL', 'STANDARD_BRAWL'])

export function practiceRecipe(format: DeckFormat | null): LobbyRecipe {
  if (format === null || COMMANDER_SHAPED.has(format)) {
    return recipeForOptions({ ...defaultOptions('RANDOM', true), deck: { kind: 'RANDOM' } })
  }
  const base = recipeForOptions(defaultOptions('CONSTRUCTED', true))
  return { ...base, settings: { ...base.settings, deckFormat: format } }
}

/** What the warm-up button promises, so it never starts something the player didn't expect. */
export function practiceCaption(format: DeckFormat | null): string {
  return format === null || COMMANDER_SHAPED.has(format)
    ? 'Random deck · you stay in the queue'
    : 'Pick a deck · you stay in the queue'
}
