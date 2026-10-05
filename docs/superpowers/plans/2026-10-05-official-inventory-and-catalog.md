# Official Inventory and Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Official Inventory that is a real copy of today's Inventory and starts empty, then rename today's Inventory to Catalog all the way down.

**Architecture:** The Official Inventory is made by copying the Inventory's files and replacing names, so both lists have their own classes, tables and screens. The rename is a second pass of name replacements plus one migration that renames the two tables. Rooms, photo storage, the photo field and the item form's texts stay shared.

**Tech Stack:** Spring Boot 4 (Java 25), SQLite with Flyway, React 19 with TypeScript and Vite.

**Spec:** `docs/superpowers/specs/2026-10-05-official-inventory-and-catalog-design.md`

## Global Constraints

- Order: first the Official Inventory (Tasks 1–2), then the rename (Tasks 3–4).
- Names are exactly those of the spec's *Names* table.
- Migrations: `V20__official_inventory.sql`, then `V21__rename_inventory_to_catalog.sql`. No existing migration file is edited.
- No redirect from `#/inventory` or `/api/items`.
- Items accept images only; a PDF is refused with 415.
- After each task `./mvnw -o verify` passes. It runs the backend tests and type-checks and builds the frontend.
- All commands run from the repository root, `/home/daco_dv/dev/attic`, on branch `official-inventory`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- **The two lists must not share rows.** An item created in one must not appear in the other, and a photo of one must not be served below the other's address. Pinned in Task 1, Step 4.
- **The rename must keep existing data.** A database at version 20 with an item and a photo still has both afterwards, found through `CatalogRepository`. Pinned in Task 3, Step 3.
- **A database that never had Catalog data** (fresh install) migrates from nothing to 21. Covered by every `@SpringBootTest`, which starts on an empty database.
- **Deleting an Official Inventory item removes its photo files**, as for the Inventory. Covered by the copied controller test `deletingItemDeletesAllPhotoFiles` in Task 1.
- **Old addresses.** `#/inventory` after the rename falls back to the House tab instead of a blank page. Checked by reading `viewFromHash` in Task 4, Step 3; there is no frontend test runner.

---

### Task 1: Official Inventory backend

**Files:**
- Create: `src/main/resources/db/migration/V20__official_inventory.sql`
- Create: `src/main/java/com/mephys/attic/model/OfficialInventoryItem.java`
- Create: `src/main/java/com/mephys/attic/dto/OfficialInventoryItemRequest.java`, `OfficialInventoryItemResponse.java`
- Create: `src/main/java/com/mephys/attic/repository/OfficialInventoryRepository.java`
- Create: `src/main/java/com/mephys/attic/controller/OfficialInventoryController.java`
- Test: `src/test/java/com/mephys/attic/controller/OfficialInventoryControllerTests.java`, `src/test/java/com/mephys/attic/repository/OfficialInventoryRepositoryTests.java`
- Modify: `src/test/java/com/mephys/attic/repository/DatabaseMigrationTests.java`, `src/test/java/com/mephys/attic/config/SecurityTests.java`, `src/test/java/com/mephys/attic/controller/RolesTests.java`

**Interfaces:**
- Produces: REST API below `/api/official-inventory` with the operations and JSON of `/api/items`; photo addresses `/api/official-inventory/<id>/pictures/<pictureId>`.

- [ ] **Step 1: Write the failing tests** by copying the Inventory tests

```bash
T=src/test/java/com/mephys/attic
copy() { sed -E 's/\bInventory/OfficialInventory/g; s/\binventory_item\b/official_inventory_item/g; s/\binventory_picture\b/official_inventory_picture/g; s#/api/items#/api/official-inventory#g' "$1" > "$2"; }
copy $T/controller/InventoryControllerTests.java $T/controller/OfficialInventoryControllerTests.java
copy $T/repository/InventoryRepositoryTests.java $T/repository/OfficialInventoryRepositoryTests.java
sed -i 's/\.isEqualTo("19");/.isEqualTo("20");/' $T/repository/DatabaseMigrationTests.java
sed -i 's#"/api/items", "/api/heirs"#"/api/items", "/api/official-inventory", "/api/heirs"#' $T/config/SecurityTests.java $T/controller/RolesTests.java
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -o -q -Dskip.npm compiler:compile compiler:testCompile`
Expected: compilation errors, `cannot find symbol ... OfficialInventoryRepository`.

