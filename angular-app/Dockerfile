FROM node:22.15.0-alpine AS builder
WORKDIR /app

# Workspace manifests + lockfile only (for layer caching). The lockfile references
# api-db/services workspaces that are NOT shipped in this image; --ignore-scripts
# prevents the root postinstall ("npm run -w api-db install:headless-browser") from
# running, which is what previously broke the build with "No workspaces found".
COPY --chown=node:node package*.json ./
COPY --chown=node:node packages/shared-schemas/package.json packages/shared-schemas/package.json
COPY --chown=node:node angular-app/package.json angular-app/package.json

RUN npm ci --ignore-scripts

# Build the shared-schemas package the Angular app depends on (tsc -> build/)
COPY --chown=node:node packages/shared-schemas packages/shared-schemas
RUN npm run -w packages/shared-schemas build

# Angular app source + config
COPY --chown=node:node angular-app angular-app

# Build using the workspace-installed CLI, which matches the app's Angular major (21).
# No global @angular/cli install needed and no version-mismatch risk.
RUN npm -w angular-app run build --configuration=production

FROM nginx:1.28.0-alpine3.21-slim
COPY --from=builder /app/angular-app/dist/angular-app/browser /usr/share/nginx/html
COPY angular-app/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80