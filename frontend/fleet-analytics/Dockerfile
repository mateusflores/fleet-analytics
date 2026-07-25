FROM node:20-alpine AS build

WORKDIR /app

ARG NG_CONFIGURATION=production
ARG API_BASE_URL=http://localhost:8080/api/v1

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

RUN ENV_FILE="src/environments/environment.prod.ts" && \
    if [ "$NG_CONFIGURATION" = "staging" ]; then ENV_FILE="src/environments/environment.staging.ts"; fi && \
    sed -i "s|apiBaseUrl: '.*'|apiBaseUrl: '${API_BASE_URL}'|" "$ENV_FILE"

RUN npm run build -- --configuration "${NG_CONFIGURATION}"

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/fleet-analytics/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
