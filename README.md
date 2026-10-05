# Attic

A small, self-hosted web app for keeping a family's records in one place: the household
inventory, the heirs and their share of the heritage, and everyone's personal documents with
their expiry dates. It runs on a home computer and is reached from your own devices through
Tailscale.

## Features

- **Inventory** — items with quantity, date, room, value in euros, owner, comments and a picture;
  search, filter and export the list as PDF.
- **Heirs** — date of birth, address, filiation, sex and share of the heritage, kept as a fraction
  exactly as written (`2/6` stays `2/6`).
- **Documents** — ID cards, passports, licences, certificates, contracts… per heir, with a
  validity badge (valid, expires soon, expired).
- **Accounts and roles** — *super-administrator*, *administrator* (edits everything, creates
  accounts) and *user* (reads everything, edits only their own heir and password). The first
  account is created on the setup screen. Administrators either create accounts themselves or
  hand out an invitation link (one-time, valid for 7 days) with which the person picks their own
  username and password.
- **Languages** — Portuguese, French and English, chosen from the browser or switched in the app.
- Works on phones; *Add to Home Screen* makes it look like an app.

## Tech stack

| Part | Built with |
|---|---|
| Backend | Java 25, Spring Boot 4 (Web MVC, JDBC, Security, Actuator) |
| Database | SQLite, migrated by Flyway (`src/main/resources/db/migration`) |
| Frontend | React 19, TypeScript, Vite, jsPDF (`frontend/`) |
| Build | Maven; the frontend is built by `frontend-maven-plugin` and served by Spring Boot |

## Getting started

Requirements: JDK 25. Maven downloads Node and npm itself.

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080 and create the first account.

For frontend work with hot reload, run the backend as above and, in another terminal:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, forwards /api to the backend
```

To skip the frontend build when only working on the backend:

```bash
./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm
```

## Tests and build

```bash
./mvnw test        # backend tests
./mvnw package     # runnable jar in target/, frontend included
```

## Configuration

Set in `src/main/resources/application.properties`, or overridden as usual for Spring Boot
(environment variables, `--name=value`):

| Property | Default | Meaning |
|---|---|---|
| `attic.database.file` | `data/attic.db` | SQLite database file |
| `attic.picture.dir` | `pictures` next to the database | Where pictures are stored |
| `attic.picture.max-size` | `10MB` | Largest picture accepted |

The `prod` profile (`application-prod.properties`) listens only on `127.0.0.1` and trusts the
forwarded headers of the local proxy.

## Running at home

`deploy/` installs Attic as a systemd user service with daily backups and makes it reachable
over HTTPS through `tailscale serve`. See [deploy/README.md](deploy/README.md).

## License

[MIT](LICENSE) © 2026 David da Costa
