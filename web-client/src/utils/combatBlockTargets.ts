import type { EntityId } from '@/types'

/** Keep only assignments that remain legal in the latest server blocker offer. */
export function retainLegalBlockAssignments(
  assignments: Readonly<Record<EntityId, readonly EntityId[]>>,
  validBlockers: readonly EntityId[],
  validBlockTargets?: Readonly<Record<EntityId, readonly EntityId[]>>,
): Record<EntityId, EntityId[]> {
  const valid = new Set(validBlockers)
  const retained: Record<EntityId, EntityId[]> = {}
  for (const [id, attackers] of Object.entries(assignments)) {
    const blocker = id as EntityId
    if (!valid.has(blocker)) continue
    const targets = attackers.filter((attacker) =>
      !validBlockTargets || validBlockTargets[blocker]?.includes(attacker)
    )
    if (targets.length > 0) retained[blocker] = targets
  }
  return retained
}
