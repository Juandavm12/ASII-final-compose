FROM node:22.14.0-bookworm-slim
WORKDIR /app
COPY snapshots/API-Festivos-Taller1/package*.json ./
RUN npm ci --omit=dev
COPY snapshots/API-Festivos-Taller1/ ./
COPY docker/entrypoint.sh /entrypoint.sh
USER node
ENTRYPOINT ["sh", "/entrypoint.sh"]
