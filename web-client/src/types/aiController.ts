import type { AiDeckSpecView } from './messages'

/** Generic per-seat AI controller identity. profileId is opaque to Argentum clients. */
export interface AiControllerSpec {
  readonly mode: string
  readonly profileId?: string | null
}

/** One controller/profile the server can currently satisfy. */
export interface AiControllerOptionView {
  readonly spec: AiControllerSpec
  readonly displayName: string
  readonly description?: string | null
  /** Summary only; provider-owned deck lists never cross this catalog boundary. */
  readonly deck?: AiDeckSpecView | null
}

/** Authoritative explicit selection for one AI seat. null/absent means server fallback. */
export interface AiControllerSeatSelectionView {
  readonly playerId: string
  readonly spec?: AiControllerSpec | null
}

/** Read-only server projection of selectable controllers and, for a lobby, current AI-seat state. */
export interface AiControllerCatalogMessage {
  readonly type: 'aiControllerCatalog'
  readonly options: readonly AiControllerOptionView[]
  readonly lobbyId?: string | null
  readonly seats: readonly AiControllerSeatSelectionView[]
}

export interface GetAiControllerCatalogMessage {
  readonly type: 'getAiControllerCatalog'
  readonly lobbyId?: string | null
}

export interface SetLobbyAiControllerMessage {
  readonly type: 'setLobbyAiController'
  readonly playerId: string
  readonly spec?: AiControllerSpec | null
}

export interface SetQuickGameAiControllerMessage {
  readonly type: 'setQuickGameAiController'
  readonly spec?: AiControllerSpec | null
}

export type AiControllerClientMessage =
  | GetAiControllerCatalogMessage
  | SetLobbyAiControllerMessage
  | SetQuickGameAiControllerMessage
