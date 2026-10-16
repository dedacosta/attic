import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ComponentType, type SVGProps } from 'react'
import { useI18n } from '../i18n'
import { ChevronLeftIcon, ChevronRightIcon } from './icons'

export interface SectionTab {
  id: string
  href: string
  label: string
  Icon: ComponentType<SVGProps<SVGSVGElement>>
}

interface Props {
  tabs: readonly SectionTab[]
  current: string
}

/**
 * The row of section tabs. When they do not all fit, the row scrolls sideways, and an arrow on
 * each side that has more tabs scrolls it by most of its width.
 */
export default function SectionTabs({ tabs, current }: Props) {
  const { t } = useI18n()
  const row = useRef<HTMLElement>(null)
  const [more, setMore] = useState({ before: false, after: false })
  // The tabs are a new list on each render: only a change of which tabs there are matters
  const ids = tabs.map((tab) => tab.id).join()

  const update = useCallback(() => {
    const el = row.current
    if (!el) {
      return
    }
    // A pixel of slack: zoomed pages report fractional positions
    const before = el.scrollLeft > 1
    const after = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
    setMore((m) => (m.before === before && m.after === after ? m : { before, after }))
  }, [])

  useEffect(() => {
    const el = row.current
    if (!el) {
      return
    }
    // The row changes size with the window, and its tabs with the language
    const observer = new ResizeObserver(update)
    observer.observe(el)
    Array.from(el.children).forEach((tab) => observer.observe(tab))
    el.addEventListener('scroll', update, { passive: true })
    return () => {
      observer.disconnect()
      el.removeEventListener('scroll', update)
    }
  }, [ids, update])

  // Keep the current tab in sight, also when the page opens on a tab at the far end
  useLayoutEffect(() => {
    const el = row.current
    const tab = el?.querySelector<HTMLElement>('[aria-current="page"]')
    if (!el || !tab) {
      return
    }
    // The row is positioned, so offsetLeft counts from the start of its scrolled content
    const left = tab.offsetLeft
    if (left < el.scrollLeft || left + tab.offsetWidth > el.scrollLeft + el.clientWidth) {
      el.scrollLeft = left - (el.clientWidth - tab.offsetWidth) / 2
    }
  }, [current, ids])

  const scroll = (direction: -1 | 1) =>
    row.current?.scrollBy({ left: direction * row.current.clientWidth * 0.7, behavior: 'smooth' })

  return (
    <div className="tab-strip">
      <nav ref={row} className="tabs" aria-label={t.sections}>
        {tabs.map(({ id, href, label, Icon }) => (
          <a key={id} href={href} className="tab" aria-current={current === id ? 'page' : undefined}>
            <Icon width={18} height={18} /> {label}
          </a>
        ))}
      </nav>
      {/* Keyboard users reach every tab with Tab, which scrolls it into view: the arrows are for pointers */}
      {more.before && (
        <button type="button" className="tab-scroll tab-scroll-before" tabIndex={-1} aria-label={t.scrollTabsBack}
          onClick={() => scroll(-1)}>
          <ChevronLeftIcon width={18} height={18} />
        </button>
      )}
      {more.after && (
        <button type="button" className="tab-scroll tab-scroll-after" tabIndex={-1} aria-label={t.scrollTabsForward}
          onClick={() => scroll(1)}>
          <ChevronRightIcon width={18} height={18} />
        </button>
      )}
    </div>
  )
}
