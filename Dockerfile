# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

# Runtime tooling: ffmpeg + python (yt-dlp).
#
# There is deliberately no Node.js here. A PO Token provider used to be built in a separate stage and
# started by the entrypoint, but it was never the cause of the YouTube media-URL blocks: those come
# from the data centre IP reputation, not from a missing token. Keeping it cost build time and image
# size for no benefit. YTDLP_POT_ENABLED stays false, and the Java side only passes the provider
# extractor-arg when that flag is on, so this image stays consistent.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        python3 \
        python3-venv \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# yt-dlp (latest)
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade yt-dlp

ENV PATH="/opt/venv/bin:$PATH"

COPY --from=build /app/target/*.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && ls -la /app/

EXPOSE 8080

ENV JAVA_OPTS=""
ENV YTDLP_POT_ENABLED=false
ENV YTDLP_COOKIES_PATH=/app/cookies.txt

# Run as an unprivileged user; the cookies secret is mounted read-only by Render.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser \
    && chown -R appuser:appuser /app
USER appuser

ENTRYPOINT ["/app/entrypoint.sh"]
