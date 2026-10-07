/**
 * "Find an opponent" — the home hub's way to play someone you don't know. One quiet line: a casual /
 * ranked choice, a format, and a Search button; while searching, the same line becomes the status.
 * The server pairs you with another searcher and {@link MatchFoundDialog} asks both of you to accept.
 * The panel only captures intent and mirrors the server's queue state.
 */
import { useEffect, useState } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import { useAuthStore } from '@/store/authStore'
import type { DeckFormat, MatchmakingQueueCount } from '@/types'
import styles from './Matchmaking.module.css'
import {
  QUEUE_FORMATS,
  activeQueues,
  formatWait,
  queueLabel,
  searchingIn,
} from './queues'

const STORAGE_KEY = 'argentum-matchmaking-choice'

interface QueueChoice {
  format: DeckFormat | null
  ranked: boolean
}

function loadChoice(): QueueChoice {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<QueueChoice>
      const format = QUEUE_FORMATS.some((o) => o.format === parsed.format) ? parsed.format ?? null : null
      return { format, ranked: parsed.ranked === true }
    }
  } catch { /* storage unavailable — fall back to the default */ }
  return { format: null, ranked: false }
}

function saveChoice(choice: QueueChoice) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(choice)) } catch { /* not essential */ }
}

export function FindOpponentPanel({ onSignIn }: { onSignIn: () => void }) {
  const status = useGameStore((s) => s.matchmaking)
  const counts = useGameStore((s) => s.matchmakingQueues)
  const joinMatchmaking = useGameStore((s) => s.joinMatchmaking)
  const leaveMatchmaking = useGameStore((s) => s.leaveMatchmaking)
  const dismissNotice = useGameStore((s) => s.dismissMatchmakingNotice)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const signedIn = useAuthStore((s) => s.status === 'authenticated')
  const [choice, setChoice] = useState<QueueChoice>(loadChoice)

  // First paint; after this the server pushes every change.
  useEffect(() => {
    if (counts !== null) return
    let cancelled = false
    fetch('/api/matchmaking/queues')
      .then((res) => (res.ok ? res.json() as Promise<MatchmakingQueueCount[]> : null))
      .then((data) => { if (!cancelled && data) useGameStore.setState({ matchmakingQueues: data }) })
      .catch(() => { /* the socket push fills it in */ })
    return () => { cancelled = true }
  }, [counts])

  const rankedAvailable = accountsEnabled && signedIn
  const effective: QueueChoice = { format: choice.format, ranked: choice.ranked && rankedAvailable }
  const choose = (next: QueueChoice) => {
    setChoice(next)
    saveChoice(next)
  }

  const searching = status?.searching === true
  // Somewhere else a game is waiting: worth one line, never a list.
  const elsewhere = activeQueues(counts).find((c) =>
    !((c.format ?? null) === effective.format && c.ranked === effective.ranked) && (!c.ranked || rankedAvailable),
  )

  return (
    <section className={styles.panel} aria-labelledby="find-opponent-title" data-testid="find-opponent">
      <span id="find-opponent-title" className={styles.title}>Find an opponent</span>

      {searching ? (
        <SearchingRow
          label={queueLabel(status.format, status.ranked)}
          since={status.searchingSince ?? null}
          others={Math.max(0, searchingIn(counts, status.format ?? null, status.ranked ?? false) - 1)}
          onCancel={leaveMatchmaking}
        />
      ) : (
        <div className={styles.controls}>
          {accountsEnabled && (
            <select
              className={`${styles.pick} ${styles.pickMode}`}
              aria-label="Casual or ranked"
              value={effective.ranked ? 'ranked' : 'casual'}
              onChange={(e) => {
                if (e.target.value === 'signin') { onSignIn(); return }
                choose({ ...choice, ranked: e.target.value === 'ranked' })
              }}
            >
              <option value="casual">Casual</option>
              {rankedAvailable
                ? <option value="ranked">Ranked</option>
                : <option value="signin">Ranked — sign in</option>}
            </select>
          )}
          <select
            className={`${styles.pick} ${styles.pickFormat}`}
            aria-label="Format"
            value={effective.format ?? ''}
            onChange={(e) => choose({ ...choice, format: (e.target.value || null) as DeckFormat | null })}
          >
            {QUEUE_FORMATS.map((o) => {
              const n = searchingIn(counts, o.format, effective.ranked)
              return (
                <option key={o.label} value={o.format ?? ''}>
                  {o.label}{n > 0 ? ` · ${n} waiting` : ''}
                </option>
              )
            })}
          </select>
          <button
            type="button"
            className={styles.search}
            data-testid="find-opponent-search"
            onClick={() => joinMatchmaking(effective.format, effective.ranked)}
          >
            Search
          </button>
        </div>
      )}

      {status?.notice ? (
        <p className={styles.footnote} role="status">
          {status.notice}
          <button type="button" className={styles.footnoteDismiss} aria-label="Dismiss" onClick={dismissNotice}>×</button>
        </p>
      ) : !searching && elsewhere ? (
        <p className={styles.footnote}>
          {elsewhere.searching} waiting in{' '}
          <button
            type="button"
            className={styles.footnoteLink}
            onClick={() => {
              const next = { format: elsewhere.format ?? null, ranked: elsewhere.ranked }
              choose(next)
              joinMatchmaking(next.format, next.ranked)
            }}
          >
            {queueLabel(elsewhere.format, elsewhere.ranked)}
          </button>
        </p>
      ) : null}
    </section>
  )
}

function SearchingRow({
  label,
  since,
  others,
  onCancel,
}: {
  label: string
  since: number | null
  others: number
  onCancel: () => void
}) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div
      className={styles.searchingRow}
      role="status"
      aria-live="polite"
      title={others > 0 ? `${others} other ${others === 1 ? 'player' : 'players'} in this queue` : 'You’ll be paired as soon as someone joins'}
    >
      <span className={styles.spinner} aria-hidden />
      <span className={styles.searchingText}>{label}</span>
      {since !== null && <span className={styles.wait}>{formatWait(now - since)}</span>}
      <button type="button" className={styles.cancel} onClick={onCancel} data-testid="find-opponent-cancel">
        Cancel
      </button>
    </div>
  )
}
