import { describe, expect, it } from 'vitest'
import type { AiControllerOptionView, AiControllerSpec } from '@/types/aiController'
import {
  buildControllerChoices,
  controllerSpecKey,
  parseControllerSpecKey,
} from './aiControllerSelection'

const mode = 'external-test'
const alpha: AiControllerSpec = { mode, profileId: 'binding/alpha:v1' }
const beta: AiControllerSpec = { mode, profileId: 'binding/beta:v1' }

const options: readonly AiControllerOptionView[] = [
  { spec: alpha, displayName: 'Alpha', deck: { kind: 'deck', label: 'Alpha deck', cardCount: 100 } },
  { spec: beta, displayName: 'Beta', deck: { kind: 'deck', label: 'Beta deck', cardCount: 100 } },
]

describe('AI controller selection model', () => {
  it('round-trips distinct opaque profiles without aliasing them', () => {
    expect(parseControllerSpecKey(controllerSpecKey(alpha))).toEqual(alpha)
    expect(parseControllerSpecKey(controllerSpecKey(beta))).toEqual(beta)
    expect(controllerSpecKey(alpha)).not.toBe(controllerSpecKey(beta))
  })

  it('keeps two seat selections independently addressable from one mixed-profile catalog', () => {
    const alphaChoices = buildControllerChoices(options, alpha)
    const betaChoices = buildControllerChoices(options, beta)

    expect(alphaChoices.find((choice) => choice.key === controllerSpecKey(alpha))?.label)
      .toBe('Alpha — Alpha deck')
    expect(betaChoices.find((choice) => choice.key === controllerSpecKey(beta))?.label)
      .toBe('Beta — Beta deck')
  })

  it('keeps a stale explicit selection visible and disabled instead of falling back silently', () => {
    const stale: AiControllerSpec = { mode, profileId: 'binding/missing:v1' }
    const choices = buildControllerChoices(options, stale)
    const selected = choices.find((choice) => choice.key === controllerSpecKey(stale))

    expect(selected).toMatchObject({
      spec: stale,
      disabled: true,
      label: 'Unavailable — external-test / binding/missing:v1',
    })
    expect(choices.find((choice) => choice.key === '')).toMatchObject({
      spec: null,
      disabled: false,
      label: 'Server default',
    })
  })

  it('round-trips the explicit server fallback separately from provider profiles', () => {
    expect(controllerSpecKey(null)).toBe('')
    expect(parseControllerSpecKey('')).toBeNull()
  })
})
