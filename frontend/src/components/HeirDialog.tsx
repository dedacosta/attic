import { useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/api'
import { documentTypeLabel, formatDate, formatDateTime, shortId } from '../lib/format'
import { parseFraction } from '../lib/fraction'
import { useModal } from '../lib/useModal'
import { validity } from '../lib/validity'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { CloseIcon, PlusIcon, TrashIcon } from './icons'
import ValidityBadge from './ValidityBadge'
import type { Heir, HeirDocument, HeirInput } from '../api/types'

interface Props {
  heir: Heir | null
  sexes: string[]
  documents: HeirDocument[]
  onSave: (input: HeirInput) => Promise<void>
  onDelete: () => void
  onClose: () => void
  onOpenDocument: (document: HeirDocument) => void
  onAddDocument: () => void
}

export default function HeirDialog(props: Props) {
  const { heir, sexes, documents, onSave, onDelete, onClose, onOpenDocument, onAddDocument } = props
  const { t } = useI18n()
  const { canEdit, ownHeirId } = usePermissions()
  // Administrators edit everybody; a user edits their own card, apart from the heritage share
  const editable = canEdit || (heir !== null && heir.id === ownHeirId)
  const ref = useModal()
  const [form, setForm] = useState({
    name: heir?.name ?? '',
    birthDate: heir?.birthDate ?? '',
    sex: heir?.sex ?? '',
    heritageShare: heir?.heritageShare ?? '',
    filiation: heir?.filiation ?? '',
    address: heir?.address ?? '',
    comments: heir?.comments ?? '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!form.name.trim()) {
      return setError(t.errorName)
    }
    if (form.heritageShare.trim() && !parseFraction(form.heritageShare)) {
      return setError(t.errorHeritageShare)
    }
    setSaving(true)
    setError(null)
    try {
      await onSave({
        name: form.name.trim(),
        birthDate: form.birthDate || null,
        sex: form.sex || null,
        heritageShare: form.heritageShare.trim() || null,
        filiation: form.filiation.trim() || null,
        address: form.address.trim() || null,
        comments: form.comments.trim() || null,
      })
    } catch (e) {
      setError(apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="heir-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="heir-dialog-title">
            {!editable ? t.viewHeir : heir ? t.editHeir : t.newHeir}
            {heir && <code className="short-id">{shortId(heir.id)}</code>}
          </h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>

        <div className="dialog-content">
          {heir?.createdAt && (
            <p className="timestamps">
              {t.addedOn(formatDateTime(heir.createdAt, t.locale))}
              {heir.updatedAt && heir.updatedAt !== heir.createdAt
                && ` · ${t.changedOn(formatDateTime(heir.updatedAt, t.locale))}`}
            </p>
          )}
          <fieldset className="fields" disabled={!editable}>
            <label className="field field-wide">
              <span>{t.name}</span>
              <input value={form.name} onChange={(e) => set('name', e.target.value)} required autoFocus={!heir}
                autoComplete="off" />
            </label>
            <label className="field">
              <span>{t.birthDate} <small>{t.optional}</small></span>
              <input type="date" value={form.birthDate} onChange={(e) => set('birthDate', e.target.value)} />
            </label>
            <label className="field">
              <span>{t.sex} <small>{t.optional}</small></span>
              <select value={form.sex} onChange={(e) => set('sex', e.target.value)}>
                <option value="">—</option>
                {sexes.map((sex) => <option key={sex} value={sex}>{t.sexes[sex] ?? sex}</option>)}
              </select>
            </label>
            <label className="field">
              <span>{t.heritageShare} <small>{t.optional}</small></span>
              <input value={form.heritageShare} onChange={(e) => set('heritageShare', e.target.value)} disabled={!canEdit}
                placeholder={t.heritageShareHint} inputMode="numeric" autoComplete="off" />
            </label>
            <label className="field field-wide">
              <span>{t.filiation} <small>{t.filiationHint}</small></span>
              <textarea rows={2} value={form.filiation} onChange={(e) => set('filiation', e.target.value)} />
            </label>
            <label className="field field-wide">
              <span>{t.address} <small>{t.optional}</small></span>
              <textarea rows={2} value={form.address} onChange={(e) => set('address', e.target.value)}
                autoComplete="street-address" />
            </label>
            <label className="field field-wide">
              <span>{t.comments} <small>{t.optional}</small></span>
              <textarea rows={2} value={form.comments} onChange={(e) => set('comments', e.target.value)} />
            </label>
          </fieldset>

          <section className="heir-documents-section" aria-labelledby="heir-documents-title">
            <div className="section-header">
              <h3 id="heir-documents-title">{t.heirDocuments}</h3>
              {heir && canEdit && (
                <button type="button" className="button button-small" onClick={onAddDocument} disabled={saving}>
                  <PlusIcon width={16} height={16} /> {t.addDocument}
                </button>
              )}
            </div>
            {!heir && <p className="hint">{t.saveFirstToAddDocuments}</p>}
            {heir && documents.length === 0 && <p className="hint">{t.noHeirDocuments}</p>}
            {documents.length > 0 && (
              <ul className="document-list">
                {documents.map((document) => (
                  <li key={document.id}>
                    <button type="button" className="document-row" onClick={() => onOpenDocument(document)}>
                      <span className="document-row-type">{documentTypeLabel(document.type, t)}</span>
                      <ValidityBadge validity={validity(document.validUntil)} />
                      <span className="document-row-date">
                        {document.validUntil ? formatDate(document.validUntil, t.locale) : t.noExpiry}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {error && <p className="form-error" role="alert">{error}</p>}
        </div>

        <footer className="dialog-actions">
          {heir && canEdit && (
            <button type="button" className="button button-quiet button-danger-text" onClick={onDelete}
              disabled={saving}>
              <TrashIcon width={18} height={18} /> {t.delete}
            </button>
          )}
          <span className="spacer" />
          <button type="button" className="button" onClick={onClose} disabled={saving}>
            {editable ? t.cancel : t.close}
          </button>
          <button type="submit" className="button button-primary" disabled={saving} hidden={!editable}>
            {saving ? t.saving : heir ? t.save : t.create}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
