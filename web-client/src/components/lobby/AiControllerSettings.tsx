import { useEffect } from 'react'
import { useAiControllerStore } from '@/store/aiControllerStore'
import type { AiControllerSpec } from '@/types/aiController'
import type { UnifiedLobbyView } from './lobbyViewModel'
import {
  buildControllerChoices,
  controllerSpecKey,
  parseControllerSpecKey,
} from './aiControllerSelection'
import styles from '../ui/GameUI.module.css'

/**
 * Generic per-seat controller/profile selectors for the lobby shapes that expose host-owned AI
 * configuration. Provider profile IDs stay opaque: labels and optional deck summaries come entirely
 * from the server catalog, and mutations send the selected AiControllerSpec back unchanged.
 */
export function AiControllerSettings({ view }: { view: UnifiedLobbyView }) {
  const catalog = useAiControllerStore((state) => state.catalog)
  const requestCatalog = useAiControllerStore((state) => state.requestCatalog)
  const setQuickGameController = useAiControllerStore((state) => state.setQuickGameController)
  const setLobbyController = useAiControllerStore((state) => state.setLobbyController)
  const aiSeats = view.players.filter((player) => player.isAi)

  const supportedShape =
    view.kind === 'QUICK' ||
    (view.kind === 'TOURNAMENT' && view.axes.cards.kind === 'BRING_A_DECK')
  const visible = view.isWaiting && view.isHost && supportedShape && aiSeats.length > 0
  const seatKey = aiSeats.map((seat) => seat.playerId).join(',')

  useEffect(() => {
    if (!visible) return
    requestCatalog(view.lobbyId)
  }, [visible, view.lobbyId, seatKey, requestCatalog])

  if (!visible) return null

  const currentCatalog = catalog?.lobbyId === view.lobbyId ? catalog : null

  return (
    <>
      {aiSeats.map((seat) => {
        const current = currentCatalog?.seats.find((selection) => selection.playerId === seat.playerId)?.spec ?? null
        const choices = buildControllerChoices(currentCatalog?.options ?? [], current)
        const currentChoice = choices.find((choice) => choice.key === controllerSpecKey(current))
        const unavailable = currentChoice?.disabled === true

        const apply = (spec: AiControllerSpec | null) => {
          if (view.kind === 'QUICK') {
            setQuickGameController(spec, view.lobbyId)
          } else {
            setLobbyController(seat.playerId, spec, view.lobbyId)
          }
        }

        return (
          <div key={seat.playerId} className={`${styles.settingsRow} ${styles.settingsRowSub}`}>
            <span className={styles.settingsLabel}>{seat.name} controller</span>
            <div className={styles.variantGroup}>
              <select
                value={controllerSpecKey(current)}
                onChange={(event) => apply(parseControllerSpecKey(event.target.value))}
                className={styles.settingsSelect}
                disabled={currentCatalog === null}
                data-testid={`ai-controller-${seat.playerId}`}
                title={unavailable ? 'This explicit selection is no longer available on the server' : 'Choose the controller for this AI seat'}
              >
                {currentCatalog === null ? (
                  <option value="">Loading controllers…</option>
                ) : (
                  choices.map((choice) => (
                    <option key={choice.key || 'default'} value={choice.key} disabled={choice.disabled}>
                      {choice.label}
                    </option>
                  ))
                )}
              </select>
              {currentCatalog !== null && (
                <div className={styles.variantCaption}>
                  {unavailable
                    ? 'This explicit selection is unavailable. Choose another controller before starting.'
                    : currentChoice?.description ?? 'Controller selection is stored per AI seat.'}
                </div>
              )}
            </div>
          </div>
        )
      })}
    </>
  )
}
