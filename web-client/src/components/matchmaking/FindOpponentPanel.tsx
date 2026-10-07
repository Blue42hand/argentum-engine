/**
 * "Find an opponent" — the home hub's way to play someone you don't know. Pick a format and casual
 * or ranked, then search; the server pairs you with another searcher and {@link MatchFoundDialog}
 * asks both of you to accept. The panel only captures intent and mirrors the server's queue state.
 */
import { useEffect, useState } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import { useAuthStore } from '@/store/authStore'
import type { DeckFormat, MatchmakingQueueCount } from '@/types'
import panel from '../ui/GameUI.module.css'
import styles from './Matchmaking.module.css'
import {
  QUEUE_FORMATS,
  activeQueues,
  formatWait,
  queueFormatLabel,
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
  const busy = activeQueues(counts)
  const totalSearching = busy.reduce((sum, c) => sum + c.searching, 0)

  return (
    <section className={panel.publicTournamentPanel} aria-labelledby="find-opponent-title" data-testid="find-opponent">
      <div className={panel.publicTournamentHeader}>
        <span id="find-opponent-title" className={panel.publicTournamentTitle}>Find an opponent</span>
        {totalSearching > 0 && (
          <span className={panel.onlinePlayersBadge}>
            <span className={panel.onlinePlayersDot} />
            {totalSearching} searching
          </span>
        )}
      </div>

      {status?.notice && (
        <div className={styles.notice} role="status">
          <span>{status.notice}</span>
          <button type="button" className={styles.noticeDismiss} aria-label="Dismiss" onClick={dismissNotice}>×</button>
        </div>
      )}

      {searching ? (
        <SearchingState
          format={status.format ?? null}
          ranked={status.ranked ?? false}
          since={status.searchingSince ?? null}
          others={Math.max(0, searchingIn(counts, status.format ?? null, status.ranked ?? false) - 1)}
          onCancel={leaveMatchmaking}
        />
      ) : (
        <>
          <p className={panel.publicTournamentEmpty}>
            Get paired with another player looking for the same game.
          </p>
          <label className={styles.fieldLabel} htmlFor="matchmaking-format">Format</label>
          <select
            id="matchmaking-format"
            className={styles.select}
            value={effective.format ?? ''}
            onChange={(e) => choose({ ...choice, format: (e.target.value || null) as DeckFormat | null })}
          >
            {QUEUE_FORMATS.map((o) => {
              const n = searchingIn(counts, o.format, effective.ranked)
              return (
                <option key={o.label} value={o.format ?? ''}>
                  {o.label}{o.format === null ? ' (sealed pool)' : ''}{n > 0 ? ` — ${n} searching` : ''}
                </option>
              )
            })}
          </select>

          {accountsEnabled && (
            <div className={styles.segmented} role="radiogroup" aria-label="Casual or ranked">
              <button
                type="button"
                role="radio"
                aria-checked={!effective.ranked}
                className={styles.segment}
                data-active={!effective.ranked}
                onClick={() => choose({ ...choice, ranked: false })}
              >
                Casual
              </button>
              <button
                type="button"
                role="radio"
                aria-checked={effective.ranked}
                className={styles.segment}
                data-active={effective.ranked}
                disabled={!rankedAvailable}
                title={rankedAvailable ? 'Counts toward your rating' : 'Sign in to play ranked'}
                onClick={() => choose({ ...choice, ranked: true })}
              >
                Ranked
              </button>
            </div>
          )}
          {accountsEnabled && !signedIn && (
            <p className={panel.publicTournamentEmpty}>
              <button type="button" className={styles.linkButton} onClick={onSignIn}>Sign in</button> to play ranked.
            </p>
          )}

          <button
            type="button"
            className={styles.primary}
            data-testid="find-opponent-search"
            onClick={() => joinMatchmaking(effective.format, effective.ranked)}
          >
            Find opponent
          </button>

          {busy.length > 0 && (
            <div className={styles.busy}>
              <span className={styles.busyLabel}>Searching now</span>
              <div className={styles.chips}>
                {busy.map((c) => (
                  <button
                    key={`${c.format ?? 'LIMITED'}-${c.ranked}`}
                    type="button"
                    className={styles.chip}
                    disabled={c.ranked && !rankedAvailable}
                    title={`Search ${queueLabel(c.format, c.ranked)}`}
                    onClick={() => {
                      const next = { format: c.format ?? null, ranked: c.ranked }
                      choose(next)
                      joinMatchmaking(next.format, next.ranked)
                    }}
                  >
                    {queueLabel(c.format, c.ranked)} · {c.searching}
                  </button>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </section>
  )
}

function SearchingState({
  format,
  ranked,
  since,
  others,
  onCancel,
}: {
  format: DeckFormat | null
  ranked: boolean
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
    <div className={styles.searching} role="status" aria-live="polite">
      <div className={styles.searchingRow}>
        <span className={styles.spinner} aria-hidden />
        <span className={styles.searchingText}>
          Searching {ranked ? 'ranked' : 'casual'} {queueFormatLabel(format)}
        </span>
        {since !== null && <span className={styles.wait}>{formatWait(now - since)}</span>}
      </div>
      <p className={panel.publicTournamentEmpty}>
        {others > 0
          ? `${others} other ${others === 1 ? 'player is' : 'players are'} searching here — pairing shortly.`
          : 'Nobody else is in this queue yet. You’ll be paired as soon as someone joins.'}
        {ranked ? ' Ranked pairs you with a similar rating first, widening the longer you wait.' : ''}
      </p>
      <button type="button" className={styles.secondary} onClick={onCancel} data-testid="find-opponent-cancel">
        Stop searching
      </button>
    </div>
  )
}
