import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import PropertyDialog from '../components/PropertyDialog'
import PropertyPage from '../components/PropertyPage'
import SearchBar from '../components/SearchBar'
import { ChevronLeftIcon, HouseIcon, LandIcon, PlusIcon } from '../components/icons'
import { formatEuros, normalize } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { Heritage, Property, PropertyKind } from '../api/types'

/** The property shown, from a hash like #/heritage/<id>; null for the lists */
function propertyFromHash(): string | null {
  const match = /^#\/heritage\/([0-9a-f-]+)$/i.exec(window.location.hash)
  return match ? match[1] : null
}

function matches(property: Property, words: string[]): boolean {
  const text = normalize(
    [property.name, property.address ?? '', property.comments ?? '',
      ...property.facts.flatMap((f) => [f.label, f.value])].join(' '),
  )
  return words.every((word) => text.includes(word))
}

/** The heritage: its houses and its land parcels, and the page of one of them. */
export default function HeritageView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [heritage, setHeritage] = useState<Heritage | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [propertyId, setPropertyId] = useState(propertyFromHash)
  const [creating, setCreating] = useState<PropertyKind | null>(null)

  const reload = useCallback(async () => {
    try {
      setHeritage(await api.heritage())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    const onHashChange = () => setPropertyId(propertyFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [reload])

  const words = useMemo(() => normalize(query).split(/\s+/).filter(Boolean), [query])

  const backLink = (
    <a className="link property-back" href="#/heritage">
      <ChevronLeftIcon width={16} height={16} /> {t.backToHeritage}
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

  if (heritage === null) {
    return <main className="content"><p className="empty">{t.loading}</p></main>
  }

  if (propertyId !== null) {
    const property = [...heritage.houses, ...heritage.lands].find((p) => p.id === propertyId)
    if (!property) {
      return (
        <main className="content">
          {backLink}
          <p className="empty">{t.propertyNotFound}</p>
        </main>
      )
    }
    return (
      <>
        <div className="content property-back-bar">{backLink}</div>
        <PropertyPage property={property} onChanged={reload}
          onDeleted={() => { window.location.hash = '#/heritage'; reload() }} />
      </>
    )
  }

  /** One of the two lists, with its count, its total and its button to add */
  function section(kind: PropertyKind, all: Property[]) {
    const isHouse = kind === 'HOUSE'
    const Icon = isHouse ? HouseIcon : LandIcon
    const visible = all.filter((p) => matches(p, words))
    // Properties without an estimated value are left out of the total
    const total = visible.reduce((sum, p) => sum + (p.valueEur ?? 0), 0)
    const count = isHouse
      ? (query ? t.filteredHouseCount(visible.length, all.length) : t.houseCount(all.length))
      : (query ? t.filteredLandCount(visible.length, all.length) : t.landCount(all.length))
    return (
      <section className="property-section heritage-section">
        <div className="property-section-header">
          <h2>{isHouse ? t.houses : t.lands}</h2>
          {all.length > 0 && (
            <span className="count">{count}{' · '}{t.totalValue(formatEuros(total, t.locale))}</span>
          )}
          {canEdit && (
            <button type="button" className="button button-small" onClick={() => setCreating(kind)}>
              <PlusIcon width={16} height={16} /> {isHouse ? t.newHouse : t.newLand}
            </button>
          )}
        </div>

        {all.length === 0 && <p className="hint">{isHouse ? t.emptyHouses : t.emptyLand}</p>}
        {all.length > 0 && visible.length === 0 && (
          <p className="hint">{isHouse ? t.noHouseMatches : t.noLandMatches}</p>
        )}

        {visible.length > 0 && (
          <div className="grid">
            {visible.map((property) => (
              <article key={property.id} className="card">
                <a className="card-main" href={`#/heritage/${property.id}`}>
                  <div className="card-image">
                    {property.pictures[0]?.thumbnailUrl ? (
                      <img src={property.pictures[0].thumbnailUrl} alt="" loading="lazy" />
                    ) : (
                      <Icon className="card-placeholder" width={40} height={40} />
                    )}
                  </div>
                  <div className="card-body">
                    <h3 className="card-title">{property.name}</h3>
                    <dl className="card-facts">
                      {property.address && (
                        <div>
                          <dt>{isHouse ? t.address : t.landLocation}</dt>
                          <dd>{property.address}</dd>
                        </div>
                      )}
                      <div>
                        <dt>{t.estimatedValue}</dt>
                        <dd>{property.valueEur != null ? formatEuros(property.valueEur, t.locale) : t.notEstimated}</dd>
                      </div>
                      <div>
                        <dt>{t.officialDocuments}</dt>
                        <dd>{property.documents.length}</dd>
                      </div>
                    </dl>
                  </div>
                </a>
              </article>
            ))}
          </div>
        )}
      </section>
    )
  }

  return (
    <main className="content">
      <SearchBar query={query} onQuery={setQuery} placeholder={t.propertySearchPlaceholder}
        label={t.propertySearchLabel} />

      {section('HOUSE', heritage.houses)}
      {section('LAND', heritage.lands)}

      {creating && (
        <PropertyDialog property={null} kind={creating}
          onSaved={(saved) => { setCreating(null); reload(); window.location.hash = `#/heritage/${saved.id}` }}
          onClose={() => { setCreating(null); reload() }} />
      )}
    </main>
  )
}
