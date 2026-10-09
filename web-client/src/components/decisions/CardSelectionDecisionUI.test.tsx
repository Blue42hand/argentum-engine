import type { ReactElement } from 'react'
import { beforeEach, expect, it, vi } from 'vitest'
import { entityId, type SelectCardsDecision } from '@/types'
import type { ResponsiveSizes } from '@/hooks/useResponsive'

const hooks = vi.hoisted(() => ({ values: [] as unknown[], cursor: 0, submit: vi.fn() }))
vi.mock('react', async (original) => ({
  ...await original<typeof import('react')>(),
  useMemo: (calculate: () => unknown) => calculate(),
  useState: (initial: unknown) => {
    const index = hooks.cursor++
    if (!(index in hooks.values)) hooks.values[index] = initial
    return [hooks.values[index], (next: unknown) => {
      hooks.values[index] = typeof next === 'function' ? next(hooks.values[index]) : next
    }]
  },
}))
vi.mock('@/store/gameStore.ts', () => ({
  useGameStore: (selector: (state: unknown) => unknown) => selector({
    submitDecision: hooks.submit, gameState: null,
  }),
}))
const { CardSelectionDecision } = await import('./CardSelectionDecisionUI')
const { DecisionCard } = await import('./DecisionComponents')
const options = ['land', 'instant', 'artifact', 'creature'].map(entityId)
const decision: SelectCardsDecision = {
  type: 'SelectCardsDecision', id: 'intent-search', playerId: entityId('player'),
  prompt: 'Choose 1 card', context: { phase: 'RESOLUTION', sourceName: 'Diabolic Intent' },
  options, minSelections: 1, maxSelections: 1, ordered: false,
  cardInfo: Object.fromEntries(options.map((id, i) => [id, {
    name: ['Swamp', 'Dark Ritual', 'Sol Ring', 'Grizzly Bears'][i]!,
    typeLine: ['Basic Land — Swamp', 'Instant', 'Artifact', 'Creature — Bear'][i]!,
    manaCost: '', imageUri: null,
  }])),
}
const responsive = { viewportWidth: 1200, containerPadding: 16, isMobile: false } as ResponsiveSizes
type Node = ReactElement<Record<string, unknown>>
function nodes(value: unknown): Node[] {
  if (Array.isArray(value)) return value.flatMap(nodes)
  if (!value || typeof value !== 'object' || !('props' in value)) return []
  const node = value as Node
  return [node, ...nodes(node.props.children)]
}
function render(input = decision) {
  hooks.cursor = 0
  return nodes(CardSelectionDecision({ decision: input, responsive }))
}
function confirm(tree: Node[]) {
  return tree.find((node) => node.type === 'button' && node.props.children === 'Confirm Selection')!
}
beforeEach(() => { hooks.values = []; hooks.submit.mockClear() })

it.each(options)('shows and submits the server-offered %s without a creature filter', (id) => {
  let tree = render()
  const cards = tree.filter((node) => node.type === DecisionCard)
  expect(cards.map((node) => node.props.cardId)).toEqual(options)
  expect(confirm(tree).props.disabled).toBe(true)
  const card = cards.find((node) => node.props.cardId === id)!
  expect(card.props.nonSelectable).toBe(false)
  ;(card.props.onClick as () => void)()
  tree = render()
  expect(confirm(tree).props.disabled).toBe(false)
  ;(confirm(tree).props.onClick as () => void)()
  expect(hooks.submit).toHaveBeenCalledExactlyOnceWith(decision.id, [id])
})

it('does not add options that the server omitted', () => {
  const restricted = { ...decision, options: [options[0]!] }
  expect(render(restricted).filter((node) => node.type === DecisionCard)
    .map((node) => node.props.cardId)).toEqual(restricted.options)
})
