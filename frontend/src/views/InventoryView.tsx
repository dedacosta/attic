import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { saveWithPhotos } from '../lib/photos'
import ConfirmDialog from '../components/ConfirmDialog'
import ItemCard from '../components/ItemCard'
import ItemDialog from '../components/ItemDialog'
import SearchBar from '../components/SearchBar'
import { formatDate, locationLabel, normalize } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n, type Messages } from '../i18n'
import { FileIcon, PlusIcon } from '../components/icons'
import type { Item, ItemInput, PhotoEntry } from '../api/types'

type Presence = 'all' | 'present' | 'missing'

function matches(item: Item, words: string[], t: Messages): boolean {
  const text = normalize(
    [
      item.name,
      item.owner,
      item.comments ?? '',
      item.id,
      item.location ? locationLabel(item.location, t) : '',
      item.date ? formatDate(item.date, t.locale) : '',
    ].join(' '),
  )
  return words.every((word) => text.includes(word))
}

export default function InventoryView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [items, setItems] = useState<Item[] | null>(null)
  const [locations, setLocations] = useState<string[]>([])
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [locationFilter, setLocationFilter] = useState('')
  const [presence, setPresence] = useState<Presence>('all')
  const [editing, setEditing] = useState<Item | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Item | null>(null)

  const reload = useCallback(async () => {
    try {
      setItems(await api.listItems())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    api.listLocations().then(setLocations, () => setLocations([]))
  }, [reload])

  const visible = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    return (items ?? []).filter(
      (item) =>
        (!locationFilter || item.location === locationFilter) &&
        (presence === 'all' || item.existent === (presence === 'present')) &&
        matches(item, words, t),
    )
  }, [items, query, locationFilter, presence, t])

  const filtered = query !== '' || locationFilter !== '' || presence !== 'all'

  async function downloadPdf() {
    const filters = [
      query.trim() && t.searchFilter(query.trim()),
      locationFilter && `${t.location}: ${locationLabel(locationFilter, t)}`,
      presence !== 'all' && (presence === 'present' ? t.present : t.missing),
    ].filter((f): f is string => Boolean(f))
    const { exportPdf } = await import('../lib/pdf')
    await exportPdf(visible, items?.length ?? 0, filters, t)
  }

  async function save(input: ItemInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editing === 'new' || editing === null ? null : editing
    // From the first save on the item exists: a retry after a failed upload updates it
    await saveWithPhotos(
      () => (existing ? api.updateItem(existing.id, input) : api.createItem(input)),
      '/api/items', photos, setEditing, onPhotos,
    )
    await reload()
    setEditing(null)
  }

  function closeEditor() {
    setEditing(null)
    reload()
  }

  async function confirmDelete() {
    if (!deleting) {
      return
    }
    await api.deleteItem(deleting.id)
    // Refresh before closing so the deleted card is gone when the dialog disappears
    await reload()
    setDeleting(null)
    setEditing(null)
  }

  return (
    <>
      <main className="content">
        <SearchBar query={query} onQuery={setQuery} placeholder={t.searchPlaceholder} label={t.searchLabel}>
          {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}
            aria-label={t.newItem}>
            <PlusIcon width={18} height={18} /> <span className="button-label">{t.newItem}</span>
          </button>}
        </SearchBar>

        <div className="toolbar">
          <select value={locationFilter} onChange={(e) => setLocationFilter(e.target.value)}
            aria-label={t.filterByRoom}>
            <option value="">{t.allRooms}</option>
            {locations.map((location) => (
              <option key={location} value={location}>{locationLabel(location, t)}</option>
            ))}
          </select>
          <div className="segmented" role="group" aria-label={t.filterByPresence}>
            {(['all', 'present', 'missing'] as const).map((value) => (
              <button key={value} type="button" aria-pressed={presence === value} onClick={() => setPresence(value)}>
                {value === 'all' ? t.all : value === 'present' ? t.present : t.missing}
              </button>
            ))}
          </div>
          {items && (
            <span className="count">
              {filtered ? t.filteredCount(visible.length, items.length) : t.itemCount(items.length)}
            </span>
          )}
          <button type="button" className="button" onClick={downloadPdf} disabled={visible.length === 0}
            aria-label={t.exportPdfLabel} title={t.exportPdfLabel}>
            <FileIcon width={18} height={18} /> PDF
          </button>
        </div>

        {loadError !== null && (
          <div className="banner" role="alert">
            {apiErrorMessage(loadError, t)}{' '}
            <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
          </div>
        )}

        {items === null && loadError === null && <p className="empty">{t.loading}</p>}

        {items !== null && items.length === 0 && (
          <div className="empty">
            <p>{t.emptyAttic}</p>
            {canEdit && <button type="button" className="button button-primary" onClick={() => setEditing('new')}>
              <PlusIcon width={18} height={18} /> {t.addFirstItem}
            </button>}
          </div>
        )}

        {items !== null && items.length > 0 && visible.length === 0 && (
          <p className="empty">{t.noMatches}</p>
        )}

        <div className="grid">
          {visible.map((item) => (
            <ItemCard key={item.id} item={item} onEdit={() => setEditing(item)} onDelete={() => setDeleting(item)} />
          ))}
        </div>
      </main>

      {editing !== null && (
        <ItemDialog
          key="item-dialog"
          item={editing === 'new' ? null : editing}
          locations={locations}
          onSave={save}
          onDelete={() => editing !== 'new' && setDeleting(editing)}
          onClose={closeEditor}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title={t.deleteTitle}
          message={t.deleteMessage(deleting.name)}
          confirmLabel={t.delete}
          onConfirm={confirmDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}
