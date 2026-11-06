FROM node:20-alpine

WORKDIR /app
COPY package.json proxy-server.js ./

EXPOSE 2375

HEALTHCHECK --interval=5s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:2375/_ping || exit 1

CMD ["node", "proxy-server.js"]

