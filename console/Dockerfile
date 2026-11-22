# The operations console as a container: the built files behind nginx, which also forwards
# /identity and /api on the same origin (see nginx.conf). Built by compose (profile app).

# Stage 1: type check and bundle, exactly as "npm run build" does on a developer machine.
FROM docker.io/library/node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
# Map tiles for the route map; empty: OpenStreetMap. Pass MAP_TILE_ORIGIN below to match.
ARG VITE_MAP_TILE_URL=
ARG VITE_MAP_TILE_ATTRIBUTION=
RUN npm run build

# Stage 2: only the static files and nginx. Runs as a non-root user on port 8080.
FROM docker.io/nginxinc/nginx-unprivileged:stable-alpine
# nginx.conf is a template: the entrypoint fills in the DNS server of the container network.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
# Where map images may come from (nginx.conf, img-src); the origin of VITE_MAP_TILE_URL.
ENV MAP_TILE_ORIGIN=https://tile.openstreetmap.org
# The two backends (nginx.conf). Short names work with compose's DNS; Kubernetes needs full names.
ENV IDENTITY_UPSTREAM=http://identity:8085
ENV APP_UPSTREAM=http://edge:8081
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
