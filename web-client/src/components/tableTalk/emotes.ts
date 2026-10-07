/**
 * How each preset emote reads on screen. The server only knows the names; the words and icons are
 * presentation, and the order here is the picker's order — openers first, the closer last.
 */
import type { Emote } from '@/types'

export interface EmoteInfo {
  readonly emote: Emote
  readonly label: string
  readonly icon: string
}

export const EMOTES: readonly EmoteInfo[] = [
  { emote: 'HELLO', label: 'Hello!', icon: '👋' },
  { emote: 'GOOD_LUCK', label: 'Good luck!', icon: '🍀' },
  { emote: 'NICE_PLAY', label: 'Nice play!', icon: '👏' },
  { emote: 'THINKING', label: 'Thinking…', icon: '🤔' },
  { emote: 'OOPS', label: 'Oops!', icon: '😅' },
  { emote: 'THANKS', label: 'Thanks!', icon: '🙏' },
  { emote: 'SORRY', label: 'Sorry!', icon: '😬' },
  { emote: 'GOOD_GAME', label: 'Good game', icon: '🤝' },
]

export function emoteInfo(emote: Emote): EmoteInfo {
  return EMOTES.find((e) => e.emote === emote) ?? { emote, label: emote, icon: '💬' }
}
