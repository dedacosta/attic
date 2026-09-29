import { useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/api'
import { byName, documentTypeLabel, shortId } from '../lib/format'
import { useModal } from '../lib/useModal'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { CloseIcon, TrashIcon } from './icons'
import PhotosField from './PhotosField'
import { PhotoUploadError } from '../lib/photos'
import { storedPhotos, type DocumentInput, type Heir, type HeirDocument, type PhotoEntry } from '../api/types'

interface Props {
  document: HeirDocument | null
  types: string[]
  heirs: Heir[]
  /** Heir to select for a new document, e.g. the one being filtered on */
  defaultHeirId?: string
  onSave: (input: DocumentInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) => Promise<void>
  onDelete: () => void
  onClose: () => void
}

export default function DocumentDialog({ document, types, heirs, defaultHeirId, onSave, onDelete, onClose }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const ref = useModal()
  const [heirId, setHeirId] = useState(document?.heirId ?? defaultHeirId ?? '')
  const [type, setType] = useState(document?.type ?? '')
  const [validUntil, setValidUntil] = useState(document?.validUntil ?? '')
  const [comments, setComments] = useState(document?.comments ?? '')
  const [photos, setPhotos] = useState<PhotoEntry[]>(() => storedPhotos(document?.pictures ?? []))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!type) {
      return setError(t.errorType)
    }
    setSaving(true)
    setError(null)
    try {
      await onSave(
        { heirId: heirId || null, type, validUntil: validUntil || null, comments: comments.trim() || null },
        photos,
        setPhotos,
      )
    } catch (e) {
      setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="document-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="document-dialog-title">
            {!canEdit ? t.viewDocument : document ? t.editDocument : t.newDocument}
            {document && <code className="short-id">{shortId(document.id)}</code>}
          </h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>

        <div className="dialog-content">
          <PhotosField readOnly={!canEdit || saving} photos={photos} onChange={setPhotos} onError={setError} />

          <fieldset className="fields" disabled={!canEdit}>
            <label className="field">
              <span>{t.heir} <small>{t.optional}</small></span>
              <select value={heirId} onChange={(e) => setHeirId(e.target.value)}>
                <option value="">{t.noHeir}</option>
                {byName(heirs, t.locale).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </label>
            <label className="field">
              <span>{t.documentType}</span>
              <select value={type} onChange={(e) => setType(e.target.value)} required>
                <option value="" disabled>—</option>
                {types.map((value) => (
                  <option key={value} value={value}>{documentTypeLabel(value, t)}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>{t.validUntil} <small>{t.optional}</small></span>
              <input type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
            </label>
            <label className="field field-wide">
              <span>{t.comments} <small>{t.optional}</small></span>
              <textarea rows={3} value={comments} onChange={(e) => setComments(e.target.value)} />
            </label>
          </fieldset>

          {error && <p className="form-error" role="alert">{error}</p>}
        </div>

        <footer className="dialog-actions">
          {document && canEdit && (
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
            {saving ? t.saving : document ? t.save : t.create}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
