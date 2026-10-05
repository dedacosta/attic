import { useState, type FormEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { saveWithPhotos, PhotoUploadError } from '../lib/photos'
import { useModal } from '../lib/useModal'
import ConfirmDialog from './ConfirmDialog'
import { CloseIcon, TrashIcon } from './icons'
import PhotosField from './PhotosField'
import { storedPhotos, type PhotoEntry, type PropertyDocument } from '../api/types'

interface Props {
  propertyId: string
  /** Null to add a new document */
  document: PropertyDocument | null
  types: string[]
  onChanged: () => void
  onClose: () => void
}

/** A document with its files under the name saveWithPhotos expects */
const withPictures = (document: PropertyDocument) => ({ ...document, pictures: document.files })

/** An official document of a building or a land parcel, with its image or PDF files. */
export default function PropertyDocumentDialog({ propertyId, document, types, onChanged, onClose }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const ref = useModal()
  const [saved, setSaved] = useState<PropertyDocument | null>(document)
  const [type, setType] = useState(document?.type ?? '')
  const [date, setDate] = useState(document?.date ?? '')
  const [notes, setNotes] = useState(document?.notes ?? '')
  const [files, setFiles] = useState<PhotoEntry[]>(() => storedPhotos(document?.files ?? []))
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!type) {
      return setError(t.errorType)
    }
    const input = { type, date: date || null, notes: notes.trim() || null }
    setSaving(true)
    setError(null)
    try {
      // From the first save on the document exists: a retry after a failed upload updates it
      await saveWithPhotos(
        async () => withPictures(saved
          ? await api.updatePropertyDocument(saved.id, input)
          : await api.createPropertyDocument(propertyId, input)),
        '/api/property-documents', files, setSaved, setFiles,
      )
      onChanged()
      onClose()
    } catch (e) {
      setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  async function confirmDelete() {
    if (saved) {
      await api.deletePropertyDocument(saved.id)
    }
    onChanged()
    onClose()
  }

  const typeLabel = (value: string) => t.propertyDocumentTypes[value] ?? value

  return (
    <>
      <dialog ref={ref} className="dialog" aria-labelledby="property-document-title"
        onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
        <form onSubmit={submit} noValidate>
          <header className="dialog-header">
            <h2 id="property-document-title">
              {!canEdit ? (document ? typeLabel(document.type) : t.viewDocument) : saved ? t.editDocument : t.newDocument}
            </h2>
            <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
              <CloseIcon />
            </button>
          </header>

          <div className="dialog-content">
            <PhotosField allowPdf readOnly={!canEdit || saving} photos={files} onChange={setFiles} onError={setError} />

            <fieldset className="fields" disabled={!canEdit || saving}>
              <label className="field">
                <span>{t.documentType}</span>
                <select value={type} onChange={(e) => setType(e.target.value)} required>
                  <option value="" disabled>—</option>
                  {types.map((value) => <option key={value} value={value}>{typeLabel(value)}</option>)}
                </select>
              </label>
              <label className="field">
                <span>{t.date} <small>{t.optional}</small></span>
                <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
              </label>
              <label className="field field-wide">
                <span>{t.notes} <small>{t.optional}</small></span>
                <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </label>
            </fieldset>

            {error && <p className="form-error" role="alert">{error}</p>}
          </div>

          <footer className="dialog-actions">
            {saved && canEdit && (
              <button type="button" className="button button-quiet button-danger-text"
                onClick={() => setConfirmingDelete(true)} disabled={saving}>
                <TrashIcon width={18} height={18} /> {t.delete}
              </button>
            )}
            <span className="spacer" />
            <button type="button" className="button" onClick={onClose} disabled={saving}>
              {canEdit ? t.cancel : t.close}
            </button>
            <button type="submit" className="button button-primary" disabled={saving} hidden={!canEdit}>
              {saving ? t.saving : saved ? t.save : t.create}
            </button>
          </footer>
        </form>
      </dialog>

      {confirmingDelete && saved && (
        <ConfirmDialog
          title={t.deleteDocumentTitle}
          message={t.deleteMessage(typeLabel(saved.type))}
          confirmLabel={t.delete}
          onConfirm={confirmDelete}
          onCancel={() => setConfirmingDelete(false)}
        />
      )}
    </>
  )
}
