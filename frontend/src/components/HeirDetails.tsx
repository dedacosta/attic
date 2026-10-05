import type { ReactNode } from 'react'
import { age, localDate } from '../lib/age'
import { documentTypeLabel, formatDate, formatDateTime } from '../lib/format'
import { useModal } from '../lib/useModal'
import { validity } from '../lib/validity'
import { useI18n } from '../i18n'
import { CloseIcon, DownloadIcon, PencilIcon } from './icons'
import ValidityBadge from './ValidityBadge'
import type { Heir, HeirDocument } from '../api/types'

interface Props {
  heir: Heir
  /** Everybody this account sees, for the names of the parent and the children */
  heirs: Heir[]
  documents: HeirDocument[]
  /** Open the heir for editing; left out for those who may not edit them */
  onEdit?: () => void
  onClose: () => void
}

/** Everything about an heir, read-only, with their documents to download. */
export default function HeirDetails({ heir, heirs, documents, onEdit, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const parent = heirs.find((other) => other.id === heir.parentId)
  const children = heirs.filter((other) => other.parentId === heir.id)
  const ageText = heir.birthDate && (!heir.deceased || heir.deathDate)
    ? t.age(age(heir.birthDate, heir.deathDate ? localDate(heir.deathDate) : undefined))
    : null

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="heir-details-title"
      onCancel={(e) => { e.preventDefault(); onClose() }}>
      <header className="dialog-header">
        <h2 id="heir-details-title">{heir.name}</h2>
        <button type="button" className="icon-button" onClick={onClose} aria-label={t.close}>
          <CloseIcon />
        </button>
      </header>

      <div className="dialog-content">
        {heir.createdAt && (
          <p className="timestamps">
            {t.addedOn(formatDateTime(heir.createdAt, t.locale))}
            {heir.updatedAt && heir.updatedAt !== heir.createdAt
              && ` · ${t.changedOn(formatDateTime(heir.updatedAt, t.locale))}`}
          </p>
        )}

        <dl className="details">
          <Detail label={t.birthDate}>
            {heir.birthDate && formatDate(heir.birthDate, t.locale)}
            {heir.birthDate && !heir.deceased && ageText && ` · ${ageText}`}
          </Detail>
          {heir.deceased && (
            <Detail label={t.deathDate}>
              {heir.deathDate ? formatDate(heir.deathDate, t.locale) : t.deceased}
              {heir.deathDate && ageText && ` · ${ageText}`}
            </Detail>
          )}
          <Detail label={t.sex}>{heir.sex && (t.sexes[heir.sex] ?? heir.sex)}</Detail>
          <Detail label={t.heritageShare}>
            {heir.calculatedShare && <strong>{heir.calculatedShare}</strong>}
            {heir.calculatedShare && heir.deceased && children.length > 0 && ` · ${t.sharePassedOn}`}
            {!heir.calculatedShare && heir.deceased && children.length === 0 && t.notInHeritage}
          </Detail>
          <Detail label={t.parentHeir}>{parent?.name}</Detail>
          <Detail label={t.children}>{children.map((child) => child.name).join(', ')}</Detail>
          <Detail label={t.filiation} multiline>{heir.filiation}</Detail>
          <Detail label={t.address} multiline>{heir.address}</Detail>
          <Detail label={t.comments} multiline>{heir.comments}</Detail>
        </dl>

        <section className="heir-documents-section" aria-labelledby="heir-details-documents">
          <div className="section-header">
            <h3 id="heir-details-documents">{t.heirDocuments}</h3>
          </div>
          {documents.length === 0 && <p className="hint">{t.noHeirDocuments}</p>}
          {documents.length > 0 && (
            <ul className="document-list">
              {documents.map((document) => (
                <li key={document.id} className="document-row document-row-static">
                  <span className="document-row-type">{documentTypeLabel(document.type, t)}</span>
                  <ValidityBadge validity={validity(document.validUntil)} />
                  <span className="document-row-date">
                    {document.validUntil ? formatDate(document.validUntil, t.locale) : t.noExpiry}
                  </span>
                  {document.pictureUrl ? (
                    // The server names the file after the heir and the document
                    <a className="button button-small" href={document.pictureUrl} download>
                      <DownloadIcon width={16} height={16} /> {t.download}
                    </a>
                  ) : (
                    <span className="document-row-date">{t.noDocumentFile}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <footer className="dialog-actions">
        <span className="spacer" />
        {onEdit && (
          <button type="button" className="button" onClick={onEdit}>
            <PencilIcon width={18} height={18} /> {t.edit}
          </button>
        )}
        <button type="button" className="button button-primary" onClick={onClose}>{t.close}</button>
      </footer>
    </dialog>
  )
}

const blank = (node: ReactNode): boolean =>
  node === null || node === undefined || node === false || node === '' || (Array.isArray(node) && node.every(blank))

/** One fact about the heir; left out when there is nothing to show */
function Detail({ label, multiline = false, children }: { label: string; multiline?: boolean; children: ReactNode }) {
  if (blank(children)) {
    return null
  }
  return (
    <div>
      <dt>{label}</dt>
      <dd className={multiline ? 'details-multiline' : undefined}>{children}</dd>
    </div>
  )
}
