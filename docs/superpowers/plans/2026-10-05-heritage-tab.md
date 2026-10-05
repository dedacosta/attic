# Heritage Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the House and Land tabs with one Properties tab that lists houses and land, and allow several houses.

**Architecture:** The backend already has one `property` type with a kind. A migration drops the one-house index, the list endpoint returns all kinds when none is asked for, and the single-house endpoint goes away. In the frontend one new view, `PropertiesView`, replaces `HouseView` and `LandView` and reuses `PropertyPage` and `PropertyDialog`.

**Tech Stack:** Spring Boot 4 (Java 25), SQLite with Flyway, React 19 with TypeScript and Vite.

**Spec:** `docs/superpowers/specs/2026-10-05-heritage-tab-design.md`

> **Changed while it was carried out.** The tab is called **Heritage**, not Properties, and
> `House` and `Land` are subclasses of `Property`. What was built differs from the tasks below
> in these points; the spec describes the result:
>
> - Task 1 was followed by a further backend step: `Property` became an abstract class with the
>   subclasses `House` and `Land`, `Heritage` holds both lists, and `GET /api/heritage` returns
>   them. `PropertyTests` and a controller test cover it.
> - Task 2 built `HeritageView` at `#/heritage` instead of `PropertiesView` at `#/properties`. It
>   loads `api.heritage()` instead of `api.listProperties()`. The text keys are `tabHeritage`,
>   `lands` and `backToHeritage` instead of `tabProperties`, `land` and `backToProperties`.

## Global Constraints

- Migration: `V23__several_houses.sql`. No existing migration file is edited.
- Tab names: Properties / Propriedades / Propriétés. Sections: Houses, Land / Casas, Terrenos / Maisons, Terrains.
- Tab order: Properties, Heirs, Catalog, Inventory, Documents, Contributions, Renovations, Users.
- Addresses: `#/` and `#/properties` for the list, `#/properties/<id>` for one property. No redirect from `#/land`.
- "The kind cannot change" stays.
- After each task `./mvnw -o clean verify` passes.
- All commands run from `/home/daco_dv/dev/attic`, on branch `official-inventory`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- **An existing database with one house** keeps it and accepts a second. Pinned in Task 1, Step 2 (`SeveralHousesMigrationTests`).
- **Total with missing values:** a property without an estimated value must not make the section total `NaN`. Handled by `?? 0` in `PropertiesView`; there is no frontend test runner, so it is checked by reading the code in Task 2, Step 5.
- **A property id in the address that no longer exists** shows "This property no longer exists." with the link back, not a blank page. Checked in Task 2, Step 5.
- **Deleting the last house** leaves an empty Houses section with its "New house" button, not the removed "not set up" screen. Checked in Task 2, Step 5.
- **A search that matches only land** shows the Houses section saying nothing matches, not hiding the "New house" button. Checked in Task 2, Step 5.

---

### Task 1: Several houses in the backend

**Files:**
- Create: `src/main/resources/db/migration/V23__several_houses.sql`
- Create: `src/test/java/com/mephys/attic/property/SeveralHousesMigrationTests.java`
- Modify: `src/main/java/com/mephys/attic/property/PropertyController.java`, `PropertyRepository.java`
- Modify: `src/main/java/com/mephys/attic/controller/ApiExceptionHandler.java`
- Delete: `src/main/java/com/mephys/attic/property/HouseAlreadyExistsException.java`
- Modify: `src/test/java/com/mephys/attic/property/PropertyControllerTests.java`, `PropertyAccessTests.java`, `src/test/java/com/mephys/attic/repository/DatabaseMigrationTests.java`

**Interfaces:**
- Produces: `GET /api/properties` (all kinds, by name) and `GET /api/properties?kind=HOUSE|LAND`. `GET /api/properties/house` no longer exists.

- [ ] **Step 1: Change the controller tests**

In `PropertyControllerTests`:

- Replace every `get("/api/properties/house")` in `houseLifecycle` with `get("/api/properties/{id}", id)`. The first one, before the house is created, is removed, and so is the one after the delete (the line after it already checks the id).
- Replace the tests `onlyOneHouse` and `databaseRefusesASecondHouseEvenWithoutTheCheck` with:

