import { useEffect, useMemo, useState } from 'react'
import { useGameStore } from '@/store/gameStore'
import { getCdnArtCropUrl } from '@/utils/cardImages'
import { useHasHover } from '@/hooks/useHasHover'
import type { JumpstartOffer } from '@/types'
import { JumpstartPackExplorer } from './JumpstartPackExplorer'
import { ColorPips, CurveBars, RarityDot } from './JumpstartPackParts'
import { colorsLabel, faceCard, mergeColors, summarizePack, type PackSummary } from './jumpstartPack'
import styles from './JumpstartOverlay.module.css'

function PackArt({ pack, className }: { pack: JumpstartOffer; className: string | undefined }) {
  const face = faceCard(pack.cards)
  const src = face?.imageUri ? getCdnArtCropUrl(face.imageUri) ?? face.imageUri : null
  return src ? <img src={src} alt="" className={className} /> : <div className={className} />
}

/** One half of the deck: a chosen pack, the pack being considered, or an empty slot. */
function DeckSlot({ pack, label, tone, ghost, onView, onChange }: {
  pack: JumpstartOffer | undefined
  label: string
  tone: 'base' | 'pack'
  ghost: boolean
  onView?: (() => void) | undefined
  onChange?: (() => void) | undefined
}) {
  const summary = useMemo(() => pack ? summarizePack(pack.cards) : null, [pack])
  if (!pack || !summary) return <div className={`${styles.slot} ${styles.slotEmpty}`}>
    <span className={styles.slotLabel}>{label}</span>
    <span className={styles.slotPlaceholder}>Not chosen yet</span>
  </div>
  return <div className={`${styles.slot} ${tone === 'base' ? styles.slotBase : styles.slotPack} ${ghost ? styles.slotGhost : ''}`}>
    <PackArt pack={pack} className={styles.slotArt} />
    <div className={styles.slotText}>
      <span className={styles.slotLabel}>{ghost ? 'Considering' : label}</span>
      <strong>{pack.theme}</strong>
      <ColorPips colors={summary.colors} size={15} />
    </div>
    <div className={styles.slotActions}>
      {onView && <button className={styles.slotView} onClick={onView} aria-label={`View ${pack.theme}`}>View</button>}
      {onChange && <button className={styles.slotView} onClick={onChange} aria-label={`Change ${pack.theme}`}>Change</button>}
    </div>
  </div>
}

/**
 * The deck under construction — Arena's two-packet strip. The pack chosen first stays on screen
 * while the second is picked, and hovering an offer drops it into its empty slot so the colors,
 * counts and curve shown are those of the 40-card deck the pair would make. During pick one both
 * slots can hold a candidate: one from each round's offers.
 */
function DeckBar({ chosen, candidates, onViewChosen, onChangeFirst }: {
  chosen: readonly JumpstartOffer[]
  candidates: readonly [JumpstartOffer | undefined, JumpstartOffer | undefined]
  onViewChosen: (index: number) => void
  /** Present only while the second pack is undecided. */
  onChangeFirst: (() => void) | undefined
}) {
  const first = chosen[0] ?? candidates[0]
  const second = chosen[1] ?? candidates[1]
  const parts = useMemo(() => [first, second].filter((p): p is JumpstartOffer => p !== undefined)
    .map((p) => summarizePack(p.cards)), [first, second])
  const colors = mergeColors(...parts.map((p) => p.colors))
  const total = (key: keyof Pick<PackSummary, 'creatures' | 'spells' | 'lands'>) => parts.reduce((n, p) => n + p[key], 0)
  const complete = parts.length === 2
  return <section className={styles.deckBar} aria-label="Your deck">
    <div className={styles.slots}>
      <DeckSlot pack={first} label="First pack" tone="base" ghost={!chosen[0] && !!candidates[0]}
        onView={chosen[0] ? () => onViewChosen(0) : undefined} onChange={onChangeFirst} />
      <span className={styles.plus} aria-hidden>+</span>
      <DeckSlot pack={second} label="Second pack" tone="pack" ghost={!chosen[1] && !!candidates[1]}
        onView={chosen[1] ? () => onViewChosen(1) : undefined} />
    </div>
    <div className={styles.deckSummary}>
      <div className={styles.deckStats}>
        <span className={styles.slotLabel}>{complete ? (chosen.length === 2 ? 'Your 40-card deck' : 'Together they make') : 'Your deck'}</span>
        {parts.length > 0 ? <>
          <div className={styles.deckColors}><ColorPips colors={colors} size={16} /><strong>{colorsLabel(colors)}</strong></div>
          <span className={styles.deckCounts}>{total('creatures')} creatures · {total('spells')} spells · {total('lands')} lands</span>
        </> : <>
          <div className={styles.deckColors}><strong className={styles.deckEmpty}>Two packs, forty cards</strong></div>
          <span className={styles.deckCounts}>Hover a pack to preview it here.</span>
        </>}
      </div>
      {/* Always drawn, so previewing a pack never changes the bar's height under the cursor. */}
      <CurveBars curve={parts[parts.length - 1]?.curve ?? [0, 0, 0, 0, 0, 0]} base={complete ? parts[0]!.curve : undefined} />
    </div>
  </section>
}

