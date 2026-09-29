import { useEffect, useMemo, useState } from 'react'
import { useI18n } from '../i18n'
import { FileIcon, ImageIcon, PlusIcon, TrashIcon } from './icons'
import PhotoViewer from './PhotoViewer'
import type { PhotoEntry } from '../api/types'

export const PICTURE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/avif']

const PDF = 'application/pdf'

// HEIC is accepted but not offered: then iPhones convert photos to JPEG, which gets a thumbnail
const OFFERED_PICTURE_TYPES = PICTURE_TYPES.filter((type) => type !== 'image/heic')

interface Props {
  /** The photos as they should be after saving, cover first */
  photos: PhotoEntry[]
  onChange: (photos: PhotoEntry[]) => void
  onError: (message: string | null) => void
  /** Only show the photos, without add / remove / make cover */
  readOnly?: boolean
  /** Also accept PDF files, for the pages of official documents */
  allowPdf?: boolean
}

const keyOf = (entry: PhotoEntry) => (entry.kind === 'stored' ? entry.picture.id : entry.key)

export const isPdf = (entry: PhotoEntry) =>
  (entry.kind === 'stored' ? entry.picture.contentType : entry.file.type) === PDF

/** A row of photos with add / remove / make cover, used by the item, document and property forms. */
export default function PhotosField({ photos, onChange, onError, readOnly = false, allowPdf = false }: Props) {
  const { t } = useI18n()
  const [viewing, setViewing] = useState<number | null>(null)

  // Previews of photos that are not uploaded yet, by entry key
  const localUrls = useMemo(
    () => new Map(photos.flatMap((p) => (p.kind === 'new' ? [[p.key, URL.createObjectURL(p.file)] as const] : []))),
    [photos],
  )
  useEffect(() => () => localUrls.forEach((url) => URL.revokeObjectURL(url)), [localUrls])

  const thumbnail = (entry: PhotoEntry) =>
    entry.kind === 'stored' ? entry.picture.thumbnailUrl : isPdf(entry) ? null : (localUrls.get(entry.key) ?? null)
  const full = (entry: PhotoEntry) =>
    entry.kind === 'stored' ? entry.picture.url : (localUrls.get(entry.key) ?? '')

  // The viewer shows images only; PDFs open in the browser's own viewer
  const images = photos.filter((entry) => !isPdf(entry))

  function open(entry: PhotoEntry) {
    if (isPdf(entry)) {
      window.open(full(entry), '_blank', 'noopener')
    } else {
      setViewing(images.indexOf(entry))
    }
  }

  function add(files: FileList | null) {
    const chosen = [...(files ?? [])]
    if (chosen.length === 0) {
      return
    }
    if (chosen.some((file) => !PICTURE_TYPES.includes(file.type) && !(allowPdf && file.type === PDF))) {
      onError(allowPdf ? t.errorPictureOrPdfType : t.errorPictureType)
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
              <button type="button" className="photo-open" onClick={() => open(entry)}
                aria-label={t.openPhoto(index + 1)}>
                {src ? <img src={src} alt="" /> : isPdf(entry) ? (
                  <span className="photo-pdf"><FileIcon width={32} height={32} /> PDF</span>
                ) : <ImageIcon width={32} height={32} />}
              </button>
              {index === 0 && !allowPdf && <span className="photo-cover">{t.cover}</span>}
              {!readOnly && (
                <div className="photo-actions">
                  {index > 0 && !allowPdf && (
                    <button type="button" className="button button-small" onClick={() => makeCover(index)}>
                      {t.makeCover}
                    </button>
                  )}
                  <span className="spacer" />
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
              <span>{allowPdf ? t.addFiles : t.addPhotos}</span>
              <input type="file" accept={[...OFFERED_PICTURE_TYPES, ...(allowPdf ? [PDF] : [])].join(',')} multiple hidden
                onChange={(e) => { add(e.target.files); e.target.value = '' }} />
            </label>
          </li>
        )}
      </ul>
      {viewing !== null && (
        <PhotoViewer sources={images.map(full)} start={viewing} onClose={() => setViewing(null)} />
      )}
    </div>
  )
}
