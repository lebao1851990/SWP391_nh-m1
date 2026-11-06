FROM node:20-alpine

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY src/ ./src/

RUN chown -R node:node /app

USER node

EXPOSE 8085

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8085/api/health || exit 1

CMD ["node", "src/server.js"]