- [ ] **Step 3: Write the migration and the classes**

`src/main/resources/db/migration/V20__official_inventory.sql`:

```sql
-- The official inventory: a second list of items with the structure of inventory_item and
-- inventory_picture. It starts empty.
CREATE TABLE official_inventory_item (
	id          TEXT    PRIMARY KEY,
	name        TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	date        TEXT,
	location    TEXT,
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0),
	owner       TEXT    NOT NULL DEFAULT 'Heritage',
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT
);

-- Photos: files named <id>.<extension>, position 0 is the cover
CREATE TABLE official_inventory_picture (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL REFERENCES official_inventory_item (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
CREATE INDEX official_inventory_picture_item_id ON official_inventory_picture (item_id, position);
```

The classes are copies with the names replaced:

```bash
J=src/main/java/com/mephys/attic
copy() { sed -E 's/\bInventory/OfficialInventory/g; s/\binventory_item\b/official_inventory_item/g; s/\binventory_picture\b/official_inventory_picture/g; s#/api/items/#/api/official-inventory/#g; s#@RequestMapping\("/items"\)#@RequestMapping("/official-inventory")#' "$1" > "$2"; }
copy $J/model/InventoryItem.java $J/model/OfficialInventoryItem.java
copy $J/dto/InventoryItemRequest.java $J/dto/OfficialInventoryItemRequest.java
copy $J/dto/InventoryItemResponse.java $J/dto/OfficialInventoryItemResponse.java
copy $J/repository/InventoryRepository.java $J/repository/OfficialInventoryRepository.java
copy $J/controller/InventoryController.java $J/controller/OfficialInventoryController.java
```

- [ ] **Step 4: Add the test that the two lists are separate**

Add to `OfficialInventoryControllerTests`, before its first private helper method:

```java
	@Test
	void officialInventoryIsSeparateFromInventory() throws Exception {
		String official = create("{\"name\":\"Official chair\"}");
		String photo = addPhoto(official, TestImages.png(20, 20));

		// Not in the other list, and its photo is not served there
		mvc.perform(get("/api/items")).andExpect(jsonPath("$[?(@.id == '" + official + "')]").doesNotExist());
		mvc.perform(get("/api/items/{id}", official)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}/pictures/{pictureId}", official, photo)).andExpect(status().isNotFound());
		mvc.perform(get("/api/official-inventory/{id}/pictures/{pictureId}", official, photo))
			.andExpect(status().isOk());
	}
```

`create` and `addPhoto` are the helpers the copied test class already has (`create(String json)` returns the new id; `addPhoto(String itemId, byte[] image)` returns the photo id). If their names differ in the file, use the names found there.

- [ ] **Step 5: Run the tests**

Run: `./mvnw -o -Dskip.npm resources:resources resources:testResources compiler:compile compiler:testCompile surefire:test`
Expected: `BUILD SUCCESS`, with `OfficialInventoryControllerTests` and `OfficialInventoryRepositoryTests` in the list.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "Add the official inventory, a second list with the structure of the inventory"
```

---

### Task 2: Official Inventory frontend

**Files:**
- Modify: `frontend/src/api/types.ts`, `frontend/src/api/api.ts`, `frontend/src/lib/pdf.ts`, `frontend/src/views/InventoryView.tsx`, `frontend/src/App.tsx`, `frontend/src/components/icons.tsx`
- Modify: `frontend/src/i18n/en.json`, `pt.json`, `fr.json`
- Create: `frontend/src/views/OfficialInventoryView.tsx`, `frontend/src/components/OfficialInventoryItemCard.tsx`, `frontend/src/components/OfficialInventoryItemDialog.tsx`

**Interfaces:**
- Consumes: `/api/official-inventory` from Task 1.
- Produces: `exportPdf(items, totalCount, filters, t, title: string, fileName: string)`; texts `tabOfficialInventory`, `pdfTitleOfficialInventory`; icon `ClipboardIcon`.

- [ ] **Step 1: Types and API calls**

In `frontend/src/api/types.ts`, after the line `export type ItemInput = Omit<Item, 'id' | 'pictures'>`:

```ts