function OfferCard({ offer, base, pending, disabled, onChoose, onExplore, onPreview }: {
  offer: JumpstartOffer
  base: JumpstartOffer | undefined
  pending: boolean
  disabled: boolean
  /** Absent for pick two's offers previewed during pick one. */
  onChoose: (() => void) | undefined
  onExplore: () => void
  onPreview: (previewing: boolean) => void
}) {
  const summary = useMemo(() => summarizePack(offer.cards), [offer])
  const baseColors = useMemo(() => base ? summarizePack(base.cards).colors : undefined, [base])
  const paired = baseColors ? mergeColors(baseColors, summary.colors) : undefined
  return <article className={styles.pack} onMouseEnter={() => onPreview(true)} onMouseLeave={() => onPreview(false)}
    onFocus={() => onPreview(true)}>
    <button className={styles.artFrame} onClick={onExplore} aria-label={`See every card in ${offer.theme}`} tabIndex={-1}>
      <PackArt pack={offer} className={styles.art} />
      <span className={styles.artPips}><ColorPips colors={summary.colors} size={20} /></span>
      {paired && <span className={styles.pairBadge}>Makes {colorsLabel(paired)}</span>}
      <span className={styles.packLabel}>20 cards · lands included</span>
    </button>
    <div className={styles.packBody}>
      <h2>{offer.theme}</h2>
      <ul className={styles.highlights} aria-label="Notable cards">
        {summary.highlights.slice(0, 3).map((card) => <li key={card.name}><RarityDot card={card} /><span>{card.name}</span></li>)}
      </ul>
      <div className={styles.packStats}>
        <CurveBars curve={summary.curve} />
        <span>{summary.creatures} creatures<br />{summary.spells} spells</span>
      </div>
      <div className={styles.packActions}>
        <button className={styles.explore} onClick={onExplore}>Explore this pack</button>
        {onChoose ? <button className={styles.choose} disabled={disabled} onClick={onChoose}>
          {pending ? 'Choosing…' : `Choose ${offer.theme}`}
        </button> : <span className={styles.upcomingTag}>Offered in pick two</span>}
      </div>
    </div>
  </article>
}

type Explorer = { from: 'offers' | 'upcoming' | 'chosen'; index: number }

