import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { exportInventoryLatex } from '../lib/latex'
import { saveWithPhotos } from '../lib/photos'
import InventoryRoomDialog from '../components/InventoryRoomDialog'
import SearchBar from '../components/SearchBar'
import { formatEuros, normalize } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import { FileIcon, PencilIcon } from '../components/icons'
import type { Building, InventoryItem, InventoryRoom, InventoryRoomInput, PhotoEntry } from '../api/types'

/** The lists of a house that are not rooms */
type HouseList = 'other' | 'missing'

/** A section of the list: a room or a list of the house, with the items that are shown */
interface Section {
  key: string
  room: InventoryRoom | null
  list: HouseList | null
  title: string
  items: InventoryItem[]
}

/** Sums are rounded to cents, as 33.33 + 66.67 is not exactly 100 in floating point */
const total = (items: InventoryItem[]) => Math.round(items.reduce((sum, item) => sum + item.valueEur, 0) * 100) / 100

export default function InventoryView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  // The houses to choose from, and the one whose inventory is shown
  const [houses, setHouses] = useState<Building[] | null>(null)
  const [houseId, setHouseId] = useState('')
  const [rooms, setRooms] = useState<InventoryRoom[] | null>(null)
  const [otherItems, setOtherItems] = useState<InventoryItem[]>([])
  const [missingItems, setMissingItems] = useState<InventoryItem[]>([])
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  // A room or a list of the house. The rooms themselves are entered on the house.
  const [editing, setEditing] = useState<InventoryRoom | HouseList | null>(null)

  const loadHouses = useCallback(async () => {
    try {
      const { buildings } = await api.estate()
      setHouses(buildings)
      // The first house unless another is chosen already
      setHouseId((current) => (buildings.some((house) => house.id === current) ? current : (buildings[0]?.id ?? '')))
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  const reload = useCallback(async () => {
    if (!houseId) {
      return
    }
    try {
      const [loadedRooms, loadedOtherItems, loadedMissingItems] = await Promise.all([
        api.listInventory(houseId), api.listOtherInventoryItems(houseId), api.listMissingInventoryItems(houseId),
      ])
      setRooms(loadedRooms)
      setOtherItems(loadedOtherItems)
      setMissingItems(loadedMissingItems)
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [houseId])

  useEffect(() => {
    loadHouses()
  }, [loadHouses])

  useEffect(() => {
    // Another house: its inventory replaces the one shown
    setRooms(null)
    setOtherItems([])
    setMissingItems([])
    reload()
  }, [reload])

  const house = houses?.find((candidate) => candidate.id === houseId) ?? null
  const totalCount = (rooms ?? []).reduce((sum, room) => sum + room.items.length, 0) + otherItems.length
    + missingItems.length

  // The rooms in the order of the house, then its other items and its missing items. A search finds
  // a room by what is said of it, with all its items, or single items by their description.
  const sections = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean)
    const has = (text: string) => words.every((word) => normalize(text).includes(word))
    const shown = (items: InventoryItem[], all: boolean) => items.filter((item) => all || has(item.description))
    const list: Section[] = (rooms ?? []).map((room) => ({
      key: room.id,
      room,
      list: null,
      title: room.title,
      items: shown(room.items, has([room.title, room.description, room.pdfTitle, room.comments].join(' '))),
    }))
    list.push({ key: 'other', room: null, list: 'other', title: t.otherItems, items: shown(otherItems, has(t.otherItems)) })
    list.push({ key: 'missing', room: null, list: 'missing', title: t.missingItems,
      items: shown(missingItems, has(t.missingItems)) })
    // Without a search every room is listed, also an empty one; the lists of the house only when
    // they have items or can be filled
    return list.filter((section) => section.items.length > 0 || (query === '' && (section.room !== null || canEdit)))
  }, [rooms, otherItems, missingItems, query, canEdit, t])

  const visibleCount = sections.reduce((sum, section) => sum + section.items.length, 0)
  // What is missing is not part of what the house holds
  const houseTotal = total(sections.filter((section) => section.list !== 'missing').flatMap((section) => section.items))

  async function download(format: 'pdf' | 'latex') {
    const filters = query.trim() ? [t.searchFilter(query.trim())] : []
    const rooms = sections.filter((section) => section.items.length > 0).map((section) => ({
      // In the PDF a room has its own title, if it was given one
      title: section.room?.pdfTitle ?? section.title,
      comments: section.room?.comments ?? null,
      items: section.items,
      missing: section.list === 'missing',
    }))
    if (format === 'latex') {
      exportInventoryLatex(rooms, totalCount, filters, t, house?.name ?? '')
      return
    }
    // Loaded on demand: the PDF library is large
    const { exportInventoryPdf } = await import('../lib/pdf')
    await exportInventoryPdf(rooms, totalCount, filters, t, house?.name ?? '')
  }

  async function saveRoom(input: Pick<InventoryRoomInput, 'pdfTitle' | 'comments' | 'items'>, photos: PhotoEntry[],
    onPhotos: (photos: PhotoEntry[]) => void) {
    if (editing === null || editing === 'other' || editing === 'missing') {
      return
    }
    const room = editing
    await saveWithPhotos(
      () => api.updateInventoryRoom(room.id, { ...input, title: room.title, propertyId: room.propertyId }),
      '/api/inventory', photos, setEditing, onPhotos,
    )
    await reload()
    setEditing(null)
  }

  async function saveList(items: InventoryItem[]) {
    if (editing === 'missing') {
      await api.saveMissingInventoryItems(houseId, items)
    } else {
      await api.saveOtherInventoryItems(houseId, items)
    }
    await reload()
    setEditing(null)
  }

  function closeEditor() {
    setEditing(null)
    reload()
  }

  return (
    <>
      <main className="content">
        <SearchBar query={query} onQuery={setQuery} placeholder={t.inventorySearchPlaceholder}
          label={t.inventorySearchLabel} />

        <div className="toolbar">
          {houses && houses.length > 0 && (
            <select value={houseId} onChange={(e) => setHouseId(e.target.value)} aria-label={t.inventoryHouse}
              title={t.inventoryHouse}>
              {houses.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
            </select>
          )}
          {rooms && (
            <span className="count">
              {query ? t.filteredCount(visibleCount, totalCount) : t.itemCount(totalCount)}
              {' · '}{t.total}{t.colon} <strong>{formatEuros(houseTotal, t.locale)}</strong>
            </span>
          )}
          <button type="button" className="button" onClick={() => download('pdf')} disabled={visibleCount === 0}
            aria-label={t.exportPdfLabel} title={t.exportPdfLabel}>
            <FileIcon width={18} height={18} /> PDF
          </button>
          <button type="button" className="button" onClick={() => download('latex')} disabled={visibleCount === 0}
            aria-label={t.exportLatexLabel} title={t.exportLatexLabel}>
            <FileIcon width={18} height={18} /> LaTeX
          </button>
        </div>

        {loadError !== null && (
          <div className="banner" role="alert">
            {apiErrorMessage(loadError, t)}{' '}
            <button type="button" className="link" onClick={houses ? reload : loadHouses}>{t.tryAgain}</button>
          </div>
        )}

        {houses !== null && houses.length === 0 && <p className="empty">{t.inventoryNeedsHouse}</p>}

        {(houses === null || (house && rooms === null)) && loadError === null && <p className="empty">{t.loading}</p>}

        {rooms !== null && rooms.length === 0 && (
          <div className="empty">
            <p>{t.emptyInventory}</p>
            {canEdit && <a className="button button-primary" href={`#/estate/${houseId}`}>{t.openHouse}</a>}
          </div>
        )}

        {rooms !== null && totalCount > 0 && query !== '' && visibleCount === 0 && (
          <p className="empty">{t.noMatches}</p>
        )}

        {rooms !== null && sections.map(({ key, room, list, title, items }) => (
          <section key={key} className="group inventory-room" aria-label={title}>
            <div className="inventory-room-header">
              {room?.pictures[0]?.thumbnailUrl && (
                <button type="button" className="inventory-room-photo" onClick={() => setEditing(room)}
                  aria-label={t.showNamed(title)}>
                  <img src={room.pictures[0].thumbnailUrl} alt="" loading="lazy" />
                </button>
              )}
              <div>
                <h2 className="group-title">
                  {title} <span className="group-count">{items.length}</span>
                  {(canEdit || room) && (
                    <button type="button" className="icon-button" onClick={() => setEditing(room ?? list)}
                      aria-label={canEdit ? t.editNamed(title) : t.showNamed(title)}
                      title={!canEdit ? t.viewRoom : room ? t.editRoom : list === 'missing' ? t.editMissingItems : t.editOtherItems}>
                      <PencilIcon width={18} height={18} />
                    </button>
                  )}
                </h2>
                {room?.description && <p className="inventory-room-description">{room.description}</p>}
                {room?.comments && <p className="inventory-room-comments">{room.comments}</p>}
              </div>
            </div>
            {items.length === 0 && <p className="hint">{t.emptyRoom}</p>}
            {items.length > 0 && (
              <ul className="document-list">
                {items.map((item, index) => (
                  <li key={index} className="inventory-line">
                    <span className="inventory-line-quantity">×{item.quantity}</span>
                    <span className="inventory-line-description">{item.description}</span>
                    <span className="inventory-line-value">{formatEuros(item.valueEur, t.locale)}</span>
                  </li>
                ))}
                <li className="inventory-line inventory-line-total">
                  <span />
                  <span>{t.total}</span>
                  <span className="inventory-line-value">{formatEuros(total(items), t.locale)}</span>
                </li>
              </ul>
            )}
          </section>
        ))}
      </main>

      {editing !== null && (
        <InventoryRoomDialog
          key="inventory-room-dialog"
          room={editing}
          listItems={editing === 'missing' ? missingItems : otherItems}
          onSaveRoom={saveRoom}
          onSaveList={saveList}
          onClose={closeEditor}
        />
      )}
    </>
  )
}
