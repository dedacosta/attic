import { age } from '../lib/age'
import { formatDate } from '../lib/format'
import { parseFraction } from '../lib/fraction'
import { validity } from '../lib/validity'
import { useI18n } from '../i18n'
import { usePermissions } from '../lib/permissions'
import { IdCardIcon, TrashIcon } from './icons'
import type { Heir, HeirDocument } from '../api/types'

interface Props {
  heir: Heir
  documents: HeirDocument[]
  onEdit: () => void
  onDelete: () => void
}

function initials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean)
  return ((words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase()
}

export default function HeirCard({ heir, documents, onEdit, onDelete }: Props) {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const share = heir.heritageShare ? parseFraction(heir.heritageShare) : null
  const expired = documents.filter((d) => validity(d.validUntil) === 'expired').length
  const expiring = documents.filter((d) => validity(d.validUntil) === 'expiring').length
  return (
    <article className="card heir-card">
      <button type="button" className="card-main" onClick={onEdit} aria-label={t.editNamed(heir.name)}>
        <div className="heir-header">
          <span className="avatar" aria-hidden="true">{initials(heir.name)}</span>
          <div>
            <h3 className="card-title">{heir.name}</h3>
            {heir.birthDate && (
              <p className="heir-subtitle">
                {formatDate(heir.birthDate, t.locale)} · {t.age(age(heir.birthDate))}
              </p>
            )}
          </div>
        </div>
        <div className="card-body">
          <div className="card-tags">
            {heir.sex && <span className="tag">{t.sexes[heir.sex] ?? heir.sex}</span>}
            {share && (
              <span className="tag tag-accent">
                {heir.heritageShare}
              </span>
            )}
          </div>
          <p className="heir-documents">
            <IdCardIcon width={16} height={16} />
            <span>{documents.length === 0 ? t.noHeirDocuments : t.documentCount(documents.length)}</span>
            {expired > 0 && <span className="tag tag-expired">{t.expiredCount(expired)}</span>}
            {expiring > 0 && <span className="tag tag-expiring">{t.expiringCount(expiring)}</span>}
          </p>
          {heir.comments && <p className="card-comments">{heir.comments}</p>}
        </div>
      </button>
      {canEdit && <button type="button" className="icon-button card-delete" onClick={onDelete} aria-label={t.deleteNamed(heir.name)}>
        <TrashIcon width={18} height={18} />
      </button>}
    </article>
  )
}
