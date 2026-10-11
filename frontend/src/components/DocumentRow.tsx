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

/** A document on one line of the list: what it is, and whether and until when it is valid. */
export default function DocumentRow({ document, onEdit, onDelete }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const status = validity(document.validUntil)
  return (
    <li className={status === 'expired' ? 'document-item document-expired' : 'document-item'}>
      <button type="button" className="document-row document-row-wide" onClick={onEdit}
        aria-label={t.editNamed(documentName(document, t))}>
        <span className="document-thumbnail">
          {document.pictures[0]?.thumbnailUrl ? (
            <img src={document.pictures[0].thumbnailUrl} alt="" loading="lazy" />
          ) : document.pictures[0]?.contentType === PDF_TYPE ? (
            <FileIcon width={20} height={20} />
          ) : (
            <IdCardIcon width={20} height={20} />
          )}
        </span>
        <span>
          <span className="document-row-type">
            {documentTypeLabel(document.type, t)}
            {document.pictures.length > 1 && (
              <span className="document-row-photos" aria-label={t.photoCount(document.pictures.length)}>
                <ImageIcon width={14} height={14} /> {document.pictures.length}
              </span>
            )}
          </span>
          {document.comments && <span className="document-row-comments">{document.comments}</span>}
        </span>
        <span className="document-row-validity">
          <span><ValidityBadge validity={status} /></span>
          <span className="document-row-date">
            {document.validUntil ? formatDate(document.validUntil, t.locale) : t.noExpiry}
          </span>
        </span>
      </button>
      {canEdit && <button type="button" className="icon-button document-row-delete" onClick={onDelete}
        aria-label={t.deleteNamed(documentName(document, t))}>
        <TrashIcon width={18} height={18} />
      </button>}
    </li>
  )
}
