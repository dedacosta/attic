# House and Land Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two new tabs, House (one page) and Land (a list of parcels opening as pages). Each property has details, photos, and official documents whose files may be PDF.

**Architecture:** One `property` model with kind `HOUSE`/`LAND` in a new package `com.mephys.attic.property`. It reuses `PictureRepository` for property photos (images) and for official-document files (images or PDF). The frontend adds a shared `PropertyPage`, `PropertyDialog` and `PropertyDocumentDialog`, and extends `PhotosField` with `allowPdf`.

**Tech Stack:** Java 25, Spring Boot 4 (Web MVC, JdbcClient, Security), SQLite + Flyway, JUnit 5 + MockMvc; React 19 + TypeScript + Vite.

**Spec:** `docs/superpowers/specs/2026-09-29-house-and-land-design.md`

## Global Constraints

- The Inventory tab and its code are not changed.
- `GET` needs a signed-in account; other methods below `/api` need `ADMIN` (already in `SecurityConfiguration`).
- File size limit: `attic.picture.max-size` (10 MB). Photos are images only; document files are images or `application/pdf`.
- A PDF is served with `Content-Type: application/pdf` and `Content-Disposition: inline`.
- All new user-facing text exists in English, Portuguese and French.
- Map link: `https://www.google.com/maps/search/?api=1&query=` + `encodeURIComponent(address)`.
- Commands:
  - backend: `./mvnw test -Dskip.npm -Dskip.installnodenpm`
  - frontend type-check (in `frontend/`): `PATH="$PWD/../target/node:$PATH" node_modules/.bin/tsc --noEmit -p .`
  - full build: `./mvnw -q package`

## Review Focus

1. **A second house** created at the same time or by an old client: `409`, and the database index also refuses it.
2. **PDF sent to a photo endpoint:** `400`. **SVG or plain text sent to a document-file endpoint:** `400` / `415`.
3. **Deleting a property with documents:** every photo file and every document file is removed from disk, and none belonging to another property is touched.
4. **Detail lines with blank labels or values,** or with surrounding spaces: they are dropped or stripped, never stored as empty rows.
5. **Land detail page for an unknown or deleted id** (`#/land/<bad id>`): a "not found" message with a way back, not a blank page.

---

## File Structure

Backend (`src/main/java/com/mephys/attic/…`):
- `resources/db/migration/V13__house_and_land.sql`: **create**
- `picture/Picture.java`: **modify** (accept `application/pdf`)
- `picture/PictureUploads.java`: **modify** (`readImageOrPdf`, `inline` disposition for PDF)
- `property/PropertyKind.java`, `property/PropertyDocumentType.java`: **create** (enums)
- `property/Property.java`, `property/PropertyFact.java`: **create** (records with validation)
- `property/PropertyRequest.java`, `property/PropertyResponse.java`: **create**
- `property/PropertyDocument.java`, `property/PropertyDocumentRequest.java`, `property/PropertyDocumentResponse.java`: **create**
- `property/PropertyRepository.java`: **create** (properties, facts, photos, documents, document files)
- `property/PropertyController.java`, `property/PropertyDocumentController.java`: **create**
- `property/HouseAlreadyExistsException.java`: **create** (mapped to `409` in `web/ApiExceptionHandler.java`: **modify**)

Backend tests: `property/PropertyControllerTests.java`, `property/PropertyDocumentControllerTests.java`, `property/PropertyAccessTests.java`, and `inventory/DatabaseMigrationTests.java` (version 12 → 13).

Frontend (`frontend/src/…`):
- `api/types.ts`, `api/api.ts`: **modify**
- `components/icons.tsx`: **modify** (`LandIcon`, `MapPinIcon`)
- `components/PhotosField.tsx`: **modify** (`allowPdf`)
- `components/PropertyPage.tsx`, `components/PropertyDialog.tsx`, `components/PropertyDocumentDialog.tsx`, `components/DetailsEditor.tsx`: **create**
- `views/HouseView.tsx`, `views/LandView.tsx`: **create**
- `App.tsx`, `i18n/index.tsx`, `styles.css`: **modify**

