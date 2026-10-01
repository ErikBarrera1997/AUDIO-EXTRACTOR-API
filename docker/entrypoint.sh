#!/bin/sh
set -e

APP_DIR=/app
APP_JAR="$APP_DIR/app.jar"
POT_PROVIDER_HOME=/opt/bgutil-ytdlp-pot-provider
POT_PROVIDER_PORT="${YTDLP_POT_PROVIDER_PORT:-4416}"
POT_PROVIDER_HOST=127.0.0.1
POT_PROVIDER_LOG=/tmp/pot-provider.log
YTDLP_COOKIES_PATH="${YTDLP_COOKIES_PATH:-/app/cookies.txt}"
export YTDLP_COOKIES_PATH

cd "$APP_DIR"

# Render mounts secret files as root with restrictive permissions while this container runs as the
# unprivileged appuser, so a mounted cookies.txt is unreadable here no matter who owns it. Passing
# the cookies as a secret environment variable sidesteps that: the content lands in this process,
# which can write a file it owns. The Java side is untouched and still only reads
# YTDLP_COOKIES_PATH.
#
# When YTDLP_COOKIES_CONTENT is unset the filesystem is left alone, so a deployment that mounts the
# secret where appuser can read it keeps working with no change.
materialise_cookies() {
    if [ -z "${YTDLP_COOKIES_CONTENT:-}" ]; then
        if [ -r "$YTDLP_COOKIES_PATH" ]; then
            echo "[entrypoint] Reusing the cookies file already present at $YTDLP_COOKIES_PATH"
        else
            echo "[entrypoint] WARNING: no YTDLP_COOKIES_CONTENT and nothing readable at $YTDLP_COOKIES_PATH."
            echo "[entrypoint] WARNING: yt-dlp will refuse to run, so search and extraction fail with a 503."
        fi
        return 0
    fi

    umask 077
    # Writing in place is the normal path. If a stale Render secret file is still mounted at the
    # destination it belongs to root and is not writable here, so that write fails with EACCES;
    # fall back to a neighbouring temp file plus rename, which only needs write permission on the
    # directory, which appuser owns. Without this fallback a leftover mount would abort the
    # container under set -e instead of just degrading.
    # The 2> comes first on purpose: redirections apply left to right, so silencing stderr only
    # works if the shell has already redirected it when the failing open of the file is reported.
    if ! printf '%s\n' "$YTDLP_COOKIES_CONTENT" 2>/dev/null > "$YTDLP_COOKIES_PATH"; then
        tmp_path="$YTDLP_COOKIES_PATH.materialise.$$"
        if printf '%s\n' "$YTDLP_COOKIES_CONTENT" > "$tmp_path" &&
            mv -f "$tmp_path" "$YTDLP_COOKIES_PATH" 2>/dev/null; then
            echo "[entrypoint] Destination was not writable; replaced it via rename."
        else
            rm -f "$tmp_path" 2>/dev/null || true
            echo "[entrypoint] ERROR: could not write the cookies file at $YTDLP_COOKIES_PATH"
            echo "[entrypoint] ERROR: if Render still has a secret file mounted there, delete it;"
            echo "[entrypoint] ERROR: a root-owned mount cannot be replaced by this unprivileged user."
            return 1
        fi
    fi
    # The env var is the primary control on the secret; the mode is defence in depth, so a chmod
    # failure must not block startup on its own.
    chmod 600 "$YTDLP_COOKIES_PATH" 2>/dev/null || true

    # Only the header line is inspected, never the contents, so no cookie reaches the logs.
    first_line=""
    IFS= read -r first_line < "$YTDLP_COOKIES_PATH" || true
    case "$first_line" in
        "# Netscape HTTP Cookie File"*)
            echo "[entrypoint] Cookies materialised from YTDLP_COOKIES_CONTENT at $YTDLP_COOKIES_PATH" ;;
        *)
            echo "[entrypoint] WARNING: the cookies content does not start with the Netscape header."
            echo "[entrypoint] WARNING: yt-dlp will most likely reject it; expected a Netscape export." ;;
    esac
}

start_pot_provider() {
    if [ "${YTDLP_POT_ENABLED:-true}" != "true" ]; then
        echo "[entrypoint] PO Token provider disabled by configuration"
        return 0
    fi

    if [ ! -f "$POT_PROVIDER_HOME/server/build/main.js" ]; then
        echo "[entrypoint] PO Token provider not present, continuing without it"
        return 0
    fi

    echo "[entrypoint] Starting PO Token provider"
    ( cd "$POT_PROVIDER_HOME/server" \
        && exec node build/main.js --port "$POT_PROVIDER_PORT" --host "$POT_PROVIDER_HOST" \
    ) > "$POT_PROVIDER_LOG" 2>&1 &
    POT_PROVIDER_PID=$!

    attempts=0
    while [ $attempts -lt 30 ]; do
        if node -e "
const http = require('http');
const req = http.request({
    host: process.argv[1],
    port: Number(process.argv[2]),
    path: '/ping',
    timeout: 2000
}, (res) => process.exit(res.statusCode && res.statusCode < 500 ? 0 : 1));
req.on('error', () => process.exit(1));
req.on('timeout', () => { req.destroy(); process.exit(1); });
req.end();
" "$POT_PROVIDER_HOST" "$POT_PROVIDER_PORT" > /dev/null 2>&1; then
            echo "[entrypoint] PO Token provider is ready (pid $POT_PROVIDER_PID)"
            return 0
        fi

        if ! kill -0 "$POT_PROVIDER_PID" 2>/dev/null; then
            echo "[entrypoint] PO Token provider exited unexpectedly:"
            cat "$POT_PROVIDER_LOG" || true
            return 0
        fi

        attempts=$((attempts + 1))
        sleep 1
    done

    echo "[entrypoint] PO Token provider did not become ready, continuing without it"
    return 0
}

materialise_cookies
start_pot_provider

if [ ! -f "$APP_JAR" ]; then
    echo "[entrypoint] ERROR: application jar not found at $APP_JAR"
    echo "[entrypoint] Contents of $APP_DIR:"
    ls -la "$APP_DIR" || true
    exit 1
fi

echo "[entrypoint] Starting application from $APP_JAR"
cd "$APP_DIR"
exec java $JAVA_OPTS -jar "$APP_JAR"
