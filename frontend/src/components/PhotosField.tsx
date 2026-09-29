import { useEffect, useMemo, useState } from 'react'
import { useI18n } from '../i18n'
import { ImageIcon, PlusIcon, TrashIcon } from './icons'
import PhotoViewer from './PhotoViewer'
import type { PhotoEntry } from '../api/types'

export const PICTURE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/avif']

// HEIC is accepted but not offered: then iPhones convert photos to JPEG, which gets a thumbnail
const OFFERED_PICTURE_TYPES = PICTURE_TYPES.filter((type) => type !== 'image/heic').join(',')

interface Props {
  /** The photos as they should be after saving, cover first */
  photos: PhotoEntry[]
  onChange: (photos: PhotoEntry[]) => void
  onError: (message: string | null) => void
  /** Only show the photos, without add / remove / make cover */
  readOnly?: boolean
}

const keyOf = (entry: PhotoEntry) => (entry.kind === 'stored' ? entry.picture.id : entry.key)

/** A row of photos with add / remove / make cover, used by the item and document forms. */
export default function PhotosField({ photos, onChange, onError, readOnly = false }: Props) {
  const { t } = useI18n()
  const [viewing, setViewing] = useState<number | null>(null)

  // Previews of photos that are not uploaded yet, by entry key
  const localUrls = useMemo(
    () => new Map(photos.flatMap((p) => (p.kind === 'new' ? [[p.key, URL.createObjectURL(p.file)] as const] : []))),
    [photos],
  )
  useEffect(() => () => localUrls.forEach((url) => URL.revokeObjectURL(url)), [localUrls])

  const thumbnail = (entry: PhotoEntry) =>
    entry.kind === 'stored' ? entry.picture.thumbnailUrl : (localUrls.get(entry.key) ?? null)
  const full = (entry: PhotoEntry) =>
    entry.kind === 'stored' ? entry.picture.url : (localUrls.get(entry.key) ?? '')

  function add(files: FileList | null) {
    const chosen = [...(files ?? [])]
    if (chosen.length === 0) {
      return
    }
    if (chosen.some((file) => !PICTURE_TYPES.includes(file.type))) {
      onError(t.errorPictureType)
      return
    }
    onError(null)
    onChange([...photos, ...chosen.map((file): PhotoEntry => ({ kind: 'new', key: crypto.randomUUID(), file }))])
  }

  const remove = (index: number) => onChange(photos.filter((_, i) => i !== index))
  const makeCover = (index: number) => onChange([photos[index], ...photos.filter((_, i) => i !== index)])

  if (readOnly && photos.length === 0) {
    return null
  }

  return (
    <div className="photos-field">
      <ul className="photos-list">
        {photos.map((entry, index) => {
          const src = thumbnail(entry)
          return (
            <li key={keyOf(entry)} className="photo">
              <button type="button" className="photo-open" onClick={() => setViewing(index)}
                aria-label={t.openPhoto(index + 1)}>
                {src ? <img src={src} alt="" /> : <ImageIcon width={32} height={32} />}
              </button>
              {index === 0 && <span className="photo-cover">{t.cover}</span>}
              {!readOnly && (
                <div className="photo-actions">
                  {index > 0 && (
                    <button type="button" className="button button-small" onClick={() => makeCover(index)}>
                      {t.makeCover}
                    </button>
                  )}
                  <button type="button" className="icon-button" onClick={() => remove(index)}
                    aria-label={t.removePhoto}>
                    <TrashIcon width={16} height={16} />
                  </button>
                </div>
              )}
            </li>
          )
        })}
        {!readOnly && (
          <li className="photo photo-add">
            <label className="photo-add-button">
              <PlusIcon width={20} height={20} />
              <span>{t.addPhotos}</span>
              <input type="file" accept={OFFERED_PICTURE_TYPES} multiple hidden
                onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            </label>
          </li>
        )}
      </ul>
      {viewing !== null && (
        <PhotoViewer sources={photos.map(full)} start={viewing} onClose={() => setViewing(null)} />
      )}
    </div>
  )
}