---

### Task 1: Backend — properties with details and photos

**Files:** the migration, `Picture`, `PictureUploads`, `ApiExceptionHandler`, and the property package, excluding documents. Test: `PropertyControllerTests`, plus the version bump in `DatabaseMigrationTests`.

**Interfaces (produces):**

| Route | Response |
|---|---|
| `GET /api/properties?kind=LAND` | `PropertyResponse[]` |
| `GET /api/properties/house` | `PropertyResponse` or `404` |
| `GET/PUT/DELETE /api/properties/{id}`, `POST /api/properties` | — |
| `…/properties/{id}/pictures…` | same routes as item photos |
| `GET /api/property-labels` | `string[]` |

`PropertyResponse` is `{id, kind, name, address, valueEur, comments, facts:[{label,value}], pictures:[{id,url,thumbnailUrl}], documents:[…]}`. `documents` stays empty until Task 2.

- [ ] **Step 1: Write the failing tests.** Add `PropertyControllerTests` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(SignedInMockMvc.class)`, temp database like `DocumentControllerTests`). Cover:
  - `houseLifecycle`: `GET /house` gives `404`; `POST {kind:HOUSE,name:"Casa de Viseu",address:"Rua…",valueEur:250000,facts:[{label:"Área (m²)",value:"180"}]}` gives `201`; `GET /house` returns it; `PUT` changes the name and facts; `DELETE` gives `204`; `GET /house` gives `404` again.
  - `onlyOneHouse`: a second `POST` with kind `HOUSE` gives `409`.
  - `kindCannotChange`: `PUT` with a different kind gives `400`.
  - `landListedByName`: parcels "Vinha", "Olival" and "Mata" are listed in the order Mata, Olival, Vinha. The house is not in `?kind=LAND`.
  - `factsKeepOrderAndDropBlankLines`: facts `[{" A "," 1 "},{"",""},{"B","2"}]` are stored as `[{A,1},{B,2}]`, and a later `PUT` replaces them.
  - `validation`: a blank name gives `400` "name must not be blank"; `valueEur:-1` gives `400`; an unknown kind gives `400`.
  - `photos`: adding two images and reordering work like item photos; a PDF upload to `/pictures` gives `400`.
  - `deletingPropertyDeletesPhotoFiles`: the file count in the picture directory goes down by the number of photos.
  - `labels`: `GET /api/property-labels` is the distinct, sorted list of labels used.
- [ ] **Step 2: Run them and confirm they fail** with `404`s.
- [ ] **Step 3: Write the migration `V13__house_and_land.sql`,** exactly as the spec's Database table describes, including `CHECK (kind IN ('HOUSE','LAND'))`, `CHECK (value_cents IS NULL OR value_cents >= 0)`, the document type CHECK, `CREATE UNIQUE INDEX property_one_house ON property (kind) WHERE kind = 'HOUSE'`, and an index on `property_picture (property_id, position)` and on `property_document_file (document_id, position)`.
- [ ] **Step 4: Change `Picture` and `PictureUploads`.**
  - Add `"application/pdf", "pdf"` to `Picture.EXTENSIONS` (a `Map.of` with 7 entries).
  - `PictureUploads.read` stays as it is, so PDF becomes `image/pdf` and is rejected.
  - Add `readImageOrPdf(contentType, data)`: after the size check, if the parsed type is `application/pdf`, build `new Picture("application/pdf", data)`; otherwise delegate to `read`.
  - `pictureResponse`: when the content type is `application/pdf`, add `Content-Disposition: inline`.
  - `PictureInfo` gains `String contentType`, read from the `content_type` column in `list`, `listAll` and `add`. `PictureResponse` gains `contentType`, so the frontend can tell PDFs from images. Items and documents get the extra field too, which is harmless.
- [ ] **Step 5: Write the property package.**
  - Records validate like `InventoryItem` and `Heir`: a blank name throws `IllegalArgumentException("name must not be blank")`, and a negative value throws `IllegalArgumentException("valueEur must not be negative")`. `PropertyFact` strips its label and value, and blank facts are filtered in `PropertyRequest`.
  - `PropertyRepository` owns the `property` and `property_fact` SQL and a `PictureRepository` for `property_picture`. Save is an upsert like `HeirRepository`, then `DELETE FROM property_fact WHERE property_id = ?` and inserts in order.
  - `PropertyController` checks that `kind` is unchanged on `PUT`. On `POST HOUSE`, it catches the unique-index violation (`DataIntegrityViolationException`), and also checks `findHouse()` first, throwing `HouseAlreadyExistsException`, which `ApiExceptionHandler` maps to `409`.
  - `DELETE` is `@Transactional`. It deletes the documents' files (Task 2 fills this in), then `pictures.deleteOwner(id)`.
- [ ] **Step 6: Run the tests until they pass,** then run the full backend suite (bump the version in `DatabaseMigrationTests` to `"13"`).
- [ ] **Step 7: Commit** "Add houses and land parcels with details and photos".

### Task 2: Backend — official documents with image or PDF files

**Files:** `PropertyDocument*` and `PropertyDocumentController`; the documents part of `PropertyRepository`; `PropertyResponse.documents`. Test: `PropertyDocumentControllerTests`, `PropertyAccessTests`.

**Interfaces (produces):**
- `POST /api/properties/{id}/documents` gives `201 PropertyDocumentResponse`; `PUT/DELETE /api/property-documents/{id}`.
- `…/property-documents/{id}/pictures…`: file routes that accept images or PDF.
- `GET /api/property-document-types`.
- `PropertyDocumentResponse` is `{id, propertyId, type, date, notes, files:[{id,url,thumbnailUrl}]}`, listed in `PropertyResponse.documents` ordered by date (newest first, no date last), then type.

- [ ] **Step 1: Write the failing tests.**
  - `documentLifecycle`: create, update and delete; the document appears in the property response.
  - `filesAcceptImagesAndPdf`: add a JPEG and a PDF (`%PDF-1.4…` bytes). The PDF is served as `application/pdf` with `Content-Disposition` containing `inline`, and has a `null` thumbnail. SVG gives `400` and `text/plain` gives `415`.
  - `deletingDocumentDeletesFiles`.
  - `deletingPropertyDeletesDocumentFilesToo`: a property with 1 photo and 2 documents holding 3 files in total. After delete, the file count drops by 4, and another property's files remain.
  - `unknownPropertyOrType`: `404` for a document on an unknown property; `400` for an unknown type.
  - `PropertyAccessTests`, for a user (not admin): every `GET` gives `200`; `POST/PUT/DELETE` on properties, documents, pictures and order give `403`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement it.** Add a second `PictureRepository` for `property_document_file` (owner table `property_document`, owner column `document_id`). The upload mapping `consumes = {"image/*", "application/pdf"}` and uses `uploads.readImageOrPdf`. In property delete, call `deleteOwner` for each document first.
- [ ] **Step 4: Run the tests and the full suite.**
- [ ] **Step 5: Commit** "Add official documents with image or PDF files to house and land".

### Task 3: Frontend — API, shared components, and PDF in `PhotosField`

**Files:** `types.ts`, `api.ts`, `icons.tsx`, `PhotosField.tsx`, `DetailsEditor.tsx`, `PropertyDialog.tsx`, `PropertyDocumentDialog.tsx`, `PropertyPage.tsx`, `i18n`, `styles.css`.

**Interfaces (produces):**
- **Types:**
  - `interface Property { id; kind: 'HOUSE' | 'LAND'; name; address: string | null; valueEur: number | null; comments: string | null; facts: Fact[]; pictures: Picture[]; documents: PropertyDocument[] }`
  - `interface Fact { label: string; value: string }`
  - `interface PropertyDocument { id; propertyId; type; date: string | null; notes: string | null; files: Picture[] }`
  - `PropertyInput`, `PropertyDocumentInput`
- **API:** `api.house()`, `api.listLand()`, `api.getProperty(id)`, `api.createProperty`, `api.updateProperty`, `api.deleteProperty`, `api.propertyLabels()`, `api.propertyDocumentTypes()`, `api.createPropertyDocument(propertyId, input)`, `api.updatePropertyDocument`, `api.deletePropertyDocument`.
- **Components:**
  - `PropertyPage({ property, onChanged, onDeleted? })`
  - `PropertyDialog({ property | null, kind, onSaved, onClose })`
  - `PropertyDocumentDialog({ propertyId, document | null, types, onSaved, onDeleted, onClose })`

- [ ] **Step 1: Types and API calls.**
  - Frontend `Picture` gains `contentType: string`, which Task 1 adds to the response. A stored file is a PDF when `contentType === 'application/pdf'`; a new one when `file.type` is.
  - Add the types and API calls listed above.
- [ ] **Step 2: `PhotosField` `allowPdf`.**
  - The accepted types add `application/pdf` when set, and the error message says so.
  - A PDF entry shows `FileIcon` and "PDF".
  - Tapping a PDF opens `url` (or the object URL) with `window.open(url, '_blank', 'noopener')`.
  - The viewer receives only the image entries, and the index is mapped accordingly.
- [ ] **Step 3: `DetailsEditor`:**
  - rows of label and value inputs, with the label input using a `<datalist id="property-labels">` of the translated suggestions plus the used labels;
  - buttons for up, down and remove;
  - "Add detail".
- [ ] **Step 4: `PropertyDialog`:**
  - name, address or location (the label depends on the kind), estimated value (empty is allowed; the same decimal parsing as items), `DetailsEditor`, `PhotosField`, and comments;
  - it saves with `saveWithPhotos(() => create/update, '/api/properties', …)`;
  - errors are handled like `ItemDialog`.
- [ ] **Step 5: `PropertyDocumentDialog`:**
  - type select, date, notes, and `PhotosField allowPdf`;
  - saves with `saveWithPhotos(…, '/api/property-documents', …)`;
  - Delete asks via `ConfirmDialog`;
  - read-only for users.
- [ ] **Step 6: `PropertyPage`**, laid out as the spec's Frontend section describes. The map link is shown only when the address is not blank.
- [ ] **Step 7: Type-check, then commit** "Add house and land page, dialogs and PDF files in the photo field".

### Task 4: Frontend — House and Land tabs

**Files:** `HouseView.tsx`, `LandView.tsx`, `App.tsx`, `i18n`, `styles.css`.

- [ ] **Step 1: `App.tsx`.**
  - `View` gains `'house' | 'land'`.
  - `viewFromHash` maps `#/house` to `'house'` and `#/land…` to `'land'`, using `startsWith('#/land')`.
  - Add the two tabs after Documents, allowed for every account, with the icons `HouseIcon` and `LandIcon`.
- [ ] **Step 2: `HouseView`.**
  - Loads `api.house()`. A `404` shows the empty state: "The house is not set up yet", plus a *Set up the house* button for administrators, which opens `PropertyDialog` with kind `HOUSE`.
  - Otherwise it renders `PropertyPage`, and reloads after changes.
- [ ] **Step 3: `LandView`.**
  - It reads the parcel id from the hash (`#/land/<id>`).
  - **Without an id:** search, count, total value, *New land* for administrators, and cards linking to `#/land/<id>`.
  - **With an id:** load that parcel (from the list or `api.getProperty`). Show `PropertyPage` with a *Back to land* link, or a "not found" message with the same link. After deletion, go to `#/land`.
- [ ] **Step 4: Strings and styles** for all new text in three languages, the property page layout (a wide cover, a two-column details list on desktop that stacks on phones), and the land cards (reusing `.card`).
- [ ] **Step 5: Full build** (`./mvnw -q package`), then commit "Add the House and Land tabs".

### Task 5: Check in the browser and update the README

- [ ] **Step 1: Walk through the browser check** from the spec's Testing section, on the service or a copy of the data, at desktop and phone width.
- [ ] **Step 2: README.** Add a Features bullet for House and Land.
- [ ] **Step 3: Commit.**