/** An item of the official inventory; it has the structure of a catalog item but is its own list */
export interface OfficialInventoryItem {
  id: string
  name: string
  quantity: number
  date: string | null
  location: string | null
  existent: boolean
  valueEur: number
  owner: string
  comments: string | null
  /** Cover first */
  pictures: Picture[]
}

export type OfficialInventoryItemInput = Omit<OfficialInventoryItem, 'id' | 'pictures'>
```

In `frontend/src/api/api.ts`, add `OfficialInventoryItem, OfficialInventoryItemInput` to the type import, and after the `deleteItem` line:

```ts
  listOfficialInventory: () => request<OfficialInventoryItem[]>('/api/official-inventory'),
  createOfficialInventoryItem: (input: OfficialInventoryItemInput) =>
    request<OfficialInventoryItem>('/api/official-inventory', json('POST', input)),
  updateOfficialInventoryItem: (id: string, input: OfficialInventoryItemInput) =>
    request<OfficialInventoryItem>(`/api/official-inventory/${id}`, json('PUT', input)),
  deleteOfficialInventoryItem: (id: string) =>
    request<void>(`/api/official-inventory/${id}`, { method: 'DELETE' }),
```

- [ ] **Step 2: The PDF export takes its title and file name**

In `frontend/src/lib/pdf.ts` change the signature and the two places that knew the name:

```ts
/**
 * Download the given items as a PDF table, one line per item. Comments are left out.
 * `fileName` is without date and extension, e.g. "attic-inventory".
 */
