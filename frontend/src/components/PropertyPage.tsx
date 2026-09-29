import { useEffect, useState } from 'react'
import { api } from '../api/api'
import { formatDate, formatEuros } from '../lib/format'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import ConfirmDialog from './ConfirmDialog'
import { FileIcon, HouseIcon, LandIcon, MapPinIcon, PlusIcon, TrashIcon } from './icons'
import PhotosField from './PhotosField'
import PropertyDialog from './PropertyDialog'
import PropertyDocumentDialog from './PropertyDocumentDialog'
import { storedPhotos, type Property, type PropertyDocument } from '../api/types'

interface Props {
  property: Property
  /** Reload after an edit, a new document, ... */
  onChanged: () => void
  /** Offered for land only: the house is not deleted from its page */
  onDeleted?: () => void
}

export const mapsUrl = (address: string) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`

/** The page of the house or a land parcel: photos, main facts, details, official documents, comments. */
export default function PropertyPage({ property, onChanged, onDeleted }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [editing, setEditing] = useState(false)
  const [openDocument, setOpenDocument] = useState<PropertyDocument | 'new' | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [documentTypes, setDocumentTypes] = useState<string[]>([])

  useEffect(() => {
    api.propertyDocumentTypes().then(setDocumentTypes, () => setDocumentTypes([]))
  }, [])

  const cover = property.pictures[0]
  const isHouse = property.kind === 'HOUSE'
  const PlaceholderIcon = isHouse ? HouseIcon : LandIcon

  return (
    <main className="content property-page">
      <section className="property-hero">
        <div className="property-cover">
          {cover ? <img src={cover.url} alt="" /> : <PlaceholderIcon width={64} height={64} />}
        </div>
        <div className="property-summary">
          <h1 className="property-name">{property.name}</h1>
          {property.address && (
            <p className="property-address">
              <MapPinIcon width={18} height={18} />
              <span>{property.address}</span>
              <a className="link" href={mapsUrl(property.address)} target="_blank" rel="noreferrer">{t.openInMaps}</a>
            </p>
          )}
          <p className="property-value">
            <span>{t.estimatedValue}</span>
            <strong>{property.valueEur != null ? formatEuros(property.valueEur, t.locale) : t.notEstimated}</strong>
          </p>
          {canEdit && (
            <div className="property-actions">
              <button type="button" className="button button-primary" onClick={() => setEditing(true)}>
                {isHouse ? t.editHouse : t.editLand}
              </button>
              {onDeleted && (
                <button type="button" className="button button-quiet button-danger-text" onClick={() => setDeleting(true)}>
                  <TrashIcon width={18} height={18} /> {t.delete}
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      {property.facts.length > 0 && (
        <section className="property-section">
          <h2>{t.details}</h2>
          <dl className="property-facts">
            {property.facts.map((fact, index) => (
              <div key={index}>
                <dt>{fact.label}</dt>
                <dd>{fact.value}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      <section className="property-section">
        <h2>{t.photos}</h2>
        {property.pictures.length > 0
          ? <PhotosField readOnly photos={storedPhotos(property.pictures)} onChange={() => {}} onError={() => {}} />
          : <p className="muted">{t.noPhotos}</p>}
      </section>

      <section className="property-section">
        <div className="property-section-header">
          <h2>{t.officialDocuments}</h2>
          {canEdit && (
            <button type="button" className="button" onClick={() => setOpenDocument('new')}>
              <PlusIcon width={16} height={16} /> {t.addDocument}
            </button>
          )}
        </div>
        {property.documents.length === 0 && <p className="muted">{t.noOfficialDocuments}</p>}
        <ul className="property-documents">
          {property.documents.map((document) => (
            <li key={document.id}>
              <button type="button" className="property-document" onClick={() => setOpenDocument(document)}>
                <FileIcon width={22} height={22} />
                <span className="property-document-text">
                  <strong>{t.propertyDocumentTypes[document.type] ?? document.type}</strong>
                  <span>
                    {[document.date && formatDate(document.date, t.locale), t.fileCount(document.files.length)]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                  {document.notes && <span className="property-document-notes">{document.notes}</span>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      {property.comments && (
        <section className="property-section">
          <h2>{t.comments}</h2>
          <p className="property-comments">{property.comments}</p>
        </section>
      )}

      {editing && (
        <PropertyDialog property={property} kind={property.kind}
          onSaved={() => { setEditing(false); onChanged() }} onClose={() => { setEditing(false); onChanged() }} />
      )}

      {openDocument !== null && (
        <PropertyDocumentDialog propertyId={property.id} document={openDocument === 'new' ? null : openDocument}
          types={documentTypes} onChanged={onChanged} onClose={() => setOpenDocument(null)} />
      )}

      {deleting && onDeleted && (
        <ConfirmDialog
          title={t.deletePropertyTitle}
          message={t.deletePropertyMessage(property.name)}
          confirmLabel={t.delete}
          onConfirm={async () => { await api.deleteProperty(property.id); onDeleted() }}
          onCancel={() => setDeleting(false)}
        />
      )}
    </main>
  )
}
