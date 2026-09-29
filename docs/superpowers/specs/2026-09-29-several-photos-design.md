# Several photos for items and documents

Step 1 of 3 (photos → house fund → properties). The house fund reuses this for expense
receipts, and properties reuse it for photos and plans.

## Goal

An inventory item or a document can have several photos instead of one: different views of an
item, the front and back of an ID card, every page of a contract. The first photo is the
*cover* and is the one shown on cards.

## Decisions

- Items and documents both get photo lists, and the single-picture code is removed.
- Existing pictures are kept: each becomes the cover (position 0) of its owner.
- The order is set with "make cover", which moves a photo to the front. There is no
  drag-and-drop.
- Photo changes in a dialog apply on **Save**; **Cancel** discards them.
- Only images, at most `attic.picture.max-size` (10 MB) each, as today. PDF comes with
  properties (step 3).
- Access is unchanged. Everyone signed in may view item photos. Document photos follow the
  document's visibility: a user sees those of their own heir, and only administrators see those
  without heir. Only administrators add, remove or reorder.

## Database

Migration `V12__several_photos.sql` rebuilds `inventory_picture` and `document_picture`, which
nothing references:

- The `UNIQUE` constraint on the owner column (`item_id`, `document_id`) is dropped.
- A `position INTEGER NOT NULL` column is added. Existing rows get `0`.
- An index is added on `(owner column, position)`.
- `ON DELETE CASCADE` from the owner is kept.

Files stay where they are (`<picture dir>/<picture id>.<ext>`), so backups are unchanged.

## Backend

`PictureRepository` changes from one picture per owner to an ordered list per owner:

| Method | Does |
|---|---|
| `add(ownerId, picture)` | Stores the file and appends it at the end. Returns the new `PictureInfo`, or empty if the owner does not exist. |
| `list(ownerId)` / `listAll()` | `PictureInfo`s in order; `listAll` is grouped by owner for list endpoints. |
| `find(ownerId, pictureId)` / `findThumbnail(ownerId, pictureId)` | One picture or its thumbnail, only if it belongs to that owner. |
| `delete(ownerId, pictureId)` | Removes the row and then the file. |
| `reorder(ownerId, pictureIds)` | Sets positions 0..n-1. Rejected (400) unless the ids are exactly the owner's pictures. |
| `deleteOwner(ownerId)` | Deletes the owner row (the cascade removes picture rows), then all its files. |

Positions are renumbered 0..n-1 after a delete, so they have no gaps.

`PictureInfo` becomes `(UUID id, boolean hasThumbnail)`. URL helpers build
`<base>/pictures/<id>` and `<base>/pictures/<id>/thumbnail`. Picture ids are never reused, so
these responses are sent with `Cache-Control: private, max-age=31536000, immutable`.

### API

Items shown; documents are the same below `/api/documents/{id}`.

| Request | Response |
|---|---|
| `GET /api/items`, `GET /api/items/{id}` | Each item has `pictures: [{ id, url, thumbnailUrl }]` in order (`thumbnailUrl` may be `null`). `pictureUrl` / `thumbnailUrl` are removed. |
| `POST /api/items/{id}/pictures` (body: the image, `Content-Type: image/*`) | `201` with `{ id, url, thumbnailUrl }`; `404` unknown item; `413` too large; `400` unsupported type. |
| `GET /api/items/{id}/pictures/{pictureId}` | The image; `404` if not this item's. |
| `GET /api/items/{id}/pictures/{pictureId}/thumbnail` | JPEG thumbnail; `404` if none. |
| `DELETE /api/items/{id}/pictures/{pictureId}` | `204`; `404` if not this item's. |
| `PUT /api/items/{id}/pictures/order` (body: `["id", …]`) | `204`; `400` if the ids are not exactly this item's pictures. |

The old `/picture` and `/thumbnail` endpoints are removed; the app's own frontend is their only
client.

Security rules follow the existing patterns: `GET` needs a signed-in account, everything else
needs `ADMIN`, and document photo `GET`s check `CurrentAccount.maySee` like the document itself.

## Frontend

- **Types:** `Item` and `HeirDocument` get `pictures: Picture[]`
  (`{ id, url, thumbnailUrl }`). `PictureChange` is replaced by a list change: photos to add
  (`File[]`), ids to remove, and the wanted order.
- **Cards** (`ItemCard`, `DocumentCard`) show `pictures[0]`, and a small count badge (camera icon + number) when there are
  two or more.
- **`PhotosField`** (replaces `PictureField`) is a row of thumbnails; the first is labelled
  *Cover*.
  - "Add photos" takes several files at once. Phones also offer the camera.
  - Each photo has *Remove* and *Make cover*. New photos show a preview from the local file
    until saved.
  - Read-only accounts see the thumbnails only.
  - HEIC is accepted but not offered, as today.
- **`PhotoViewer`** opens full screen on a thumbnail tap. It has previous/next buttons, arrow
  keys, swipe on touch screens, and Escape or a close button. It replaces the "Open full picture"
  link.
- **Saving** (one shared helper replacing `lib/documents.ts` and the picture code in `InventoryView`): save the
  item/document → upload new photos one by one → delete removed ones → `PUT` the order if it
  changed.
  - The owner is known as soon as it is saved, so a retry never creates it twice. That is the
    existing pattern.
  - A failed upload keeps the dialog open with an error naming the file. Photos already
    uploaded are not uploaded again.
- **i18n:** new strings (add photos, cover, make cover, remove photo, photo n of m, previous,
  next, upload failed for a file) in English, Portuguese and French. Old single-picture strings
  are removed.

## Testing

Backend (MockMvc, like the existing tests):
- Add three photos; the order is the insertion order; the list response has three entries and
  the first is the cover.
- Reorder: `PUT order` changes the order; a wrong or partial id list gives `400`.
- Delete one photo: its file is gone, the others stay, and positions are renumbered.
- Deleting an item or document removes all its photo files.
- Access: users can view item photos but not add, delete or reorder (`403`). Users cannot get
  photos of another heir's documents or of documents without heir (`404`).
- A photo id of another owner gives `404` on get, delete and thumbnail.
- Migration: a database at V11 with one picture per item and document migrates to V12 with each
  picture at position 0, and it is still served.

Frontend: the `tsc` type-check in the build, then a manual check in the browser (add several
photos, make cover, remove, cancel, viewer on desktop and phone width).

## Out of scope

Drag-and-drop ordering, captions per photo, PDF, and photos in the inventory PDF export.
