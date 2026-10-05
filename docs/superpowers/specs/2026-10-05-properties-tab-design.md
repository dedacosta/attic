# Properties tab

Requested on 2026-10-05. It changes the House and Land tabs of the roadmap's step 2.

## Goal

The House and Land tabs become **one tab, Properties**. It shows a list of houses and a list of
land parcels. There can be several houses from now on.

## What exists already

House and land are one type in the code and the database: a *property* with a kind of `HOUSE`
or `LAND`. They share the page, the dialog, the photos, the details and the official documents.
That stays. What changes is the screen that leads to them, and the rule that there is at most
one house.

## Decisions

- **One tab**, "Properties", replaces House and Land. It is the page the app opens on.
- **Several houses** are allowed, like land parcels. A house can be deleted, as a parcel can.
- **The page** has two sections, **Houses** then **Land**. Each has:
  - a heading with its count and the total estimated value of what is shown. Properties without
    a value are left out of the total;
  - a button to add one ("New house", "New land"), for administrators;
  - its properties as cards: cover photo, name, address (for land: location), estimated value,
    number of official documents;
  - a line saying it is empty, when it is.
- **One search box** filters both sections by name, address, comments and details. A section
  with no match says so.
- **A card opens the property's page** at `#/properties/<id>`, the page used today, with a link
  back to Properties. An id that does not exist shows "This property no longer exists."
- **Access** does not change: everyone signed in sees the tab, only administrators create,
  change or delete.
- **Names:**

  | | English | Portuguese | French |
  |---|---|---|---|
  | Tab | Properties | Propriedades | Propriétés |
  | Sections | Houses, Land | Casas, Terrenos | Maisons, Terrains |

- **Tab order:** Properties, Heirs, Catalog, Inventory, Documents, Contributions, Renovations,
  Users.
- **Not included:**
  - other kinds of property, such as apartments or garages;
  - changing a house into land or back;
  - a redirect from `#/land` or `#/land/<id>`. Old bookmarks open Properties.

## Database

Migration `V23__several_houses.sql` drops the index `property_one_house`, which allowed only
one row of kind `HOUSE`. No row changes; the existing house is the first entry of Houses.

## Backend

- `GET /api/properties` returns all properties by name when `kind` is not given, and those of
  one kind when it is, as today.
- `GET /api/properties/house` is removed.
- `POST /api/properties` no longer refuses a second house. `HouseAlreadyExistsException` and its
  handler in `ApiExceptionHandler` are removed.
- Everything else of the property API stays, including "the kind cannot change".

## Frontend

- `PropertiesView` replaces `HouseView` and `LandView`. It loads all properties once with
  `api.listProperties()` and splits them by kind.
- `api.house` and `api.listLand` are removed.
- `PropertyPage` and `PropertyDialog` stay. The dialog's title for a new house is "New house"
  instead of "Set up the house", and the delete confirmation says "Delete property?" for both
  kinds.
- `App.tsx`: the views `house` and `land` become `properties`, at `#/` and `#/properties`, and
  `#/properties/<id>` for one property.
- Texts that only the two old screens used are removed; the new ones are added in `en.json`,
  `pt.json` and `fr.json`.

## Testing

- `PropertyControllerTests`:
  - a second house is created, and both are listed;
  - the list without `kind` has houses and land, by name;
  - `GET /api/properties/house` no longer returns a house: "house" is read as an id, which is
    not valid, so the answer is an error (4xx);
  - the tests of the one-house rule are removed.
- `DatabaseMigrationTests` expects version 23.
- A migration test starts from version 22 with one house, migrates, and inserts a second house.
- `PropertyAccessTests` uses the list instead of `/api/properties/house`.
- `./mvnw verify` passes, which also type-checks and builds the frontend.

## Installing it

The new jar applies V23 on the next start, with nothing to do by hand.
