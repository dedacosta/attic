import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { exportCatalogLatex } from '../lib/latex'
import { saveWithPhotos } from '../lib/photos'
import ConfirmDialog from '../components/ConfirmDialog'
import PdfViewer from '../components/PdfViewer'
import CatalogItemCard from '../components/CatalogItemCard'
import CatalogItemDialog, { type Place } from '../components/CatalogItemDialog'
import SearchBar from '../components/SearchBar'
import { formatDate, normalize } from '../lib/format'
import { ownerNames } from '../lib/export'
import { usePermissions } from '../lib/permissions'
import { useI18n, type Messages } from '../i18n'
import { FileIcon, PlusIcon } from '../components/icons'
import type { Building, CatalogItem, CatalogItemInput, Heir, InventoryRoom, PhotoEntry } from '../api/types'
import type { GeneratedPdf } from '../lib/pdf'

type Presence = 'all' | 'present' | 'missing'

/** The owner filter for the items of no heir */
const HERITAGE = 'heritage'

function matches(item: CatalogItem, place: string, words: string[], t: Messages): boolean {
  const text = normalize(
    [
      item.name,
      ownerNames(item, t),
      item.comments ?? '',
      item.id,
      place,
      item.date ? formatDate(item.date, t.locale) : '',
    ].join(' '),
  )
  return words.every((word) => text.includes(word))
}

