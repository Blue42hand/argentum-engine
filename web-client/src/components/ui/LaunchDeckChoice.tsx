/**
 * The launch panel's "Your deck" field.
 *
 * It used to be a native `<select>` of saved deck names — or, with no saved decks, a sentence saying
 * to go and pick one in the lobby. So a first game of Constructed against the AI went: Play, a
 * lobby full of settings, Choose deck, an empty "My Decks", Examples, a tile that opened a textarea,
 * Done, I'm ready. Now the deck is named here, where everything else about the game already is, and
 * an AI game goes from the tile to the table.
 *
 * Collapsed it is one row naming the deck (the panel stays short on a phone); "Change" opens the
 * choices inline, grouped by where the deck comes from: yours, the server's starter decks, a rolled
 * one where the lobby can roll it, or the explicit "choose in the lobby".
 */
import { useId, useState, type ReactNode } from 'react'
import type { UnifiedDeck } from '@/store/useUnifiedDecks'
import { starterDeckSize, type StarterDeck } from '@/store/useStarterDecks'
import type { PanelDeck } from '../lobby/playModes'
import styles from './PlayHub.module.css'

export function LaunchDeckChoice({
  value,
  saved,
  starters,
  canRoll,
  onChange,
}: {
  value: PanelDeck
  /** Your decks, newest first, already narrowed to what this mode accepts. */
  saved: readonly UnifiedDeck[]
  /** Starter decks for this mode, or null while they load. */
  starters: readonly StarterDeck[] | null
  canRoll: boolean
  onChange: (deck: PanelDeck) => void
}) {
  const [open, setOpen] = useState(false)
  const listId = useId()
  const current = describe(value, saved, starters)

  const choose = (deck: PanelDeck) => {
    onChange(deck)
    setOpen(false)
  }

  return (
    <div className={styles.deckChoice}>
      <button
        type="button"
        className={styles.deckChoiceButton}
        aria-expanded={open}
        aria-controls={listId}
        data-testid="launch-deck"
        onClick={() => setOpen((o) => !o)}
      >
        <DeckGlyph kind={value.kind} />
        <span className={styles.deckChoiceText}>
          <span className={styles.deckChoiceName}>{current.name}</span>
          <span className={styles.deckChoiceMeta}>{current.meta}</span>
        </span>
        <span className={styles.setChange}>{open ? 'Done' : 'Change'}</span>
      </button>

      {open && (
        <div id={listId} className={styles.deckList} role="listbox" aria-label="Your deck">
          {saved.length > 0 && (
            <DeckGroup label="Your decks">
              {saved.map((d) => (
                <DeckOption
                  key={d.id}
                  kind="SAVED"
                  name={d.name}
                  meta={`${deckSize(d)} cards`}
                  selected={value.kind === 'SAVED' && value.name === d.name}
                  onSelect={() => choose({ kind: 'SAVED', name: d.name })}
                />
              ))}
            </DeckGroup>
          )}
          <DeckGroup label="Starter decks">
            {starters === null && <p className={styles.deckListNote}>Loading…</p>}
            {starters?.length === 0 && <p className={styles.deckListNote}>None for this format.</p>}
            {starters?.map((d) => (
              <DeckOption
                key={d.id}
                kind="EXAMPLE"
                name={d.name}
                meta={d.description}
                selected={value.kind === 'EXAMPLE' && value.name === d.name}
                onSelect={() => choose({ kind: 'EXAMPLE', name: d.name })}
              />
            ))}
          </DeckGroup>
          <DeckGroup label="Or">
            {canRoll && (
              <DeckOption
                kind="RANDOM"
                name="Random deck"
                meta="The server builds one for you when the game starts"
                selected={value.kind === 'RANDOM'}
                onSelect={() => choose({ kind: 'RANDOM' })}
              />
            )}
            <DeckOption
              kind="LOBBY"
              name="Choose in the lobby"
              meta="Paste a list, or decide once you're there"
              selected={value.kind === 'LOBBY'}
              onSelect={() => choose({ kind: 'LOBBY' })}
            />
          </DeckGroup>
        </div>
      )}
    </div>
  )
}

/** The collapsed row's two lines for a choice. */
function describe(
  deck: PanelDeck,
  saved: readonly UnifiedDeck[],
  starters: readonly StarterDeck[] | null,
): { name: string; meta: string } {
  switch (deck.kind) {
    case 'SAVED': {
      const match = saved.find((d) => d.name === deck.name)
      return { name: deck.name, meta: match ? `Your deck · ${deckSize(match)} cards` : 'Your deck' }
    }
    case 'EXAMPLE': {
      const match = starters?.find((d) => d.name === deck.name)
      return { name: deck.name, meta: match ? `Starter deck · ${starterDeckSize(match)} cards` : 'Starter deck' }
    }
    case 'RANDOM':
      return { name: 'Random deck', meta: 'Built for you when the game starts' }
    case 'LOBBY':
      return { name: 'Choose in the lobby', meta: 'Pick or paste a deck before you ready up' }
  }
}

function deckSize(deck: UnifiedDeck): number {
  return Object.values(deck.cards).reduce((a, b) => a + b, 0) + (deck.commander ? 1 : 0)
}

function DeckGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.deckGroup} role="group" aria-label={label}>
      <span className={styles.deckGroupLabel}>{label}</span>
      {children}
    </div>
  )
}

function DeckOption({
  kind,
  name,
  meta,
  selected,
  onSelect,
}: {
  kind: PanelDeck['kind']
  name: string
  meta: string
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={selected}
      className={styles.deckOption}
      onClick={onSelect}
    >
      <DeckGlyph kind={kind} />
      <span className={styles.deckChoiceText}>
        <span className={styles.deckChoiceName}>{name}</span>
        <span className={styles.deckChoiceMeta}>{meta}</span>
      </span>
      {selected && (
        <svg className={styles.deckOptionCheck} viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M5 12.5l4.5 4.5L19 7" />
        </svg>
      )}
    </button>
  )
}

const GLYPHS: Record<PanelDeck['kind'], string> = {
  SAVED: 'M8 4h9a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M4 7v11a3 3 0 0 0 3 3',
  EXAMPLE: 'M12 3l2.4 5 5.6.8-4 3.9.9 5.6-4.9-2.6-4.9 2.6.9-5.6-4-3.9 5.6-.8z',
  RANDOM: 'M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z M9 9h.01 M15 9h.01 M12 12h.01 M9 15h.01 M15 15h.01',
  LOBBY: 'M4 12h12 M12 6l6 6-6 6',
}

function DeckGlyph({ kind }: { kind: PanelDeck['kind'] }) {
  return (
    <span className={styles.deckGlyph} data-kind={kind} aria-hidden>
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d={GLYPHS[kind]} />
      </svg>
    </span>
  )
}
