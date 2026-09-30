#!/bin/sh
set -e

POT_PROVIDER_HOME=/opt/bgutil-ytdlp-pot-provider
POT_PROVIDER_PORT="${YTDLP_POT_PROVIDER_PORT:-4416}"
POT_PROVIDER_HOST=127.0.0.1
POT_PROVIDER_LOG=/tmp/pot-provider.log

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
    cd "$POT_PROVIDER_HOME/server"
    node build/main.js --port "$POT_PROVIDER_PORT" --host "$POT_PROVIDER_HOST" > "$POT_PROVIDER_LOG" 2>&1 &
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

start_pot_provider

echo "[entrypoint] Starting application"
exec sh -c "java $JAVA_OPTS -jar app.jar"
