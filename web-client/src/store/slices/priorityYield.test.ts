import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ClientGameState, PriorityYieldMode } from '@/types'
import type { GameWebSocket } from '@/network/websocket'

vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
vi.stubGlobal('window', { addEventListener: () => {}, removeEventListener: () => {} })

const { useGameStore } = await import('../gameStore')
const { createGameplayHandlers } = await import('./handlers/gameplayHandlers')
const { setWebSocket } = await import('./shared')
const { entityId, Phase, Step } = await import('@/types')
const me = entityId('me')
const send = vi.fn()
const handlers = createGameplayHandlers(useGameStore.setState, useGameStore.getState)
const position: ClientGameState = {
  viewingPlayerId: me, cards: {}, zones: [], players: [], currentPhase: Phase.PRECOMBAT_MAIN,
  currentStep: Step.PRECOMBAT_MAIN, activePlayerId: me, priorityPlayerId: me,
  turnNumber: 1, isGameOver: false, winnerId: null, combat: null,
}
function receive(priorityYield: PriorityYieldMode | null, delta = false, isGameOver = false) {
  const common = { events: [], legalActions: [], interactionEpoch: 'live', stateVersion: 7, priorityYield }
  if (delta) handlers.onStateDeltaUpdate({ type: 'stateDeltaUpdate', delta: { players: [], isGameOver }, ...common })
  else handlers.onStateUpdate({ type: 'stateUpdate', state: { ...position, isGameOver }, ...common })
}

beforeEach(() => {
  send.mockClear()
  setWebSocket({ send, onStateVersionReceived: vi.fn(), requestResync: vi.fn() } as unknown as GameWebSocket)
  useGameStore.setState({ sessionId: 'game', playerId: me, priorityYield: null, gameOverState: null, priorityMode: 'forcedOnly', fullControl: false })
  receive(null)
})

describe('temporary priority yield', () => {
  it('sends exact rendered epoch and version without claiming unacknowledged activation or cancellation', () => {
    useGameStore.getState().setPriorityYield('endOfTurn', 'live', 7)
    expect(send).toHaveBeenLastCalledWith({ type: 'setPriorityYield', mode: 'endOfTurn', interactionEpoch: 'live', stateVersion: 7 })
    expect(useGameStore.getState().priorityYield).toBeNull()
    receive('endOfTurn')
    useGameStore.getState().setPriorityYield(null, 'live', 7)
    expect(send).toHaveBeenLastCalledWith({ type: 'setPriorityYield', mode: null, interactionEpoch: 'live', stateVersion: 7 })
    expect(useGameStore.getState().priorityYield).toBe('endOfTurn')
    receive(null, true)
    expect(useGameStore.getState().priorityYield).toBeNull()
  })

  it('cancels across a racing version update but never across a replaced timeline', () => {
    receive('nextTurn')
    useGameStore.setState({ stateVersion: 8 })
    useGameStore.getState().setPriorityYield('endOfTurn', 'live', 7)
    expect(send).not.toHaveBeenCalled()
    useGameStore.getState().setPriorityYield(null, 'live', 7)
    expect(send).toHaveBeenCalledExactlyOnceWith({ type: 'setPriorityYield', mode: null, interactionEpoch: 'live', stateVersion: 8 })
    send.mockClear()
    useGameStore.setState({ interactionEpoch: 'replacement' })
    useGameStore.getState().setPriorityYield(null, 'live', 7)
    expect(send).not.toHaveBeenCalled()
  })

  it('preserves Full Control without blocking cancellation', () => {
    useGameStore.setState({ fullControl: true })
    useGameStore.getState().setPriorityYield('nextTurn', 'live', 7)
    expect(send).not.toHaveBeenCalled()
    useGameStore.setState({ fullControl: false, priorityMode: 'fullControl' })
    useGameStore.getState().setPriorityYield('nextTurn', 'live', 7)
    expect(send).not.toHaveBeenCalled()
    useGameStore.getState().setPriorityYield(null, 'live', 7)
    expect(send).toHaveBeenCalledExactlyOnceWith({ type: 'setPriorityYield', mode: null, interactionEpoch: 'live', stateVersion: 7 })
  })

  it('rejects stale, missing, ended and disconnected origins', () => {
    const setYield = useGameStore.getState().setPriorityYield
    setYield('nextTurn', 'old', 7)
    setYield('nextTurn', 'live', 6)
    setYield('nextTurn', null, 7)
    setYield('nextTurn', 'live', null)
    useGameStore.setState({ gameState: { ...position, isGameOver: true } })
    setYield('nextTurn', 'live', 7)
    useGameStore.setState({ gameState: position, sessionId: null })
    setYield('nextTurn', 'live', 7)
    expect(send).not.toHaveBeenCalled()
  })

  it('uses server acknowledgements for full and delta updates, including expiry and old servers', () => {
    receive('nextTurn')
    expect(useGameStore.getState().priorityYield).toBe('nextTurn')
    receive('endOfTurn', true)
    expect(useGameStore.getState().priorityYield).toBe('endOfTurn')
    handlers.onStateDeltaUpdate({ type: 'stateDeltaUpdate', delta: { players: [] }, events: [], legalActions: [], interactionEpoch: 'live' })
    expect(useGameStore.getState().priorityYield).toBeNull()
    expect(useGameStore.getState().stateVersion).toBeNull()
  })

  it('clears temporary yield when the game ends or is replaced', () => {
    receive('nextTurn')
    receive('nextTurn', true, true)
    expect(useGameStore.getState().priorityYield).toBeNull()
    receive('nextTurn')
    handlers.onGameCancelled({ type: 'gameCancelled' })
    expect(useGameStore.getState().priorityYield).toBeNull()
    useGameStore.setState({ priorityYield: 'nextTurn' })
    handlers.onGameCreated({ type: 'gameCreated', sessionId: 'replacement' })
    expect(useGameStore.getState().priorityYield).toBeNull()
    useGameStore.setState({ priorityYield: 'endOfTurn' })
    useGameStore.getState().returnToMenu()
    expect(useGameStore.getState().priorityYield).toBeNull()
    expect(useGameStore.getState().priorityMode).toBe('forcedOnly')
  })

  it('cycles all existing modes and the new forced-only mode', () => {
    for (const mode of ['stops', 'fullControl', 'auto', 'forcedOnly']) {
      useGameStore.getState().cyclePriorityMode()
      expect(send).toHaveBeenLastCalledWith({ type: 'setPriorityMode', mode })
    }
  })
})
