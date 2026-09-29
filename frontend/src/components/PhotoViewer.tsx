import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useI18n } from '../i18n'
import { useModal } from '../lib/useModal'
import { ChevronLeftIcon, ChevronRightIcon, CloseIcon } from './icons'

interface Props {
  /** Full-size photo URLs, in order */
  sources: string[]
  /** Index of the photo to show first */
  start: number
  onClose: () => void
}

/** Minimum horizontal finger movement, in pixels, that counts as a swipe */
const SWIPE_DISTANCE = 50

/** Full-screen photos with previous / next buttons, arrow keys and swipe. */
export default function PhotoViewer({ sources, start, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const [index, setIndex] = useState(start)
  const swipeStart = useRef<number | null>(null)
  const many = sources.length > 1

  const go = (step: number) => setIndex((i) => (i + step + sources.length) % sources.length)

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'ArrowLeft') {
      go(-1)
    } else if (event.key === 'ArrowRight') {
      go(1)
    }
  }

  function onPointerUp(event: PointerEvent) {
    if (swipeStart.current === null) {
      return
    }
    const distance = event.clientX - swipeStart.current
    swipeStart.current = null
    if (many && Math.abs(distance) >= SWIPE_DISTANCE) {
      go(distance < 0 ? 1 : -1)
    }
  }

  return (
    <dialog ref={ref} className="photo-viewer" aria-label={t.photoOf(index + 1, sources.length)}
      onCancel={(e) => { e.preventDefault(); onClose() }} onKeyDown={onKeyDown}>
      <div className="photo-viewer-stage"
        onPointerDown={(e) => { swipeStart.current = e.clientX }}
        onPointerUp={onPointerUp}
        onPointerCancel={() => { swipeStart.current = null }}>
        <img src={sources[index]} alt={t.photoOf(index + 1, sources.length)} draggable={false} />
      </div>
      <button type="button" className="icon-button photo-viewer-close" onClick={onClose} aria-label={t.close}>
        <CloseIcon />
      </button>
      {many && (
        <>
          <button type="button" className="icon-button photo-viewer-previous" onClick={() => go(-1)}
            aria-label={t.previousPhoto}>
            <ChevronLeftIcon />
          </button>
          <button type="button" className="icon-button photo-viewer-next" onClick={() => go(1)}
            aria-label={t.nextPhoto}>
            <ChevronRightIcon />
          </button>
          <p className="photo-viewer-count" aria-hidden="true">{index + 1} / {sources.length}</p>
        </>
      )}
    </dialog>
  )
}
