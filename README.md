# Wish List Application

A CRUD app for wishes with a **Java 21 / Spring Boot 3** API, Angular frontend, and a Node image-scraper sidecar.

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

## Production

```bash
docker compose up --build
```

- Frontend: http://localhost:8080
- Backend API: http://localhost:3000
- Elasticsearch: http://localhost:9200

## API notes

| Endpoint | Status |
|---|---|
| `GET /wishes/search` | **Primary list/search** used by the Angular UI (empty `q` = browse) |
| `GET /all-wishes` | **Legacy / debug** — kept for curl and older tests; UI must not use it for the main list |
| `POST /internal/etl/sync` | Manual delta ETL trigger (refresh=true) |

## Testing

```bash
# Java API (Testcontainers for ES / Redis / Postgres on ETL & search suites)
cd api-java && mvn test

# Angular
npm -w angular-app test
```

## Project Structure

```
├── angular-app/              # Angular frontend
├── api-java/                 # Spring Boot API
├── services/image-scraper/   # Playwright SSE scraper
├── packages/shared-schemas/  # Shared Zod schemas
└── docker-compose.yaml
```