```java
	@Test
	void thereCanBeSeveralHouses() throws Exception {
		String first = create("{\"kind\":\"HOUSE\",\"name\":\"Casa de Viseu\"}");
		String second = create("{\"kind\":\"HOUSE\",\"name\":\"Casa da praia\"}");

		String body = mvc.perform(get("/api/properties").param("kind", "HOUSE"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$[*].id");
		List<String> kinds = JsonPath.read(body, "$[*].kind");
		assertThat(ids).containsExactlyInAnyOrder(first, second);
		assertThat(kinds).containsOnly("HOUSE");
	}

	@Test
	void theListWithoutKindHasHousesAndLandByName() throws Exception {
		create("{\"kind\":\"LAND\",\"name\":\"Vinha\"}");
		create("{\"kind\":\"HOUSE\",\"name\":\"Casa\"}");
		create("{\"kind\":\"LAND\",\"name\":\"Olival\"}");

		String body = mvc.perform(get("/api/properties")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(body, "$[*].name");
		assertThat(names).containsSubsequence("Casa", "Olival", "Vinha");
	}

	@Test
	void theSingleHouseAddressIsGone() throws Exception {
		create("{\"kind\":\"HOUSE\",\"name\":\"Casa\"}");

		mvc.perform(get("/api/properties/house")).andExpect(status().is4xxClientError());
	}
```

- Remove the imports `DataAccessException` and `assertThatExceptionOfType` if nothing else uses them.

In `PropertyAccessTests`, in the array of paths, replace `"/api/properties/house"` with `"/api/properties"`.

In `DatabaseMigrationTests`, `.isEqualTo("22")` becomes `.isEqualTo("23")`.

- [ ] **Step 2: Write the migration test**

`src/test/java/com/mephys/attic/property/SeveralHousesMigrationTests.java`:

```java
package com.mephys.attic.property;

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
 * The house that was the only one is kept, and more can be added.
 */
@SpringBootTest
class SeveralHousesMigrationTests {

	private static final UUID HOUSE = UUID.fromString("11111111-1111-4111-8111-111111111111");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion22Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("22").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO property (id, kind, name) VALUES ('" + HOUSE + "', 'HOUSE', 'Casa de Viseu')");
		}
	}

	@Autowired
	private PropertyRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void theHouseIsKeptAndASecondOneIsAccepted() {
		assertThat(repository.findById(HOUSE)).get().extracting(Property::name).isEqualTo("Casa de Viseu");

		jdbc.sql("INSERT INTO property (id, kind, name) VALUES (?, 'HOUSE', 'Casa da praia')")
			.param(UUID.randomUUID().toString())
			.update();

		assertThat(repository.findAll(PropertyKind.HOUSE)).extracting(Property::name)
			.containsExactly("Casa da praia", "Casa de Viseu");
	}

}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./mvnw -o -Dskip.npm clean resources:resources resources:testResources compiler:compile compiler:testCompile surefire:test`
Expected: failures in `PropertyControllerTests` (`thereCanBeSeveralHouses` gets 409, the list without `kind` gets 400), `SeveralHousesMigrationTests` and `DatabaseMigrationTests`.

- [ ] **Step 4: Write the migration and change the backend**

`src/main/resources/db/migration/V23__several_houses.sql`:

```sql
-- There can be several houses from now on, as there can be several land parcels.
DROP INDEX property_one_house;
```

In `PropertyController`:

- `list` takes an optional kind:

```java
	/** All properties by name, or those of one kind */
	@GetMapping("/properties")
	List<PropertyResponse> list(@RequestParam(required = false) @Nullable PropertyKind kind) {
```

  with `import org.jspecify.annotations.Nullable;` if it is not imported yet.
- The method `house()` (`@GetMapping("/properties/house")`) is deleted.
- `create` becomes:

```java
	@PostMapping("/properties")
	@Transactional
	ResponseEntity<PropertyResponse> create(@RequestBody PropertyRequest request) {
		Property saved = repository.save(request.toProperty(null));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(saved.id());
		return ResponseEntity.created(location).body(toResponse(saved));
	}
```

