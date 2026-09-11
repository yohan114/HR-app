# syntax=docker/dockerfile:1.7

# ===========================================================================
# Stage 1: Build Vite Single Page Application
# ===========================================================================
FROM node:22-alpine AS builder

WORKDIR /app

# Cache package dependencies before source copy
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Copy source tree and build distribution bundle
COPY . .
RUN npm run build

# ===========================================================================
# Stage 2: Production Nginx Server
# ===========================================================================
FROM nginx:1.27-alpine AS runtime

RUN apk add --no-cache curl tzdata

# Non-root unprivileged execution setup
RUN touch /tmp/nginx.pid && \
    chown -R nginx:nginx /tmp/nginx.pid /var/cache/nginx /var/log/nginx

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy compiled SPA bundle from builder stage
COPY --from=builder --chown=nginx:nginx /app/dist /usr/share/nginx/html

ENV TZ=UTC

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -fsS http://localhost:80/healthz || exit 1

STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]
