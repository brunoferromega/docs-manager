# docs-manager

Document management API: documents with tagging, status lifecycle and versioned file references,
backed by PostgreSQL with Flyway-managed migrations.

## Requirements

| Tool   | Version | Notes                                              |
|--------|---------|----------------------------------------------------|
| JDK    | 25      | Toolchain is pinned in `build.gradle.kts`          |
| Docker | 20.10+  | Runs PostgreSQL 17 (and the test containers)       |

The Gradle wrapper is committed, so no local Gradle install is needed.

## Quick start

```bash
docker compose up --build
```

That is all that is needed: it builds the API image, starts `postgres:17`, waits until it is
healthy, applies the Flyway migrations and serves the API on `http://localhost:8080`.

```bash
curl http://localhost:8080/api/documents
```

Stop with `Ctrl+C`, or `docker compose down` (add `-v` to also drop the database volume).

## Set up the environment

Only needed to build or run the application outside Docker.

Point `JAVA_HOME` at a JDK 25 install:

```bash
export JAVA_HOME=/path/to/jdk-25
java -version   # should print 25.x
```

The database runs on `localhost:5432` with database `docs_storage` and user/password
`bruno`/`bruno`, which are the application's defaults. They can be overridden with
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
Data is kept in the `pgdata` volume, so it survives restarts; `docker compose down -v` wipes it.

### Compose files

| File                      | Contains          | Used by                                            |
|---------------------------|-------------------|----------------------------------------------------|
| `docker-compose.yaml`     | PostgreSQL + API  | `docker compose up` — the whole stack               |
| `docker-compose.dev.yaml` | PostgreSQL only   | `bootRun`/IDE, started automatically by Spring Boot |

The split keeps local development from starting a second copy of the API in a container while one
is already running on the host.

## Build

```bash
./gradlew build -x test   # compile and assemble
./gradlew build           # compile, assemble and run the test suite
```

## Run migrations

Flyway runs automatically on every application start and applies anything new in
`src/main/resources/db/migration`. Hibernate is set to `ddl-auto: validate`, so the schema is owned
by the migrations alone and the application refuses to start if the entities and the schema disagree.

There is no separate migrate task: starting the application (or running the tests) is what applies
pending migrations. To check what has been applied:

```bash
docker compose exec postgres psql -U bruno -d docs_storage \
  -c 'select version, description, success from flyway_schema_history order by installed_rank;'
```

Migrations are named `V<n>__<description>.sql`:

| Version | Adds                                                        |
|---------|-------------------------------------------------------------|
| `V1`    | `documents`, `document_files`, `document_status` enum        |
| `V2`    | `document_tags` plus indexes for tag and `created_at` filters |

## Execute tests

```bash
./gradlew test              # unit + integration tests
./gradlew test --tests '*DocumentServiceTest'
```

Integration tests start their own PostgreSQL through Testcontainers, so Docker must be running.
They do not use the `docker compose` database and will not touch its data.

## Run the application

Whole stack in containers:

```bash
docker compose up --build
```

Or run the API on the host, with only the database in Docker:

```bash
./gradlew bootRun
```

`bootRun` uses Spring Boot's Docker Compose support to start `docker-compose.dev.yaml`
automatically, so the database does not need to be started by hand. Either way the API listens on
`http://localhost:8080`.

## Authentication

Every endpoint except `POST /api/auth/login` requires a bearer token.

The `users` table is seeded by migration `V3` with three static accounts (passwords are BCrypt
hashed in the database):

| Username | Password    | Role     |
|----------|-------------|----------|
| `admin`  | `admin123`  | `ADMIN`  |
| `user`   | `user123`   | `USER`   |
| `viewer` | `viewer123` | `VIEWER` |

Exchange credentials for a token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
```

Then send it on every call:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/documents
```

Tokens are HS256 JWTs valid for one hour, carrying the username as `sub`, the user id as `uid` and
the role in a `roles` claim. The signing key comes from `app.security.jwt.secret`, overridable with
the `JWT_SECRET` environment variable — the committed value is a development default and must be
replaced in any real deployment. Bad credentials, unknown users and disabled accounts all return the
same `401`, so the endpoint cannot be used to enumerate usernames.

