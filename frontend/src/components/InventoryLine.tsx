import { useRef, type KeyboardEvent, type RefObject } from 'react'
import { useI18n } from '../i18n'
import { ChevronIcon, TrashIcon } from './icons'
import type { ItemForm } from '../lib/inventory'

interface Props {
  form: ItemForm
  /** A new line takes the focus, to be typed in right away */
  autoFocus: boolean
  onChange: (form: ItemForm) => void
  /** The line is left, or Enter is pressed on its value: then the next line is wanted */
  onCommit: (next: boolean) => void
  onCancel: () => void
  onRemove: () => void
  /** Move the line up or down; a new line is not moved */
  onMove?: (step: number) => void
  first?: boolean
  last?: boolean
}

/**
 * An item of the Inventory tab edited in place. It is saved when the focus leaves the line, so
 * moving between its fields does not save it. Enter goes to the next field, and on the value to
 * the next line.
 */
export default function InventoryLine({ form, autoFocus, onChange, onCommit, onCancel, onRemove, onMove, first, last }: Props) {
  const { t } = useI18n()
  const description = useRef<HTMLInputElement>(null)
  const value = useRef<HTMLInputElement>(null)

  const onKey = (next: RefObject<HTMLInputElement | null> | null) => (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      if (next) {
        next.current?.focus()
      } else {
        onCommit(true)
      }
    } else if (event.key === 'Escape') {
      onCancel()
    }
  }

  return (
    <li className="inventory-line inventory-line-edit"
      onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) onCommit(false) }}>
      <input type="number" min={0} step={1} inputMode="numeric" value={form.quantity}
        aria-label={t.quantity} title={t.quantity} onKeyDown={onKey(description)}
        onChange={(e) => onChange({ ...form, quantity: e.target.value })} />
      <input ref={description} value={form.description} placeholder={t.description} aria-label={t.description}
        autoFocus={autoFocus} onKeyDown={onKey(value)}
        onChange={(e) => onChange({ ...form, description: e.target.value })} />
      <input ref={value} className="inventory-item-value" inputMode="decimal" value={form.value}
        placeholder={t.valueEur} aria-label={t.valueEur} title={t.valueEur} enterKeyHint="next"
        onKeyDown={onKey(null)} onChange={(e) => onChange({ ...form, value: e.target.value })} />
      <span className="inventory-line-buttons">
        {onMove && (
          <>
            <button type="button" className="icon-button" onClick={() => onMove(-1)} disabled={first}
              aria-label={t.moveUp} title={t.moveUp}>
              <ChevronIcon className="chevron-up" width={16} height={16} />
            </button>
            <button type="button" className="icon-button" onClick={() => onMove(1)} disabled={last}
              aria-label={t.moveDown} title={t.moveDown}>
              <ChevronIcon width={16} height={16} />
            </button>
          </>
        )}
        <button type="button" className="icon-button" onClick={onRemove} aria-label={t.removeItem}
          title={t.removeItem}>
          <TrashIcon width={16} height={16} />
        </button>
      </span>
    </li>
  )
}
