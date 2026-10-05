import { formatDate, formatEuros, locationLabel } from '../lib/format'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { ImageIcon, TrashIcon } from './icons'
import type { CatalogItem } from '../api/types'

interface Props {
  item: CatalogItem
  onEdit: () => void
  onDelete: () => void
}

export default function CatalogItemCard({ item, onEdit, onDelete }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  return (
    <article className={item.existent ? 'card' : 'card card-missing'}>
      <button type="button" className="card-main" onClick={onEdit} aria-label={t.editNamed(item.name)}>
        <div className="card-image">
          {item.pictures[0]?.thumbnailUrl ? (
            <img src={item.pictures[0].thumbnailUrl} alt="" loading="lazy" />
          ) : (
            <ImageIcon className="card-placeholder" width={40} height={40} />
          )}
          {item.pictures.length > 1 && (
            <span className="card-photo-count" aria-label={t.photoCount(item.pictures.length)}>
              <ImageIcon width={14} height={14} /> {item.pictures.length}
            </span>
          )}
          {item.quantity !== 1 && <span className="card-quantity">×{item.quantity}</span>}
        </div>
        <div className="card-body">
          <h3 className="card-title">{item.name}</h3>
          <div className="card-tags">
            {item.location && <span className="tag">{locationLabel(item.location, t)}</span>}
            {!item.existent && <span className="tag tag-missing">{t.missing}</span>}
          </div>
          <dl className="card-facts">
            <div>
              <dt>{t.value}</dt>
              <dd>{formatEuros(item.valueEur, t.locale)}</dd>
            </div>
            <div>
              <dt>{t.owner}</dt>
              <dd>{item.owner}</dd>
            </div>
            {item.date && (
              <div>
                <dt>{t.date}</dt>
                <dd>{formatDate(item.date, t.locale)}</dd>
              </div>
            )}
          </dl>
          {item.comments && <p className="card-comments">{item.comments}</p>}
        </div>
      </button>
      {canEdit && <button type="button" className="icon-button card-delete" onClick={onDelete} aria-label={t.deleteNamed(item.name)}>
        <TrashIcon width={18} height={18} />
      </button>}
    </article>
  )
}
