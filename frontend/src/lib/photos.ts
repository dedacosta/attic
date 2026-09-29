import { api } from '../api/api'
import type { Picture, PhotoEntry } from '../api/types'

/** Uploading one photo failed; the dialog names the file */
export class PhotoUploadError extends Error {
  constructor(readonly fileName: string, readonly reason: unknown) {
    super(`Upload of ${fileName} failed`)
  }
}

/**
 * Save an item or document, then make its stored photos match the form. New photos are
 * uploaded in order, removed ones deleted, and the order saved when it differs.
 *
 * `onSaved` gets the owner as soon as it exists, and `onPhotos` gets the form's photos after
 * each upload, with uploaded ones now stored. After a failure, a retry then updates the owner
 * instead of creating it again, and does not upload the same photo twice.
 */
export async function saveWithPhotos<T extends { id: string; pictures: Picture[] }>(
  saveOwner: () => Promise<T>,
  base: string,
  photos: PhotoEntry[],
  onSaved: (saved: T) => void,
  onPhotos: (photos: PhotoEntry[]) => void,
): Promise<T> {
  const saved = await saveOwner()
  onSaved(saved)
  const owner = `${base}/${saved.id}`

  let current = photos
  const uploaded: string[] = []
  for (const entry of photos) {
    if (entry.kind !== 'new') {
      continue
    }
    let picture: Picture
    try {
      picture = await api.addPicture(owner, entry.file)
    } catch (e) {
      throw new PhotoUploadError(entry.file.name, e)
    }
    uploaded.push(picture.id)
    current = current.map((c) => (c === entry ? { kind: 'stored', picture } : c))
    onPhotos(current)
  }

  // Every entry is stored now
  const wanted = current.flatMap((entry) => (entry.kind === 'stored' ? [entry.picture.id] : []))
  for (const picture of saved.pictures) {
    if (!wanted.includes(picture.id)) {
      await api.removePicture(owner, picture.id)
    }
  }
  // After uploads and removals the server has the kept photos in their old order, then the new ones
  const onServer = [...saved.pictures.map((p) => p.id).filter((id) => wanted.includes(id)), ...uploaded]
  if (onServer.some((id, i) => id !== wanted[i])) {
    await api.orderPictures(owner, wanted)
  }
  return saved
}