- The import of `DataAccessException` is removed.

In `PropertyRepository`:

- `findHouse()` is deleted.
- `findAll` accepts `null` for every kind. Its query `SELECT * FROM property WHERE kind = ?` with `.param(kind.name())` becomes:

```java
		return jdbc.sql("SELECT * FROM property WHERE :kind IS NULL OR kind = :kind")
			.param("kind", (kind != null) ? kind.name() : null)
```

  keeping the rest of the statement (ordering and mapping) as it is. The parameter is declared `@Nullable PropertyKind kind`, and the comment becomes "All properties by name, or those of one kind."

Delete `HouseAlreadyExistsException.java`, and in `ApiExceptionHandler` remove its import and the `houseAlreadyExists` method.

- [ ] **Step 5: Run the tests**

Run: `./mvnw -o -Dskip.npm clean resources:resources resources:testResources compiler:compile compiler:testCompile surefire:test`
Expected: `BUILD SUCCESS`.

Run: `grep -rn "HouseAlreadyExists\|findHouse\|properties/house\"" src/main`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "Allow several houses and list all properties"
```

---

### Task 2: The Properties tab

**Files:**
- Create: `frontend/src/views/PropertiesView.tsx`
- Delete: `frontend/src/views/HouseView.tsx`, `frontend/src/views/LandView.tsx`
- Modify: `frontend/src/api/api.ts`, `frontend/src/App.tsx`, `frontend/src/components/PropertyPage.tsx`, `frontend/src/components/PropertyDialog.tsx`, `frontend/src/i18n/index.tsx`, `en.json`, `pt.json`, `fr.json`, `frontend/src/styles.css`, `README.md`

**Interfaces:**
- Consumes: `GET /api/properties` from Task 1.
- Produces: `api.listProperties(): Promise<Property[]>`.

- [ ] **Step 1: API call**

In `frontend/src/api/api.ts` the lines of `house` (with its comment) and `listLand` become:

```ts
  /** Houses and land, by name */
  listProperties: () => request<Property[]>('/api/properties'),
```

- [ ] **Step 2: Texts**

In each of `en.json`, `pt.json`, `fr.json`:

Remove: `tabHouse`, `tabLand`, `houseNotSetUp`, `setUpHouse`, `landSearchPlaceholder`, `landSearchLabel`, `addFirstLand`, `backToLand`, `landNotFound`.

Change:

| Key | en | pt | fr |
|---|---|---|---|
| `deletePropertyTitle` | Delete property? | Eliminar propriedade? | Supprimer la propriété ? |

The French `?` keeps the narrow non-breaking space the file already uses before it.

Add:

| Key | en | pt | fr |
|---|---|---|---|
| `tabProperties` | Properties | Propriedades | Propriétés |
| `houses` | Houses | Casas | Maisons |
| `land` | Land | Terrenos | Terrains |
| `newHouse` | New house | Nova casa | Nouvelle maison |
| `houseCount` | `{"one": "{count} house", "other": "{count} houses"}` | `{"one": "{count} casa", "other": "{count} casas"}` | `{"one": "{count} maison", "other": "{count} maisons"}` |
| `filteredHouseCount` | `{"one": "{shown} of {count} house", "other": "{shown} of {count} houses"}` | `{"one": "{shown} de {count} casa", "other": "{shown} de {count} casas"}` | `{"one": "{shown} sur {count} maison", "other": "{shown} sur {count} maisons"}` |
| `emptyHouses` | No houses yet. | Ainda não há casas. | Aucune maison pour l’instant. |
| `noHouseMatches` | No house matches your search. | Nenhuma casa corresponde à pesquisa. | Aucune maison ne correspond à la recherche. |
| `propertySearchPlaceholder` | Search name, address, details… | Pesquisar nome, morada, detalhes… | Rechercher nom, adresse, détails… |
| `propertySearchLabel` | Search properties | Pesquisar propriedades | Rechercher des propriétés |
| `backToProperties` | All properties | Todas as propriedades | Toutes les propriétés |
| `propertyNotFound` | This property no longer exists. | Esta propriedade já não existe. | Cette propriété n’existe plus. |

`emptyLand`, `noLandMatches`, `newLand`, `landCount` and `filteredLandCount` stay as they are.

In `frontend/src/i18n/index.tsx`, add to `PARAMS`:

```ts
  houseCount: ['count'],
  filteredHouseCount: ['shown', 'count'],
