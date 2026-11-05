/**
 * Display geometry for dexterity flips (Chaos Orb).
 *
 * The server owns the table layout and decides where a flip lands; these helpers only turn its
 * millimetre table into screen space and draw previews from the numbers it sends (the release
 * profile carries the engine's own scatter terms, so the aim ring shows what the engine scores).
 */
import type { FlipReleaseProfile, FlipTable, FlipTableCard } from '@/types'

export const CARD_W = 63
export const CARD_H = 88

/** Room either side of the table — the release gauge stands in the right-hand one. */
const SIDE_MARGIN = 96
const FAR_MARGIN = 34
const HAND_MARGIN = 64

export interface Point {
  readonly x: number
  readonly y: number
}

/**
 * Maps table coordinates to the stage the viewer sees. The flipper sees the table from their own
 * seat (their battlefield at the bottom); everyone else sees it turned round, from the far side.
 */
export interface FlipView {
  readonly stageWidth: number
  readonly stageHeight: number
  readonly fromFarSide: boolean
  toStage(p: Point): Point
  fromStage(p: Point): Point
  /** Turn a landing angle into the viewer's frame. */
  rotation(deg: number): number
}

export function makeFlipView(table: FlipTable, fromFarSide: boolean): FlipView {
  const stageWidth = table.width + 2 * SIDE_MARGIN
  const stageHeight = table.height + FAR_MARGIN + (table.handY - table.height) + HAND_MARGIN
  // Flipper's view: table top sits FAR_MARGIN down. Far side: the hand edge is at the top.
  const toStage = (p: Point): Point =>
    fromFarSide
      ? { x: SIDE_MARGIN + (table.width - p.x), y: stageHeight - FAR_MARGIN - p.y }
      : { x: SIDE_MARGIN + p.x, y: FAR_MARGIN + p.y }
  const fromStage = (p: Point): Point =>
    fromFarSide
      ? { x: table.width - (p.x - SIDE_MARGIN), y: stageHeight - FAR_MARGIN - p.y }
      : { x: p.x - SIDE_MARGIN, y: p.y - FAR_MARGIN }
  return {
    stageWidth,
    stageHeight,
    fromFarSide,
    toStage,
    fromStage,
    rotation: (deg) => (fromFarSide ? deg + 180 : deg),
  }
}

/** 1 for a clean release, falling to 0 as it strays outside the clean zone (mirrors the engine). */
export function releaseQuality(profile: FlipReleaseProfile, strength: number): number {
  const s = Math.min(1, Math.max(0, strength))
  const miss = s < profile.cleanFrom ? profile.cleanFrom - s : s > profile.cleanTo ? s - profile.cleanTo : 0
  return 1 - Math.min(1, miss / profile.slopRange)
}

/** Signed carry error (mm) of a release: negative falls short of the aim, positive sails past it. */
export function carryDrift(profile: FlipReleaseProfile, strength: number): number {
  return (Math.min(1, Math.max(0, strength)) - profile.sweetSpot) * profile.depthPerStrength
}

/** Sideways carry error (mm) of a release at hand sway [sway] (-1..1): positive pulls to the thrower's right. */
export function swayPull(profile: FlipReleaseProfile, sway: number): number {
  return Math.min(1, Math.max(-1, sway)) * profile.sidePerSway
}

/**
 * Where a release of [strength] at hand sway [sway] aimed at [aim] carries to before scatter —
 * along the line from the hand, and across it (mirrors the engine's carry).
 */
export function carryPoint(table: FlipTable, profile: FlipReleaseProfile, aim: Point, strength: number, sway = 0): Point {
  const dx = aim.x - table.handX
  const dy = aim.y - table.handY
  const length = Math.hypot(dx, dy)
  const [ux, uy] = length < 1 ? [0, -1] : [dx / length, dy / length]
  const drift = carryDrift(profile, strength)
  // The thrower faces along (ux, uy); their right-hand side is (-uy, ux).
  const pull = swayPull(profile, sway)
  return { x: aim.x + ux * drift - uy * pull, y: aim.y + uy * drift + ux * pull }
}

/** A carry error as the player reads it: "on target", "6 cm short · 3 cm left", "4 cm long". */
export function driftLabel(drift: number, pull = 0): string {
  const depth = Math.round(Math.abs(drift) / 10)
  const side = Math.round(Math.abs(pull) / 10)
  const parts = [
    ...(depth === 0 ? [] : [`${depth} cm ${drift < 0 ? 'short' : 'long'}`]),
    ...(side === 0 ? [] : [`${side} cm ${pull < 0 ? 'left' : 'right'}`]),
  ]
  return parts.length === 0 ? 'on target' : parts.join(' · ')
}

/** One standard deviation of scatter (mm) for a throw at [aim] — the engine's formula, its numbers. */
export function scatterSigma(table: FlipTable, profile: FlipReleaseProfile, aim: Point, strength: number): number {
  const distance = Math.hypot(aim.x - table.handX, aim.y - table.handY)
  return (
    profile.baseScatter +
    profile.scatterPerMm * distance +
    profile.sloppyScatter * Math.pow(1 - releaseQuality(profile, strength), 1.6)
  )
}

/** Whether an unrotated card resting at [p] would overlap [card]'s touchable area (aim preview). */
export function previewTouches(p: Point, card: FlipTableCard): boolean {
  return (
    p.x + CARD_W / 2 >= card.x - card.width / 2 &&
    p.x - CARD_W / 2 <= card.x + card.width / 2 &&
    p.y + CARD_H / 2 >= card.hitTop &&
    p.y - CARD_H / 2 <= card.hitBottom
  )
}