/** Pack legality, offers and deck assembly all come from the server. */
export function JumpstartOverlay() {
  const lobby = useGameStore((s) => s.lobbyState)
  const pick = useGameStore((s) => s.pickJumpstartPack)
  const undo = useGameStore((s) => s.undoJumpstartPick)
  const leave = useGameStore((s) => s.leaveLobby)
  const error = useGameStore((s) => s.lastError)
  const connection = useGameStore((s) => s.connectionStatus)
  const [pending, setPending] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState<string | null>(null)
  const [previewingUpcoming, setPreviewingUpcoming] = useState<string | null>(null)
  const [explorer, setExplorer] = useState<Explorer | null>(null)
  // Touch has no hover: previewing on tap would reflow the page under the finger.
  const hasHover = useHasHover()
  const state = lobby?.jumpstart
  const roundKey = `${lobby?.lobbyId}:${state?.pickNumber}`
  useEffect(() => { setPending(null) }, [roundKey, error, connection])
  useEffect(() => { setPreviewing(null); setPreviewingUpcoming(null); setExplorer(null) }, [roundKey])
  if (!state || !lobby) return null
  const ready = state.selectedPacks.length === 2
  const chosen = state.selected ?? []
  const base = chosen[0]
  const setName = lobby.settings.setNames.join(' + ')
  const canPick = pending === null && connection === 'connected'
  const choose = (offer: JumpstartOffer) => {
    if (!canPick) return
    setPending(offer.id)
    pick(offer.id, state.pickNumber)
  }
  const upcoming = state.pickNumber === 1 ? state.upcomingOffers ?? [] : []
  // With both rounds on screen, a preview sticks after the pointer leaves, so a pack from one row
  // stays in its slot while the other row is browsed for a partner.
  const planning = upcoming.length > 0
  const candidate = ready ? undefined : state.offers.find((o) => o.id === (pending ?? previewing))
  const upcomingCandidate = upcoming.find((o) => o.id === previewingUpcoming)
  const candidates = state.pickNumber === 1
    ? [candidate, upcomingCandidate] as const
    : [undefined, candidate] as const
  const explorerPacks = explorer?.from === 'chosen' ? chosen : explorer?.from === 'upcoming' ? upcoming : state.offers
  const explorerBase = explorer?.from === 'chosen' ? chosen[0]
    : explorer?.from === 'upcoming' ? candidate : base ?? upcomingCandidate
  const preview = (set: typeof setPreviewing, id: string) => (on: boolean) =>
    hasHover && set((prev) => on ? id : prev === id && !planning ? null : prev)
  const table = lobby.settings.gameMode === 'FREE_FOR_ALL' ? `Free-for-All · ${lobby.players.length} players` : null
  return (
    <div className={styles.overlay}>
      <main className={styles.content}>
        <header className={styles.header}>
          <div>
            <span className={styles.eyebrow}>JUMP IN</span>
            <span className={styles.setName}>{[setName, table].filter(Boolean).join(' · ')}</span>
          </div>
          <button className={styles.leave} onClick={leave}>Leave lobby</button>
        </header>
        <ol className={styles.steps} aria-label="Your deck progress">
          {['First pack', 'Second pack', 'Play'].map((label, i) => <li key={label}
            className={state.selectedPacks.length >= i ? styles.currentStep : ''}
            aria-current={state.selectedPacks.length === i ? 'step' : undefined}>
            <span>{state.selectedPacks.length > i ? '✓' : i + 1}</span>{label}
          </li>)}
        </ol>
        <h1>{ready ? 'Two themes. One deck. Let’s play.' : `Choose your ${state.pickNumber === 1 ? 'first' : 'second'} theme`}</h1>
        <p className={styles.intro}>
          {ready ? 'Your 40-card deck is ready. The game starts when everyone has chosen.'
            : state.pickNumber === 1 ? (planning
              ? 'Find a theme you love, with a partner in mind. Below your choices are the packs your second pick will offer — hover one from each row to see the deck the pair would make. All the lands are included.'
              : 'Find a theme you love. You’ll pair it with a second pack to make your deck — all the lands are included.')
              : `Pick a partner for ${base?.theme ?? 'your first pack'}. Hover a pack to see the deck the two would make, or explore it card by card. Changed your mind? You can still swap your first pack.`}
        </p>
        {state.selectedPacks.length > 0 &&
          <span className={styles.srOnly} aria-label="Chosen themes">{state.selectedPacks.map((name) => `✓ ${name}`).join(' ')}</span>}
        <DeckBar chosen={chosen} candidates={candidates} onViewChosen={(index) => setExplorer({ from: 'chosen', index })}
          onChangeFirst={state.selectedPacks.length === 1 && canPick ? () => { setPending('undo'); undo(state.pickNumber) } : undefined} />
        {connection !== 'connected' && <p role="status" className={styles.intro}>Reconnecting… Your choices are saved.</p>}
        {error && <p className={styles.error} role="alert">{error.message}</p>}
        {!ready && <div className={styles.packs} aria-busy={pending !== null}>
          {state.offers.map((offer, i) => <OfferCard key={`${state.pickNumber}-${offer.id}`}
            offer={offer} base={base ?? upcomingCandidate} pending={pending === offer.id} disabled={!canPick}
            onChoose={() => choose(offer)}
            onExplore={() => setExplorer({ from: 'offers', index: i })}
            onPreview={preview(setPreviewing, offer.id)} />)}
        </div>}
        {!ready && planning && <section aria-label="Second pick offers">
          <h2 className={styles.upcomingHeading}>Your second pick will offer</h2>
          <div className={styles.packs}>
            {upcoming.map((offer, i) => <OfferCard key={`upcoming-${offer.id}`}
              offer={offer} base={candidate} pending={false} disabled
              onChoose={undefined}
              onExplore={() => setExplorer({ from: 'upcoming', index: i })}
              onPreview={preview(setPreviewingUpcoming, offer.id)} />)}
          </div>
        </section>}
        {ready && <section className={styles.waiting} aria-label="Player readiness">
          <h2>At the table</h2>
          <ul>{lobby.players.map((player) => <li key={player.playerId}>
            <span>{player.playerName}{player.isAi ? ' · AI' : ''}</span>
            <span className={player.deckSubmitted ? styles.ready : styles.picking}>
              {player.deckSubmitted ? '✓ Ready to play' : 'Choosing themes…'}
            </span>
          </li>)}</ul>
          <p role="status">Waiting for the rest of the table</p>
        </section>}
        <p className={styles.note}>Published Jumpstart packs · Two picks · No deckbuilding</p>
      </main>
      {explorer && explorerPacks[explorer.index] && <JumpstartPackExplorer
        packs={explorerPacks}
        index={explorer.index}
        onIndex={(index) => setExplorer({ ...explorer, index })}
        base={explorerBase}
        onChoose={explorer.from === 'offers' ? choose : undefined}
        chooseDisabled={!canPick}
        onClose={() => setExplorer(null)}
      />}
    </div>
  )
}
