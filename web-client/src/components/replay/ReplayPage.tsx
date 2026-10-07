/**
 * `/replay/:gameId` — the shareable, deep-linkable replay URL.
 *
 * This route owns only "which replay, and did it load": the id from the URL, the public-replay
 * fetch, loading/error states, and stamping the seat→team map. Everything after that — transport,
 * scrubber, share/export, the board — is {@link ReplayPlayer}, shared with the in-app overlay so
 * the two cannot drift apart again.
 */
import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useGameStore } from '@/store/gameStore.ts'
import {
  reconstructSnapshots,
  type PublicReplayData,
  type SpectatorStateUpdate,
} from '@/replay/reconstructSnapshots.ts'
import { ReplayPlayer, type ReplayMetadata } from './ReplayPlayer'
import { PageShell, pageStyles } from '@/components/ui/PageShell'
import styles from './Replay.module.css'

export function ReplayPage() {
  const { gameId } = useParams<{ gameId: string }>()
  const navigate = useNavigate()

  const [snapshots, setSnapshots] = useState<SpectatorStateUpdate[]>([])
  const [metadata, setMetadata] = useState<ReplayMetadata | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const setSeatTeams = useGameStore((s) => s.setSeatTeams)

  useEffect(() => {
    if (!gameId) return
    let cancelled = false

    async function loadReplay() {
      setLoading(true)
      setError(null)
      try {
        const response = await fetch(`/api/public/replays/${gameId}`)
        if (!response.ok) {
          setError(
            response.status === 404
              ? 'Replay not found. It may have expired or the game ID is invalid.'
              : 'Failed to load replay.',
          )
          setLoading(false)
          return
        }
        const data = await response.json() as PublicReplayData
        if (cancelled) return
        setMetadata(data.metadata)
        setSnapshots(reconstructSnapshots(data.initialSnapshot, data.deltas))
        // Stamp the seat → team map from the replay roster so a team-game replay lights up the
        // team-grouped rail, ally treatment, and team-split layout (replay frames are fed
        // straight into the store, bypassing the state-update handler that re-derives this in
        // live play).
        const roster = data.initialSnapshot.players
        if (roster?.some((p) => p.teamIndex != null)) {
          const teams: Record<string, number> = {}
          for (const p of roster) if (p.teamIndex != null) teams[p.playerId] = p.teamIndex
          setSeatTeams(teams, roster.some((p) => p.teamSharedLife))
        }
      } catch {
        if (!cancelled) setError('Failed to load replay.')
      }
      if (!cancelled) setLoading(false)
    }

    loadReplay()
    return () => { cancelled = true }
  }, [gameId, setSeatTeams])

  useEffect(() => {
    return () => { setSeatTeams({}) }
  }, [setSeatTeams])

  const goHome = useCallback(() => { navigate('/') }, [navigate])

  if (loading || error) {
    return (
      <PageShell title="Replay" width="narrow" plain>
        <div className={styles.stateCenter}>
          <section className={`${pageStyles.panel} ${styles.stateCard}`} aria-live="polite">
            {loading ? (
              <>
                <span className={styles.spinner} aria-hidden />
                <p className={styles.stateText}>Loading replay…</p>
              </>
            ) : (
              <>
                <h1 className={styles.stateTitle}>Replay unavailable</h1>
                <p className={styles.stateText}>{error}</p>
                <button type="button" onClick={goHome} className={pageStyles.buttonPrimary}>
                  Back to home
                </button>
              </>
            )}
          </section>
        </div>
      </PageShell>
    )
  }

  return (
    <ReplayPlayer
      snapshots={snapshots}
      gameId={gameId ?? ''}
      metadata={metadata}
      onExit={goHome}
      onHome={goHome}
    />
  )
}
