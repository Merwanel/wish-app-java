FROM node:24-slim

# Chromium runtime dependencies & curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends \
  curl \
  libglib2.0-0 \
  libnss3 \
  libnspr4 \
  libatk1.0-0 \
  libatk-bridge2.0-0 \
  libcups2 \
  libdrm2 \
  libxkbcommon0 \
  libxcomposite1 \
  libxdamage1 \
  libxfixes3 \
  libxrandr2 \
  libgbm1 \
  libasound2 \
  libatspi2.0-0 \
  libcairo2 \
  libdbus-1-3 \
  libexpat1 \
  libx11-6 \
  libxcb1 \
  libxshmfence1 \
  && rm -rf /var/lib/apt/lists/*

USER node
WORKDIR /workspace

# Copy scraper service package definition
COPY --chown=node:node services/image-scraper/package.json ./package.json

RUN npm install --omit=peer

# Install headless browser binary
RUN npm run install:headless-browser

# Copy scraper service source and configurations
COPY --chown=node:node services/image-scraper ./

# Compile TypeScript (same as root `npm run build:scraper`)
RUN npm run build

# Prune devDependencies for a lean image
RUN npm prune --omit=dev

ENV PORT=3001
EXPOSE 3001

HEALTHCHECK --interval=5s --timeout=3s --retries=5 --start-period=10s \
  CMD curl -f -s http://localhost:3001/health || exit 1

CMD ["node", "./build/server.js"]
