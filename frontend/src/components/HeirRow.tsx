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

/** An heir on one line of the list: who they are, their share and their documents. */
export default function HeirRow({ heir, documents, childCount, onOpen }: Props) {
  const { t } = useI18n()
  const share = heir.calculatedShare ? parseFraction(heir.calculatedShare) : null
  // A deceased heir's share went on to their children
  const passedOn = heir.deceased && childCount > 0
  const expired = documents.filter((d) => validity(d.validUntil) === 'expired').length
  const expiring = documents.filter((d) => validity(d.validUntil) === 'expiring').length
  return (
    <button type="button" className={heir.deceased ? 'heir-row heir-deceased' : 'heir-row'} onClick={onOpen}
      aria-label={t.showNamed(heir.name)}>
      <span className="avatar" aria-hidden="true">{initials(heir.name)}</span>
      <span>
        <span className="heir-name">{heir.name}</span>
        {(heir.birthDate || heir.deceased) && (
          <span className="heir-subtitle">
            {heir.birthDate && formatDate(heir.birthDate, t.locale)}
            {heir.birthDate && heir.deceased && ' – '}
            {heir.deceased && `† ${heir.deathDate ? formatDate(heir.deathDate, t.locale) : t.deceased}`}
            {heir.birthDate && (!heir.deceased || heir.deathDate)
              && ` · ${t.age(age(heir.birthDate, heir.deathDate ? localDate(heir.deathDate) : undefined))}`}
          </span>
        )}
      </span>
      <span className="heir-share">
        {t.heritageShare}{t.colon} <strong>{share ? heir.calculatedShare : '—'}</strong>
        {share && passedOn && <span className="heir-share-note">{t.sharePassedOn}</span>}
        {!share && heir.deceased && childCount === 0 && <span className="heir-share-note">{t.notInHeritage}</span>}
      </span>
      <span className="heir-documents">
        <IdCardIcon width={16} height={16} />
        <span>{documents.length === 0 ? t.noHeirDocuments : t.documentCount(documents.length)}</span>
        {expired > 0 && <span className="tag tag-expired">{t.expiredCount(expired)}</span>}
        {expiring > 0 && <span className="tag tag-expiring">{t.expiringCount(expiring)}</span>}
      </span>
    </button>
  )
}
