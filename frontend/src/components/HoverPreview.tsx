import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Picture } from '../api/types'

/** How long the mouse rests on the photo before it is shown larger, in ms */
const DELAY = 600

/** The size of the larger photo, in px, and its distance from the card */
const SIZE = 320
const GAP = 12

interface Props {
  picture: Picture | undefined
  children: ReactNode
}

/**
 * Shows a photo larger next to what it wraps once the mouse rests on it, and hides it when the
 * mouse leaves. The small photo is shown at once, the full one over it once loaded. Not on touch
 * screens, and not for photos the browser cannot show (without a thumbnail).
 */
export default function HoverPreview({ picture, children }: Props) {
  const frame = useRef<HTMLDivElement>(null)
  const timer = useRef<number | undefined>(undefined)
  const [place, setPlace] = useState<{ left: number; top: number } | null>(null)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  // Fixed in the window, it would no longer be next to the card once the page scrolls
  useEffect(() => {
    if (!place) {
      return
    }
    const close = () => setPlace(null)
    window.addEventListener('scroll', close, { capture: true, passive: true })
    return () => window.removeEventListener('scroll', close, { capture: true })
  }, [place])

  function show() {
    const box = frame.current?.getBoundingClientRect()
    if (!box) {
      return
    }
    // Right of the card, or left of it when there is no room; within the window from top to bottom
    const right = box.right + GAP + SIZE <= window.innerWidth
    const left = right ? box.right + GAP : Math.max(GAP, box.left - GAP - SIZE)
    const top = Math.min(Math.max(GAP, box.top + box.height / 2 - SIZE / 2), window.innerHeight - SIZE - GAP)
    setPlace({ left, top })
  }

  function hide() {
    window.clearTimeout(timer.current)
    setPlace(null)
  }

  return (
    <div ref={frame} className="hover-preview-frame"
      onPointerEnter={(e) => {
        if (e.pointerType === 'mouse' && picture?.thumbnailUrl) {
          timer.current = window.setTimeout(show, DELAY)
        }
      }}
      onPointerLeave={hide} onPointerDown={hide}>
      {children}
      {place && picture?.thumbnailUrl && createPortal(
        <div className="hover-preview" aria-hidden="true"
          style={{ left: place.left, top: place.top, width: SIZE, height: SIZE,
            backgroundImage: `url("${picture.thumbnailUrl}")` }}>
          <img src={picture.url} alt="" />
        </div>,
        document.body,
      )}
    </div>
  )
}
