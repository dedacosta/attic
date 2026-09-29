import { api } from '../api/api'
import type { DocumentInput, HeirDocument, PictureChange } from '../api/types'

/**
 * Create or update a document, then apply the picture change. {@code onSaved} is called as soon
 * as the document exists, so that a retry after a failed picture upload updates it instead of
 * creating it again.
 */
export async function saveDocument(
  existing: HeirDocument | null,
  input: DocumentInput,
  picture: PictureChange,
  onSaved: (saved: HeirDocument) => void,
): Promise<HeirDocument> {
  const saved = existing ? await api.updateDocument(existing.id, input) : await api.createDocument(input)
  onSaved(saved)
  if (picture.kind === 'replace') {
    await api.putPicture(`/api/documents/${saved.id}`, picture.file)
  } else if (picture.kind === 'remove' && saved.pictureUrl) {
    await api.deletePicture(`/api/documents/${saved.id}`)
  }
  return saved
}
