FROM node:24.13.1-slim AS builder

WORKDIR /opt/status-tacos

# layer 1 - cache dependencies
COPY package*.json ./
RUN npm ci --no-audit --no-fund && npx playwright install && npx playwright install-deps

# layer 2 - all of our files and the build
COPY . .
ARG VITE_GIT_VERSION
ARG VITE_APP_VERSION
ENV VITE_GIT_VERSION=$VITE_GIT_VERSION
ENV VITE_APP_VERSION=$VITE_APP_VERSION

RUN echo '{ "version": "'$VITE_APP_VERSION'", "git": "'$VITE_GIT_VERSION'" }' > public/version.json && \
      # npm run lint && \
      # npm run type-check && \
      # npx vitest run && \
      npm run build

FROM nginx:alpine

# Create a non-root user
RUN addgroup -g 1001 -S nginx-user && \
    adduser -S -D -H -u 1001 -h /var/cache/nginx -s /sbin/nologin -G nginx-user -g nginx-user nginx-user

# Create necessary directories and set permissions
RUN mkdir -p /var/cache/nginx /var/log/nginx /var/run /usr/share/nginx/html && \
    chown -R nginx-user:nginx-user /var/cache/nginx /var/log/nginx /var/run /etc/nginx /usr/share/nginx/html && \
    chmod -R 755 /var/cache/nginx /var/log/nginx && \
    chmod 777 /var/run

# Cache bust and create a custom nginx.conf that works with non-root user
COPY nginx.conf /etc/nginx/nginx.conf

# Switch to non-root user
USER nginx-user

WORKDIR /usr/share/nginx/html

COPY --from=builder /opt/status-tacos/dist .

# Expose the unprivileged port
EXPOSE 8080
