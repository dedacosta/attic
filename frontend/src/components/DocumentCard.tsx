import { documentName, documentTypeLabel, formatDate } from '../lib/format'
import { validity } from '../lib/validity'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { FileIcon, IdCardIcon, ImageIcon, TrashIcon } from './icons'
import { PDF_TYPE } from './PhotosField'
import ValidityBadge from './ValidityBadge'
import type { HeirDocument } from '../api/types'

interface Props {
  document: HeirDocument
  onEdit: () => void
  onDelete: () => void
}

export default function DocumentCard({ document, onEdit, onDelete }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const title = documentTypeLabel(document.type, t)
  const status = validity(document.validUntil)
  return (
    <article className={status === 'expired' ? 'card card-expired' : 'card'}>
      <button type="button" className="card-main" onClick={onEdit} aria-label={t.editNamed(documentName(document, t))}>
        <div className="card-image card-image-document">
          {document.pictures[0]?.thumbnailUrl ? (
            <img src={document.pictures[0].thumbnailUrl} alt="" loading="lazy" />
          ) : document.pictures[0]?.contentType === PDF_TYPE ? (
            <span className="picture-pdf">
              <FileIcon width={44} height={44} />
              <span>PDF</span>
            </span>
          ) : (
            <IdCardIcon className="card-placeholder" width={44} height={44} />
          )}
          {document.pictures.length > 1 && (
            <span className="card-photo-count" aria-label={t.photoCount(document.pictures.length)}>
              <ImageIcon width={14} height={14} /> {document.pictures.length}
            </span>
          )}
        </div>
        <div className="card-body">
          <h3 className="card-title">{title}</h3>
          <div className="card-tags">
            <ValidityBadge validity={status} />
          </div>
          <dl className="card-facts">
            <div>
              <dt>{t.validUntil}</dt>
              <dd>{document.validUntil ? formatDate(document.validUntil, t.locale) : t.noExpiry}</dd>
            </div>
          </dl>
          {document.comments && <p className="card-comments">{document.comments}</p>}
        </div>
      </button>
      {canEdit && <button type="button" className="icon-button card-delete" onClick={onDelete}
        aria-label={t.deleteNamed(documentName(document, t))}>
        <TrashIcon width={18} height={18} />
      </button>}
    </article>
  )
}
