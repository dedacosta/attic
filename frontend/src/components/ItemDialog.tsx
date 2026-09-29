import { useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/api'
import { decimalSeparator, locationLabel, shortId } from '../lib/format'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { CloseIcon, TrashIcon } from './icons'
import PhotosField from './PhotosField'
import { PhotoUploadError } from '../lib/photos'
import { useModal } from '../lib/useModal'
import { storedPhotos, type Item, type ItemInput, type PhotoEntry } from '../api/types'

const VALUE_PATTERN = /^\d+([.,]\d{1,2})?$/

interface Props {
  item: Item | null
  locations: string[]
  onSave: (input: ItemInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) => Promise<void>
  onDelete: () => void
  onClose: () => void
}

interface FormState {
  name: string
  quantity: string
  date: string
  location: string
  existent: boolean
  valueEur: string
  owner: string
  comments: string
}

function initialState(item: Item | null, locale: string): FormState {
  return {
    name: item?.name ?? '',
    quantity: String(item?.quantity ?? 1),
    date: item?.date ?? '',
    location: item?.location ?? '',
    existent: item?.existent ?? true,
    valueEur: (item?.valueEur ?? 0).toFixed(2).replace('.', decimalSeparator(locale)),
    owner: item?.owner ?? '',
    comments: item?.comments ?? '',
  }
}

export default function ItemDialog({ item, locations, onSave, onDelete, onClose }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const ref = useModal()
  const [form, setForm] = useState(() => initialState(item, t.locale))
  const [photos, setPhotos] = useState<PhotoEntry[]>(() => storedPhotos(item?.pictures ?? []))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    const name = form.name.trim()
    const quantity = Number(form.quantity)
    const value = form.valueEur.trim() === '' ? '0' : form.valueEur.trim()
    if (!name) {
      return setError(t.errorName)
    }
    if (!Number.isInteger(quantity) || quantity < 0) {
      return setError(t.errorQuantity)
    }
    if (!VALUE_PATTERN.test(value)) {
      return setError(t.errorValue)
    }
    setSaving(true)
    setError(null)
    try {
      await onSave(
        {
          name,
          quantity,
          date: form.date || null,
          location: form.location || null,
          existent: form.existent,
          valueEur: Number(value.replace(',', '.')),
          owner: form.owner.trim(),
          comments: form.comments.trim() || null,
        },
        photos,
        setPhotos,
      )
    } catch (e) {
      setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="item-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="item-dialog-title">
            {!canEdit ? t.viewItem : item ? t.editItem : t.newItemTitle}
            {item && <code className="short-id">{shortId(item.id)}</code>}
          </h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>

        <div className="dialog-content">
          <PhotosField readOnly={!canEdit || saving} photos={photos} onChange={setPhotos} onError={setError} />

          <fieldset className="fields" disabled={!canEdit}>
            <label className="field field-wide">
              <span>{t.name}</span>
              <input value={form.name} onChange={(e) => set('name', e.target.value)} required autoFocus
                placeholder={t.namePlaceholder} />
            </label>
            <label className="field">
              <span>{t.quantity}</span>
              <input type="number" min={0} step={1} inputMode="numeric" value={form.quantity}
                onChange={(e) => set('quantity', e.target.value)} />
            </label>
            <label className="field">
              <span>{t.valueEur}</span>
              <input inputMode="decimal" value={form.valueEur} onChange={(e) => set('valueEur', e.target.value)}
                placeholder={`0${decimalSeparator(t.locale)}00`} />
            </label>
            <label className="field">
              <span>{t.location} <small>{t.optional}</small></span>
              <select value={form.location} onChange={(e) => set('location', e.target.value)}>
                <option value="">—</option>
                {locations.map((location) => (
                  <option key={location} value={location}>{locationLabel(location, t)}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>{t.date} <small>{t.optional}</small></span>
              <input type="date" value={form.date} onChange={(e) => set('date', e.target.value)} />
            </label>
            <label className="field field-wide">
              <span>{t.owner}</span>
              <input value={form.owner} onChange={(e) => set('owner', e.target.value)} placeholder="Heritage" />
            </label>
            <label className="field field-wide">
              <span>{t.comments} <small>{t.optional}</small></span>
              <textarea rows={3} value={form.comments} onChange={(e) => set('comments', e.target.value)} />
            </label>
            <label className="checkbox field-wide">
              <input type="checkbox" checked={form.existent} onChange={(e) => set('existent', e.target.checked)} />
              <span>{t.stillInHouse}</span>
            </label>
          </fieldset>

          {error && <p className="form-error" role="alert">{error}</p>}
        </div>

        <footer className="dialog-actions">
          {item && canEdit && (
            <button type="button" className="button button-quiet button-danger-text" onClick={onDelete}
              disabled={saving}>
              <TrashIcon width={18} height={18} /> {t.delete}
            </button>
          )}
          <span className="spacer" />
          <button type="button" className="button" onClick={onClose} disabled={saving}>
            {canEdit ? t.cancel : t.close}
          </button>
          <button type="submit" className="button button-primary" disabled={saving} hidden={!canEdit}>
            {saving ? t.saving : item ? t.save : t.create}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
