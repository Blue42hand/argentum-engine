/**
 * Presentation helpers for the matchmaking queues. The server owns the queues and pairs players;
 * this module only names the choices and reads the server's searching counts.
 */
import type { DeckFormat, MatchmakingQueueCount } from '@/types'

export interface QueueFormatOption {
  /** Null is Limited: a random sealed pool each, built by the server. */
  readonly format: DeckFormat | null
  readonly label: string
}

/** Every queue a player can search in. Limited first: it needs no deck, so it's the easiest yes. */
export const QUEUE_FORMATS: readonly QueueFormatOption[] = [
  { format: null, label: 'Limited' },
  { format: 'STANDARD', label: 'Standard' },
  { format: 'PIONEER', label: 'Pioneer' },
  { format: 'MODERN', label: 'Modern' },
  { format: 'PAUPER', label: 'Pauper' },
  { format: 'LEGACY', label: 'Legacy' },
  { format: 'VINTAGE', label: 'Vintage' },
  { format: 'PREMODERN', label: 'Premodern' },
  { format: 'COMMANDER', label: 'Commander' },
  { format: 'BRAWL', label: 'Brawl' },
  { format: 'STANDARD_BRAWL', label: 'Standard Brawl' },
]

export function queueFormatLabel(format: DeckFormat | null | undefined): string {
  return QUEUE_FORMATS.find((o) => o.format === (format ?? null))?.label ?? String(format)
}

/** "Ranked Pauper", "Casual Limited". */
export function queueLabel(format: DeckFormat | null | undefined, ranked: boolean | undefined): string {
  return `${ranked ? 'Ranked' : 'Casual'} ${queueFormatLabel(format)}`
}

/** Players searching in one queue. */
export function searchingIn(
  counts: readonly MatchmakingQueueCount[] | null,
  format: DeckFormat | null,
  ranked: boolean,
): number {
  return counts?.find((c) => (c.format ?? null) === format && c.ranked === ranked)?.searching ?? 0
}

/** Queues with someone in them, busiest first — where a game is most likely right now. */
export function activeQueues(counts: readonly MatchmakingQueueCount[] | null): readonly MatchmakingQueueCount[] {
  return [...(counts ?? [])].filter((c) => c.searching > 0).sort((a, b) => b.searching - a.searching)
}

/** "0:07", "2:41" — time spent searching. */
export function formatWait(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
