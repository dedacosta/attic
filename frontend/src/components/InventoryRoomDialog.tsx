import { useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/api'
import { AMOUNT_PATTERN, decimalSeparator, parseAmount } from '../lib/format'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { ChevronLeftIcon, ChevronRightIcon, CloseIcon, PlusIcon, TrashIcon } from './icons'
import PhotosField from './PhotosField'
import { PhotoUploadError } from '../lib/photos'
import { useModal } from '../lib/useModal'
import { storedPhotos, type InventoryItem, type InventoryRoom, type InventoryRoomInput, type PhotoEntry } from '../api/types'

interface Props {
  /** The room to edit, or one of the lists of the house: its other items, its missing items */
  room: InventoryRoom | 'other' | 'missing'
  /** The items of the list of the house, when one of those is edited */
  listItems: InventoryItem[]
  onSaveRoom: (input: Pick<InventoryRoomInput, 'pdfTitle' | 'comments' | 'items'>, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) => Promise<void>
  onSaveList: (items: InventoryItem[]) => Promise<void>
  onClose: () => void
}

/** An item while it is edited: the quantity and the value as typed */
interface ItemForm {
  description: string
  quantity: string
  value: string
}

/**
 * A room of the inventory with its title for the PDF, photos, comments and items, saved together.
 * Its name is the one it has on the house. The
 * lists of the house, its other items and its missing items, are only lists of items. Rooms are
 * added and removed on the house, not here.
 */
export default function InventoryRoomDialog({ room, listItems, onSaveRoom, onSaveList, onClose }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const ref = useModal()
  const existing = room !== 'other' && room !== 'missing' ? room : null
  const isList = existing === null
  // No value is shown as an empty field, not as 0
  const toForm = (item: InventoryItem): ItemForm => ({
    description: item.description,
    quantity: String(item.quantity),
    value: item.valueEur ? item.valueEur.toFixed(2).replace('.', decimalSeparator(t.locale)) : '',
  })
  const [pdfTitle, setPdfTitle] = useState(existing?.pdfTitle ?? '')
  const [comments, setComments] = useState(existing?.comments ?? '')
  const [items, setItems] = useState<ItemForm[]>(() => (existing ? existing.items : listItems).map(toForm))
  const [photos, setPhotos] = useState<PhotoEntry[]>(() => storedPhotos(existing?.pictures ?? []))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const setItem = (index: number, change: Partial<ItemForm>) =>
    setItems((list) => list.map((item, i) => (i === index ? { ...item, ...change } : item)))

  function move(index: number, step: number) {
    setItems((list) => {
      const moved = [...list]
      ;[moved[index], moved[index + step]] = [moved[index + step], moved[index]]
      return moved
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    // Lines left without a description are not kept
    const lines = items.filter((item) => item.description.trim())
    if (lines.some((item) => !Number.isInteger(Number(item.quantity)) || Number(item.quantity) < 0 || item.quantity.trim() === '')) {
      return setError(t.errorQuantity)
    }
    if (lines.some((item) => item.value.trim() !== '' && !AMOUNT_PATTERN.test(item.value.trim()))) {
      return setError(t.errorValue)
    }
    const saved = lines.map((item) => ({
      description: item.description.trim(),
      quantity: Number(item.quantity),
      valueEur: item.value.trim() ? parseAmount(item.value) : 0,
    }))
    setSaving(true)
    setError(null)
    try {
      if (isList) {
        await onSaveList(saved)
      } else {
        await onSaveRoom({ pdfTitle: pdfTitle.trim() || null, comments: comments.trim() || null, items: saved }, photos, setPhotos)
      }
    } catch (e) {
      setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="inventory-room-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="inventory-room-dialog-title">
            {existing ? existing.title : room === 'missing' ? t.missingItems : t.otherItems}
          </h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>

        <div className="dialog-content">
          {!isList && <PhotosField readOnly={!canEdit || saving} photos={photos} onChange={setPhotos} onError={setError} />}

          <fieldset className="fields" disabled={!canEdit}>
            {!isList && (
              <>
                <label className="field field-wide">
                  <span>{t.pdfRoomTitle} <small>{t.optional}</small></span>
                  <input value={pdfTitle} onChange={(e) => setPdfTitle(e.target.value)} placeholder={existing?.title}
                    title={t.pdfRoomTitleHint} />
                </label>
                <label className="field field-wide">
                  <span>{t.comments} <small>{t.optional}</small></span>
                  <textarea rows={3} value={comments} onChange={(e) => setComments(e.target.value)} />
                </label>
              </>
            )}

            <div className="details-editor field-wide">
              <span className="details-editor-title">{t.roomItems}</span>
              {items.map((item, index) => (
                <div key={index} className="inventory-item-row">
                  <input type="number" min={0} step={1} inputMode="numeric" value={item.quantity}
                    aria-label={t.quantity} title={t.quantity}
                    onChange={(e) => setItem(index, { quantity: e.target.value })} />
                  <input value={item.description} placeholder={t.description} aria-label={t.description}
                    onChange={(e) => setItem(index, { description: e.target.value })} />
                  <input className="inventory-item-value" inputMode="decimal" value={item.value}
                    placeholder={t.valueEur} aria-label={t.valueEur} title={t.valueEur}
                    onChange={(e) => setItem(index, { value: e.target.value })} />
                  <button type="button" className="icon-button details-move" onClick={() => move(index, -1)}
                    disabled={index === 0} aria-label={t.moveUp}>
                    <ChevronLeftIcon width={16} height={16} />
                  </button>
                  <button type="button" className="icon-button details-move" onClick={() => move(index, 1)}
                    disabled={index === items.length - 1} aria-label={t.moveDown}>
                    <ChevronRightIcon width={16} height={16} />
                  </button>
                  <button type="button" className="icon-button"
                    onClick={() => setItems((list) => list.filter((_, i) => i !== index))} aria-label={t.removeItem}>
                    <TrashIcon width={16} height={16} />
                  </button>
                </div>
              ))}
              {items.length === 0 && !canEdit && <p className="hint">{t.emptyRoom}</p>}
              {canEdit && (
                <button type="button" className="button button-quiet"
                  onClick={() => setItems((list) => [...list, { description: '', quantity: '1', value: '' }])}>
                  <PlusIcon width={16} height={16} /> {t.addItem}
                </button>
              )}
            </div>
          </fieldset>

          {error && <p className="form-error" role="alert">{error}</p>}
        </div>

        <footer className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="button" onClick={onClose} disabled={saving}>
            {canEdit ? t.cancel : t.close}
          </button>
          <button type="submit" className="button button-primary" disabled={saving} hidden={!canEdit}>
            {saving ? t.saving : t.save}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
