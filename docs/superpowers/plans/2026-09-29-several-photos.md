# Several Photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inventory items and documents get an ordered list of photos, with the first one as the cover, instead of a single picture.

**Architecture:**
- A Flyway migration turns the two picture tables into ordered lists.
- `PictureRepository` becomes a per-owner photo list that both owners share, and the item and document controllers expose the same `/pictures` endpoints.
- On the frontend, a `PhotosField` edits a list of `PhotoEntry` values (stored or new) and a `PhotoViewer` shows them full screen. The shared `saveWithPhotos` helper applies the changes when the dialog is saved.

**Tech Stack:** Java 25, Spring Boot 4 (Web MVC, JdbcClient, Security), SQLite + Flyway, JUnit 5 + MockMvc; React 19 + TypeScript + Vite.

**Spec:** `docs/superpowers/specs/2026-09-29-several-photos-design.md`

## Global Constraints

- Photo size limit: `attic.picture.max-size`, default `10MB`. Accepted types are unchanged: `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `image/heic`, `image/avif`.
- Picture file names stay `<picture id>.<ext>` in the picture directory. No files move.
- Photo responses carry `Cache-Control: private, max-age=31536000, immutable`.
- `GET` needs a signed-in account; every other method below `/api` needs `ADMIN`. This already holds through `SecurityConfiguration`, so don't change it.
- Document photo `GET`s check `CurrentAccount.maySee` like the document itself.
- Every user-facing string exists in English, Portuguese and French (`frontend/src/i18n/index.tsx`).
- Code style: tabs in Java, 2 spaces in TypeScript. Comments are short and say *why*, like the surrounding code.
- Run backend commands from the repository root and frontend commands from `frontend/`.
  - Backend only: `./mvnw test -Dskip.npm -Dskip.installnodenpm`
  - Frontend type-check: `PATH="$PWD/../target/node:$PATH" node_modules/.bin/tsc --noEmit -p .`

## Review Focus

1. **Photo of another owner:** `GET/DELETE /api/items/A/pictures/<photo of B>` must give `404` and never serve or delete B's photo. Tested in Task 1.
2. **Reorder with a wrong list** (missing, extra, duplicate or foreign id): `400`, and the order is unchanged. Tested in Task 1.
3. **Retry after a failed upload:** photos already uploaded are not uploaded twice, and the item is not created twice. This is handled by `saveWithPhotos` reporting progress (Task 3) and checked by hand in Task 5.
4. **Delete then add:** positions stay contiguous, so a new photo lands last and doesn't tie with another. Tested in Task 1.
5. **Heir-less or other-heir document photos seen by a user:** `404`. Tested in Task 1 (`OwnHeirTests`).

---

## File Structure

Backend (`src/main/java/com/mephys/attic/…`):
- `resources/db/migration/V12__several_photos.sql`: **create**. Rebuilds both picture tables with `position`.
- `picture/PictureRepository.java`: **rewrite**. An ordered photo list per owner.
- `picture/PictureInfo.java`: **rewrite**. `(UUID id, boolean hasThumbnail)` plus URL helpers.
- `picture/PictureResponse.java`: **create**. The JSON `{ id, url, thumbnailUrl }`.
- `picture/PictureUploads.java`: **modify**. Adds the cache header to photo responses.
- `inventory/InventoryRepository.java`, `inventory/InventoryController.java`, `inventory/InventoryItemResponse.java`: **modify**.
- `document/DocumentRepository.java`, `document/DocumentController.java`, `document/DocumentResponse.java`: **modify**.

Backend tests (`src/test/java/com/mephys/attic/…`):
- `inventory/InventoryControllerTests.java`, `document/DocumentControllerTests.java`, `heir/HeirControllerTests.java`, `security/OwnHeirTests.java`, `security/RolesTests.java`, `inventory/DatabaseMigrationTests.java`: **modify**.
- `picture/PicturesMigrationTests.java`: **create**.

Frontend (`frontend/src/…`):
- `api/types.ts`, `api/api.ts`: **modify**.
- `lib/photos.ts`: **create**. `saveWithPhotos` and `PhotoUploadError`.
- `lib/documents.ts`: **delete** (replaced by `lib/photos.ts`).
- `components/PhotoViewer.tsx`: **create**.
- `components/PhotosField.tsx`: **create**.
- `components/PictureField.tsx`: **delete**.
- `components/ItemCard.tsx`, `components/DocumentCard.tsx`, `components/ItemDialog.tsx`, `components/DocumentDialog.tsx`: **modify**.
- `views/InventoryView.tsx`, `views/DocumentsView.tsx`, `views/HeirsView.tsx`: **modify**.
- `i18n/index.tsx`, `styles.css`: **modify**.

---

### Task 1: Backend — ordered photo lists for items and documents

The migration, repository and both controllers change together: once the migration drops the
`UNIQUE` owner column, the old `ON CONFLICT (item_id)` upsert stops working. So this is one task
with checkpoints inside it.

**Files:**
- Create: `src/main/resources/db/migration/V12__several_photos.sql`
- Create: `src/main/java/com/mephys/attic/picture/PictureResponse.java`
- Rewrite: `src/main/java/com/mephys/attic/picture/PictureRepository.java`, `src/main/java/com/mephys/attic/picture/PictureInfo.java`
- Modify: `src/main/java/com/mephys/attic/picture/PictureUploads.java`
- Modify: `src/main/java/com/mephys/attic/inventory/{InventoryRepository,InventoryController,InventoryItemResponse}.java`
- Modify: `src/main/java/com/mephys/attic/document/{DocumentRepository,DocumentController,DocumentResponse}.java`
- Test: `src/test/java/com/mephys/attic/inventory/InventoryControllerTests.java`, `src/test/java/com/mephys/attic/document/DocumentControllerTests.java`, `src/test/java/com/mephys/attic/heir/HeirControllerTests.java`, `src/test/java/com/mephys/attic/security/OwnHeirTests.java`, `src/test/java/com/mephys/attic/security/RolesTests.java`, `src/test/java/com/mephys/attic/inventory/DatabaseMigrationTests.java`, `src/test/java/com/mephys/attic/picture/PicturesMigrationTests.java`

**Interfaces:**
- Produces (HTTP, for Tasks 3–5), with the same routes under `/api/documents/{id}`:

  | Route | Request | Response |
  |---|---|---|
  | `GET /api/items`, `GET /api/items/{id}` | — | Each item has `pictures: [{id, url, thumbnailUrl}]`. |
  | `POST /api/items/{id}/pictures` | image body | `201` `{id, url, thumbnailUrl}` |
  | `GET /api/items/{id}/pictures/{pictureId}` | — | the image |
  | `GET /api/items/{id}/pictures/{pictureId}/thumbnail` | — | JPEG thumbnail |
  | `DELETE /api/items/{id}/pictures/{pictureId}` | — | `204` |
  | `PUT /api/items/{id}/pictures/order` | `["uuid", …]` | `204` |

- Produces (Java, used by the fund and properties later):
  - `PictureRepository.add(UUID, Picture): Optional<PictureInfo>`
  - `list(UUID): List<PictureInfo>`
  - `listAll(): Map<UUID, List<PictureInfo>>`
  - `find(UUID, UUID): Optional<Picture>`
  - `findThumbnail(UUID, UUID): Optional<byte[]>`
  - `delete(UUID, UUID): boolean`
  - `reorder(UUID, List<UUID>): void`
  - `deleteOwner(UUID): boolean`
  - `PictureResponse.list(String base, List<PictureInfo>): List<PictureResponse>`

- [ ] **Step 1: Write the failing inventory photo tests**

In `InventoryControllerTests.java`, delete the `pictureLifecycle`, `rejectsBadPictures` and `thumbnailUrl` methods. In `createAppliesDefaults`, replace the two `pictureUrl`/`thumbnailUrl` expectations with:

```java
			.andExpect(jsonPath("$.pictures").isEmpty());