export async function exportPdf(items: Item[], totalCount: number, filters: string[], t: Messages, title: string,
  fileName: string) {
```

`drawTitle(doc, t.pdfTitle, …)` becomes `drawTitle(doc, title, …)`, and the last line becomes ``doc.save(`${fileName}-${isoDay(now)}.pdf`)``.

In `frontend/src/views/InventoryView.tsx` the call becomes:

```ts
    await exportPdf(visible, items?.length ?? 0, filters, t, t.pdfTitle, 'attic-inventory')
```

- [ ] **Step 3: Copy the view, card and dialog**

```bash
F=frontend/src
copy() { sed -E 's/\bItemCard\b/OfficialInventoryItemCard/g; s/\bItemDialog\b/OfficialInventoryItemDialog/g; s/\bItemInput\b/OfficialInventoryItemInput/g; s/\bItem\b/OfficialInventoryItem/g; s/\bInventoryView\b/OfficialInventoryView/g; s/\bapi\.listItems\b/api.listOfficialInventory/g; s/\bapi\.createItem\b/api.createOfficialInventoryItem/g; s/\bapi\.updateItem\b/api.updateOfficialInventoryItem/g; s/\bapi\.deleteItem\b/api.deleteOfficialInventoryItem/g; s#/api/items#/api/official-inventory#g; s/item-dialog/official-inventory-item-dialog/g; s/t\.pdfTitle, .attic-inventory./t.pdfTitleOfficialInventory, '"'"'attic-official-inventory'"'"'/' "$1" > "$2"; }
copy $F/views/InventoryView.tsx $F/views/OfficialInventoryView.tsx
copy $F/components/ItemCard.tsx $F/components/OfficialInventoryItemCard.tsx
copy $F/components/ItemDialog.tsx $F/components/OfficialInventoryItemDialog.tsx
```

- [ ] **Step 4: Texts, icon and tab**

Add to each of `en.json`, `pt.json`, `fr.json` (new keys at the end of the object):

| Key | en | pt | fr |
|---|---|---|---|
| `tabOfficialInventory` | Official inventory | Inventário oficial | Inventaire officiel |
| `pdfTitleOfficialInventory` | Official inventory | Inventário oficial | Inventaire officiel |

Add to `frontend/src/components/icons.tsx`, after `BoxIcon`:

```tsx
export const ClipboardIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <rect x="5" y="4" width="14" height="17" rx="2" />
    <path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3" />
  </svg>
)
```

In `frontend/src/App.tsx`:
- import `ClipboardIcon` and `OfficialInventoryView`;
- add `'official-inventory'` to the `View` type;
- in `viewFromHash`, add `case '#/official-inventory': return 'official-inventory'`;
- in `tabs`, after the `inventory` line: `{ id: 'official-inventory', href: '#/official-inventory', label: t.tabOfficialInventory, Icon: ClipboardIcon },`
- after `{view === 'inventory' && <InventoryView />}`: `{view === 'official-inventory' && <OfficialInventoryView />}`.

The `inventory` tab line moves up so the order is House, Land, Heirs, Inventory, Official inventory, Documents, Contributions, Renovations, Users.

- [ ] **Step 5: Build**

Run: `./mvnw -o verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "Add the Official inventory tab"
```

---

### Task 3: Rename the Inventory backend to Catalog

**Files:**
- Create: `src/main/resources/db/migration/V21__rename_inventory_to_catalog.sql`
- Rename: `model/InventoryItem.java` → `CatalogItem.java`; `dto/InventoryItemRequest.java`, `InventoryItemResponse.java` → `CatalogItem*`; `repository/InventoryRepository.java` → `CatalogRepository.java`; `controller/InventoryController.java` → `CatalogController.java`
- Rename: `InventoryControllerTests.java` → `CatalogControllerTests.java`; `InventoryRepositoryTests.java` → `CatalogRepositoryTests.java`
- Create: `src/test/java/com/mephys/attic/repository/CatalogMigrationTests.java`
- Modify: every other test that says `/api/items`, `InventoryItem`, `InventoryRepository`, or expects migration version 20

**Interfaces:**
- Produces: REST API below `/api/catalog`; photo addresses `/api/catalog/<id>/pictures/<pictureId>`. `/api/items` no longer exists.

- [ ] **Step 1: Rename the tests first**

```bash
T=src/test/java/com/mephys/attic
git mv $T/controller/InventoryControllerTests.java $T/controller/CatalogControllerTests.java
git mv $T/repository/InventoryRepositoryTests.java $T/repository/CatalogRepositoryTests.java
grep -rlE '\bInventory(Item|Repository|Controller)|/api/items' src/test | xargs sed -i -E 's/\bInventory(ItemRequest|ItemResponse|Item|Repository|ControllerTests|RepositoryTests|Controller)\b/Catalog\1/g; s#/api/items#/api/catalog#g'
sed -i 's/\.isEqualTo("20");/.isEqualTo("21");/' $T/repository/DatabaseMigrationTests.java
```

The renamed tests that query tables directly must read the renamed tables. `DatabaseMigrationTests` and `PicturesMigrationTests` create the old tables at an old version on purpose, so only statements that run **after** the migration change:

```bash
sed -i -E 's/\binventory_item\b/catalog_item/g; s/\binventory_picture\b/catalog_picture/g' $T/controller/CatalogControllerTests.java $T/repository/CatalogRepositoryTests.java
sed -i -E '/@Test/,$ s/"inventory_picture", "item_id", "inventory_item"/"catalog_picture", "item_id", "catalog_item"/; /@Test/,$ s/FROM inventory_picture/FROM catalog_picture/' $T/repository/PicturesMigrationTests.java
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -o -q -Dskip.npm compiler:compile compiler:testCompile`
Expected: compilation errors, `cannot find symbol ... CatalogRepository`.

- [ ] **Step 3: Write the migration test**

`src/test/java/com/mephys/attic/repository/CatalogMigrationTests.java`:

```java
package com.mephys.attic.repository;

