/**
 * REST client for the admin Live Games view (`/api/admin/live-games`): every game session and every
 * running tournament lobby the server holds in memory right now. Needs no database, so it works on a
 * server without accounts too. Auth is the dashboard's shared {@link AdminAuth}.
 */
import { type AdminAuth, adminAuthHeaders } from './adminAuth'

export interface LiveSeat {
  readonly name: string
  readonly isAi: boolean
  readonly connected: boolean
  /** Null before the game has started. */
  readonly life: number | null
}

export interface LiveGame {
  readonly gameSessionId: string
  readonly seats: LiveSeat[]
  /** LobbyGameMode name, QUICK_GAME, or CASUAL — the same vocabulary as recorded match history. */
  readonly gameMode: string
  readonly format: string
  readonly setCode: string | null
  readonly ranked: boolean
  readonly publicSpectate: boolean
  readonly tournamentLobbyId: string | null
  readonly started: boolean
  readonly gameOver: boolean
  readonly turnNumber: number | null
  readonly activePlayerName: string | null
  readonly step: string | null
  readonly startedAt: string | null
  readonly lastActionAt: string | null
  readonly spectatorCount: number
  /** At least one human seat with an open socket — a restart would interrupt this game. */
  readonly hasConnectedHuman: boolean
}

export interface LiveLobby {
  readonly lobbyId: string
  readonly gameMode: string
  readonly format: string
  /** DRAFTING, DECK_BUILDING or TOURNAMENT_ACTIVE. */
  readonly state: string
  readonly setNames: string[]
  readonly humanPlayers: number
  readonly connectedHumans: number
  readonly currentRound: number | null
  readonly totalRounds: number | null
}

export interface LiveOverview {
  readonly generatedAt: string
  readonly onlinePlayers: number
  readonly games: LiveGame[]
  readonly lobbies: LiveLobby[]
}

export async function fetchLiveOverview(auth: AdminAuth): Promise<LiveOverview> {
  const res = await fetch('/api/admin/live-games', { headers: adminAuthHeaders(auth) })
  if (!res.ok) throw new Error(`Failed to load live games (${res.status})`)
  return (await res.json()) as LiveOverview
}
