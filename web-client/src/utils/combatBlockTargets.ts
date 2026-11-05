import type { EntityId, LegalActionInfo } from '@/types'
import type { CombatState } from '@/store/slices/types'

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

/** Refresh a block draft from the complete latest server offer, preserving legal pair choices. */
export function refreshBlockerOffer(
  combat: CombatState,
  offer: LegalActionInfo | undefined,
  attackers: readonly { creatureId: EntityId; mustBeBlockedByAll?: boolean | undefined }[],
): CombatState {
  const validCreatures = [...(offer?.validBlockers ?? [])]
  const validBlockTargets = offer?.validBlockTargets
  const attackingCreatures = [...(offer?.blockableAttackers ?? [])]
  const proposedBlockerAssignments = offer?.proposedBlockerAssignments ?? {}
  const blockerMaxBlockCounts = offer?.blockerMaxBlockCounts ?? {}
  const maxTotalBlockers = offer?.maxTotalBlockers
  const attackerSet = new Set(attackingCreatures)
  const mustBeBlockedAttackers = attackers
    .filter((attacker) => attackerSet.has(attacker.creatureId) && attacker.mustBeBlockedByAll)
    .map((attacker) => attacker.creatureId)
  const changed = [
    [validCreatures, combat.validCreatures],
    [validBlockTargets, combat.validBlockTargets],
    [attackingCreatures, combat.attackingCreatures],
    [proposedBlockerAssignments, combat.proposedBlockerAssignments ?? {}],
    [blockerMaxBlockCounts, combat.blockerMaxBlockCounts],
    [maxTotalBlockers, combat.maxTotalBlockers],
    [mustBeBlockedAttackers, combat.mustBeBlockedAttackers],
  ].some(([next, previous]) => JSON.stringify(next) !== JSON.stringify(previous))
  if (!changed) return combat
  const legalPairs = retainLegalBlockAssignments(combat.blockerAssignments, validCreatures, validBlockTargets)
  const blockerAssignments: Record<EntityId, EntityId[]> = {}
  for (const [blockerId, targets] of Object.entries(legalPairs)) {
    const retained = targets.filter((id) => attackerSet.has(id))
    if (retained.length > 0) blockerAssignments[blockerId as EntityId] = retained
  }
  return {
    ...combat, validCreatures, validBlockTargets, attackingCreatures,
    proposedBlockerAssignments, blockerMaxBlockCounts, maxTotalBlockers,
    mustBeBlockedAttackers, blockerAssignments,
  }
}

/** Necessary bounds supplied by the server; joint/team and other combat rules remain native. */
export function blockerDraftExceedsLimits(combat: CombatState | null): boolean {
  if (!combat || combat.mode !== 'declareBlockers') return false
  const assignments = Object.entries(combat.blockerAssignments).filter(([, targets]) => targets.length > 0)
  if (combat.maxTotalBlockers != null && assignments.length > combat.maxTotalBlockers) return true
  // The native offer emits entries only for blockers permitted to block more than one attacker.
  return assignments.some(([blocker, targets]) => targets.length > (combat.blockerMaxBlockCounts[blocker as EntityId] ?? 1))
}
