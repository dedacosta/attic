# Official Inventory and Catalog

Requested on 2026-10-05. It is not a step of the roadmap (photos → house and land → house fund).

## Goal

Attic gets a second list of items:

- **Official Inventory** is new. It has exactly the structure of today's Inventory and starts
  empty.
- **Catalog** is today's Inventory under a new name. Its items and photos stay.

The two lists are separate: an item belongs to one of them, and nothing links them.

## Decisions

- The work is done in **two steps, in this order**: first create the Official Inventory, then
  rename the old Inventory to Catalog.
- The Official Inventory is a **real copy**: it has its own classes, screens and tables,
  duplicated from today's Inventory. The two lists may differ later without affecting each
  other. A fix to one has to be repeated in the other.
- The rename goes **all the way down**: tab, address, API, classes and database tables. After
  it, nothing in the code calls the Catalog "inventory".
- "Oficial" is the Portuguese spelling. The code says `Official`.
- **Shared, not copied**, because they belong to neither list:
  - the rooms (`Location` and `GET /api/locations`);
  - the photo storage (`PictureRepository`, `PictureStorage`, `PictureUploads`, `Thumbnails`);
  - the photo field and viewer (`PhotosField`, `PhotoViewer`, `lib/photos.ts`);
  - the texts of the item form and card (name, quantity, room, value, owner, comments…).
- **Access** is the same for both lists as for today's Inventory: everyone signed in sees them,
  only administrators create, change or delete. The rules in `SecurityConfiguration` already
  cover any path below `/api`, so they do not change.
- **Tab order:** House, Land, Heirs, Catalog, Official inventory, Documents, Contributions,
  Renovations, Users.
- **Not included:**
  - copying or moving items between the two lists;
  - any field that only one of the lists has;
  - a redirect from the old address `#/inventory` or the old API `/api/items`.

## Names

| | Catalog (today's Inventory) | Official Inventory (new) |
|---|---|---|
| Tab, English | Catalog | Official inventory |
| Tab, Portuguese | Catálogo | Inventário oficial |
| Tab, French | Catalogue | Inventaire officiel |
| Address | `#/catalog` | `#/official-inventory` |
| API | `/api/catalog` | `/api/official-inventory` |
| Tables | `catalog_item`, `catalog_picture` | `official_inventory_item`, `official_inventory_picture` |
| Model | `CatalogItem` | `OfficialInventoryItem` |
| Request and response | `CatalogItemRequest`, `CatalogItemResponse` | `OfficialInventoryItemRequest`, `OfficialInventoryItemResponse` |
| Repository | `CatalogRepository` | `OfficialInventoryRepository` |
| Controller | `CatalogController` | `OfficialInventoryController` |
| Frontend types | `CatalogItem`, `CatalogItemInput` | `OfficialInventoryItem`, `OfficialInventoryItemInput` |
| View | `CatalogView` | `OfficialInventoryView` |
| Card and dialog | `CatalogItemCard`, `CatalogItemDialog` | `OfficialInventoryItemCard`, `OfficialInventoryItemDialog` |
| PDF file | `attic-catalog-<date>.pdf` | `attic-official-inventory-<date>.pdf` |

## Step 1: Official Inventory

### Database

Migration `V20__official_inventory.sql` creates two tables with the columns the Inventory
tables have today:

| Table | Columns |
|---|---|
| `official_inventory_item` | The columns of `inventory_item`: `id`, `name`, `quantity`, `date`, `location`, `existent`, `value_cents`, `owner`, `comments`, `created_at`, `updated_at`, with the same defaults and checks. The implementation plan copies them from the current schema. |
| `official_inventory_picture` | `id`, `item_id` → `official_inventory_item` ON DELETE CASCADE, `position`, `file_name`, `content_type`, `thumbnail`; index on (`item_id`, `position`). |

Photo files go to the same pictures folder as all others. They are named by their id, so they
cannot collide.

### Backend

Copies of the Inventory classes under the names in the table above, in the same packages
(`model`, `dto`, `repository`, `controller`). `OfficialInventoryController` serves the same
operations as `InventoryController` below `/api/official-inventory`:

- list, get, create, replace and delete items;
- add a photo, get a photo and its thumbnail, delete a photo, reorder the photos.

Items accept images only, as today. A PDF is refused with 415.

### Frontend

- `api.ts` gets the calls of the Official Inventory beside those of the Inventory. The photo
  calls (`addPicture`, `removePicture`, `orderPictures`) already take the owner's address and
  are reused.
- `OfficialInventoryView`, `OfficialInventoryItemCard` and `OfficialInventoryItemDialog` are
  copies of `InventoryView`, `ItemCard` and `ItemDialog`.
- `exportPdf` in `lib/pdf.ts` takes the title and the file name instead of knowing them, so
  both lists use it.
- `App.tsx` gets the view `official-inventory` and its tab, with a new icon.
- New texts in `en.json`, `pt.json` and `fr.json`: the tab name and the PDF title of the
  Official Inventory.

## Step 2: rename Inventory to Catalog

### Database

Migration `V21__rename_inventory_to_catalog.sql`:

- `ALTER TABLE inventory_item RENAME TO catalog_item`;
- `ALTER TABLE inventory_picture RENAME TO catalog_picture`. SQLite updates the foreign key to
  the renamed item table by itself;
- the index `inventory_picture_item_id` is dropped and created again as
  `catalog_picture_item_id`.

No row and no photo file changes.

### Backend

The `Inventory*` classes are renamed to `Catalog*`, and `CatalogController` serves
`/api/catalog`. Photo addresses in responses become `/api/catalog/<id>/pictures/<pictureId>`.

### Frontend

- The types `Item` and `ItemInput` become `CatalogItem` and `CatalogItemInput`; the calls in
  `api.ts` are renamed and point to `/api/catalog`.
- `InventoryView`, `ItemCard` and `ItemDialog` become `CatalogView`, `CatalogItemCard` and
  `CatalogItemDialog`.
- The view `inventory` becomes `catalog`, at `#/catalog`.
- Texts: `tabInventory` becomes `tabCatalog`, and the PDF title becomes "Catalog". Sentences
  that mention "inventory" in general, such as the one on the setup screen, are reworded to
  name both lists.

### Other

`README.md` describes both lists.

## Testing

- **Step 1:** copies of `InventoryControllerTests` and `InventoryRepositoryTests` for the
  Official Inventory. `DatabaseMigrationTests` expects version 20. `RolesTests` and
  `SecurityTests` also check `/api/official-inventory`.
- **Step 2:**
  - the Inventory tests are renamed with the classes and use `/api/catalog`;
  - a new migration test starts from version 20 with an item and a photo, migrates, and finds
    both through `CatalogRepository`;
  - `DatabaseMigrationTests` expects version 21, and `PicturesMigrationTests` reads the renamed
    table.
- After each step `./mvnw verify` passes, which also type-checks and builds the frontend.

## Installing it

The installed service uses the real database. Installing the new jar applies V20 and V21 in
order on the next start, with nothing to do by hand. Bookmarks to `#/inventory` open the House
tab afterwards.

## Later change

Later on 2026-10-05 the Official Inventory was renamed to **Inventory**, the name that became
free when the old Inventory became the Catalog: tab "Inventory", `#/inventory`,
`/api/inventory`, tables `inventory_item` and `inventory_picture`, classes `Inventory*`.
Migration `V22__rename_official_inventory_to_inventory.sql` renames the tables and keeps their
rows. The names in the sections above are those of before this change.
