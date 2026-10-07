import { useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTableTalkStore } from '@/store/tableTalkStore'
import type { EntityId } from '@/types'
import { emoteInfo } from './emotes'
import styles from './TableTalk.module.css'

/**
 * The speech bubble over a seat's life orb while their last emote is showing. [placement] says which
 * side of the orb has room — above yours at the bottom of the screen, below an opponent's at the top.
 *
 * Rendered where the orb is (an invisible anchor fills the orb, which is `position: relative`) but
 * portalled to the body at the anchor's rect: the HUD row the orb lives in clips its overflow and
 * stacks below the battlefields, so a bubble drawn in place would be cut off.
 */
export function EmoteBubble({ playerId, placement }: { playerId: EntityId; placement: 'above' | 'below' }) {
  const bubble = useTableTalkStore((s) => s.bubbles[playerId])
  const anchorRef = useRef<HTMLSpanElement>(null)
  const [rect, setRect] = useState<DOMRect | null>(null)

  useLayoutEffect(() => {
    if (!bubble) return
    setRect(anchorRef.current?.getBoundingClientRect() ?? null)
  }, [bubble])

  const info = bubble ? emoteInfo(bubble.emote) : null
  return (
    <>
      <span ref={anchorRef} className={styles.bubbleAnchor} aria-hidden />
      {bubble && info && rect && createPortal(
        <div
          // Keyed on the bubble id so a repeat of the same emote replays the pop.
          key={bubble.id}
          className={styles.bubble}
          data-placement={placement}
          style={{
            left: rect.left + rect.width / 2,
            ...(placement === 'above'
              ? { bottom: window.innerHeight - rect.top + 12 }
              : { top: rect.bottom + 12 }),
          }}
          role="status"
          aria-live="polite"
          data-testid={`emote-bubble-${playerId}`}
        >
          <span className={styles.bubbleIcon} aria-hidden>{info.icon}</span>
          <span className={styles.bubbleText}>{info.label}</span>
        </div>,
        document.body,
      )}
    </>
  )
}
