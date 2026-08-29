# docs-manager

Document management API: documents with tagging, status lifecycle and versioned file references,
backed by PostgreSQL with Flyway-managed migrations.

## Requirements

| Tool   | Version | Notes                                              |
|--------|---------|----------------------------------------------------|
| JDK    | 25      | Toolchain is pinned in `build.gradle.kts`          |
| Docker | 20.10+  | Runs PostgreSQL 17 (and the test containers)       |

The Gradle wrapper is committed, so no local Gradle install is needed.

## Set up the environment

Point `JAVA_HOME` at a JDK 25 install:

```bash
export JAVA_HOME=/path/to/jdk-25
java -version   # should print 25.x
```

Start PostgreSQL:

```bash
docker compose up -d
```

This brings up `postgres:17` on `localhost:5432` with database `docs_storage` and user/password
`bruno`/`bruno`, matching the defaults the application connects with. Data is kept in the `pgdata`
volume, so it survives restarts; `docker compose down -v` wipes it.

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

```bash
./gradlew bootRun
```

The app listens on `http://localhost:8080`. In development it uses Spring Boot's Docker Compose
support to start PostgreSQL automatically, so `docker compose up -d` is optional when using
`bootRun`.

## API

Base path: `/api/documents`

| Method   | Path                  | Description                                |
|----------|-----------------------|--------------------------------------------|
| `POST`   | `/`                   | Create a document (always starts `DRAFT`)  |
| `GET`    | `/{id}`               | Fetch one document                         |
| `GET`    | `/`                   | List with filters and pagination           |
| `PUT`    | `/{id}`               | Update title, description and tags         |
| `PATCH`  | `/{id}/status`        | Move through the status lifecycle          |
| `DELETE` | `/{id}`               | Delete a document and its files/tags       |

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
