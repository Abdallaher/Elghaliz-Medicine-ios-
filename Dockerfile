# Stage 1: Build Vite PWA static assets into dist/
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY index.html vite.config.ts tsconfig.json ./
COPY public ./public
COPY src ./src

RUN npm run build

# Stage 2: Lightweight production server on 0.0.0.0:${PORT:-8080}
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=8080

COPY package.json server.mjs ./
COPY --from=builder /app/dist ./dist

EXPOSE 8080

CMD ["npm", "start"]
