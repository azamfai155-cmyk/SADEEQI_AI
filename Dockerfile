FROM node:22-alpine

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev && npm cache clean --force

# The Express API entrypoint and its input-validation dependency.
COPY server.js ./server.js
COPY security ./security
COPY render-server.js ./render-server.js

ENV NODE_ENV=production
ENV PORT=10000

USER node

EXPOSE 10000

CMD ["node", "render-server.js"]
