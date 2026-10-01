# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# PO Token provider build stage
FROM node:22-bookworm-slim AS pot-build
ARG BGUTIL_VERSION=master
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/bgutil-ytdlp-pot-provider
RUN git clone --depth 1 --branch ${BGUTIL_VERSION} https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git . \
    && cd server \
    && npm ci \
    && npx tsc

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_MAJOR=22

# Runtime tooling: ffmpeg + python (yt-dlp), Node 22 (PO Token provider)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        python3 \
        python3-venv \
        ca-certificates \
        curl \
        gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
        | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# yt-dlp (latest) + the PO Token provider plugin (latest)
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade yt-dlp bgutil-ytdlp-pot-provider

ENV PATH="/opt/venv/bin:$PATH"

# PO Token provider server
COPY --from=pot-build /opt/bgutil-ytdlp-pot-provider /opt/bgutil-ytdlp-pot-provider

COPY --from=build /app/target/*.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && ls -la /app/

EXPOSE 8080

ENV JAVA_OPTS=""
ENV YTDLP_POT_ENABLED=true
ENV YTDLP_POT_PROVIDER_URL=http://127.0.0.1:4416
ENV YTDLP_COOKIES_PATH=/app/cookies.txt

# Run as an unprivileged user; the cookies secret is mounted read-only by Render.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser \
    && chown -R appuser:appuser /app
USER appuser

ENTRYPOINT ["/app/entrypoint.sh"]
