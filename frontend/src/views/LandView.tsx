import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import PropertyDialog from '../components/PropertyDialog'
import PropertyPage from '../components/PropertyPage'
import SearchBar from '../components/SearchBar'
import { ChevronLeftIcon, LandIcon, PlusIcon } from '../components/icons'
import { formatEuros, normalize } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { Property } from '../api/types'

/** The parcel shown, from a hash like #/land/<id>; null for the list */
function parcelFromHash(): string | null {
  const match = /^#\/land\/([0-9a-f-]+)$/i.exec(window.location.hash)
  return match ? match[1] : null
}

function matches(land: Property, words: string[]): boolean {
  const text = normalize(
    [land.name, land.address ?? '', land.comments ?? '', ...land.facts.flatMap((f) => [f.label, f.value])].join(' '),
  )
  return words.every((word) => text.includes(word))
}

/** The land parcels of the heritage, and the page of one parcel. */
export default function LandView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [land, setLand] = useState<Property[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [parcelId, setParcelId] = useState(parcelFromHash)
  const [creating, setCreating] = useState(false)

  const reload = useCallback(async () => {
    try {
      setLand(await api.listLand())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    const onHashChange = () => setParcelId(parcelFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [reload])

  const visible = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    return (land ?? []).filter((parcel) => matches(parcel, words))
  }, [land, query])

  const total = visible.reduce((sum, parcel) => sum + (parcel.valueEur ?? 0), 0)
  const backLink = (
    <a className="link property-back" href="#/land">
      <ChevronLeftIcon width={16} height={16} /> {t.backToLand}
    </a>
  )

  if (loadError !== null) {
    return (
      <main className="content">
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      </main>
    )
  }

  if (land === null) {
    return <main className="content"><p className="empty">{t.loading}</p></main>
  }

  if (parcelId !== null) {
    const parcel = land.find((p) => p.id === parcelId)
    if (!parcel) {
      return (
        <main className="content">
          {backLink}
          <p className="empty">{t.landNotFound}</p>
        </main>
      )
    }
    return (
      <>
        <div className="content property-back-bar">{backLink}</div>
        <PropertyPage property={parcel} onChanged={reload}
          onDeleted={() => { window.location.hash = '#/land'; reload() }} />
      </>
    )
  }

  return (
    <main className="content">
      <SearchBar query={query} onQuery={setQuery} placeholder={t.landSearchPlaceholder} label={t.landSearchLabel}>
        {canEdit && (
          <button type="button" className="button button-primary" onClick={() => setCreating(true)} aria-label={t.newLand}>
            <PlusIcon width={18} height={18} /> <span className="button-label">{t.newLand}</span>
          </button>
        )}
      </SearchBar>

      {land.length > 0 && (
        <div className="toolbar">
          <span className="count">
            {query ? t.filteredLandCount(visible.length, land.length) : t.landCount(land.length)}
            {' · '}
            {t.totalValue(formatEuros(total, t.locale))}
          </span>
        </div>
      )}

      {land.length === 0 && (
        <div className="empty">
          <LandIcon width={48} height={48} />
          <p>{t.emptyLand}</p>
          {canEdit && (
            <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
              <PlusIcon width={18} height={18} /> {t.addFirstLand}
            </button>
          )}
        </div>
      )}

      {land.length > 0 && visible.length === 0 && <p className="empty">{t.noLandMatches}</p>}

      <div className="grid">
        {visible.map((parcel) => (
          <article key={parcel.id} className="card">
            <a className="card-main" href={`#/land/${parcel.id}`}>
              <div className="card-image">
                {parcel.pictures[0]?.thumbnailUrl ? (
                  <img src={parcel.pictures[0].thumbnailUrl} alt="" loading="lazy" />
                ) : (
                  <LandIcon className="card-placeholder" width={40} height={40} />
                )}
              </div>
              <div className="card-body">
                <h3 className="card-title">{parcel.name}</h3>
                <dl className="card-facts">
                  {parcel.address && (
                    <div>
                      <dt>{t.landLocation}</dt>
                      <dd>{parcel.address}</dd>
                    </div>
                  )}
                  <div>
                    <dt>{t.estimatedValue}</dt>
                    <dd>{parcel.valueEur != null ? formatEuros(parcel.valueEur, t.locale) : t.notEstimated}</dd>
                  </div>
                  <div>
                    <dt>{t.officialDocuments}</dt>
                    <dd>{parcel.documents.length}</dd>
                  </div>
                </dl>
              </div>
            </a>
          </article>
        ))}
      </div>

      {creating && (
        <PropertyDialog property={null} kind="LAND"
          onSaved={(saved) => { setCreating(false); reload(); window.location.hash = `#/land/${saved.id}` }}
          onClose={() => { setCreating(false); reload() }} />
      )}
    </main>
  )
}
