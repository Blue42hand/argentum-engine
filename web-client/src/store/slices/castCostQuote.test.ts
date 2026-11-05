import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ClientGameState, LegalActionInfo } from '@/types'
import type { CastCostQuoteMessage } from '@/types/messages'
import type { GameWebSocket } from '@/network/websocket'
vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
vi.stubGlobal('window', { addEventListener: () => {}, removeEventListener: () => {} })
const { useGameStore } = await import('../gameStore')
const { setWebSocket } = await import('./shared')
const { entityId, Phase, Step } = await import('@/types')
const ME = entityId('me'), SPELL = entityId('spell'), MANA = entityId('mana'), GRAVE = entityId('grave')
const send = vi.fn()
const position: ClientGameState = {
  viewingPlayerId: ME, cards: {}, zones: [], players: [], currentPhase: Phase.PRECOMBAT_MAIN,
  currentStep: Step.PRECOMBAT_MAIN, activePlayerId: ME, priorityPlayerId: ME,
  turnNumber: 1, isGameOver: false, winnerId: null, combat: null,
}
const offer: LegalActionInfo = {
  interactionEpoch: 'quote-epoch', actionType: 'CastSpell', description: 'Cast repeat spell',
  action: { type: 'CastSpell', playerId: ME, cardId: SPELL },
  manaCostString: '{0}', optionalManaCostString: '{1}', maxOptionalCostTimes: 1000000000,
  availableManaSources: [{ entityId: MANA, name: 'Source', producesColorless: true }],
  autoTapPreview: [MANA],
}
function begin(extra: Partial<LegalActionInfo> = {}, count = 2) {
  const info = { ...offer, ...extra }
  useGameStore.setState({ legalActions: [info] })
  useGameStore.getState().startPipeline(info, { forceManualTap: true })
  useGameStore.getState().advancePipeline({ type: 'optionalCostCount', count })
}
function reply(manaCostString: string, extra: Partial<CastCostQuoteMessage> = {}) {
  const pending = useGameStore.getState().pipelineState!.pendingCostQuote!
  const message: CastCostQuoteMessage = { type: 'castCostQuote', requestId: pending.requestId,
    stateVersion: 7, interactionEpoch: 'quote-epoch', manaCostString, ...extra }
  useGameStore.getState().receiveCastCostQuote(message)
  return message
}
beforeEach(() => {
  vi.useFakeTimers(); send.mockClear()
  setWebSocket({ send } as unknown as GameWebSocket)
  useGameStore.getState().cancelPipeline()
  useGameStore.setState({ gameState: position, interactionEpoch: 'quote-epoch', stateVersion: 7,
    playerId: ME, sessionId: 'quote-test', autoTapEnabled: true, gameOverState: null })
})
afterEach(() => { useGameStore.getState().cancelPipeline(); vi.clearAllTimers(); vi.useRealTimers() })
describe('native optional-payment quotes', () => {
  it('uses a zero total under excess reduction without scaling the raw repeat price or enabling AutoPay', () => {
    begin()
    expect(send).toHaveBeenCalledWith(expect.objectContaining({ type: 'requestCastCostQuote',
      action: expect.objectContaining({ declaredCostTimes: 2 }), stateVersion: 7, interactionEpoch: 'quote-epoch' }))
    expect(useGameStore.getState().manaSelectionState).toBeNull()
    reply('{0}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{0}')
    expect(useGameStore.getState().pipelineState?.actionInfo.autoTapPreview).toBeUndefined()
    expect(send.mock.calls.every(([message]) => message.type === 'requestCastCostQuote')).toBe(true)
  })
  it('keeps native empty-string zero cost Manual through confirmation and submits no resource payment', () => {
    begin({ manaCostString: '{5}', autoTapPreview: [MANA] })
    reply('', { delveGenericCap: 0, tapForGenericMax: 0, tapForGenericCostString: '' })
    const selection = useGameStore.getState().manaSelectionState!
    expect(selection).not.toBeNull()
    expect(selection.manaCost).toBe('')
    expect(selection.selectedSources).toEqual([])
    expect(selection.phyrexianLifePipIndices).toEqual([])
    expect(selection.actionInfo.autoTapPreview).toBeUndefined()
    expect(useGameStore.getState().pipelineState?.remainingPhases).toEqual([{ type: 'manaSource' }])
    expect(send).toHaveBeenCalledTimes(1)
    const directActionFallback = vi.fn()
    useGameStore.getState().confirmManaSelection('quote-epoch', selection, directActionFallback)
    expect(directActionFallback).not.toHaveBeenCalled()
    expect(send).toHaveBeenLastCalledWith({
      type: 'submitAction', interactionEpoch: 'quote-epoch',
      action: { type: 'CastSpell', playerId: ME, cardId: SPELL, declaredCostTimes: 2,
        paymentStrategy: { type: 'Explicit', manaAbilitiesToActivate: [], phyrexianLifePayments: [] } },
    })
    expect(send).toHaveBeenCalledTimes(2)
    expect(useGameStore.getState().pipelineState).toBeNull()
    expect(useGameStore.getState().manaSelectionState).toBeNull()
  })
  it('sends a large count once and waits for the native price without allocating a local repeated string', () => {
    begin({}, 1000000000)
    expect(send).toHaveBeenCalledTimes(1)
    expect(send.mock.calls[0]![0].action.declaredCostTimes).toBe(1000000000)
    reply('{1999999998}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{1999999998}')
  })
  it('rebuilds newly payable delve and uses the native cap, then quotes the chosen payment', () => {
    begin({ hasDelve: true, validDelveCards: [{ entityId: GRAVE, name: 'Graveyard card' }] })
    reply('{3}', { delveGenericCap: 1 })
    expect(useGameStore.getState().delveSelectionState?.maxDelve).toBe(1)
    useGameStore.getState().advancePipeline({ type: 'delve', delvedCards: [GRAVE], modifiedManaCost: '{2}' })
    expect(send).toHaveBeenCalledTimes(2)
    expect(send.mock.calls[1]![0].action.alternativePayment.delvedCards).toEqual([GRAVE])
    reply('{2}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{2}')
  })
  it('preserves Phyrexian manual selection in the quoted bill', () => {
    begin()
    reply('{G/P}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{G/P}')
    expect(useGameStore.getState().pipelineState?.accumulatedAction).not.toHaveProperty('paymentStrategy.type', 'AutoPay')
  })
  it('ignores replies from canceled or superseded requests', () => {
    begin(); const stale = useGameStore.getState().pipelineState!.pendingCostQuote!
    useGameStore.getState().cancelPipeline(); begin()
    useGameStore.getState().receiveCastCostQuote({ type: 'castCostQuote', ...stale, interactionEpoch: 'quote-epoch', manaCostString: '{99}' })
    expect(useGameStore.getState().manaSelectionState).toBeNull()
    expect(useGameStore.getState().pipelineState!.pendingCostQuote!.requestId).not.toBe(stale.requestId)
    reply('{0}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{0}')
  })
  it.each(['epoch', 'version'])('abandons a matching reply when its %s is stale', (kind) => {
    begin()
    useGameStore.setState(kind === 'epoch' ? { interactionEpoch: 'new-epoch' } : { stateVersion: 8 })
    reply('{0}')
    expect(useGameStore.getState().pipelineState).toBeNull()
    expect(useGameStore.getState().manaSelectionState).toBeNull()
  })
  it('unlocks the draft on server error or lost reply without submitting', () => {
    begin(); reply('', { error: 'The action is no longer offered.' })
    expect(useGameStore.getState().pipelineState).toBeNull()
    begin(); vi.advanceTimersByTime(15000)
    expect(useGameStore.getState().pipelineState).toBeNull()
    expect(send.mock.calls.every(([message]) => message.type === 'requestCastCostQuote')).toBe(true)
  })
  it('rebuilds delve after extra mana for counters makes generic payable', () => {
    begin({ hasDelve: true, validDelveCards: [{ entityId: GRAVE, name: 'Graveyard card' }], maxAdditionalManaForCounters: 4 })
    reply('{0}', { delveGenericCap: 0 })
    expect(useGameStore.getState().pipelineState?.remainingPhases[0]?.type).toBe('additionalManaForCounters')
    useGameStore.getState().advancePipeline({ type: 'additionalManaForCounters', amount: 3 })
    reply('{3}', { delveGenericCap: 3 })
    expect(useGameStore.getState().pipelineState?.remainingPhases[0]?.type).toBe('delve')
    expect(useGameStore.getState().delveSelectionState?.maxDelve).toBe(1)
    expect(useGameStore.getState().pipelineState?.remainingPhases.some(p => p.type === 'optionalCostCount' || p.type === 'additionalManaForCounters')).toBe(false)
  })
  it('preserves convoke selection and replaces its local remainder with the native final quote', () => {
    begin({ hasConvoke: true, validConvokeCreatures: [{ entityId: GRAVE, name: 'Creature', colors: ['G'] }] })
    reply('{2}{G}')
    expect(useGameStore.getState().convokeSelectionState).not.toBeNull()
    useGameStore.getState().advancePipeline({ type: 'convoke', convokedCreatures: { [GRAVE]: { color: 'GREEN' } } })
    expect(send.mock.calls[1]![0].action.alternativePayment.convokedCreatures).toEqual({ [GRAVE]: { color: 'GREEN' } })
    reply('{2}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{2}')
    expect(send).toHaveBeenCalledTimes(2)
  })
  it('uses native tap caps and bill for the next payment selector', () => {
    begin({ hasTapForGeneric: true, validTapForGenericPermanents: [{ entityId: GRAVE, name: 'Artifact', isCreature: false }] })
    reply('{4}', { tapForGenericMax: 2, tapForGenericCostString: '{2}' })
    expect(useGameStore.getState().tapForGenericSelectionState).toMatchObject({ maxTaps: 2, manaCost: '{2}' })
  })
  it('does not overwrite a final target-count quote with the original first-repeat table', () => {
    begin({ manaCostByTargetCount: { 0: '{99}' } })
    reply('{0}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{0}')
  })
  it('does not subtract harmonize again from a native net quote', () => {
    begin({ hasHarmonize: true, validHarmonizeCreatures: [{ entityId: GRAVE, name: 'Creature', power: 3 }] })
    reply('{4}')
    useGameStore.getState().advancePipeline({ type: 'harmonize', harmonizeCreature: GRAVE, reduction: 3 })
    reply('{1}')
    expect(useGameStore.getState().manaSelectionState).toMatchObject({ manaCost: '{1}', harmonizeReduction: 0 })
  })

  it('does not overwrite a final emerge quote with the original sacrifice-price table', () => {
    begin({ action: { type: 'CastSpell', playerId: ME, cardId: SPELL, useAlternativeCost: true, alternativeCostType: 'EMERGE' },
      additionalCostInfo: { costType: 'SacrificePermanent', description: 'Sacrifice a creature',
        validSacrificeTargets: [GRAVE], sacrificeCount: 1, costAfterSacrifice: { [GRAVE]: '{99}' } } })
    reply('{4}')
    useGameStore.getState().advancePipeline({ type: 'costPayment', costType: 'SacrificePermanent', selectedTargets: [GRAVE] })
    reply('{1}')
    expect(useGameStore.getState().manaSelectionState?.manaCost).toBe('{1}')
  })

})