```

Add these fields, tests and helpers to the class. Also add the imports `java.util.List`, `org.springframework.jdbc.core.simple.JdbcClient`, `org.springframework.http.HttpHeaders`, and `static org.hamcrest.Matchers.containsString`.

```java
	@Autowired
	private JdbcClient jdbc;

	@Test
	void photosKeepTheirOrderAndTheFirstIsTheCover() throws Exception {
		String id = create("{\"name\":\"Painting\"}");
		byte[] front = TestImages.png(600, 300);
		String first = addPhoto(id, front);
		String second = addPhoto(id, TestImages.jpeg(50, 50));
		String third = addPhoto(id, TestImages.png(20, 20));

		mvc.perform(get("/api/items/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(org.hamcrest.Matchers.contains(first, second, third)))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/items/" + id + "/pictures/" + first))
			.andExpect(jsonPath("$.pictures[0].thumbnailUrl").value("/api/items/" + id + "/pictures/" + first + "/thumbnail"));
		mvc.perform(get("/api/items")).andExpect(jsonPath("$[?(@.id == '" + id + "')].pictures[0].id").value(first));

		mvc.perform(get("/api/items/{id}/pictures/{pictureId}", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(front))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
		mvc.perform(get("/api/items/{id}/pictures/{pictureId}/thumbnail", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
	}

	@Test
	void reorderMakesAnotherPhotoTheCover() throws Exception {
		String id = create("{\"name\":\"Chair\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(put("/api/items/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + second + "\",\"" + first + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/items/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(org.hamcrest.Matchers.contains(second, first)));
	}

	@Test
	void reorderRejectsAListThatIsNotExactlyThePhotos() throws Exception {
		String id = create("{\"name\":\"Table\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String foreign = addPhoto(create("{\"name\":\"Other\"}"), TestImages.png(10, 10));

		for (String order : new String[] { "[\"" + first + "\"]", "[\"" + first + "\",\"" + first + "\"]",
				"[\"" + first + "\",\"" + second + "\",\"" + foreign + "\"]", "[\"" + first + "\",\"" + foreign + "\"]" }) {
			mvc.perform(put("/api/items/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON).content(order))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/items/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(org.hamcrest.Matchers.contains(first, second)));
		mvc.perform(put("/api/items/{id}/pictures/order", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isNotFound());
	}

	@Test
	void deletingAPhotoKeepsTheOthersInOrderWithoutGaps() throws Exception {
		String id = create("{\"name\":\"Lamp\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String third = addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/items/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNoContent());
		String fourth = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(get("/api/items/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(org.hamcrest.Matchers.contains(second, third, fourth)));
		List<Integer> positions = jdbc.sql("SELECT position FROM inventory_picture WHERE item_id = ? ORDER BY position")
			.param(id).query(Integer.class).list();
		assertThat(positions).containsExactly(0, 1, 2);
		assertThat(countPictureFiles()).isEqualTo(filesBefore);
		mvc.perform(delete("/api/items/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
	}

	@Test
	void photoOfAnotherItemIsNotFound() throws Exception {
		String mine = create("{\"name\":\"Mine\"}");
		String other = create("{\"name\":\"Other\"}");
		String othersPhoto = addPhoto(other, TestImages.png(10, 10));

		mvc.perform(get("/api/items/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}/pictures/{pictureId}/thumbnail", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/items/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}/pictures/{pictureId}", other, othersPhoto)).andExpect(status().isOk());
	}

	@Test
	void deletingAnItemDeletesAllItsPhotoFiles() throws Exception {
		String id = create("{\"name\":\"Mirror\"}");
		addPhoto(id, TestImages.png(10, 10));
		addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/items/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(filesBefore - 2);
	}

	@Test
	void rejectsBadPhotos() throws Exception {
		String id = create("{\"name\":\"Vase\"}");

		mvc.perform(post("/api/items/{id}/pictures", id).contentType(MediaType.TEXT_PLAIN).content("hi"))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/api/items/{id}/pictures", id).contentType("image/svg+xml").content("<svg/>"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/items/{id}/pictures", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(post("/api/items/{id}/pictures", "00000000-0000-0000-0000-000000000000").contentType(MediaType.IMAGE_PNG)
			.content(new byte[] { 1 })).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}", id)).andExpect(jsonPath("$.pictures").isEmpty());
	}

	private String addPhoto(String itemId, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/items/{id}/pictures", itemId).contentType(MediaType.IMAGE_PNG).content(image))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static long countPictureFiles() throws Exception {
		Path directory = tempDir.resolve("pictures");
		if (!java.nio.file.Files.exists(directory)) {
			return 0;
		}
		try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(directory)) {
			return files.count();
		}
	}
```

`addPhoto` always sends `image/png`. That's fine: the stored content type is only what's served back, and only the first test checks the bytes and type of a real PNG.

- [ ] **Step 2: Run the inventory tests and confirm they fail**

Run: `./mvnw test -Dskip.npm -Dskip.installnodenpm -Dtest=InventoryControllerTests`
Expected: FAIL. The new tests get `404`/`405` from `POST …/pictures`, and `createAppliesDefaults` fails on `$.pictures`.

- [ ] **Step 3: Write the migration**

Create `src/main/resources/db/migration/V12__several_photos.sql`:

```sql
-- An item or a document can have several photos, in order; position 0 is the cover shown on
-- cards. SQLite cannot drop the UNIQUE constraint on the owner column, so both tables are
-- rebuilt. No other table refers to them. Existing pictures become covers.
CREATE TABLE inventory_picture_new (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
INSERT INTO inventory_picture_new (id, item_id, position, file_name, content_type, thumbnail)
SELECT id, item_id, 0, file_name, content_type, thumbnail FROM inventory_picture;
DROP TABLE inventory_picture;
ALTER TABLE inventory_picture_new RENAME TO inventory_picture;
CREATE INDEX inventory_picture_item_id ON inventory_picture (item_id, position);

CREATE TABLE document_picture_new (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL REFERENCES heir_document (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
INSERT INTO document_picture_new (id, document_id, position, file_name, content_type, thumbnail)
SELECT id, document_id, 0, file_name, content_type, thumbnail FROM document_picture;
DROP TABLE document_picture;
ALTER TABLE document_picture_new RENAME TO document_picture;
CREATE INDEX document_picture_document_id ON document_picture (document_id, position);
```

- [ ] **Step 4: Rewrite `PictureInfo` and add `PictureResponse`**

Replace the whole content of `src/main/java/com/mephys/attic/picture/PictureInfo.java`:

```java
package com.mephys.attic.picture;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What is known about a stored picture without loading it.
 */
public record PictureInfo(UUID id, boolean hasThumbnail) {

	/**
	 * URL of the picture below {@code base} (e.g. {@code /api/items/<id>}). Picture ids are never
	 * reused, so the browser may cache it forever.
	 */
	public String url(String base) {
		return base + "/pictures/" + id;
	}

	/**
	 * URL of the thumbnail below {@code base}, or {@code null} when the format has none.
	 */
	public @Nullable String thumbnailUrl(String base) {
		return hasThumbnail ? url(base) + "/thumbnail" : null;
	}

}
```

Create `src/main/java/com/mephys/attic/picture/PictureResponse.java`:

```java
package com.mephys.attic.picture;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A photo in a response body. {@code thumbnailUrl} is {@code null} for formats without thumbnail.
 */
public record PictureResponse(UUID id, String url, @Nullable String thumbnailUrl) {

	public static PictureResponse of(String base, PictureInfo info) {
		return new PictureResponse(info.id(), info.url(base), info.thumbnailUrl(base));
	}

	public static List<PictureResponse> list(String base, List<PictureInfo> pictures) {
		return pictures.stream().map((info) -> of(base, info)).toList();
	}

}
```

- [ ] **Step 5: Add the cache header in `PictureUploads`**

In `src/main/java/com/mephys/attic/picture/PictureUploads.java`, replace `pictureResponse` and `thumbnailResponse` with:

```java
	/** Picture ids are never reused, so a picture never changes behind its URL */
	private static final String CACHE_FOREVER = "private, max-age=31536000, immutable";

	public static ResponseEntity<byte[]> pictureResponse(Optional<Picture> picture) {
		return picture
			.map((p) -> ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CACHE_FOREVER)
				.contentType(MediaType.parseMediaType(p.contentType()))
				.body(p.data()))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	public static ResponseEntity<byte[]> thumbnailResponse(Optional<byte[]> thumbnail) {
		return thumbnail
			.map((t) -> ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CACHE_FOREVER)
				.contentType(MediaType.IMAGE_JPEG)
				.body(t))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
```

Add `import org.springframework.http.HttpHeaders;`. Spring Security's own `Cache-Control: no-cache` header is only written when the response has none. The first test in Step 1 checks that this header gets through.

- [ ] **Step 6: Rewrite `PictureRepository`**

Replace the whole content of `src/main/java/com/mephys/attic/picture/PictureRepository.java`:

```java
package com.mephys.attic.picture;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stores an ordered list of pictures per owner row (an inventory item, a document, ...). The
 * first picture (position 0) is the cover. Picture files live in {@link PictureStorage}; the
 * picture table holds the file name, the position and a thumbnail.
 * <p>
 * The picture table must have the columns {@code id, <owner column>, position, file_name,
 * content_type, thumbnail}, with the owner column referencing the owner table
 * {@code ON DELETE CASCADE}. Positions are kept at 0..n-1 without gaps.
 * <p>
 * Changes of several rows ({@link #delete}, {@link #reorder}) must run in a transaction; the
 * callers' controller methods are {@code @Transactional}.
 */
public class PictureRepository {

	private final JdbcClient jdbc;

	private final PictureStorage storage;

	// Table and column names are fixed by the caller's code, never user input
	private final String table;

	private final String ownerColumn;

	private final String ownerTable;

	public PictureRepository(JdbcClient jdbc, PictureStorage storage, String table, String ownerColumn,
			String ownerTable) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.table = table;
		this.ownerColumn = ownerColumn;
		this.ownerTable = ownerTable;
	}

	/**
	 * Store the picture as a new file, after the owner's other pictures.
	 * @return the new picture, or empty if the owner does not exist
	 */
	public Optional<PictureInfo> add(UUID ownerId, Picture picture) {
		UUID pictureId = UUID.randomUUID();
		byte[] thumbnail = Thumbnails.create(picture.data()).orElse(null);
		String fileName = storage.store(pictureId, picture);
		int added;
		try {
			// Positions have no gaps, so the number of pictures is the next position
			added = jdbc.sql("""
					INSERT INTO %1$s (id, %2$s, position, file_name, content_type, thumbnail)
					SELECT :id, o.id, (SELECT count(*) FROM %1$s WHERE %2$s = o.id), :fileName, :contentType, :thumbnail
					FROM %3$s o WHERE o.id = :ownerId
					""".formatted(table, ownerColumn, ownerTable))
				.param("id", pictureId.toString())
				.param("ownerId", ownerId.toString())
				.param("fileName", fileName)
				.param("contentType", picture.contentType())
				.param("thumbnail", thumbnail)
				.update();
		}
		catch (RuntimeException ex) {
			storage.delete(fileName);
			throw ex;
		}
		if (added == 0) {
			storage.delete(fileName);
			return Optional.empty();
		}
		return Optional.of(new PictureInfo(pictureId, thumbnail != null));
	}

	/**
	 * The owner's pictures, cover first.
	 */
	public List<PictureInfo> list(UUID ownerId) {
		return jdbc.sql("""
				SELECT id, thumbnail IS NOT NULL AS has_thumbnail FROM %s WHERE %s = ? ORDER BY position
				""".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query((rs, rowNum) -> mapInfo(rs))
			.list();
	}

	/**
	 * The pictures of every owner that has any, each list cover first.
	 */
	public Map<UUID, List<PictureInfo>> listAll() {
		return jdbc.sql("""
				SELECT id, %1$s, thumbnail IS NOT NULL AS has_thumbnail FROM %2$s ORDER BY %1$s, position
				""".formatted(ownerColumn, table))
			.query((rs, rowNum) -> Map.entry(UUID.fromString(rs.getString(ownerColumn)), mapInfo(rs)))
			// list() rather than stream(): a JdbcClient stream keeps its connection until closed
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
	}

	public Optional<Picture> find(UUID ownerId, UUID pictureId) {
		return jdbc.sql("SELECT file_name, content_type FROM %s WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query((rs, rowNum) -> {
				String contentType = rs.getString("content_type");
				return storage.read(rs.getString("file_name")).map((data) -> new Picture(contentType, data));
			})
			.optional()
			.flatMap((picture) -> picture);
	}

	/**
	 * Return the JPEG thumbnail of the picture, if it belongs to the owner and has one.
	 */
	public Optional<byte[]> findThumbnail(UUID ownerId, UUID pictureId) {
		return jdbc
			.sql("SELECT thumbnail FROM %s WHERE id = ? AND %s = ? AND thumbnail IS NOT NULL".formatted(table,
					ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query((rs, rowNum) -> rs.getBytes("thumbnail"))
			.optional();
	}

	/**
	 * Delete one picture of the owner and its file; the pictures after it move up.
	 * @return {@code false} if the owner has no such picture
	 */
	public boolean delete(UUID ownerId, UUID pictureId) {
		Optional<String> file = jdbc
			.sql("SELECT file_name FROM %s WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query(String.class)
			.optional();
		if (file.isEmpty()) {
			return false;
		}
		jdbc.sql("DELETE FROM %s WHERE id = ?".formatted(table)).param(pictureId.toString()).update();
		writeOrder(ownerId, list(ownerId).stream().map(PictureInfo::id).toList());
		storage.delete(file.get());
		return true;
	}

	/**
	 * Put the owner's pictures in the given order; the first becomes the cover.
	 * @throws IllegalArgumentException unless the ids are exactly the owner's pictures, each once
	 */
	public void reorder(UUID ownerId, List<UUID> pictureIds) {
		List<UUID> current = list(ownerId).stream().map(PictureInfo::id).toList();
		if (pictureIds.size() != current.size() || new HashSet<>(pictureIds).size() != pictureIds.size()
				|| !new HashSet<>(pictureIds).equals(new HashSet<>(current))) {
			throw new IllegalArgumentException("the order must list each picture exactly once");
		}
		writeOrder(ownerId, pictureIds);
	}

	/**
	 * Delete the owner row. Its picture rows go with it (cascade) and the files are removed.
	 * @return {@code false} if the owner did not exist
	 */
	public boolean deleteOwner(UUID ownerId) {
		List<String> files = jdbc.sql("SELECT file_name FROM %s WHERE %s = ?".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query(String.class)
			.list();
		boolean deleted = jdbc.sql("DELETE FROM %s WHERE id = ?".formatted(ownerTable))
			.param(ownerId.toString())
			.update() > 0;
		files.forEach(storage::delete);
		return deleted;
	}

	private void writeOrder(UUID ownerId, List<UUID> pictureIds) {
		for (int position = 0; position < pictureIds.size(); position++) {
			jdbc.sql("UPDATE %s SET position = ? WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
				.params(position, pictureIds.get(position).toString(), ownerId.toString())
				.update();
		}
	}

	private static PictureInfo mapInfo(ResultSet rs) throws SQLException {
		return new PictureInfo(UUID.fromString(rs.getString("id")), rs.getBoolean("has_thumbnail"));
	}

}
```

- [ ] **Step 7: Switch the inventory repository, response and controller**

In `InventoryRepository.java`, replace every method from `savePicture` through `findAllPictureInfo` with the methods below, and remove the now-unused `Map` import if the compiler flags it:

```java
	Optional<PictureInfo> addPicture(UUID itemId, Picture picture) {
		return pictures.add(itemId, picture);
	}

	List<PictureInfo> listPictures(UUID itemId) {
		return pictures.list(itemId);
	}

	Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	Optional<Picture> findPicture(UUID itemId, UUID pictureId) {
		return pictures.find(itemId, pictureId);
	}

	Optional<byte[]> findThumbnail(UUID itemId, UUID pictureId) {
		return pictures.findThumbnail(itemId, pictureId);
	}

	boolean deletePicture(UUID itemId, UUID pictureId) {
		return pictures.delete(itemId, pictureId);
	}

	void reorderPictures(UUID itemId, List<UUID> pictureIds) {
		pictures.reorder(itemId, pictureIds);
	}
```

Replace the whole of `InventoryItemResponse.java`:

```java
package com.mephys.attic.inventory;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an item, with its photos cover first.
 */
record InventoryItemResponse(UUID id, String name, int quantity, @Nullable LocalDate date,
		@Nullable Location location, boolean existent, BigDecimal valueEur, String owner, @Nullable String comments,
		List<PictureResponse> pictures) {

	static InventoryItemResponse of(InventoryItem item, List<PictureInfo> pictures) {
		return new InventoryItemResponse(item.id(), item.name(), item.quantity(), item.date(), item.location(),
				item.existent(), item.valueEur(), item.owner(), item.comments(),
				PictureResponse.list(base(item.id()), pictures));
	}

	/** Where the item's photos live */
	static String base(UUID itemId) {
		return "/api/items/" + itemId;
	}

}
```

In `InventoryController.java`:
- In `list()`, replace the body with:

  ```java
  		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
  		return repository.findAll()
  			.stream()
  			.map((item) -> InventoryItemResponse.of(item, pictures.getOrDefault(item.id(), List.of())))
  			.toList();
  ```

- In `create`, change `InventoryItemResponse.of(item, null)` to `InventoryItemResponse.of(item, List.of())`.
- Replace the four old picture methods and `toResponse` with the code below.
- Add the imports `com.mephys.attic.picture.PictureResponse` and `org.springframework.transaction.annotation.Transactional`.

```java
	@PostMapping(path = "/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = InventoryItemResponse.base(id);
		return repository.addPicture(id, uploads.read(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.pictureResponse(repository.findPicture(id, pictureId));
	}

	@GetMapping("/{id}/pictures/{pictureId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.thumbnailResponse(repository.findThumbnail(id, pictureId));
	}

	@DeleteMapping("/{id}/pictures/{pictureId}")
	@Transactional
	ResponseEntity<Void> deletePicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return repository.deletePicture(id, pictureId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the item's photos in this order; the first becomes the cover.
	 */
	@PutMapping("/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderPictures(@PathVariable UUID id, @RequestBody List<UUID> pictureIds) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderPictures(id, pictureIds);
		return ResponseEntity.noContent().build();
	}

	private InventoryItemResponse toResponse(InventoryItem item) {
		return InventoryItemResponse.of(item, repository.listPictures(item.id()));
	}
```

- [ ] **Step 8: Run the inventory tests**

Run: `./mvnw test -Dskip.npm -Dskip.installnodenpm -Dtest=InventoryControllerTests`
Expected: the inventory tests PASS. Compilation may still fail in the `document` package. If so, do Step 10 first, then rerun this step.

- [ ] **Step 9: Write the failing document, heir, access and migration tests**

In `DocumentControllerTests.java`:
- In `createGetReplaceDelete`, replace `.andExpect(jsonPath("$.pictureUrl").doesNotExist())` with `.andExpect(jsonPath("$.pictures").isEmpty())`.
- Replace `pictureLifecycle` and `deletingDocumentDeletesPictureFile` with:

```java
	@Test
	void photosLifecycle() throws Exception {
		String id = createDocument(createHeir("David"), "ID_CARD");
		byte[] front = TestImages.withExifOrientation(TestImages.jpeg(640, 400), 6);
		String frontId = addPhoto(id, MediaType.IMAGE_JPEG, front);
		String backId = addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(40, 40));

		mvc.perform(get("/api/documents/{id}", id))
			.andExpect(jsonPath("$.pictures[0].id").value(frontId))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/documents/" + id + "/pictures/" + frontId))
			.andExpect(jsonPath("$.pictures[1].id").value(backId));
		mvc.perform(get("/api/documents/{id}/pictures/{pictureId}", id, frontId))
			.andExpect(status().isOk())
			.andExpect(content().bytes(front));
		mvc.perform(get("/api/documents/{id}/pictures/{pictureId}/thumbnail", id, frontId))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG));

		mvc.perform(put("/api/documents/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + backId + "\",\"" + frontId + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id)).andExpect(jsonPath("$.pictures[0].id").value(backId));

		mvc.perform(post("/api/documents/{id}/pictures", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(post("/api/documents/{id}/pictures", id).contentType("image/svg+xml").content("<a/>"))
			.andExpect(status().isBadRequest());

		mvc.perform(delete("/api/documents/{id}/pictures/{pictureId}", id, backId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id))
			.andExpect(jsonPath("$.pictures.length()").value(1))
			.andExpect(jsonPath("$.pictures[0].id").value(frontId));
	}

	@Test
	void deletingDocumentDeletesAllPhotoFiles() throws Exception {
		String id = createDocument(createHeir("David"), "HEALTH_CARD");
		addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(50, 50));
		addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(50, 50));
		long before = countPictureFiles();

		mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(before - 2);
	}

	private String addPhoto(String documentId, MediaType type, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/documents/{id}/pictures", documentId).contentType(type).content(image))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}
```

In `HeirControllerTests.deletingHeirKeepsTheirDocumentsWithoutHeir`:
- Replace the `put("/api/documents/{id}/picture", passport)…` call with `mvc.perform(post("/api/documents/{id}/pictures", passport).contentType(MediaType.IMAGE_PNG).content(TestImages.png(40, 40))).andExpect(status().isCreated());`.
- Replace `.andExpect(jsonPath("$.pictureUrl").exists())` with `.andExpect(jsonPath("$.pictures.length()").value(1))`.

In `OwnHeirTests`:
- Add a field `private String mariasPhoto;`.
- In `twoHeirsWithDocuments`, replace the `put(…/picture)` call with:

```java
		mariasPhoto = id(mvc.perform(post("/api/documents/" + mariasPassport + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(40, 40))));
```

- In `linkedUserSeesOnlyTheirOwnCardAndDocuments`, replace the three `…/picture` and `…/thumbnail` lines with:

```java
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto).session(ana))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto + "/thumbnail").session(ana))
			.andExpect(status().isNotFound());
```

  and the administrator check with:

```java
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto).session(admin))
			.andExpect(status().isOk());
```

- Also add, next to the existing check on `contract`, a check that a user cannot see the photos of a document without heir:

```java
		String contractPhoto = id(mvc.perform(post("/api/documents/" + contract + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(10, 10))));
		mvc.perform(get("/api/documents/" + contract + "/pictures/" + contractPhoto).session(ana))
			.andExpect(status().isNotFound());
```

- `id(ResultActions)` (at the end of the class) expects `201` and reads `$.id`, so it works for photo uploads as is.

In `RolesTests`, replace the `put("/api/items/" + item + "/picture")…` call with:

```java
		mvc.perform(post("/api/items/" + item + "/pictures").session(user).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(new byte[] { 1 })).andExpect(status().isForbidden());
		String photo = "00000000-0000-4000-8000-000000000001";
		mvc.perform(delete("/api/items/" + item + "/pictures/" + photo).session(user).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/items/" + item + "/pictures/order").session(user), "[]"))
			.andExpect(status().isForbidden());
```

In `DatabaseMigrationTests`, change the expected Flyway version `"11"` to `"12"`.

Create `src/test/java/com/mephys/attic/picture/PicturesMigrationTests.java`:

```java
package com.mephys.attic.picture;

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
 * The single picture an item or document had before becomes its cover.
 */
@SpringBootTest
class PicturesMigrationTests {

	private static final UUID ITEM = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID ITEM_PICTURE = UUID.fromString("22222222-2222-4222-8222-222222222222");

	private static final UUID DOCUMENT = UUID.fromString("33333333-3333-4333-8333-333333333333");

	private static final UUID DOCUMENT_PICTURE = UUID.fromString("44444444-4444-4444-8444-444444444444");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion11Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("11").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO inventory_item (id, name) VALUES ('" + ITEM + "', 'Sofa')");
			statement.execute("INSERT INTO inventory_picture (id, item_id, file_name, content_type) VALUES ('"
					+ ITEM_PICTURE + "', '" + ITEM + "', '" + ITEM_PICTURE + ".png', 'image/png')");
			statement.execute("INSERT INTO heir_document (id, type) VALUES ('" + DOCUMENT + "', 'PASSPORT')");
			statement.execute("INSERT INTO document_picture (id, document_id, file_name, content_type) VALUES ('"
					+ DOCUMENT_PICTURE + "', '" + DOCUMENT + "', '" + DOCUMENT_PICTURE + ".png', 'image/png')");
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private PictureStorage storage;

	@Test
	void existingPicturesBecomeCovers() {
		PictureRepository items = new PictureRepository(jdbc, storage, "inventory_picture", "item_id", "inventory_item");
		PictureRepository documents = new PictureRepository(jdbc, storage, "document_picture", "document_id",
				"heir_document");

		assertThat(items.list(ITEM)).extracting(PictureInfo::id).containsExactly(ITEM_PICTURE);
		assertThat(documents.list(DOCUMENT)).extracting(PictureInfo::id).containsExactly(DOCUMENT_PICTURE);
		assertThat(jdbc.sql("SELECT position FROM inventory_picture").query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("SELECT position FROM document_picture").query(Integer.class).single()).isZero();
		// Owners can have several pictures now
		assertThat(items.add(ITEM, new Picture("image/png", TestImages.png(10, 10)))).isPresent();
		assertThat(items.list(ITEM)).hasSize(2).first().extracting(PictureInfo::id).isEqualTo(ITEM_PICTURE);
	}

}
```

At V11, `heir_document` requires only `id` and `type`: `heir_id` and the timestamps may be `NULL`.

- [ ] **Step 10: Switch the document repository, response and controller**

In `DocumentRepository.java`, replace every method from `savePicture` through `findAllPictureInfo` with:

```java
	Optional<PictureInfo> addPicture(UUID documentId, Picture picture) {
		return pictures.add(documentId, picture);
	}

	List<PictureInfo> listPictures(UUID documentId) {
		return pictures.list(documentId);
	}

	Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	Optional<Picture> findPicture(UUID documentId, UUID pictureId) {
		return pictures.find(documentId, pictureId);
	}

	Optional<byte[]> findThumbnail(UUID documentId, UUID pictureId) {
		return pictures.findThumbnail(documentId, pictureId);
	}

	boolean deletePicture(UUID documentId, UUID pictureId) {
		return pictures.delete(documentId, pictureId);
	}

	void reorderPictures(UUID documentId, List<UUID> pictureIds) {
		pictures.reorder(documentId, pictureIds);
	}
```

Replace the whole of `DocumentResponse.java`:

```java
package com.mephys.attic.document;

import com.mephys.attic.document.DocumentRepository.NamedDocument;
import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a document, with the name of its heir and its photos cover first.
 * {@code heirId} and {@code heir} are {@code null} for a document without heir.
 */
record DocumentResponse(UUID id, @Nullable UUID heirId, @Nullable String heir, DocumentType type,
		@Nullable LocalDate validUntil, @Nullable String comments, List<PictureResponse> pictures) {

	static DocumentResponse of(NamedDocument named, List<PictureInfo> pictures) {
		HeirDocument document = named.document();
		return new DocumentResponse(document.id(), document.heirId(), named.heirName(), document.type(),
				document.validUntil(), document.comments(), PictureResponse.list(base(document.id()), pictures));
	}

	/** Where the document's photos live */
	static String base(UUID documentId) {
		return "/api/documents/" + documentId;
	}

}
```

In `DocumentController.java`:
- In `list()`, replace the body with:

  ```java
  		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
  		return repository.findAll()
  			.stream()
  			.filter((named) -> account.maySee(named.document().heirId()))
  			.map((named) -> DocumentResponse.of(named, pictures.getOrDefault(named.document().id(), List.of())))
  			.toList();
  ```

- Replace the four old picture methods with the code below.
- Change the last `toResponse` to `return DocumentResponse.of(named, repository.listPictures(named.document().id()));`.
- Add the imports `com.mephys.attic.picture.PictureResponse` and `org.springframework.transaction.annotation.Transactional`.

```java
	@PostMapping(path = "/documents/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = DocumentResponse.base(id);
		return repository.addPicture(id, uploads.read(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/documents/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.pictureResponse(maySee(id) ? repository.findPicture(id, pictureId) : Optional.empty());
	}

	@GetMapping("/documents/{id}/pictures/{pictureId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.thumbnailResponse(maySee(id) ? repository.findThumbnail(id, pictureId) : Optional.empty());
	}

	@DeleteMapping("/documents/{id}/pictures/{pictureId}")
	@Transactional
	ResponseEntity<Void> deletePicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return repository.deletePicture(id, pictureId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the document's photos in this order; the first becomes the cover.
	 */
	@PutMapping("/documents/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderPictures(@PathVariable UUID id, @RequestBody List<UUID> pictureIds) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderPictures(id, pictureIds);
		return ResponseEntity.noContent().build();
	}
```

- [ ] **Step 11: Run the whole backend suite**

Run: `./mvnw test -Dskip.npm -Dskip.installnodenpm`
Expected: all tests PASS. One exception: `HeirControllerTests.listsHeirsInTheOrderTheyWereAdded` is a known flaky test that compares timestamps as text. If it alone fails, rerun; do not change it in this task.

- [ ] **Step 12: Commit**

```bash
git add src/main src/test
git commit -m "Store several ordered photos per item and document

The first photo is the cover. Existing pictures become covers.
Photos are added, deleted and reordered through /pictures endpoints.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Frontend — `PhotoViewer`

This is a standalone component with no type changes, so it compiles on its own.

**Files:**
- Create: `frontend/src/components/PhotoViewer.tsx`
- Modify: `frontend/src/components/icons.tsx` (add `ChevronLeftIcon`, `ChevronRightIcon`)
- Modify: `frontend/src/i18n/index.tsx` (add `previousPhoto`, `nextPhoto`, `photoOf`)
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces: `export default function PhotoViewer(props: { sources: string[]; start: number; onClose: () => void })`

- [ ] **Step 1: Add the strings**

In `frontend/src/i18n/index.tsx`, after the `close:` line of each language, add:

English (`en`):
```ts
  previousPhoto: 'Previous photo',
  nextPhoto: 'Next photo',
  photoOf: (n: number, total: number) => `Photo ${n} of ${total}`,
```
Portuguese (`pt`):
```ts
  previousPhoto: 'Fotografia anterior',
  nextPhoto: 'Fotografia seguinte',
  photoOf: (n, total) => `Fotografia ${n} de ${total}`,
```
French (`fr`):
```ts
  previousPhoto: 'Photo précédente',
  nextPhoto: 'Photo suivante',
  photoOf: (n, total) => `Photo ${n} sur ${total}`,
```

- [ ] **Step 2: Add the icons**

Append to `frontend/src/components/icons.tsx` (`base` is the shared SVG attributes object already defined at the top of that file):

```tsx
export const ChevronLeftIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="m15 18-6-6 6-6" />
  </svg>
)

export const ChevronRightIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="m9 18 6-6-6-6" />
  </svg>
)
```

- [ ] **Step 3: Write the component**

Create `frontend/src/components/PhotoViewer.tsx`:

```tsx
import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useI18n } from '../i18n'
import { useModal } from '../lib/useModal'
import { ChevronLeftIcon, ChevronRightIcon, CloseIcon } from './icons'

interface Props {
  /** Full-size photo URLs, in order */
  sources: string[]
  /** Index of the photo to show first */
  start: number
  onClose: () => void
}

/** Minimum horizontal finger movement, in pixels, that counts as a swipe */
const SWIPE_DISTANCE = 50

/** Full-screen photos with previous / next buttons, arrow keys and swipe. */
export default function PhotoViewer({ sources, start, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const [index, setIndex] = useState(start)
  const swipeStart = useRef<number | null>(null)
  const many = sources.length > 1

  const go = (step: number) => setIndex((i) => (i + step + sources.length) % sources.length)

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'ArrowLeft') {
      go(-1)
    } else if (event.key === 'ArrowRight') {
      go(1)
    }
  }

  function onPointerUp(event: PointerEvent) {
    if (swipeStart.current === null) {
      return
    }
    const distance = event.clientX - swipeStart.current
    swipeStart.current = null
    if (many && Math.abs(distance) >= SWIPE_DISTANCE) {
      go(distance < 0 ? 1 : -1)
    }
  }

  return (
    <dialog ref={ref} className="photo-viewer" aria-label={t.photoOf(index + 1, sources.length)}
      onCancel={(e) => { e.preventDefault(); onClose() }} onKeyDown={onKeyDown}>
      <div className="photo-viewer-stage"
        onPointerDown={(e) => { swipeStart.current = e.clientX }}
        onPointerUp={onPointerUp}
        onPointerCancel={() => { swipeStart.current = null }}>
        <img src={sources[index]} alt={t.photoOf(index + 1, sources.length)} draggable={false} />
      </div>
      <button type="button" className="icon-button photo-viewer-close" onClick={onClose} aria-label={t.close}>
        <CloseIcon />
      </button>
      {many && (
        <>
          <button type="button" className="icon-button photo-viewer-previous" onClick={() => go(-1)}
            aria-label={t.previousPhoto}>
            <ChevronLeftIcon />
          </button>
          <button type="button" className="icon-button photo-viewer-next" onClick={() => go(1)}
            aria-label={t.nextPhoto}>
            <ChevronRightIcon />
          </button>
          <p className="photo-viewer-count" aria-hidden="true">{index + 1} / {sources.length}</p>
        </>
      )}
    </dialog>
  )
}
```

- [ ] **Step 4: Add the styles**

Append to `frontend/src/styles.css`:

```css
/* Full-screen photo viewer */

.photo-viewer {
  width: 100vw;
  height: 100dvh;
  max-width: none;
  max-height: none;
  margin: 0;
  padding: 0;
  border: none;
  background: rgb(0 0 0 / 0.92);
  color: #fff;
}

.photo-viewer::backdrop {
  background: rgb(0 0 0 / 0.92);
}

.photo-viewer-stage {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  touch-action: pan-y;
}

.photo-viewer-stage img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  user-select: none;
}

.photo-viewer .icon-button {
  position: absolute;
  color: #fff;
  background: rgb(0 0 0 / 0.4);
}

.photo-viewer-close {
  top: 12px;
  right: 12px;
}

.photo-viewer-previous,
.photo-viewer-next {
  top: 50%;
  transform: translateY(-50%);
}

.photo-viewer-previous {
  left: 12px;
}

.photo-viewer-next {
  right: 12px;
}

.photo-viewer-count {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  margin: 0;
  font-size: 0.9rem;
}
```

- [ ] **Step 5: Type-check**

Run in `frontend/`: `PATH="$PWD/../target/node:$PATH" node_modules/.bin/tsc --noEmit -p .`
Expected: no output (success).

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "Add a full-screen photo viewer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Frontend — types, API calls, `saveWithPhotos` and `PhotosField`

After this task, the new types and components exist next to the old single-picture ones, so the build keeps compiling. Task 4 switches the screens over.

**Files:**
- Modify: `frontend/src/api/types.ts`, `frontend/src/api/api.ts`
- Create: `frontend/src/lib/photos.ts`, `frontend/src/components/PhotosField.tsx`
- Modify: `frontend/src/i18n/index.tsx`, `frontend/src/styles.css`

**Interfaces:**
- Consumes: `PhotoViewer` from Task 2; the HTTP routes from Task 1.
- Produces:
  - `interface Picture { id: string; url: string; thumbnailUrl: string | null }`
  - `type PhotoEntry = { kind: 'stored'; picture: Picture } | { kind: 'new'; key: string; file: File }`
  - `storedPhotos(pictures: Picture[]): PhotoEntry[]`
  - `api.addPicture(owner: string, file: File): Promise<Picture>`, `api.removePicture(owner: string, pictureId: string): Promise<void>`, `api.orderPictures(owner: string, pictureIds: string[]): Promise<void>`
  - `saveWithPhotos<T extends { id: string; pictures: Picture[] }>(saveOwner: () => Promise<T>, base: string, photos: PhotoEntry[], onSaved: (saved: T) => void, onPhotos: (photos: PhotoEntry[]) => void): Promise<T>`
  - `class PhotoUploadError extends Error { fileName: string; reason: unknown }`
  - `PhotosField(props: { photos: PhotoEntry[]; onChange: (photos: PhotoEntry[]) => void; onError: (message: string | null) => void; readOnly?: boolean })`

- [ ] **Step 1: Add the types**

In `frontend/src/api/types.ts`, add at the end:

```ts
/** A stored photo of an item or document */
export interface Picture {
  id: string
  url: string
  /** Null for formats without thumbnail (HEIC) */
  thumbnailUrl: string | null
}

/** A photo in a form: already stored, or chosen and not uploaded yet */
export type PhotoEntry = { kind: 'stored'; picture: Picture } | { kind: 'new'; key: string; file: File }

export const storedPhotos = (pictures: Picture[]): PhotoEntry[] =>
  pictures.map((picture) => ({ kind: 'stored', picture }))
```

- [ ] **Step 2: Add the API calls**

In `frontend/src/api/api.ts`:
- Add `Picture` to the type import from `./types`.
- Keep the old `putPicture` / `deletePicture` for now; Task 4 removes them.
- Add after `deletePicture`:

```ts
  /** Add a photo after the others, e.g. `addPicture('/api/items/<id>', file)` */
  addPicture: (owner: string, file: File) =>
    request<Picture>(`${owner}/pictures`, {
      method: 'POST',
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
      body: file,
    }),
  removePicture: (owner: string, pictureId: string) =>
    request<void>(`${owner}/pictures/${pictureId}`, { method: 'DELETE' }),
  /** Put the photos in this order; the first becomes the cover */
  orderPictures: (owner: string, pictureIds: string[]) =>
    request<void>(`${owner}/pictures/order`, json('PUT', pictureIds)),
```

- [ ] **Step 3: Write `saveWithPhotos`**

Create `frontend/src/lib/photos.ts`:

```ts
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
```

- [ ] **Step 4: Add the `PhotosField` strings**

In `frontend/src/i18n/index.tsx`, after `photoOf` in each language, add:

English:
```ts
  addPhotos: 'Add photos',
  cover: 'Cover',
  makeCover: 'Make cover',
  removePhoto: 'Remove photo',
  openPhoto: (n: number) => `Open photo ${n}`,
  photoCount: (count: number) => (count === 1 ? '1 photo' : `${count} photos`),
  errorPhotoUpload: (file: string, reason: string) => `“${file}” could not be uploaded: ${reason}`,
```
Portuguese:
```ts
  addPhotos: 'Adicionar fotografias',
  cover: 'Capa',
  makeCover: 'Tornar capa',
  removePhoto: 'Remover fotografia',
  openPhoto: (n) => `Abrir fotografia ${n}`,
  photoCount: (count) => (count === 1 ? '1 fotografia' : `${count} fotografias`),
  errorPhotoUpload: (file, reason) => `Não foi possível enviar «${file}»: ${reason}`,
```
French:
```ts
  addPhotos: 'Ajouter des photos',
  cover: 'Couverture',
  makeCover: 'Mettre en couverture',
  removePhoto: 'Retirer la photo',
  openPhoto: (n) => `Ouvrir la photo ${n}`,
  photoCount: (count) => (count === 1 ? '1 photo' : `${count} photos`),
  errorPhotoUpload: (file, reason) => `« ${file} » n’a pas pu être envoyée : ${reason}`,
```

- [ ] **Step 5: Write `PhotosField`**

Create `frontend/src/components/PhotosField.tsx`:

```tsx
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
```

`.button-small` is new; Step 6 defines it.

- [ ] **Step 6: Add the styles**

Append to `frontend/src/styles.css`:

```css
/* Photos of an item or document */

.photos-field {
  margin-bottom: 18px;
}

.photos-list {
  display: flex;
  gap: 10px;
  margin: 0;
  padding: 0 0 4px;
  list-style: none;
  overflow-x: auto;
}

.photo {
  position: relative;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 112px;
}

.photo-open,
.photo-add-button {
  display: grid;
  place-items: center;
  width: 112px;
  height: 112px;
  padding: 0;
  border: none;
  border-radius: var(--radius-small);
  background: var(--surface-muted);
  color: var(--text-muted);
  overflow: hidden;
  cursor: pointer;
}

.photo-open img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-add-button {
  align-content: center;
  gap: 4px;
  border: 2px dashed var(--border);
  font-size: 0.85rem;
  text-align: center;
}

.photo-cover {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgb(0 0 0 / 0.6);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 600;
}

.photo-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.button-small {
  padding: 2px 8px;
  font-size: 0.8rem;
}

@media (max-width: 600px) {
  .photo,
  .photo-open,
  .photo-add-button {
    width: 96px;
  }

  .photo-open,
  .photo-add-button {
    height: 96px;
  }
}
```

`--border`, `--surface-muted`, `--text-muted` and `--radius-small` are defined at the top of `styles.css`, with dark-mode values.

- [ ] **Step 7: Type-check**

Run in `frontend/`: `PATH="$PWD/../target/node:$PATH" node_modules/.bin/tsc --noEmit -p .`
Expected: no output.

- [ ] **Step 8: Commit**

```bash
git add frontend/src
git commit -m "Add a photo list field and saving of photo changes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Frontend — switch items and documents to photo lists

**Files:**
- Modify: `frontend/src/api/types.ts`, `frontend/src/api/api.ts`
- Modify: `frontend/src/components/ItemCard.tsx`, `frontend/src/components/DocumentCard.tsx`, `frontend/src/components/ItemDialog.tsx`, `frontend/src/components/DocumentDialog.tsx`
- Modify: `frontend/src/views/InventoryView.tsx`, `frontend/src/views/DocumentsView.tsx`, `frontend/src/views/HeirsView.tsx`
- Delete: `frontend/src/components/PictureField.tsx`, `frontend/src/lib/documents.ts`
- Modify: `frontend/src/i18n/index.tsx`, `frontend/src/styles.css`

**Interfaces:**
- Consumes: everything Task 3 produces.
- Produces:
  - `ItemDialog` / `DocumentDialog` prop `onSave: (input, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) => Promise<void>`
  - `Item.pictures`, `HeirDocument.pictures`

- [ ] **Step 1: Change the types and remove the old API calls**

In `frontend/src/api/types.ts`:
- In `Item`, replace `pictureUrl` and `thumbnailUrl` with `pictures: Picture[]`, and change `ItemInput` to `Omit<Item, 'id' | 'pictures'>`.
- In `HeirDocument`, replace the two URL fields with `pictures: Picture[]`, and change `DocumentInput` to `Omit<HeirDocument, 'id' | 'heir' | 'pictures'>`.
- Delete the `PictureChange` type.

In `frontend/src/api/api.ts`, delete `putPicture`, the old `deletePicture` and their comment.

Run the type-check. The errors it lists are the remaining steps of this task.

- [ ] **Step 2: Show the cover and photo count on cards**

In `ItemCard.tsx`, replace the `{item.thumbnailUrl ? (…) : (…)}` block with:

```tsx
          {item.pictures[0]?.thumbnailUrl ? (
            <img src={item.pictures[0].thumbnailUrl} alt="" loading="lazy" />
          ) : (
            <ImageIcon className="card-placeholder" width={40} height={40} />
          )}
          {item.pictures.length > 1 && (
            <span className="card-photo-count" aria-label={t.photoCount(item.pictures.length)}>
              <ImageIcon width={14} height={14} /> {item.pictures.length}
            </span>
          )}
```

In `DocumentCard.tsx`, do the same with `document.pictures`, keeping `IdCardIcon` with `width={44} height={44}` as the placeholder. Import `ImageIcon` too.

Append to `styles.css`:

```css
.card-photo-count {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgb(0 0 0 / 0.6);
  color: #fff;
  font-size: 0.8rem;
  font-weight: 600;
}
```

Check that `.card-image` has `position: relative`. `.card-quantity` is already absolutely positioned inside it, so it should.

- [ ] **Step 3: Use `PhotosField` in `ItemDialog`**

In `ItemDialog.tsx`:
- Replace the `PictureField` import with `import PhotosField from './PhotosField'` and `import { PhotoUploadError } from '../lib/photos'`.
- Change the type import to `import { storedPhotos, type Item, type ItemInput, type PhotoEntry } from '../api/types'`.
- Change the prop to `onSave: (input: ItemInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) => Promise<void>`.
- Replace `const [picture, setPicture] = useState<PictureChange>({ kind: 'keep' })` with `const [photos, setPhotos] = useState<PhotoEntry[]>(() => storedPhotos(item?.pictures ?? []))`.
- In `submit`, pass `photos, setPhotos` instead of `picture`. Replace the `catch` body with:

  ```tsx
      } catch (e) {
        setError(e instanceof PhotoUploadError ? t.errorPhotoUpload(e.fileName, apiErrorMessage(e.reason, t)) : apiErrorMessage(e, t))
        setSaving(false)
      }
  ```

- Replace the `<PictureField … />` element with:

  ```tsx
            <PhotosField readOnly={!canEdit} photos={photos} onChange={setPhotos} onError={setError} />
  ```

- [ ] **Step 4: Use `PhotosField` in `DocumentDialog`**

Make exactly the same changes in `DocumentDialog.tsx`:
- The `DocumentInput` type replaces `ItemInput`.
- `useState<PhotoEntry[]>(() => storedPhotos(document?.pictures ?? []))`.
- The same `catch`.
- `<PhotosField readOnly={!canEdit} photos={photos} onChange={setPhotos} onError={setError} />`.

- [ ] **Step 5: Save photos from the views**

In `InventoryView.tsx`, replace `save` with the code below, import `saveWithPhotos` from `../lib/photos`, replace `PictureChange` with `PhotoEntry` in the type import, and remove the `api.putPicture` / `api.deletePicture` usage:

```tsx
  async function save(input: ItemInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editing === 'new' || editing === null ? null : editing
    // From the first save on the item exists: a retry after a failed upload updates it
    await saveWithPhotos(
      () => (existing ? api.updateItem(existing.id, input) : api.createItem(input)),
      '/api/items', photos, setEditing, onPhotos,
    )
    await reload()
    setEditing(null)
  }
```

In `DocumentsView.tsx`, replace `save` with the code below. Replace the `saveDocument` import with `saveWithPhotos`, and `PictureChange` with `PhotoEntry` in the type import:

```tsx
  async function save(input: DocumentInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editing === 'new' ? null : editing
    await saveWithPhotos(
      () => (existing ? api.updateDocument(existing.id, input) : api.createDocument(input)),
      '/api/documents', photos, setEditing, onPhotos,
    )
    await reload()
    setEditing(null)
  }
```

In `HeirsView.tsx`, replace `saveDocumentOfHeir` the same way, using `setEditingDocument`:

```tsx
  async function saveDocumentOfHeir(input: DocumentInput, photos: PhotoEntry[], onPhotos: (photos: PhotoEntry[]) => void) {
    const existing = editingDocument !== null && editingDocument !== 'new' ? editingDocument : null
    await saveWithPhotos(
      () => (existing ? api.updateDocument(existing.id, input) : api.createDocument(input)),
      '/api/documents', photos, setEditingDocument, onPhotos,
    )
    await reload()
    setEditingDocument(null)
  }
```

Delete `frontend/src/lib/documents.ts` and `frontend/src/components/PictureField.tsx`.

The dialogs are rendered with a constant `key` (`"item-dialog"`, `"document-dialog"`). So `setEditing(saved)` passes a new prop to the open dialog without remounting it, and the photo state survives a failed upload. Don't change those keys.

- [ ] **Step 6: Clean up the strings and styles**

In `frontend/src/i18n/index.tsx`, in all three languages:
- Delete `addPicture`, `changePicture`, `removePicture` and `openFullPicture`.
- Change `deleteMessage` to talk about photos:
  - en: `` (name: string) => `“${name}” and its photos will be deleted permanently.` ``
  - pt: `` (name) => `«${name}» e as suas fotografias serão eliminados permanentemente.` ``
  - fr: `` (name) => `« ${name} » et ses photos seront supprimés définitivement.` ``

In `styles.css`, delete the `.picture-field`, `.picture-preview`, `.picture-preview img` and `.picture-actions` rules, and the `.picture-preview` override in the `@media` block.

Check nothing refers to them any more:

```bash
grep -rn "picture-field\|picture-preview\|picture-actions\|PictureChange\|PictureField\|openFullPicture\|putPicture\|pictureUrl\|thumbnailUrl" frontend/src
```

Expected: only `thumbnailUrl` hits in `types.ts`, `PhotosField.tsx`, `ItemCard.tsx` and `DocumentCard.tsx`.

- [ ] **Step 7: Type-check and full build**

Run in `frontend/`: `PATH="$PWD/../target/node:$PATH" node_modules/.bin/tsc --noEmit -p .`
Expected: no output.

Run at the repository root: `./mvnw -q package`
Expected: BUILD SUCCESS. The npm "allow-scripts" warnings and the expected `WARN` log lines from the tests are fine.

- [ ] **Step 8: Commit**

```bash
git add -A frontend/src
git commit -m "Show and edit several photos on items and documents

Cards show the cover and a photo count; dialogs edit the photo list,
which is saved with the item or document.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Check in the browser and update the README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Start the app on a free port**

Port 8080 may be used by the installed service. Start the jar from Task 4 on port 8081 with a throwaway data directory, in the background:

```bash
java -jar target/attic-0.1.0-SNAPSHOT.jar --server.port=8081 --attic.database.file=target/manual-check/attic.db
```

Open http://localhost:8081 and create the first account. Also test with a copy of real data that has single pictures: `cp -r data target/manual-copy`, then start with `--attic.database.file=target/manual-copy/attic.db`. The existing pictures should appear as covers.

- [ ] **Step 2: Walk through the checks**

At desktop width and at 390 px wide (browser dev tools), confirm each of these:
1. **New item:** add three photos at once and save. The card shows the first photo and "3" in the badge.
2. **Make cover:** reopen the item, make photo 3 the cover and save. The card now shows it.
3. **Remove:** remove one photo and save. It's gone after a reload.
4. **Cancel:** add a photo, then Cancel. Nothing changes.
5. **Viewer:** tap a thumbnail. The viewer opens; arrows, swipe (touch emulation) and Escape work; Escape closes only the viewer, not the dialog.
6. **Failed upload:** stop the server, add a photo and Save. There's an error naming the file. Restart the server and Save again. The item isn't duplicated, and each photo is there once.
7. **Documents:** repeat checks 1–3 on a document, both from the Documents tab and from an heir's dialog.
8. **Read-only account:** a user account sees the photos and viewer, but no add/remove/make cover buttons.

Fix anything that fails in the task that owns it, rerun its checks, and commit the fix.

- [ ] **Step 3: Update the README**

In `README.md`, change the Inventory bullet from "…owner, comments and a picture;" to "…owner, comments and photos (the first is the cover);". In the Documents bullet, add "with photos (e.g. front and back)" after "kept without one".

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "Mention several photos in the README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
