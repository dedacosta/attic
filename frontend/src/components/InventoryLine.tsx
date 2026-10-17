import { useRef, type KeyboardEvent, type RefObject } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useI18n } from '../i18n'
import { GripIcon, TrashIcon } from './icons'
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
  /** The line among the others of its room, to be dragged by its grip; none for a new line or while searching */
  sortId?: string
  /** The lines of the room are moved: a line that is not keeps the room of the grip, to line up with them */
  inSortable?: boolean
}

/**
 * An item of the Inventory tab edited in place. It is saved when the focus leaves the line, so
 * moving between its fields does not save it. Enter goes to the next field, and on the value to
 * the next line. Its grip moves it within its room.
 */
export default function InventoryLine({ form, autoFocus, onChange, onCommit, onCancel, onRemove, sortId, inSortable }: Props) {
  const { t } = useI18n()
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: sortId ?? 'new-line', disabled: !sortId })
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
    <li ref={setNodeRef} className={`inventory-line inventory-line-edit${sortId || inSortable ? ' inventory-line-sortable' : ''}${isDragging ? ' inventory-line-dragging' : ''}`}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) onCommit(false) }}>
      {sortId ? (
        <button type="button" ref={setActivatorNodeRef} className="icon-button inventory-line-grip" {...attributes}
          {...listeners} aria-label={t.moveItem} title={t.moveItem}>
          <GripIcon width={16} height={16} />
        </button>
      ) : inSortable && <span className="inventory-line-grip-space" />}
      <input type="number" min={0} step={1} inputMode="numeric" value={form.quantity}
        aria-label={t.quantity} title={t.quantity} onKeyDown={onKey(description)}
        onChange={(e) => onChange({ ...form, quantity: e.target.value })} />
      <input ref={description} className="inventory-line-description-field" value={form.description} placeholder={t.description} aria-label={t.description}
        autoFocus={autoFocus} onKeyDown={onKey(value)}
        onChange={(e) => onChange({ ...form, description: e.target.value })} />
      <input ref={value} className="inventory-item-value" inputMode="decimal" value={form.value}
        placeholder={t.valueEur} aria-label={t.valueEur} title={t.valueEur} enterKeyHint="next"
        onKeyDown={onKey(null)} onChange={(e) => onChange({ ...form, value: e.target.value })} />
      <button type="button" className="icon-button" onClick={onRemove} aria-label={t.removeItem}
        title={t.removeItem}>
        <TrashIcon width={16} height={16} />
      </button>
    </li>
  )
}
