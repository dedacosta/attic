import { age, localDate } from '../lib/age'
import { formatDate } from '../lib/format'
import { parseFraction } from '../lib/fraction'
import { validity } from '../lib/validity'
import { useI18n } from '../i18n'
import { IdCardIcon } from './icons'
import type { Heir, HeirDocument } from '../api/types'

interface Props {
  heir: Heir
  documents: HeirDocument[]
  /** How many of their children are shown */
  childCount: number
  /** Show everything about the heir; editing and deleting are reached from there */
  onOpen: () => void
}

function initials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean)
  return ((words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase()
}

export default function HeirCard({ heir, documents, childCount, onOpen }: Props) {
  const { t } = useI18n()
  const share = heir.calculatedShare ? parseFraction(heir.calculatedShare) : null
  // A deceased heir's share went on to their children
  const passedOn = heir.deceased && childCount > 0
  const expired = documents.filter((d) => validity(d.validUntil) === 'expired').length
  const expiring = documents.filter((d) => validity(d.validUntil) === 'expiring').length
  return (
    <article className={heir.deceased ? 'card heir-card heir-deceased' : 'card heir-card'}>
      <button type="button" className="card-main" onClick={onOpen} aria-label={t.showNamed(heir.name)}>
        <div className="heir-header">
          <span className="avatar" aria-hidden="true">{initials(heir.name)}</span>
          <div>
            <h3 className="card-title">{heir.name}</h3>
            {(heir.birthDate || heir.deceased) && (
              <p className="heir-subtitle">
                {heir.birthDate && formatDate(heir.birthDate, t.locale)}
                {heir.birthDate && heir.deceased && ' – '}
                {heir.deceased && `† ${heir.deathDate ? formatDate(heir.deathDate, t.locale) : t.deceased}`}
                {heir.birthDate && (!heir.deceased || heir.deathDate)
                  && ` · ${t.age(age(heir.birthDate, heir.deathDate ? localDate(heir.deathDate) : undefined))}`}
              </p>
            )}
          </div>
        </div>
        <div className="card-body">
          <div className="card-tags">
            {heir.sex && <span className="tag">{t.sexes[heir.sex] ?? heir.sex}</span>}
          </div>
          <p className="heir-share">
            {t.heritageShare}{t.colon} <strong>{share ? heir.calculatedShare : '—'}</strong>
          </p>
          {share && passedOn && <p className="heir-share-note">{t.sharePassedOn}</p>}
          {!share && heir.deceased && childCount === 0 && <p className="heir-share-note">{t.notInHeritage}</p>}
          <p className="heir-documents">
            <IdCardIcon width={16} height={16} />
            <span>{documents.length === 0 ? t.noHeirDocuments : t.documentCount(documents.length)}</span>
            {expired > 0 && <span className="tag tag-expired">{t.expiredCount(expired)}</span>}
            {expiring > 0 && <span className="tag tag-expiring">{t.expiringCount(expiring)}</span>}
          </p>
          {heir.comments && <p className="card-comments">{heir.comments}</p>}
        </div>
      </button>
    </article>
  )
}