import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.model.PictureInfo;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The items and photos of the inventory are still there when it has become the catalog.
 */
@SpringBootTest
class CatalogMigrationTests {

	private static final UUID ITEM = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID PICTURE = UUID.fromString("22222222-2222-4222-8222-222222222222");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion20Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("20").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO inventory_item (id, name, quantity) VALUES ('" + ITEM + "', 'Sofa', 2)");
			statement.execute("INSERT INTO inventory_picture (id, item_id, position, file_name, content_type) VALUES ('"
					+ PICTURE + "', '" + ITEM + "', 0, '" + PICTURE + ".png', 'image/png')");
		}
	}

	@Autowired
	private CatalogRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void itemsAndPhotosAreKept() {
		CatalogItem sofa = repository.findById(ITEM).orElseThrow();
		assertThat(sofa.name()).isEqualTo("Sofa");
		assertThat(sofa.quantity()).isEqualTo(2);
		assertThat(repository.listPictures(ITEM)).extracting(PictureInfo::id).containsExactly(PICTURE);
	}

	@Test
	void deletingAnItemStillDeletesItsPhotoRows() {
		UUID id = repository.save(CatalogItem.of("Chair")).id();
		jdbc.sql("INSERT INTO catalog_picture (id, item_id, position, file_name, content_type) VALUES (?, ?, 0, 'x.png', 'image/png')")
			.params(UUID.randomUUID().toString(), id.toString())
			.update();

		repository.deleteById(id);

		// The foreign key followed the renamed table, so the cascade still works
		assertThat(jdbc.sql("SELECT count(*) FROM catalog_picture WHERE item_id = ?").param(id.toString())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void theOldTablesAreGone() {
		assertThat(jdbc.sql("SELECT count(*) FROM sqlite_master WHERE name LIKE 'inventory\\_%' ESCAPE '\\'")
			.query(Integer.class)
			.single()).isZero();
	}

}
```

- [ ] **Step 4: Write the migration and rename the classes**

`src/main/resources/db/migration/V21__rename_inventory_to_catalog.sql`:

```sql
-- The inventory is called the catalog from now on; the official inventory is the other list.
-- SQLite points the foreign key of the picture table to the renamed item table by itself.
ALTER TABLE inventory_item RENAME TO catalog_item;
ALTER TABLE inventory_picture RENAME TO catalog_picture;
DROP INDEX inventory_picture_item_id;
CREATE INDEX catalog_picture_item_id ON catalog_picture (item_id, position);
```

```bash
J=src/main/java/com/mephys/attic
git mv $J/model/InventoryItem.java $J/model/CatalogItem.java
git mv $J/dto/InventoryItemRequest.java $J/dto/CatalogItemRequest.java
git mv $J/dto/InventoryItemResponse.java $J/dto/CatalogItemResponse.java
git mv $J/repository/InventoryRepository.java $J/repository/CatalogRepository.java
git mv $J/controller/InventoryController.java $J/controller/CatalogController.java
grep -rlE '\bInventory(Item|Repository|Controller)|\binventory_(item|picture)\b|/api/items|"/items"' src/main/java | xargs sed -i -E 's/\bInventory(ItemRequest|ItemResponse|Item|Repository|Controller)\b/Catalog\1/g; s/\binventory_item\b/catalog_item/g; s/\binventory_picture\b/catalog_picture/g; s#/api/items/#/api/catalog/#g; s#@RequestMapping\("/items"\)#@RequestMapping("/catalog")#'
```

Then read the comments of the five renamed classes and of `PictureRepository` and `PictureInfo`, and change "inventory" to "catalog" where a comment means this list.

- [ ] **Step 5: Run the tests**

Run: `./mvnw -o -Dskip.npm resources:resources resources:testResources compiler:compile compiler:testCompile surefire:test`
Expected: `BUILD SUCCESS`, with `CatalogMigrationTests` (3 tests) in the list.

Run: `grep -rnE '\bInventory(Item|Repository|Controller)|/api/items' src`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "Rename the inventory to the catalog in the backend and the database"
```

---

### Task 4: Rename the Inventory frontend to Catalog

**Files:**
- Rename: `frontend/src/views/InventoryView.tsx` → `CatalogView.tsx`; `frontend/src/components/ItemCard.tsx` → `CatalogItemCard.tsx`; `ItemDialog.tsx` → `CatalogItemDialog.tsx`
- Modify: `frontend/src/api/types.ts`, `api.ts`, `lib/pdf.ts`, `App.tsx`, `i18n/en.json`, `pt.json`, `fr.json`, `README.md`

**Interfaces:**
- Consumes: `/api/catalog` from Task 3.

- [ ] **Step 1: Rename files and names**

```bash
F=frontend/src
git mv $F/views/InventoryView.tsx $F/views/CatalogView.tsx
git mv $F/components/ItemCard.tsx $F/components/CatalogItemCard.tsx
git mv $F/components/ItemDialog.tsx $F/components/CatalogItemDialog.tsx
grep -rlE '\b(Item|ItemInput|ItemCard|ItemDialog|InventoryView|listItems|createItem|updateItem|deleteItem)\b|/api/items|tabInventory|attic-inventory|item-dialog' $F --include=*.ts --include=*.tsx | xargs sed -i -E 's/\bItemCard\b/CatalogItemCard/g; s/\bItemDialog\b/CatalogItemDialog/g; s/\bItemInput\b/CatalogItemInput/g; s/\bItem\b/CatalogItem/g; s/\bInventoryView\b/CatalogView/g; s/\blistItems\b/listCatalog/g; s/\bcreateItem\b/createCatalogItem/g; s/\bupdateItem\b/updateCatalogItem/g; s/\bdeleteItem\b/deleteCatalogItem/g; s#/api/items#/api/catalog#g; s/\btabInventory\b/tabCatalog/g; s/\bt\.pdfTitle\b/t.pdfTitleCatalog/g; s/attic-inventory/attic-catalog/g; s/"item-dialog/"catalog-item-dialog/g'
```

- [ ] **Step 2: Texts**

In each language file rename the keys `tabInventory` → `tabCatalog` and `pdfTitle` → `pdfTitleCatalog`, and set:

| Key | en | pt | fr |
|---|---|---|---|
| `tabCatalog` | Catalog | Catálogo | Catalogue |
| `pdfTitleCatalog` | Catalog | Catálogo | Catalogue |

`setupIntro` and `deleteUserMessage` mention "inventory" in general. Reword them so they name both lists, keeping each language's sentence, e.g. English `setupIntro`: "Create your account. It will be the only way to open your catalog, official inventory, heirs and documents."; `deleteUserMessage`: "“{username}” will no longer be able to sign in. The catalog, official inventory, heirs and documents stay."

- [ ] **Step 3: The tab and its address**

In `frontend/src/App.tsx`:
- the `View` type has `'catalog'` instead of `'inventory'`;
- `viewFromHash` has `case '#/catalog': return 'catalog'` instead of the `#/inventory` case. Check that its `default` still returns `'house'`, so an old `#/inventory` bookmark opens the House tab;
- the tab line is `{ id: 'catalog', href: '#/catalog', label: t.tabCatalog, Icon: BoxIcon },`;
- `{view === 'catalog' && <CatalogView />}`.

- [ ] **Step 4: README**

In `README.md` replace the Inventory line of the feature list with:

```markdown
- **Catalog** — items with quantity, date, room, value in euros, owner, comments and photos (the first is the cover);
- **Official inventory** — a second, separate list of items with the same structure;
```

- [ ] **Step 5: Build and check nothing is left**

Run: `./mvnw -o verify`
Expected: `BUILD SUCCESS`.

Run: `grep -rnE "/api/items|tabInventory|InventoryView|#/inventory'|\bItem(Card|Dialog|Input)?\b" frontend/src --include=*.ts --include=*.tsx`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add frontend README.md
git commit -m "Rename the Inventory tab to Catalog"
```
