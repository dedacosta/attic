# House and Land

Step 2 of the roadmap (photos → **house and land** → house fund). The order was changed on
2026-09-29 at the user's request, and it replaces the earlier "Properties" idea.

## Goal

The heritage's real estate gets its own place in Attic:

- **House tab:** one page for the family house. It has the address, photos (outside, rooms…),
  details, the house's official documents, and comments.
- **Land tab:** the land parcels that belong to the heritage. Each parcel has the same kind of
  page.

The Inventory tab is not changed.

## Decisions

- There is exactly **zero or one house**. The House tab is a page, not a list.
- There can be **any number of land parcels**. The Land tab lists them, and a parcel opens as a
  page like the house.
- The house and land share one model, a *property* with a kind of `HOUSE` or `LAND`.
- Every property has:
  - a name;
  - an address. For land it is labelled *Location* and is free text, such as "Quinta do Vale,
    Viseu";
  - a link that opens the address in maps;
  - an estimated value in euros, which may be empty;
  - comments;
  - details: free label/value lines, with suggested labels;
  - photos;
  - official documents.
- An **official document** has:
  - a type: title deed, land registry certificate, tax record, plan, housing licence, or other;
  - an optional date and notes;
  - an ordered list of **files**, which are images or PDF (every page of a deed, a PDF plan…).
- **Access:** everyone signed in sees both tabs. Only administrators create, change or delete.
- The Land tab shows the **total estimated value** of all parcels. Parcels without a value are
  left out of the total.
- **Map link:** `https://www.google.com/maps/search/?api=1&query=<address>`. On phones this
  opens the Maps app. Nothing is sent until the link is tapped.
- **Not included:**
  - linking inventory items to the house;
  - GPS coordinates;
  - more than one house;
  - editing the list of suggested labels;
  - a house or land section in the PDF export.

## Database

Migration `V13__house_and_land.sql`:

| Table | Columns |
|---|---|
| `property` | `id`, `kind` (`HOUSE`/`LAND`, CHECK), `name` NOT NULL, `address`, `value_cents` (nullable, ≥ 0), `comments`, `created_at`, `updated_at` |
| `property_fact` | `property_id` → `property` ON DELETE CASCADE, `position`, `label` NOT NULL, `value` NOT NULL; primary key (`property_id`, `position`) |
| `property_picture` | Same shape as `inventory_picture`, with owner column `property_id`. The photos. |
| `property_document` | `id`, `property_id` → `property` ON DELETE CASCADE, `type` (CHECK), `date`, `notes`, `created_at`, `updated_at` |
| `property_document_file` | Same shape as `inventory_picture`, with owner column `document_id` → `property_document`. The files, which may be PDF. |

A unique partial index on `property (kind) WHERE kind = 'HOUSE'` enforces at most one house.

## Backend

New package `com.mephys.attic.property`.

**Files that may be PDF.**
- `Picture` accepts `application/pdf` (extension `pdf`) in addition to the image types.
- `PictureUploads.read(contentType, data)` stays images-only. A new
  `PictureUploads.readImageOrPdf(contentType, data)` also accepts PDF.
- Both keep the size limit `attic.picture.max-size`.
- `read` keeps rejecting PDF with `400`. It rewrites the type to `image/<subtype>`, and
  `image/pdf` is not an accepted type.
- `readImageOrPdf` keeps `application/pdf` as it is.
- Only the `property_document_file` endpoints call `readImageOrPdf`. Their upload mapping
  consumes `image/*` and `application/pdf`; all other upload mappings stay `image/*`.
- PDFs get no thumbnail (`Thumbnails.create` returns empty for them).
- A PDF is served with `Content-Type: application/pdf` and `Content-Disposition: inline`.

**Details** are saved with the property: the request carries `facts: [{label, value}]` in order,
and the save replaces all lines. Blank lines are dropped, and label and value are stripped.

**API.** `GET` needs a signed-in account; every other method needs `ADMIN`. This already holds
through `SecurityConfiguration`.

