import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useGameStore } from '@/store/gameStore'
import { EMOTE_COOLDOWN_MS, useTableTalkStore } from '@/store/tableTalkStore'
import { EMOTES } from './emotes'
import styles from './TableTalk.module.css'

/**
 * The emote button beside your life orb and the picker it opens: eight presets, with 1–8 as
 * shortcuts while it's open, and a mute switch per opponent underneath. Sending closes the picker
 * and starts a short cooldown, drawn as a ring draining around the button.
 *
 * The picker is portalled to the body and pinned to the button's rect: the HUD row the button lives
 * in stacks below the battlefields, so a popover rendered in place would open underneath them.
 */
export function EmotePicker({ opensUp = true }: { opensUp?: boolean }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const pickerRef = useRef<HTMLDivElement>(null)
  const [anchor, setAnchor] = useState<DOMRect | null>(null)
  useLayoutEffect(() => {
    if (!open) return
    const place = () => setAnchor(rootRef.current?.getBoundingClientRect() ?? null)
    place()
    window.addEventListener('resize', place)
    return () => window.removeEventListener('resize', place)
  }, [open])
  const sendEmote = useTableTalkStore((s) => s.sendEmote)
  const lastSentAt = useTableTalkStore((s) => s.lastSentAt)
  const muted = useTableTalkStore((s) => s.muted)
  const toggleMute = useTableTalkStore((s) => s.toggleMute)
  const players = useGameStore((s) => s.gameState?.players)
  const viewerId = useGameStore((s) => s.playerId)
  const opponents = useMemo(() => players?.filter((p) => p.playerId !== viewerId) ?? [], [players, viewerId])
  const cooling = useCooldown(lastSentAt)

  const send = (index: number) => {
    const info = EMOTES[index]
    if (!info || cooling) return
    sendEmote(info.emote)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setOpen(false); return }
      const n = Number(e.key)
      if (Number.isInteger(n) && n >= 1 && n <= EMOTES.length) {
        e.preventDefault()
        send(n - 1)
      }
    }
    const onPointer = (e: PointerEvent) => {
      const target = e.target as Node
      if (!rootRef.current?.contains(target) && !pickerRef.current?.contains(target)) setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('pointerdown', onPointer, true)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('pointerdown', onPointer, true)
    }
  })

  return (
    <div ref={rootRef} className={styles.pickerRoot} onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        className={styles.emoteButton}
        data-open={open || undefined}
        data-cooling={cooling || undefined}
        style={cooling ? { ['--cooldown-ms' as string]: `${EMOTE_COOLDOWN_MS}ms` } : undefined}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Emotes"
        title="Emotes"
        data-testid="emote-button"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.8-.8L3 20.5l1.5-4.3A8 8 0 0 1 3 11.5 8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5z" />
          <path d="M8.5 13.5s1.2 1.6 3.5 1.6 3.5-1.6 3.5-1.6" />
          <path d="M9 9.5h.01M15 9.5h.01" strokeWidth="2.6" />
        </svg>
      </button>

      {open && anchor && createPortal(
        <div
          ref={pickerRef}
          className={styles.picker}
          data-direction={opensUp ? 'up' : 'down'}
          role="menu"
          aria-label="Send an emote"
          onClick={(e) => e.stopPropagation()}
          style={{
            left: Math.max(8, Math.min(anchor.left - 6, window.innerWidth - 324)),
            ...(opensUp
              ? { bottom: window.innerHeight - anchor.top + 10 }
              : { top: anchor.bottom + 10 }),
          }}
        >
          <div className={styles.grid}>
            {EMOTES.map((info, i) => (
              <button
                key={info.emote}
                type="button"
                role="menuitem"
                className={styles.emoteOption}
                onClick={() => send(i)}
                disabled={cooling}
                data-testid={`emote-${info.emote}`}
              >
                <span className={styles.optionIcon} aria-hidden>{info.icon}</span>
                <span className={styles.optionLabel}>{info.label}</span>
                <kbd className={styles.optionKey} aria-hidden>{i + 1}</kbd>
              </button>
            ))}
          </div>
          {opponents.length > 0 && (
            <div className={styles.muteList}>
              {opponents.map((p) => (
                <label key={p.playerId} className={styles.muteRow}>
                  <span className={styles.muteName}>Show {opponents.length > 1 ? `${p.name}’s` : 'their'} emotes</span>
                  <input
                    type="checkbox"
                    role="switch"
                    className={styles.switch}
                    checked={!muted[p.playerId]}
                    onChange={() => toggleMute(p.playerId)}
                    data-testid={`emote-mute-${p.playerId}`}
                  />
                </label>
              ))}
            </div>
          )}
        </div>,
        document.body,
      )}
    </div>
  )
}

/** True while the last send is inside the cooldown; re-renders once when it lapses. */
function useCooldown(lastSentAt: number): boolean {
  const [, tick] = useState(0)
  const remaining = lastSentAt + EMOTE_COOLDOWN_MS - Date.now()
  useEffect(() => {
    if (remaining <= 0) return
    const id = window.setTimeout(() => tick((n) => n + 1), remaining)
    return () => window.clearTimeout(id)
  }, [remaining])
  return remaining > 0
}