export default function CatalogView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [items, setItems] = useState<CatalogItem[] | null>(null)
  // The houses of the estate and their rooms, as in the inventory
  const [houses, setHouses] = useState<Building[]>([])
  const [rooms, setRooms] = useState<InventoryRoom[]>([])
  // The heirs, who can own items
  const [heirs, setHeirs] = useState<Heir[]>([])
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [houseFilter, setHouseFilter] = useState('')
  const [roomFilter, setRoomFilter] = useState('')
  // An heir's id, HERITAGE for the items of no heir, or '' for every item
  const [ownerFilter, setOwnerFilter] = useState('')
  const [presence, setPresence] = useState<Presence>('all')
  const [editing, setEditing] = useState<CatalogItem | 'new' | null>(null)
  const [deleting, setDeleting] = useState<CatalogItem | null>(null)
  const [preview, setPreview] = useState<GeneratedPdf | null>(null)

  const reload = useCallback(async () => {
    try {
      setItems(await api.listCatalog())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    api.estate().then(({ buildings }) => setHouses(buildings), () => setHouses([]))
    api.listHeirs().then(setHeirs, () => setHeirs([]))
    api.listInventory().then(setRooms, () => setRooms([]))
  }, [reload])

  /** Where an item is, in words: its room, and its house when there are several to tell apart */
  const placeOf = useCallback((item: CatalogItem) => {
    const room = rooms.find((candidate) => candidate.id === item.roomId)
    const house = houses.find((candidate) => candidate.id === item.propertyId)
    return [room?.title, (!room || houses.length > 1) && house?.name].filter(Boolean).join(' · ')
  }, [houses, rooms])

  const visible = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    return (items ?? []).filter(
      (item) =>
        (!houseFilter || item.propertyId === houseFilter) &&
        (!roomFilter || item.roomId === roomFilter) &&
        (!ownerFilter || (ownerFilter === HERITAGE ? item.owners.length === 0
          : item.owners.some((owner) => owner.heirId === ownerFilter))) &&
        (presence === 'all' || item.existent === (presence === 'present')) &&
        matches(item, placeOf(item), words, t),
    )
  }, [items, query, houseFilter, roomFilter, ownerFilter, presence, placeOf, t])

  const filtered = query !== '' || houseFilter !== '' || roomFilter !== '' || ownerFilter !== '' || presence !== 'all'
  const filterHouse = houses.find((house) => house.id === houseFilter)
  const filterRoom = rooms.find((room) => room.id === roomFilter)
  // Rooms to filter by: those of the house chosen, or of every house under its name
  const roomGroups = houses
    .filter((house) => !houseFilter || house.id === houseFilter)
    .map((house) => ({ house, rooms: rooms.filter((room) => room.propertyId === house.id) }))
    .filter((group) => group.rooms.length > 0)

  /** Another house: a room of another house is no longer filtered by */
  function chooseHouse(id: string) {
    setHouseFilter(id)
    if (id && filterRoom && filterRoom.propertyId !== id) {
      setRoomFilter('')
    }
  }

  // A new item is put where the catalog is filtered, or in the only house there is
  const newPlace: Place = {
    propertyId: filterRoom?.propertyId ?? (houseFilter || (houses.length === 1 ? houses[0].id : '')),
    roomId: roomFilter,
  }

  async function download(format: 'pdf' | 'latex') {
    const filters = [
      query.trim() && t.searchFilter(query.trim()),
      filterHouse && `${t.inventoryHouse}: ${filterHouse.name}`,
      filterRoom && `${t.location}: ${filterRoom.title}`,
      ownerFilter && `${t.owner}: ${ownerFilter === HERITAGE ? t.heritageTitle
        : (heirs.find((heir) => heir.id === ownerFilter)?.name ?? '')}`,
      presence !== 'all' && (presence === 'present' ? t.present : t.missing),
    ].filter((f): f is string => Boolean(f))
    const exported = visible.map((item) => ({ ...item, place: placeOf(item) }))
    if (format === 'latex') {
      await exportCatalogLatex(exported, items?.length ?? 0, filters, t, t.pdfTitleCatalog, 'attic-catalog')
      return
    }
    // Loaded on demand: the PDF library is large
    const { exportPdf } = await import('../lib/pdf')
    setPreview(await exportPdf(exported, items?.length ?? 0, filters, t, t.pdfTitleCatalog, 'attic-catalog'))
  }

  async function save(input: CatalogItemInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editing === 'new' || editing === null ? null : editing
    // From the first save on the item exists: a retry after a failed upload updates it
    await saveWithPhotos(
      () => (existing ? api.updateCatalogItem(existing.id, input) : api.createCatalogItem(input)),
      '/api/catalog', photos, setEditing, onPhotos,
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
    await api.deleteCatalogItem(deleting.id)
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
          {houses.length > 0 && (
            <select value={houseFilter} onChange={(e) => chooseHouse(e.target.value)} aria-label={t.filterByHouse}
              title={t.filterByHouse}>
              <option value="">{t.allHouses}</option>
              {houses.map((house) => <option key={house.id} value={house.id}>{house.name}</option>)}
            </select>
          )}
          {roomGroups.length > 0 && (
            <select value={roomFilter} onChange={(e) => setRoomFilter(e.target.value)} aria-label={t.filterByRoom}
              title={t.filterByRoom}>
              <option value="">{t.allRooms}</option>
              {houseFilter ? roomGroups[0].rooms.map((room) => <option key={room.id} value={room.id}>{room.title}</option>)
                : roomGroups.map(({ house, rooms }) => (
                  <optgroup key={house.id} label={house.name}>
                    {rooms.map((room) => <option key={room.id} value={room.id}>{room.title}</option>)}
                  </optgroup>
                ))}
            </select>
          )}
          {heirs.length > 0 && (
            <select value={ownerFilter} onChange={(e) => setOwnerFilter(e.target.value)} aria-label={t.filterByOwner}
              title={t.filterByOwner}>
              <option value="">{t.allOwners}</option>
              <option value={HERITAGE}>{t.heritageTitle}</option>
              {heirs.map((heir) => <option key={heir.id} value={heir.id}>{heir.name}</option>)}
            </select>
          )}
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
          <button type="button" className="button" onClick={() => download('pdf')} disabled={visible.length === 0}
            aria-label={t.exportPdfLabel} title={t.exportPdfLabel}>
            <FileIcon width={18} height={18} /> PDF
          </button>
          <button type="button" className="button" onClick={() => download('latex')} disabled={visible.length === 0}
            aria-label={t.exportLatexLabel} title={t.exportLatexLabel}>
            <FileIcon width={18} height={18} /> LaTeX
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

        <div className="grid grid-compact">
          {visible.map((item) => (
            <CatalogItemCard key={item.id} item={item} place={placeOf(item)} onEdit={() => setEditing(item)} onDelete={() => setDeleting(item)} />
          ))}
        </div>
      </main>

      {editing !== null && (
        <CatalogItemDialog
          key="catalog-item-dialog"
          item={editing === 'new' ? null : editing}
          heirs={heirs}
          houses={houses}
          rooms={rooms}
          place={newPlace}
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
      {preview && (
        <PdfViewer source={preview.blob} title={preview.title} fileName={preview.fileName}
          onClose={() => setPreview(null)} />
      )}
    </>
  )
}