| Request | Response |
|---|---|
| `GET /api/properties?kind=LAND` | The parcels, by name. Each includes `facts`, `pictures` and `documents` (each document with its `files`). |
| `GET /api/properties/house` | The house, or `404` if not set up yet. |
| `GET /api/properties/{id}` | One property, or `404`. |
| `POST /api/properties` | `201`. Body `{kind, name, address, valueEur, comments, facts}`. `409` if `kind` is `HOUSE` and a house exists. |
| `PUT /api/properties/{id}` | `200`. The same body; the `kind` cannot change (`400` if it differs). |
| `DELETE /api/properties/{id}` | `204`. Deletes the facts, photos, documents and all their files. |
| `…/properties/{id}/pictures…` | Photos: add, get, thumbnail, delete, order, exactly like items. Images only. |
| `POST /api/properties/{id}/documents` | `201`. Body `{type, date, notes}`. |
| `PUT /api/property-documents/{id}`, `DELETE /api/property-documents/{id}` | `200` / `204`. Delete removes the document's files. |
| `…/property-documents/{id}/pictures…` | Files: the same routes as photos, but they accept images **or PDF**. |
| `GET /api/property-labels` | Distinct labels already used on any property, sorted. |
| `GET /api/property-document-types` | The document types, in order. |

Validation errors give `400` with a message, like the other controllers: blank name, negative
value, unknown kind or type.

## Frontend

**Navigation.** Two new tabs after Documents, visible to every signed-in account:
- **House** (`#/house`, the existing `HouseIcon`);
- **Land** (`#/land`, a new `LandIcon`). A parcel's page is `#/land/<id>`.

**`PropertyPage`** is shared by the house and each parcel, as a page rather than a dialog:
- the cover photo, large;
- the name, the address with a map link, and the estimated value;
- the details as a two-column list;
- a photos row, which opens the `PhotoViewer`;
- **Official documents:** cards with type, date, notes and file count. Tapping one opens the
  document dialog.
- comments;
- for administrators: *Edit*, *Add document* and *Delete* (land only).

**House tab.** If there is no house yet, it shows "The house is not set up yet". Administrators
also get a *Set up the house* button.

**Land tab.**
- Cards: cover, name, location, value and document count.
- A search box over name, location, details and comments.
- The count and the total value.
- Administrators get *New land*.
- Tapping a card goes to `#/land/<id>`, with a *Back to land* link.

**`PropertyDialog`** (create and edit):
- name, address or location, and estimated value;
- the details editor: rows of label and value, with add, remove, and move up/down. The label
  field uses a `<datalist>` of the suggested labels (below) plus the used ones from
  `/api/property-labels`;
- the photos row (`PhotosField`);
- comments.

It saves like items: `saveWithPhotos` with base `/api/properties`.

**`PropertyDocumentDialog`:**
- type, date and notes;
- a files row: `PhotosField` with a new `allowPdf` prop. PDF entries show `FileIcon` and "PDF",
  and tapping one opens it in a new tab; images open in the viewer.
- It saves with `saveWithPhotos` and base `/api/property-documents`. For read-only accounts it
  shows everything without edit controls.

**Suggested labels**, in the three languages:

| English | Portuguese | French |
|---|---|---|
| Area (m²) | Área (m²) | Surface (m²) |
| Cadastral article | Artigo matricial | Référence cadastrale |
| Land registry | Registo predial | Registre foncier |
| Year built | Ano de construção | Année de construction |
| Rooms | Divisões | Pièces |
| Tax value | Valor patrimonial | Valeur fiscale |
| Water supplier | Fornecedor de água | Fournisseur d'eau |
| Electricity supplier | Fornecedor de eletricidade | Fournisseur d'électricité |

All new text exists in English, Portuguese and French.

## Testing

Backend (MockMvc, like the existing tests):
- Create, get, update and delete a house and a parcel.
- A second house gives `409`, and changing the kind gives `400`.
- Details keep their order and are replaced on save; blank lines are dropped.
- `GET /api/properties/house` gives `404` before setup.
- Land is listed by name and includes pictures and documents.
- Photos accept images and reject PDF (`400`).
- Document files accept images and PDF. A PDF is served as `application/pdf` with
  `inline`, and has no thumbnail.
- Deleting a document removes its files. Deleting a property removes its photo files and all its
  documents' files.
- `/api/property-labels` returns the used labels, distinct and sorted.
- A user can read everything but gets `403` on every change.
- Migration: V13 applies on a V12 database, and a second house row is rejected by the index.

Frontend: the `tsc` type-check in the build, then a browser check:
- set up the house;
- add photos, details and a document with a PDF and two images;
- open the PDF and the images;
- add two parcels and check the total;
- the map link;
- as a user: read-only;
- phone width.
