# Wish List Application

A CRUD app for wishes with a **Java 21 / Spring Boot 3** API, Angular frontend, and a Node image-scraper sidecar.

| | Tests | Coverage |
|---------|-------|----------|
| **api-java** | [![api-java Tests](https://github.com/Merwanel/wish-app-java/actions/workflows/api-java-test-build-push.yaml/badge.svg)](https://github.com/Merwanel/wish-app-java/actions/workflows/api-java-test-build-push.yaml) | [![codecov](https://codecov.io/gh/Merwanel/wish-app-java/branch/main/graph/badge.svg?flag=api-java)](https://codecov.io/gh/Merwanel/wish-app-java) |
| **angular-app** | [![angular-app Tests](https://github.com/Merwanel/wish-app-java/actions/workflows/angular-app-test-build-push.yaml/badge.svg)](https://github.com/Merwanel/wish-app-java/actions/workflows/angular-app-test-build-push.yaml) | [![codecov](https://codecov.io/gh/Merwanel/wish-app-java/branch/main/graph/badge.svg?flag=angular-app)](https://codecov.io/gh/Merwanel/wish-app-java) |
| **image-scraper** | [![image-scraper Tests](https://github.com/Merwanel/wish-app-java/actions/workflows/image-scraper-test-build-push.yaml/badge.svg)](https://github.com/Merwanel/wish-app-java/actions/workflows/image-scraper-test-build-push.yaml) | [![codecov](https://codecov.io/gh/Merwanel/wish-app-java/branch/main/graph/badge.svg?flag=image-scraper)](https://codecov.io/gh/Merwanel/wish-app-java) |

![App diagram](diagram.png)

## Features

- **Wish Management**: Create, update, and delete wishes with names, comments, tags, and images
- **Elasticsearch Search**: Fuzzy search, tag facets, and `<mark>` highlights via `GET /wishes/search`
- **Image Processing**: Upload images with automatic WebP resizing
- **Online Image Search**: SSE-proxied Playwright scraping for wish images
- **Delta ETL**: Postgres → Elasticsearch synced on a schedule and after writes (Redis bookmark `etl:last_sync`)
- **Type-Safety**: Zod schemas shared with the Angular frontend

## Tech Stack

* **Frontend**: Angular 19, TypeScript, Zod
* **API**: Java 21, Spring Boot 3, Spring Data JPA, Flyway, elasticsearch-java, Redis
* **Sidecar**: Node.js image-scraper (Playwright)
* **Infrastructure**: nginx, Redis 8, PostgreSQL 17, Elasticsearch 8.17, Docker

## Development

From the repo root (Docker Engine required for infra and for Java tests):

```bash
# Local loop: builds shared-schemas, starts Postgres/Redis/Elasticsearch if needed
# (waits until healthy), then runs api-java + image-scraper + Angular together.
npm run dev

# Full production-like stack
npm run dev:docker
```

Local URLs when using `npm run dev`:

- Frontend (ng serve): http://localhost:4200
- Backend API: http://localhost:3000
- Image scraper: http://localhost:3001
- Elasticsearch: http://localhost:9200

`dev:api-java` runs `mvn -f api-java/pom.xml spring-boot:run` (Maven stays outside npm workspaces). Node modules are invoked with `npm run -w …`.

## Production

```bash
npm run dev:docker
# or: docker compose up --build
```

`api-java` builds via **`api-java/Dockerfile`** (multi-stage Maven → JRE). No local `mvn` required for this path.

- Frontend: http://localhost:8080
- Backend API: http://localhost:3000
- Elasticsearch: http://localhost:9200

### Fast local API image (optional)

`api-java/Dockerfile.local` is a **runtime-only** image that `COPY`s a prebuilt jar. Compose does **not** use it by default.

```bash
npm run build:api-java
docker build -f api-java/Dockerfile.local -t wish-api-java:local api-java
# Then temporarily set api-java.build.dockerfile to Dockerfile.local if desired
```

## API notes

| Endpoint | Status |
|---|---|
| `GET /wishes/search` | **Primary list/search** used by the Angular UI (empty `q` = browse) |
| `GET /all-wishes` | **Legacy / debug** — kept for curl and older tests; UI must not use it for the main list |
| `POST /internal/etl/sync` | Manual delta ETL trigger (refresh=true) |

## Testing

Root scripts build shared-schemas first when needed, then run suites in parallel (colored prefixes; fail-fast).

```bash
# All modules (Angular needs Chrome/Chromium; api-java needs Docker for Testcontainers)
npm test
npm run test:coverage

# One module
npm run test:angular-app
npm run test:scraper
npm run test:api-java

npm run test:coverage:angular-app
npm run test:coverage:scraper
npm run test:coverage:api-java
```

Java details: Testcontainers (Postgres + Redis + Elasticsearch), Docker Engine required (Compose is not). API pin 1.44 via surefire + `docker-java.properties`. SpotBugs: `cd api-java && mvn spotbugs:check`. Jacoco XML: `api-java/target/site/jacoco/jacoco.xml`.

## Project Structure

```
├── angular-app/              # Angular frontend
├── api-java/                 # Spring Boot API
├── services/image-scraper/   # Playwright SSE scraper
├── packages/shared-schemas/  # Shared Zod schemas
├── package.json              # Root npm scripts (dev / test / per-module)
└── docker-compose.yaml
```
