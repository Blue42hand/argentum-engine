/**
 * How each preset emote reads on screen. The server only knows the names; the words and icons are
 * presentation. They're the things people actually say across a Magic table — the hello and the
 * handshake, the groan at a perfect topdeck, the honest "misclick" and the eternal "where are my
 * lands?" — grouped the way the picker shows them.
 */
import type { Emote } from '@/types'

/** Tints the bubble: warm for greetings, bright for reactions, cool for table talk. */
export type EmoteMood = 'greet' | 'react' | 'talk'

export interface EmoteInfo {
  readonly emote: Emote
  readonly label: string
  readonly icon: string
  readonly mood: EmoteMood
}

export interface EmoteGroup {
  readonly title: string
  readonly mood: EmoteMood
  readonly emotes: readonly EmoteInfo[]
}

const greet = (emote: Emote, icon: string, label: string): EmoteInfo => ({ emote, icon, label, mood: 'greet' })
const react = (emote: Emote, icon: string, label: string): EmoteInfo => ({ emote, icon, label, mood: 'react' })
const talk = (emote: Emote, icon: string, label: string): EmoteInfo => ({ emote, icon, label, mood: 'talk' })

export const EMOTE_GROUPS: readonly EmoteGroup[] = [
  {
    title: 'Hello & goodbye',
    mood: 'greet',
    emotes: [
      greet('HELLO', '👋', 'Hello!'),
      greet('GOOD_LUCK', '🍀', 'Good luck, have fun!'),
      greet('THANKS', '🙏', 'Thanks!'),
      greet('GOOD_GAME', '🤝', 'Good game!'),
    ],
  },
  {
    title: 'Reactions',
    mood: 'react',
    emotes: [
      react('WELL_PLAYED', '👏', 'Well played.'),
      react('NICE_TOPDECK', '🎯', 'What a topdeck!'),
      react('NO_WAY', '😱', 'Wait, what?!'),
      react('OUCH', '💥', 'Ouch.'),
    ],
  },
  {
    title: 'Table talk',
    mood: 'talk',
    emotes: [
      talk('THINKING', '🤔', 'Hmm, let me think…'),
      talk('MISCLICK', '🙈', 'Misclick, sorry!'),
      talk('MANA_SCREW', '🏜️', 'Where are my lands?'),
      talk('MANA_FLOOD', '🌊', 'All lands, no spells…'),
    ],
  },
]

export const EMOTES: readonly EmoteInfo[] = EMOTE_GROUPS.flatMap((g) => g.emotes)

export function emoteInfo(emote: Emote): EmoteInfo {
  return EMOTES.find((e) => e.emote === emote) ?? { emote, label: emote, icon: '💬', mood: 'talk' }
}
