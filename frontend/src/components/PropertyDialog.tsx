import { useEffect, useState, type FormEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { decimalSeparator } from '../lib/format'
import { useI18n } from '../i18n'
import { saveWithPhotos, PhotoUploadError } from '../lib/photos'
import { useModal } from '../lib/useModal'
import { CloseIcon } from './icons'
import DetailsEditor from './DetailsEditor'
import PhotosField from './PhotosField'
import RoomsEditor, { type RoomName } from './RoomsEditor'
import { storedPhotos, type Fact, type InventoryRoom, type PhotoEntry, type Property, type PropertyKind } from '../api/types'

const VALUE_PATTERN = /^\d+([.,]\d{1,2})?$/

const toRoomName = (room: InventoryRoom): RoomName => ({ id: room.id, title: room.title, description: room.description ?? '' })

interface Props {
  /** Null to create a new property of the given kind */
  property: Property | null
  kind: PropertyKind
  onSaved: (property: Property) => void
  onClose: () => void
}

/**
 * Create or edit a building or a land parcel: main fields, photos and, for land, details. A
 * building has its rooms instead, by name; its inventory is kept room by room. Administrators only.
 */
export default function PropertyDialog({ property, kind, onSaved, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const [saved, setSaved] = useState<Property | null>(property)
  const [name, setName] = useState(property?.name ?? '')
  const [address, setAddress] = useState(property?.address ?? '')
  const [value, setValue] = useState(
    property?.valueEur != null ? property.valueEur.toFixed(2).replace('.', decimalSeparator(t.locale)) : '',
  )
  const [facts, setFacts] = useState<Fact[]>(property?.facts ?? [])
  const [comments, setComments] = useState(property?.comments ?? '')
  const [photos, setPhotos] = useState<PhotoEntry[]>(() => storedPhotos(property?.pictures ?? []))
  const [usedLabels, setUsedLabels] = useState<string[]>([])
  // The rooms of a building; null until those of an existing one are loaded, so that saving early keeps them
  const [rooms, setRooms] = useState<RoomName[] | null>(property ? null : [])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isBuilding = kind === 'BUILDING'

  useEffect(() => {
    api.propertyLabels().then(setUsedLabels, () => setUsedLabels([]))
    if (isBuilding && property) {
      api.listInventory(property.id).then(
        (loaded) => setRooms(loaded.map(toRoomName)),
        () => setRooms(null),
      )
    }
  }, [isBuilding, property])

  const title = saved ? (isBuilding ? t.editBuilding : t.editLand) : (isBuilding ? t.newBuilding : t.newLand)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmedValue = value.trim()
    if (!name.trim()) {
      return setError(t.errorName)
    }
    if (trimmedValue && !VALUE_PATTERN.test(trimmedValue)) {
      return setError(t.errorValue)
    }
    const input = {
      kind,
      name: name.trim(),
      address: address.trim() || null,
      valueEur: trimmedValue ? Number(trimmedValue.replace(',', '.')) : null,
      comments: comments.trim() || null,
      // A building has rooms instead of details
      facts: isBuilding ? [] : facts.filter((fact) => fact.label.trim() || fact.value.trim()),
    }
    setSaving(true)
    setError(null)
    try {
      // From the first save on the property exists: a retry after a failed upload updates it
      const result = await saveWithPhotos(
        () => (saved ? api.updateProperty(saved.id, input) : api.createProperty(input)),
        '/api/properties', photos, setSaved, setPhotos,
      )
      if (isBuilding && rooms !== null) {
        const kept = rooms.filter((room) => room.title.trim())
          .map((room) => ({ id: room.id, title: room.title.trim(), description: room.description.trim() || null }))
        const savedRooms = await api.saveInventoryRoomList(result.id, kept)
        // The new rooms exist from here on: a retry does not add them again
        setRooms(savedRooms.map(toRoomName))
      }
      onSaved(await api.getProperty(result.id))
    } catch (e) {
      setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="property-dialog-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="property-dialog-title">{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} disabled={saving} aria-label={t.close}>
            <CloseIcon />
          </button>
        </header>

        <div className="dialog-content">
          <PhotosField readOnly={saving} photos={photos} onChange={setPhotos} onError={setError} />

          <fieldset className="fields" disabled={saving}>
            <label className="field field-wide">
              <span>{t.name}</span>
              <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
            </label>
            <label className="field field-wide">
              <span>{isBuilding ? t.address : t.landLocation} <small>{t.optional}</small></span>
              <input value={address} onChange={(e) => setAddress(e.target.value)} />
            </label>
            <label className="field">
              <span>{t.estimatedValue} <small>{t.optional}</small></span>
              <input inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value)}
                placeholder={`0${decimalSeparator(t.locale)}00`} />
            </label>
            {isBuilding
              ? rooms !== null && <RoomsEditor rooms={rooms} onChange={setRooms} />
              : <DetailsEditor facts={facts} onChange={setFacts} usedLabels={usedLabels} />}
            <label className="field field-wide">
              <span>{t.comments} <small>{t.optional}</small></span>
              <textarea rows={3} value={comments} onChange={(e) => setComments(e.target.value)} />
            </label>
          </fieldset>

          {error && <p className="form-error" role="alert">{error}</p>}
        </div>

        <footer className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="button" onClick={onClose} disabled={saving}>{t.cancel}</button>
          <button type="submit" className="button button-primary" disabled={saving}>
            {saving ? t.saving : saved ? t.save : t.create}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