```

- [ ] **Step 3: The view**

`frontend/src/views/PropertiesView.tsx`:

```tsx
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiErrorMessage } from '../api/api'
import PropertyDialog from '../components/PropertyDialog'
import PropertyPage from '../components/PropertyPage'
import SearchBar from '../components/SearchBar'
import { ChevronLeftIcon, HouseIcon, LandIcon, PlusIcon } from '../components/icons'
import { formatEuros, normalize } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { Property, PropertyKind } from '../api/types'

/** The property shown, from a hash like #/properties/<id>; null for the lists */
function propertyFromHash(): string | null {
  const match = /^#\/properties\/([0-9a-f-]+)$/i.exec(window.location.hash)
  return match ? match[1] : null
}

function matches(property: Property, words: string[]): boolean {
  const text = normalize(
    [property.name, property.address ?? '', property.comments ?? '',
      ...property.facts.flatMap((f) => [f.label, f.value])].join(' '),
  )
  return words.every((word) => text.includes(word))
}

/** The houses and the land parcels of the heritage, and the page of one of them. */
export default function PropertiesView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [properties, setProperties] = useState<Property[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [query, setQuery] = useState('')
  const [propertyId, setPropertyId] = useState(propertyFromHash)
  const [creating, setCreating] = useState<PropertyKind | null>(null)

  const reload = useCallback(async () => {
    try {
      setProperties(await api.listProperties())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
    const onHashChange = () => setPropertyId(propertyFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [reload])

  const words = useMemo(() => normalize(query).split(/\s+/).filter(Boolean), [query])

  const backLink = (
    <a className="link property-back" href="#/properties">
      <ChevronLeftIcon width={16} height={16} /> {t.backToProperties}
    </a>
  )

  if (loadError !== null) {
    return (
      <main className="content">
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      </main>
    )
  }

  if (properties === null) {
    return <main className="content"><p className="empty">{t.loading}</p></main>
  }

  if (propertyId !== null) {
    const property = properties.find((p) => p.id === propertyId)
    if (!property) {
      return (
        <main className="content">
          {backLink}
          <p className="empty">{t.propertyNotFound}</p>
        </main>
      )
    }
    return (
      <>
        <div className="content property-back-bar">{backLink}</div>
        <PropertyPage property={property} onChanged={reload}
          onDeleted={() => { window.location.hash = '#/properties'; reload() }} />
      </>
    )
  }

  function section(kind: PropertyKind) {
    const isHouse = kind === 'HOUSE'
    const Icon = isHouse ? HouseIcon : LandIcon
    const all = properties!.filter((p) => p.kind === kind)
    const visible = all.filter((p) => matches(p, words))
    // Properties without an estimated value are left out of the total
    const total = visible.reduce((sum, p) => sum + (p.valueEur ?? 0), 0)
    const count = isHouse
      ? (query ? t.filteredHouseCount(visible.length, all.length) : t.houseCount(all.length))
      : (query ? t.filteredLandCount(visible.length, all.length) : t.landCount(all.length))
    const newLabel = isHouse ? t.newHouse : t.newLand
    return (
      <section className="property-section properties-section">
        <div className="property-section-header">
          <h2>{isHouse ? t.houses : t.land}</h2>
          {all.length > 0 && (
            <span className="count">{count}{' · '}{t.totalValue(formatEuros(total, t.locale))}</span>
          )}
          {canEdit && (
            <button type="button" className="button button-small" onClick={() => setCreating(kind)}>
              <PlusIcon width={16} height={16} /> {newLabel}
            </button>
          )}
        </div>

        {all.length === 0 && <p className="hint">{isHouse ? t.emptyHouses : t.emptyLand}</p>}
        {all.length > 0 && visible.length === 0 && (
          <p className="hint">{isHouse ? t.noHouseMatches : t.noLandMatches}</p>
        )}

        <div className="grid">
          {visible.map((property) => (
            <article key={property.id} className="card">
              <a className="card-main" href={`#/properties/${property.id}`}>
                <div className="card-image">
                  {property.pictures[0]?.thumbnailUrl ? (
                    <img src={property.pictures[0].thumbnailUrl} alt="" loading="lazy" />
                  ) : (
                    <Icon className="card-placeholder" width={40} height={40} />
                  )}
                </div>
                <div className="card-body">
                  <h3 className="card-title">{property.name}</h3>
                  <dl className="card-facts">
                    {property.address && (
                      <div>
                        <dt>{isHouse ? t.address : t.landLocation}</dt>
                        <dd>{property.address}</dd>
                      </div>
                    )}
                    <div>
                      <dt>{t.estimatedValue}</dt>
                      <dd>{property.valueEur != null ? formatEuros(property.valueEur, t.locale) : t.notEstimated}</dd>
                    </div>
                    <div>
                      <dt>{t.officialDocuments}</dt>
                      <dd>{property.documents.length}</dd>
                    </div>
                  </dl>
                </div>
              </a>
            </article>
          ))}
        </div>
      </section>
    )
  }

  return (
    <main className="content">
      <SearchBar query={query} onQuery={setQuery} placeholder={t.propertySearchPlaceholder}
        label={t.propertySearchLabel} />

      {section('HOUSE')}
      {section('LAND')}

      {creating && (
        <PropertyDialog property={null} kind={creating}
          onSaved={(saved) => { setCreating(null); reload(); window.location.hash = `#/properties/${saved.id}` }}
          onClose={() => { setCreating(null); reload() }} />
      )}
    </main>
  )
}
```

If `SearchBar` requires `children`, pass none only if the prop is optional; otherwise make it optional in `SearchBar`.

In `frontend/src/styles.css`, after the rule `.property-section-header h2`:

```css
/* The Houses and Land sections of the Properties tab */
.properties-section .property-section-header .count {
  margin-right: auto;
}
```

Then look at how `.property-section-header` lays out its children and adjust this rule so the heading and count sit on the left and the button on the right.

- [ ] **Step 4: Dialog, page and tab**

- `PropertyDialog.tsx`: in the title, `t.setUpHouse` becomes `t.newHouse`.
- `PropertyPage.tsx`: its comment "The page of the house or a land parcel" becomes "The page of a house or a land parcel". Nothing else: `onDeleted` is now always passed, so a house shows the delete button.
- `App.tsx`:
  - import `PropertiesView` instead of `HouseView` and `LandView`; `LandIcon` is no longer imported here;
  - the `View` type has `'properties'` instead of `'house'` and `'land'`;
  - `viewFromHash`: the `#/land` prefix check is removed, and the `default` returns `'properties'`. Its comment becomes "Properties is the start page (#/, #/properties or #/properties/<id>)";
  - `const view: View = allowed(hashView) ? hashView : 'properties'`;
  - the two tab lines become `{ id: 'properties', href: '#/', label: t.tabProperties, Icon: HouseIcon },`;
  - `{view === 'properties' && <PropertiesView />}` replaces the two lines of the old views.
- Delete `frontend/src/views/HouseView.tsx` and `frontend/src/views/LandView.tsx`.
- `README.md`: the House and Land lines of the feature list become one line:

```markdown
- **Properties** — the houses and the land parcels, each with address, estimated value, details, photos and official documents (images or PDF);
```

- [ ] **Step 5: Build and check**

Run: `./mvnw -o clean verify`
Expected: `BUILD SUCCESS`.

Run: `grep -rnE "HouseView|LandView|api\.house|listLand|tabHouse|tabLand|#/land|setUpHouse|houseNotSetUp" frontend/src`
Expected: no output.

Read `PropertiesView.tsx` once more against the Review Focus list: the total uses `?? 0`; an unknown id shows `propertyNotFound` with the back link; an empty Houses section still shows its button; a section whose properties are all filtered out shows its "no match" line and its button.

- [ ] **Step 6: Commit**

```bash
git add -A frontend README.md
git commit -m "Show houses and land in one Properties tab"
```
