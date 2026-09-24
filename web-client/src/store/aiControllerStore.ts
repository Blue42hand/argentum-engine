import { create } from 'zustand'
import type { ClientMessage } from '@/types/messages'
import type {
  AiControllerCatalogMessage,
  AiControllerClientMessage,
  AiControllerSpec,
} from '@/types/aiController'
import { getWebSocket } from './slices/shared'

interface AiControllerStore {
  catalog: AiControllerCatalogMessage | null
  requestCatalog: (lobbyId: string) => void
  setQuickGameController: (spec: AiControllerSpec | null, lobbyId: string) => void
  setLobbyController: (playerId: string, spec: AiControllerSpec | null, lobbyId: string) => void
}

/**
 * One narrow client store for the generic controller catalog.
 *
 * The existing game store mirrors lobby snapshots; this catalog is a separate request/response
 * projection, so keeping it separate avoids teaching either lobby implementation provider-specific
 * semantics. Every mutation is followed by a catalog request on the same socket. WebSocket ordering
 * therefore makes the next response the authoritative post-mutation selection rather than an
 * optimistic client guess.
 */
export const useAiControllerStore = create<AiControllerStore>(() => ({
  catalog: null,

  requestCatalog: (lobbyId) => {
    send({ type: 'getAiControllerCatalog', lobbyId })
  },

  setQuickGameController: (spec, lobbyId) => {
    send({ type: 'setQuickGameAiController', spec })
    send({ type: 'getAiControllerCatalog', lobbyId })
  },

  setLobbyController: (playerId, spec, lobbyId) => {
    send({ type: 'setLobbyAiController', playerId, spec })
    send({ type: 'getAiControllerCatalog', lobbyId })
  },
}))

/** Called by the central server-message router. */
export function receiveAiControllerCatalog(message: AiControllerCatalogMessage): void {
  useAiControllerStore.setState({ catalog: message })
}

function send(message: AiControllerClientMessage): void {
  // The Kotlin server added these generic messages after the legacy monolithic ClientMessage union.
  // Keep the compatibility cast at the socket boundary; the payload itself remains fully typed.
  getWebSocket()?.send(message as unknown as ClientMessage)
}
