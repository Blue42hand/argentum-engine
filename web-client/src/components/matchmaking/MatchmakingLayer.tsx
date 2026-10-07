/**
 * Matchmaking UI that follows the player across every route, mounted beside the router rather than
 * inside `App`: the socket outlives a client-side navigation, so a search started on the home screen
 * keeps running while the player is in the deckbuilder or the help pages.
 *
 * - The accept prompt shows wherever they are.
 * - Off the home screen, a small pill says the search is still running and offers to stop it.
 * - When the match is confirmed, a player on another page is taken to `/`, where `App` renders the
 *   matchmade lobby the server just sent.
 */
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useGameStore } from '@/store/gameStore.ts'
import { MatchFoundDialog } from './MatchFoundDialog'
import styles from './Matchmaking.module.css'
import { formatWait, queueLabel } from './queues'

/** Routes that render `App` (the `*` route), where the lobby and the home panel live. */
function isAppRoute(pathname: string): boolean {
  return pathname === '/' || pathname === ''
}

export default function MatchmakingLayer() {
  const location = useLocation()
  const navigate = useNavigate()
  const status = useGameStore((s) => s.matchmaking)
  const matchmadeLobbyId = useGameStore((s) =>
    s.quickGameLobbyState?.matchmade ? s.quickGameLobbyState.lobbyId : null,
  )
  const onAppRoute = isAppRoute(location.pathname)

  useEffect(() => {
    if (matchmadeLobbyId && !onAppRoute) navigate('/')
  }, [matchmadeLobbyId, onAppRoute, navigate])

  return (
    <>
      <MatchFoundDialog />
      {!onAppRoute && status?.searching && (
        <SearchingPill
          label={queueLabel(status.format, status.ranked)}
          since={status.searchingSince ?? null}
        />
      )}
    </>
  )
}

function SearchingPill({ label, since }: { label: string; since: number | null }) {
  const leave = useGameStore((s) => s.leaveMatchmaking)
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div className={styles.pill} role="status" aria-live="polite" data-testid="matchmaking-pill">
      <span className={styles.spinner} aria-hidden />
      <span className={styles.pillLabel}>Searching {label}</span>
      {since !== null && <span className={styles.wait}>{formatWait(now - since)}</span>}
      <button type="button" className={styles.pillClose} onClick={leave} aria-label="Stop searching" title="Stop searching">
        ×
      </button>
    </div>
  )
}
