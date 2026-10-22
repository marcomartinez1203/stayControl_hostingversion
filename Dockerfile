# Build context: stayControl_hostingversion (the directory containing this file).
FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY staycontrol/frontend/package.json staycontrol/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY staycontrol/frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY staycontrol/pom.xml ./
COPY staycontrol/src/main/ ./src/main/
COPY --from=frontend /build/frontend/dist/ ./frontend/dist/
RUN mvn -B -ntp package -Dmaven.test.skip=true

FROM tailscale/tailscale:v1.104.2 AS tailscale

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=backend --chown=app:app /build/backend/target/staycontrol-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=tailscale /usr/local/bin/tailscale /usr/local/bin/tailscale
COPY --from=tailscale /usr/local/bin/tailscaled /usr/local/bin/tailscaled
COPY --chown=app:app deploy/start-render.sh /app/start-render.sh
RUN sed -i 's/\r$//' /app/start-render.sh && bash -n /app/start-render.sh
ENV SERVER_ADDRESS=0.0.0.0 \
    SESSION_COOKIE_SECURE=true \
    AUTH_BOOTSTRAP_ENABLED=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
USER app
EXPOSE 10000
CMD ["bash", "/app/start-render.sh"]
