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
import { useApplyRecipe } from '../lobby/useApplyRecipe'
import { practiceCaption, practiceRecipe } from './practice'
import {
  QUEUE_FORMATS,
  activeQueues,
  formatWait,
  queueLabel,
  searchingIn,
} from './queues'

const STORAGE_KEY = 'argentum-matchmaking-choice'

/** Enough to show where the players are without turning the panel into a lobby list. */
const MAX_WAITING_ROWS = 3

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
  const aiEnabled = useGameStore((s) => s.aiEnabled)
  const applyRecipe = useApplyRecipe()
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
  // Before you search, everyone counted is someone else — a game you can have right now. Shown
  // first and in green, because joining their queue pairs you at once.
  const waiting = searching ? [] : activeQueues(counts).slice(0, MAX_WAITING_ROWS)
  const play = (format: DeckFormat | null, ranked: boolean) => {
    choose({ format, ranked })
    joinMatchmaking(format, ranked)
  }

  return (
    <section className={styles.panel} aria-labelledby="find-opponent-title" data-testid="find-opponent">
      <span id="find-opponent-title" className={styles.title}>Find an opponent</span>

      {waiting.length > 0 && (
        <ul className={styles.waitingList} aria-label="Players waiting for a game">
          {waiting.map((q) => {
            const needsSignIn = q.ranked && !rankedAvailable
            return (
              <li key={`${q.format ?? 'LIMITED'}-${q.ranked}`} className={styles.waitingRow} data-testid="waiting-queue">
                <span className={styles.waitingDot} aria-hidden />
                <span className={styles.waitingText}>
                  <span className={styles.waitingQueue}>{queueLabel(q.format, q.ranked)}</span>
                  <span className={styles.waitingCount}>
                    {q.searching === 1 ? '1 player waiting' : `${q.searching} players waiting`}
                  </span>
                </span>
                <button
                  type="button"
                  className={styles.play}
                  onClick={() => (needsSignIn ? onSignIn() : play(q.format ?? null, q.ranked))}
                  title={needsSignIn ? 'Sign in to play ranked' : `Play ${queueLabel(q.format, q.ranked)} now`}
                >
                  {needsSignIn ? 'Sign in' : 'Play'}
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {searching ? (
        <>
          <SearchingRow
            label={queueLabel(status.format, status.ranked)}
            since={status.searchingSince ?? null}
            others={Math.max(0, searchingIn(counts, status.format ?? null, status.ranked ?? false) - 1)}
            onCancel={leaveMatchmaking}
          />
          {aiEnabled && (
            <button
              type="button"
              className={styles.practice}
              onClick={() => applyRecipe(practiceRecipe(status.format ?? null))}
              data-testid="find-opponent-practice"
            >
              <span className={styles.practiceIcon} aria-hidden>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="4" y="7" width="16" height="12" rx="3" />
                  <path d="M12 3v4M9 12h.01M15 12h.01M9.5 16h5" />
                </svg>
              </span>
              <span className={styles.practiceText}>
                <span className={styles.practiceLabel}>Play the AI while you wait</span>
                <span className={styles.practiceCaption}>{practiceCaption(status.format ?? null)}</span>
              </span>
              <span className={styles.practiceArrow} aria-hidden>→</span>
            </button>
          )}
        </>
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
