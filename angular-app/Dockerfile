FROM node:26.10.0-alpine AS builder
WORKDIR /app

# Workspace manifests + lockfile only (for layer caching). Copy every workspace
# package.json so npm ci can resolve the monorepo lockfile; sources come later.
COPY --chown=node:node package*.json ./
COPY --chown=node:node packages/shared-schemas/package.json packages/shared-schemas/package.json
COPY --chown=node:node angular-app/package.json angular-app/package.json
COPY --chown=node:node services/image-scraper/package.json services/image-scraper/package.json

# Skip lifecycle scripts: image-scraper's Playwright postinstall is irrelevant here
# and would pull browser binaries into the Angular build image.
RUN npm ci --ignore-scripts

# Build the shared-schemas package the Angular app depends on (tsc -> build/)
COPY --chown=node:node packages/shared-schemas packages/shared-schemas
RUN npm run build:schemas

# Angular app source + config
COPY --chown=node:node angular-app angular-app

RUN npm run build:angular-app

FROM nginx:1.28.0-alpine3.21-slim
COPY --from=builder /app/angular-app/dist/angular-app/browser /usr/share/nginx/html
COPY angular-app/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80