# Documentación de la API — YOUMP3API

API REST desarrollada con **Spring Boot** que permite buscar videos en YouTube y extraer su audio en formato MP3, utilizando `yt-dlp` y `ffmpeg` como herramientas externas del sistema.

- **Nombre de la aplicación:** `YOUMP3API`
- **Puerto:** `8088` (configurable vía `PORT`)
- **Base URL local:** `http://localhost:8088`
- **Java:** 17
- **Formato de intercambio:** JSON (`application/json`)

---

## Índice

1. [Arquitectura e interacción entre clases](#1-arquitectura-e-interacción-entre-clases)
2. [Endpoints](#2-endpoints)
3. [Manejo global de excepciones](#3-manejo-global-de-excepciones)
4. [Dependencias externas, configuración y requisitos](#4-dependencias-externas-configuración-y-requisitos)

---

## 1. Arquitectura e interacción entre clases

La aplicación sigue una arquitectura en capas modular y desacoplada de Spring Boot: **Controller → Service → Provider / Registry → Repository**, con DTOs para la entrada/salida y excepciones de dominio traducidas a respuestas HTTP por un handler global.

```
Cliente HTTP
    │
    ▼
AudioExtractionController / AudioServerController  (@RestController)
    │  deserializa el request y delega
    ▼
AudioExtractionService     (@Service)
    │  valida/normaliza entrada y resuelve proveedor
    ▼
AudioServerRegistry        (@Component)
    │  resuelve AudioServerProvider según "serverId"
    ├──► YouTubeAudioServerProvider  (para Render/Cloud con cookies)
    └──► LocalAudioServerProvider    (para Red Local sin cookies)
            │
            ▼
YoutubeAudioRepository (yt-dlp CLI) / YoutubeDataApiSearchRepository
```

### Proveedores de audio (`com.msservices.app.provider`)

La interfaz `AudioServerProvider` define el contrato para los backends de búsqueda y extracción. `AudioServerRegistry` expone los proveedores disponibles y resuelve cuál atiende cada petición:

- **`YouTubeAudioServerProvider` (`id: "youtube"`)**:
  - `displayName`: `"YouTube"`
  - `supportsUserCookies`: `true`
  - Orientado al entorno en la nube (Render). Dado que las IPs de centros de datos sufren bloqueo por reputación en YouTube, requiere cookies de sesión (estáticas o enviadas por el usuario).
- **`LocalAudioServerProvider` (`id: "local"`)**:
  - `displayName`: `"Red Local"`
  - `supportsUserCookies`: `false`
  - Alias soportados: `local_network`, `local-network`, `local_api`, `local-api`, `red_local`, `red-local`.
  - Diseñado para su ejecución en red local/residencial. Utiliza el mismo servicio de `yt-dlp` del proyecto, pero **no requiere cookies**, ya que las IPs residenciales no sufren los bloqueos que afectan a los datacenters. Las cookies enviadas son ignoradas de forma transparente sin requerir consentimientos innecesarios.

---

## 2. Endpoints

### 2.1 Listar servidores de audio disponibles — `GET /api/servers`

Devuelve la lista de servidores de audio configurados, indicando el servidor por defecto y si soporta/requiere cookies.

**Request**
```http
GET http://localhost:8088/api/servers
Accept: application/json
```

**Respuesta exitosa — `200 OK`**
```json
[
  {
    "id": "youtube",
    "displayName": "YouTube",
    "supportsUserCookies": true,
    "defaultServer": true
  },
  {
    "id": "local",
    "displayName": "Red Local",
    "supportsUserCookies": false,
    "defaultServer": false
  }
]
```

---

### 2.2 Buscar videos — `POST /api/audios/search`

Busca videos coincidentes con el nombre proporcionado y devuelve resultados rankeados por relevancia/calidad.

**Request**
```http
POST http://localhost:8088/api/audios/search
Content-Type: application/json
```

```json
{
  "videoName": "never gonna give you up",
  "serverId": "local"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `videoName` | string | Sí | Término de búsqueda (3–150 caracteres tras `trim()`). |
| `serverId` | string | No | Identificador del servidor (`youtube`, `local`, `local_network`, etc.). Si se omite, se usa el servidor por defecto (`youtube`). |
| `cookies` | string | No | Contenido Netscape cookies.txt para peticiones a servidores que las requieran. |
| `dataUsageConsent` | boolean | No | Consentimiento del usuario para el uso de cookies. Requerido solo si se envían cookies a servidores que las soportan. |

**Respuesta exitosa — `200 OK`**
```json
{
  "success": true,
  "message": "Resultados obtenidos.",
  "results": [
    {
      "videoId": "dQw4w9WgXcQ",
      "title": "Rick Astley - Never Gonna Give You Up",
      "author": "Rick Astley",
      "durationSeconds": 213,
      "viewCount": 1700000000
    }
  ]
}
```

---

### 2.3 Extraer audio — `POST /api/audios/extract`

Descarga el audio del video seleccionado y lo devuelve en **MP3 codificado en Base64**.

**Request**
```http
POST http://localhost:8088/api/audios/extract
Content-Type: application/json
```

```json
{
  "videoName": "never gonna give you up",
  "videoId": "dQw4w9WgXcQ",
  "serverId": "local"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `videoName` | string | Sí* | Nombre del video. (*Opcional si se especifica `videoId`). |
| `videoId` | string | No | ID de YouTube del video. Si se especifica, se extrae directamente; si no, se toma el primer resultado del `videoName`. |
| `serverId` | string | No | Servidor a utilizar (`local`, `youtube`, etc.). |
| `cookies` | string | No | Cookies de usuario Netscape opcionales (solo aplicables a servidores que las soporten). |
| `dataUsageConsent` | boolean | No | Consentimiento de cookies cuando se envían cookies a servidores remotos. |

**Respuesta exitosa — `200 OK`**
```json
{
  "success": true,
  "message": "Audio extraido correctamente.",
  "videoTitle": "Rick Astley - Never Gonna Give You Up (Official Music Video)",
  "fileName": "dQw4w9WgXcQ.mp3",
  "contentType": "audio/mpeg",
  "audioBase64": "<contenido binario del MP3 en Base64>"
}
```

---

### 2.4 Estado del servicio — `GET /api/health`

Endpoint de diagnóstico y verificación de salud de la aplicación.

```http
GET http://localhost:8088/api/health
```

**Respuesta exitosa — `200 OK`**
```json
{
  "status": "UP",
  "cookies": "missing",
  "userCookies": "enabled",
  "poToken": "disabled",
  "proxy": "not-configured",
  "search": "data-api",
  "playerClients": ["tv_embedded", "web_safari", "ios", "android_vr"]
}
```

---

## 3. Manejo global de excepciones

Capturado de forma centralizada por `GlobalExceptionHandler` (`@RestControllerAdvice`). Todos los errores retornan un objeto JSON uniforme con `success: false`.

### Jerarquía de excepciones

```
RuntimeException
├── InvalidVideoSearchException        → 400 BAD_REQUEST
├── CookieConsentRequiredException     → 400 BAD_REQUEST
├── InvalidCookieException             → 400 BAD_REQUEST
├── UnsupportedAudioServerException    → 400 BAD_REQUEST
├── AudioExtractionException           → 502 BAD_GATEWAY
├── AudioServerUnavailableException    → 503 SERVICE_UNAVAILABLE
├── YoutubeAccessBlockedException      → 503 SERVICE_UNAVAILABLE
├── YoutubeToolUnavailableException    → 503 SERVICE_UNAVAILABLE
└── Exception (genérica)               → 500 INTERNAL_SERVER_ERROR
```

---

## 4. Dependencias externas, configuración y requisitos

- **yt-dlp** y **ffmpeg** deben estar instalados y disponibles en el `PATH` del sistema.
- Los archivos temporales se limpian de manera estricta en el bloque `finally` tras la extracción.

### Diferencias entre ejecución Local y Servidor Render

| Propiedad | Red Local (`application.properties`) | Servidor Render (`render.yaml`) | Motivo |
|---|---|---|---|
| `ytdlp.cookies-required` | `false` | `true` | En red local, las IPs residenciales no sufren bloqueos de YouTube; en Render (datacenter), YouTube bloquea sin cookies. |
| `audio.server.default` | `youtube` (o `local` configurable) | `youtube` | Selección del servidor por defecto si el cliente no envía `serverId`. |
| Cookies de usuario | Opcionales / No requeridas | Requeridas si no hay cookies estáticas en el servidor | En Render se requiere sesión válida de YouTube para evitar HTTP 403 / Bot check. |

### Ejecución local

```bash
./mvnw spring-boot:run
# API disponible en http://localhost:8088
```
