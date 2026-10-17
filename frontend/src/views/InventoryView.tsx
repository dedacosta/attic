import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type Modifier } from '@dnd-kit/core'
import { SortableContext, arrayMove, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { api, apiErrorMessage } from '../api/api'
import { exportInventoryLatex } from '../lib/latex'
import { PhotoUploadError, saveWithPhotos } from '../lib/photos'
import InventoryLine from '../components/InventoryLine'
import PdfViewer from '../components/PdfViewer'
import ConfirmDialog from '../components/ConfirmDialog'
import PhotosField from '../components/PhotosField'
import SearchBar from '../components/SearchBar'
import { AMOUNT_PATTERN, formatEuros, normalize, parseAmount } from '../lib/format'
import { NEW_ITEM, parseItemForm, toItemForm, type ItemForm } from '../lib/inventory'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import { FileIcon, PlusIcon, TrashIcon } from '../components/icons'
import { storedPhotos, type Building, type InventoryItem, type InventoryRoom, type InventoryRoomInput, type PhotoEntry } from '../api/types'
import type { GeneratedPdf } from '../lib/pdf'

/** The lists of a house that are not rooms */
type HouseList = 'other' | 'missing'

/** A section of the list: a room or a list of the house, with the items that are shown */
interface Section {
  key: string
  room: InventoryRoom | null
  list: HouseList | null
  title: string
  /** All its items, and those the search finds */
  all: InventoryItem[]
  items: InventoryItem[]
}

/** The line being typed in: an item of a section, or a new one at its end */
interface Draft {
  key: string
  /** Null for a new line */
  index: number | null
  form: ItemForm
  /** Tells new lines apart, so each one takes the focus */
  serial: number
}

/** Sums are rounded to cents, as 33.33 + 66.67 is not exactly 100 in floating point */
const sum = (values: number[]) => Math.round(values.reduce((all, value) => all + value, 0) * 100) / 100

/** What a room holds in the inventory, besides its name: then it is listed */
const hasContent = (room: InventoryRoom) =>
  room.items.length > 0 || room.pictures.length > 0 || room.comments !== null || room.pdfTitle !== null

/** A line is dragged up and down only */
const vertical: Modifier = ({ transform }) => ({ ...transform, x: 0 })

/** What a line is worth while it is typed: the saved value until the typed one is an amount */
function typedValue(form: ItemForm, saved: number): number {
  const value = form.value.trim()
  return value === '' ? 0 : AMOUNT_PATTERN.test(value) ? parseAmount(value) : saved
}

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
  const [preview, setPreview] = useState<GeneratedPdf | null>(null)
  // Items are added and changed in place, a line at a time, into the room chosen to add to
  const [draft, setDraft] = useState<Draft | null>(null)
  const [lineError, setLineError] = useState<{ key: string; message: string } | null>(null)
  // Rooms of the house chosen to add items to, listed even while they have none
  const [chosen, setChosen] = useState<string[]>([])
  // The room picked in the dropdown, added with the button next to it
  const [picked, setPicked] = useState('')
  // A room to take out of the inventory, once its removal is confirmed
  const [removing, setRemoving] = useState<InventoryRoom | null>(null)
  // Photos of a room while they are saved, the new ones shown from their files
  const [savingPhotos, setSavingPhotos] = useState<Record<string, PhotoEntry[]>>({})
  const serial = useRef(0)
  const saving = useRef(Promise.resolve())
  // The rooms as last changed here: a save runs after those before it, and sends the room as it is then
  const latestRooms = useRef<InventoryRoom[] | null>(null)

  const showRooms = useCallback((list: InventoryRoom[] | null) => {
    latestRooms.current = list
    setRooms(list)
  }, [])

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
      showRooms(loadedRooms)
      setOtherItems(loadedOtherItems)
      setMissingItems(loadedMissingItems)
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [houseId, showRooms])

  useEffect(() => {
    loadHouses()
  }, [loadHouses])

  useEffect(() => {
    // Another house: its inventory replaces the one shown
    showRooms(null)
    setOtherItems([])
    setMissingItems([])
    setDraft(null)
    setLineError(null)
    setChosen([])
    setPicked('')
    setRemoving(null)
    setSavingPhotos({})
    reload()
  }, [reload, showRooms])

  const house = houses?.find((candidate) => candidate.id === houseId) ?? null
  const totalCount = (rooms ?? []).reduce((count, room) => count + room.items.length, 0) + otherItems.length
    + missingItems.length

  // Items are dragged by their grip, with a mouse, a finger or the keyboard
  const sensors = useSensors(useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))
  // While searching only some items of a room are shown: they are not moved then
  const sortable = canEdit && query === ''

  // The section a new line is typed in, which is listed even while empty
  const adding = draft?.index === null ? draft.key : null

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
      all: room.items,
      items: shown(room.items, has([room.title, room.description, room.pdfTitle, room.comments].join(' '))),
    }))
    list.push({ key: 'other', room: null, list: 'other', title: t.otherItems, all: otherItems,
      items: shown(otherItems, has(t.otherItems)) })
    list.push({ key: 'missing', room: null, list: 'missing', title: t.missingItems, all: missingItems,
      items: shown(missingItems, has(t.missingItems)) })
    // A room is listed once it has items, photos or comments, or is chosen to add items to. What
    // is missing is always listed, unless a search finds nothing in it.
    return list.filter((section) => section.items.length > 0 || section.key === adding || chosen.includes(section.key)
      || (query === '' && (section.list === 'missing' || (section.room !== null && hasContent(section.room)))))
  }, [rooms, otherItems, missingItems, query, adding, chosen, t])

  const visibleCount = sections.reduce((count, section) => count + section.items.length, 0)

  /** The total of a section with the line being typed in as it is typed */
  function sectionTotal(section: Section): number {
    const values = section.items.map((item) => {
      const index = section.all.indexOf(item)
      return draft?.key === section.key && draft.index === index ? typedValue(draft.form, item.valueEur) : item.valueEur
    })
    if (draft?.key === section.key && draft.index === null) {
      values.push(typedValue(draft.form, 0))
    }
    return sum(values)
  }

  // What is missing is not part of what the house holds
  const houseTotal = sum(sections.filter((section) => section.list !== 'missing').map(sectionTotal))

  const itemsOf = (key: string) =>
    key === 'other' ? otherItems : key === 'missing' ? missingItems : (rooms?.find((room) => room.id === key)?.items ?? [])

  /** Save after what is saved already. On an error what the server has is shown again. */
  function queueSave(key: string, save: () => Promise<unknown>) {
    saving.current = saving.current.then(async () => { await save() }).catch(async (e) => {
      setLineError({ key, message: e instanceof PhotoUploadError
        ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t) })
      await reload()
    })
  }

  const roomInput = (room: InventoryRoom): InventoryRoomInput => ({
    title: room.title, propertyId: room.propertyId, pdfTitle: room.pdfTitle, comments: room.comments, items: room.items,
  })

  /** Show a change of a room at once, and save the room */
  function changeRoom(id: string, change: Partial<InventoryRoom>) {
    showRooms(latestRooms.current?.map((room) => (room.id === id ? { ...room, ...change } : room)) ?? null)
    queueSave(id, async () => {
      const room = latestRooms.current?.find((candidate) => candidate.id === id)
      if (room) {
        await api.updateInventoryRoom(id, roomInput(room))
      }
    })
  }

  /** Show the items of a section at once, and save them */
  function saveItems(key: string, items: InventoryItem[]) {
    const propertyId = houseId
    if (key === 'other') {
      setOtherItems(items)
      queueSave(key, () => api.saveOtherInventoryItems(propertyId, items))
    } else if (key === 'missing') {
      setMissingItems(items)
      queueSave(key, () => api.saveMissingInventoryItems(propertyId, items))
    } else {
      changeRoom(key, { items })
    }
  }

  /** A text of a room, saved when its field is left */
  function saveRoomText(room: InventoryRoom, field: 'pdfTitle' | 'comments', text: string) {
    const value = text.trim() || null
    if (value !== room[field]) {
      changeRoom(room.id, { [field]: value })
    }
  }

  /** The photos of a room as they should be: new ones are uploaded, those left out removed */
  function savePhotos(id: string, photos: PhotoEntry[]) {
    setSavingPhotos((all) => ({ ...all, [id]: photos }))
    queueSave(id, async () => {
      const room = latestRooms.current?.find((candidate) => candidate.id === id)
      let current = photos
      try {
        if (room) {
          await saveWithPhotos(() => api.updateInventoryRoom(id, roomInput(room)), '/api/inventory', photos, () => {},
            (uploaded) => {
              current = uploaded
              setSavingPhotos((all) => ({ ...all, [id]: uploaded }))
            })
          const pictures = current.flatMap((entry) => (entry.kind === 'stored' ? [entry.picture] : []))
          showRooms(latestRooms.current?.map((other) => (other.id === id ? { ...other, pictures } : other)) ?? null)
        }
      } finally {
        setSavingPhotos((all) => {
          const others = { ...all }
          delete others[id]
          return others
        })
      }
    })
  }

  // The rooms of the house not listed yet, and the other items, to choose from to add items to
  const available = [
    ...(rooms ?? []).map((room) => ({ key: room.id, title: room.title })),
    { key: 'other', title: t.otherItems },
  ].filter(({ key }) => {
    const room = rooms?.find((candidate) => candidate.id === key)
    return !(room ? hasContent(room) : itemsOf(key).length > 0) && !chosen.includes(key)
  })
  const pick = available.some(({ key }) => key === picked) ? picked : ''

  /** List a room chosen from the house, and open its first line */
  function chooseRoom(key: string) {
    setPicked('')
    setChosen((list) => [...list, key])
    addLine(key)
  }

  /** Take a room off the page; what it holds in the inventory is deleted first, once confirmed */
  function removeRoom(room: InventoryRoom) {
    if (hasContent(room)) {
      setRemoving(room)
    } else {
      dropRoom(room.id)
    }
  }

  /** The room is no longer listed, and goes back into the dropdown. It stays on the house. */
  function dropRoom(id: string) {
    setChosen((list) => list.filter((key) => key !== id))
    if (draft?.key === id) {
      setDraft(null)
    }
    if (lineError?.key === id) {
      setLineError(null)
    }
  }

  /** Its items, PDF title, comments and photos are deleted from the inventory */
  function emptyRoom(room: InventoryRoom) {
    dropRoom(room.id)
    changeRoom(room.id, { items: [], pdfTitle: null, comments: null })
    if (room.pictures.length > 0) {
      savePhotos(room.id, [])
    }
    setRemoving(null)
  }

  /** Move an item within its room, dropped by its grip on the place of another */
  function moveLine(key: string, from: number, to: number) {
    const items = [...itemsOf(key)]
    // What is typed in the line moved goes with it
    if (draft?.key === key && draft.index !== null) {
      const item = parseItemForm(draft.form)
      if (typeof item !== 'string' && item.description) {
        items[draft.index] = item
      }
      setDraft(null)
    }
    saveItems(key, arrayMove(items, from, to))
  }

  function addLine(key: string) {
    serial.current += 1
    setLineError(null)
    setDraft({ key, index: null, form: NEW_ITEM, serial: serial.current })
  }

  function changeLine(key: string, index: number | null, form: ItemForm) {
    if (draft?.key !== key || draft.index !== index) {
      setLineError(null)
    }
    setDraft((current) => ({ key, index, form, serial: current?.serial ?? 0 }))
  }

  /** Save the line left, if it is the one typed in. Without a description, it is not kept. */
  function commitLine(key: string, index: number | null, next: boolean) {
    if (draft?.key !== key || draft.index !== index) {
      return
    }
    const items = itemsOf(key)
    if (!draft.form.description.trim()) {
      setDraft(null)
      setLineError(null)
      if (index !== null) {
        saveItems(key, items.filter((_, i) => i !== index))
      }
      return
    }
    const item = parseItemForm(draft.form)
    if (typeof item === 'string') {
      return setLineError({ key, message: item === 'quantity' ? t.errorQuantity : t.errorValue })
    }
    setLineError(null)
    if (next && index === null) {
      addLine(key)
    } else {
      setDraft(null)
    }
    const old = index === null ? null : items[index]
    if (old?.description !== item.description || old.quantity !== item.quantity || old.valueEur !== item.valueEur) {
      saveItems(key, index === null ? [...items, item] : items.map((other, i) => (i === index ? item : other)))
    }
  }

  function removeLine(key: string, index: number | null) {
    if (draft?.key === key) {
      setDraft(null)
    }
    setLineError(null)
    if (index !== null) {
      saveItems(key, itemsOf(key).filter((_, i) => i !== index))
    }
  }

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
      exportInventoryLatex(rooms, totalCount, filters, t, house?.name ?? '', house?.address ?? null)
      return
    }
    // Loaded on demand: the PDF library is large
    const { exportInventoryPdf } = await import('../lib/pdf')
    setPreview(await exportInventoryPdf(rooms, totalCount, filters, t, house?.name ?? '', house?.address ?? null))
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

        {canEdit && house && rooms !== null && (
          <div className="toolbar inventory-add">
            <select value={pick} onChange={(e) => setPicked(e.target.value)} disabled={available.length === 0}
              aria-label={t.chooseRoom} title={t.chooseRoom}>
              <option value="" disabled>{t.chooseRoom}</option>
              {available.map(({ key, title }) => <option key={key} value={key}>{title}</option>)}
            </select>
            <button type="button" className="button button-primary" onClick={() => chooseRoom(pick)} disabled={!pick}>
              <PlusIcon width={18} height={18} /> {t.addRoom}
            </button>
          </div>
        )}

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

        {rooms !== null && sections.map((section) => {
          const { key, room, list, title, all, items } = section
          const newLine = draft?.key === key && draft.index === null ? draft : null
          return (
            <section key={key} className="group inventory-room" aria-label={title}>
              <div className="inventory-room-header">
                {room && (canEdit || room.pictures.length > 0) && (
                  <PhotosField compact readOnly={!canEdit} photos={savingPhotos[room.id] ?? storedPhotos(room.pictures)}
                    onChange={(photos) => savePhotos(room.id, photos)}
                    onError={(message) => setLineError(message === null ? null : { key, message })} />
                )}
                <h2 className="group-title">
                  {title} <span className="group-count">{items.length}</span>
                </h2>
                {room && canEdit && (
                  <>
                    {/* Keyed by what is saved, so they show it again after a failed save */}
                    <input key={`pdf-title-${room.pdfTitle}`} className="inventory-room-field" defaultValue={room.pdfTitle ?? ''}
                      placeholder={t.pdfRoomTitle} aria-label={t.pdfRoomTitle} title={t.pdfRoomTitleHint}
                      onBlur={(e) => saveRoomText(room, 'pdfTitle', e.target.value)} />
                    <textarea key={`comments-${room.comments}`} className="inventory-room-field inventory-room-comments-field"
                      rows={1} defaultValue={room.comments ?? ''} placeholder={t.comments} aria-label={t.comments}
                      onBlur={(e) => saveRoomText(room, 'comments', e.target.value)} />
                  </>
                )}
                {room && canEdit && (
                  <button type="button" className="icon-button inventory-room-remove" onClick={() => removeRoom(room)}
                    aria-label={`${t.removeRoom}: ${room.title}`} title={t.removeRoom}>
                    <TrashIcon width={18} height={18} />
                  </button>
                )}
              </div>
              {room?.description && <p className="inventory-room-description">{room.description}</p>}
              {!canEdit && room?.comments && <p className="inventory-room-comments">{room.comments}</p>}
              {items.length === 0 && !newLine && <p className="hint">{list === 'missing' ? t.nothingMissing : t.emptyRoom}</p>}
              {(items.length > 0 || newLine || canEdit) && (
                <DndContext sensors={sensors} collisionDetection={closestCenter} modifiers={[vertical]}
                  onDragEnd={({ active, over }) => {
                    if (over && active.id !== over.id) {
                      moveLine(key, all.findIndex((_, i) => `${key}:${i}` === active.id),
                        all.findIndex((_, i) => `${key}:${i}` === over.id))
                    }
                  }}>
                  <SortableContext items={sortable ? all.map((_, i) => `${key}:${i}`) : []} strategy={verticalListSortingStrategy}>
                    <ul className="document-list">
                      {items.map((item) => {
                        const index = all.indexOf(item)
                        return canEdit ? (
                          <InventoryLine key={index} autoFocus={false}
                            form={draft?.key === key && draft.index === index ? draft.form : toItemForm(item, t.locale)}
                            onChange={(form) => changeLine(key, index, form)}
                            onCommit={(next) => commitLine(key, index, next)}
                            onCancel={() => { setDraft(null); setLineError(null) }}
                            onRemove={() => removeLine(key, index)}
                            sortId={sortable ? `${key}:${index}` : undefined} />
                        ) : (
                          <li key={index} className="inventory-line">
                            <span className="inventory-line-quantity">×{item.quantity}</span>
                            <span className="inventory-line-description">{item.description}</span>
                            <span className="inventory-line-value">{formatEuros(item.valueEur, t.locale)}</span>
                          </li>
                        )
                      })}
                      {newLine && (
                        <InventoryLine key={`new-${newLine.serial}`} autoFocus form={newLine.form} inSortable={sortable}
                          onChange={(form) => changeLine(key, null, form)}
                          onCommit={(next) => commitLine(key, null, next)}
                          onCancel={() => { setDraft(null); setLineError(null) }}
                          onRemove={() => removeLine(key, null)} />
                      )}
                      {canEdit && (
                        <li className="inventory-line-add">
                          <button type="button" className="icon-button" onClick={() => addLine(key)}
                            aria-label={t.addItemIn(title)} title={t.addItemIn(title)}>
                            <PlusIcon width={20} height={20} />
                          </button>
                        </li>
                      )}
                      {(items.length > 0 || newLine) && (
                        <li className="inventory-line inventory-line-total">
                          <span />
                          <span>{t.total}</span>
                          <span className="inventory-line-value">{formatEuros(sectionTotal(section), t.locale)}</span>
                        </li>
                      )}
                    </ul>
                  </SortableContext>
                </DndContext>
              )}
              {lineError?.key === key && <p className="form-error" role="alert">{lineError.message}</p>}
            </section>
          )
        })}
      </main>

      {removing && (
        <ConfirmDialog
          title={t.removeRoomTitle(removing.title)}
          message={t.removeRoomMessage}
          confirmLabel={t.removeRoom}
          onConfirm={async () => emptyRoom(removing)}
          onCancel={() => setRemoving(null)}
        />
      )}
      {preview && (
        <PdfViewer source={preview.blob} title={preview.title} fileName={preview.fileName}
          onClose={() => setPreview(null)} />
      )}
    </>
  )
}