## API

Base path: `/api/documents`

| Method   | Path                  | Description                                |
|----------|-----------------------|--------------------------------------------|
| `POST`   | `/`                   | Create a document (always starts `DRAFT`)  |
| `GET`    | `/{id}`               | Fetch one document                         |
| `GET`    | `/`                   | List with filters and pagination           |
| `PUT`    | `/{id}`               | Update title, description and tags         |
| `PATCH`  | `/{id}/status`        | Move through the status lifecycle          |
| `POST`   | `/{id}/files`         | Register the next file version             |
| `DELETE` | `/{id}`               | Delete a document and its files/tags       |

### File versions

A document owns an ordered list of file versions. A file is a *reference* to an object in a storage
backend (`fileKey`) plus its `checksum`; the bytes themselves are not stored by this service.

`versionNumber` is always assigned by the server — the first file is version 1 and each upload takes
the next number. Clients never supply it, and concurrent uploads for the same document race on the
`uq_document_version` constraint, so the loser gets `409` instead of a duplicate version.

Create a document with its first version in one call:

```bash
curl -X POST http://localhost:8080/api/documents \
  -H 'Content-Type: application/json' \
  -d '{
        "title": "Spec",
        "ownerId": "<uuid>",
        "file": { "fileKey": "s3://bucket/spec-v1.pdf", "checksum": "<64 hex chars>" }
      }'
```

Add another version:

```bash
curl -X POST http://localhost:8080/api/documents/<id>/files \
  -H 'Content-Type: application/json' \
  -d '{ "fileKey": "s3://bucket/spec-v2.pdf", "checksum": "<64 hex chars>" }'
```

`checksum` must be exactly 64 hexadecimal characters (SHA-256) and is stored lower-cased.
`uploadedBy` is optional and defaults to the document owner. The document representation carries the
most recent version as `latestFile`.

### Listing filters

All filters are optional and combine with `AND`. An unmatched filter returns an empty page rather
than an error.

| Parameter     | Example                        | Meaning                                     |
|---------------|--------------------------------|---------------------------------------------|
| `ownerId`     | `?ownerId=<uuid>`              | Exact owner                                 |
| `status`      | `?status=PUBLISHED`            | Exact status                                |
| `title`       | `?title=report`                | Case-insensitive "contains"                 |
| `tag`         | `?tag=finance`                 | Exact tag, case/whitespace insensitive      |
| `createdFrom` | `?createdFrom=2026-08-01T00:00:00Z` | `created_at >=`, inclusive             |
| `createdTo`   | `?createdTo=2026-08-31T23:59:59Z`   | `created_at <=`, inclusive             |
| `page`/`size` | `?page=0&size=20`              | Pagination, defaults to 20 per page         |
| `sort`        | `?sort=title,asc`              | Defaults to `createdAt,desc`                |

```bash
curl "http://localhost:8080/api/documents?tag=finance&createdFrom=2026-08-01T00:00:00Z&size=10"
```

### Status lifecycle

`DRAFT → PUBLISHED`, `DRAFT → ARCHIVED`, `PUBLISHED → ARCHIVED`. Anything else returns `409`.
Re-applying the current status is a no-op.

```bash
curl -X PATCH http://localhost:8080/api/documents/<id>/status \
  -H 'Content-Type: application/json' -d '{"status":"PUBLISHED"}'
```

### Errors

Failures use RFC 9457 `application/problem+json`. Validation errors list the offending fields:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "errors": { "title": "title is required" }
}
```

## Project layout

```
src/main/java/io/bruno/docs_manager/
├── controller/   REST endpoints
├── service/      business rules and transactions
├── repository/   Spring Data repositories and query specifications
├── entity/       JPA entities mapped to the Flyway schema
├── dto/          request/response records
└── exception/    domain exceptions and the problem-detail handler
```
