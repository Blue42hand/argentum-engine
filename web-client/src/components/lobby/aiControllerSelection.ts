import type { AiControllerOptionView, AiControllerSpec } from '@/types/aiController'

export interface AiControllerChoice {
  readonly key: string
  readonly spec: AiControllerSpec | null
  readonly label: string
  readonly description: string | null
  readonly disabled: boolean
}

/** Stable select value without imposing any meaning on opaque provider profile IDs. */
export function controllerSpecKey(spec: AiControllerSpec | null | undefined): string {
  return spec ? JSON.stringify([spec.mode, spec.profileId ?? null]) : ''
}

export function parseControllerSpecKey(key: string): AiControllerSpec | null {
  if (key === '') return null
  const parsed = JSON.parse(key) as [string, string | null]
  return parsed[1] === null ? { mode: parsed[0] } : { mode: parsed[0], profileId: parsed[1] }
}

export function sameControllerSpec(
  left: AiControllerSpec | null | undefined,
  right: AiControllerSpec | null | undefined,
): boolean {
  return controllerSpecKey(left) === controllerSpecKey(right)
}

/**
 * Build a selector that never silently converts a stale explicit choice into server fallback.
 * An unavailable current selection remains visible (disabled) until the host picks a valid option.
 */
export function buildControllerChoices(
  options: readonly AiControllerOptionView[],
  current: AiControllerSpec | null | undefined,
): readonly AiControllerChoice[] {
  const choices: AiControllerChoice[] = [
    {
      key: '',
      spec: null,
      label: 'Server default',
      description: 'Use the server-wide AI controller fallback',
      disabled: false,
    },
    ...options.map((option) => ({
      key: controllerSpecKey(option.spec),
      spec: option.spec,
      label: option.deck?.label
        ? `${option.displayName} — ${option.deck.label}`
        : option.displayName,
      description: option.description ?? null,
      disabled: false,
    })),
  ]

  if (current && !options.some((option) => sameControllerSpec(option.spec, current))) {
    const profile = current.profileId ? ` / ${current.profileId}` : ''
    choices.push({
      key: controllerSpecKey(current),
      spec: current,
      label: `Unavailable — ${current.mode}${profile}`,
      description: 'This explicit controller selection is no longer advertised by the server',
      disabled: true,
    })
  }

  return choices
}
