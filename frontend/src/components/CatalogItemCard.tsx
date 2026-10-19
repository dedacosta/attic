import { formatEuros } from '../lib/format'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import HoverPreview from './HoverPreview'
import { ImageIcon, TrashIcon } from './icons'
import type { CatalogItem } from '../api/types'

interface Props {
  item: CatalogItem
  /** Where the item is: its room and house */
  place: string
  onEdit: () => void
  onDelete: () => void
}

export default function CatalogItemCard({ item, place, onEdit, onDelete }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  return (
    <article className={item.existent ? 'card card-compact' : 'card card-compact card-missing'}>
      <button type="button" className="card-main" onClick={onEdit} aria-label={t.editNamed(item.name)}>
        <HoverPreview picture={item.pictures[0]}>
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
        </HoverPreview>
        <div className="card-body">
          <h3 className="card-title">{item.name}</h3>
          <div className="card-tags">
            {place && <span className="tag">{place}</span>}
            {!item.existent && <span className="tag tag-missing">{t.missing}</span>}
          </div>
          <p className="card-value">{formatEuros(item.valueEur, t.locale)}</p>
        </div>
      </button>
      {canEdit && <button type="button" className="icon-button card-delete" onClick={onDelete} aria-label={t.deleteNamed(item.name)}>
        <TrashIcon width={18} height={18} />
      </button>}
    </article>
  )
}
