# SoundBits — adaptador de API intercambiable

La aplicación puede mantener una sola interfaz de resultados. El backend normaliza la respuesta de cada proveedor al mismo formato, así puedes cambiar la API sin rehacer el diseño.

## Modo de demostración (predeterminado)

```env
API_PROVIDER=revanced_mock
API_BASE_URL=https://api.revanced.app/v4
```

`GET /search?q=...` devuelve un resultado marcado como `demo: true`. Esto permite probar la ruta de la aplicación y el formato de datos sin fingir que el dominio inventado existe ni enviar peticiones que fallarán.

## API hipotética del usuario

Cuando tengas un servicio real detrás de esa dirección, configura:

```env
API_PROVIDER=revanced
API_BASE_URL=https://api.revanced.app/v4
```

El backend hará `GET https://api.revanced.app/v4/search?q=consulta`. La ruta es un contrato teórico, no una API verificada. El servicio debería responder con `items` o `results`, por ejemplo:

```json
{"items":[{"id":"VIDEO_ID","title":"Nombre de la canción","artist":"Artista","thumbnail":"https://example.com/cover.jpg","url":"https://www.youtube.com/watch?v=VIDEO_ID","duration":0}]}
```

Se aceptan también los campos alternativos `videoId`, `name`, `channelTitle`, `art` y `thumbnailUrl`.

## Cambiar a YouTube Data API v3

Configura estas variables en el servidor:

```env
API_PROVIDER=youtube_data_api
YOUTUBE_DATA_API_BASE_URL=https://www.googleapis.com/youtube/v3
YOUTUBE_DATA_API_KEY=TU_CLAVE_DE_GOOGLE_CLOUD
```

El backend llama al endpoint `/search` oficial y normaliza los metadatos al mismo formato que SoundBits usa. Habilita YouTube Data API v3 en Google Cloud y restringe la clave. No pongas la clave en `index.html` ni en un repositorio público.

## Endpoints del backend

- `GET /health`: estado del backend y proveedor seleccionado.
- `GET /search?q=Daft%20Punk`: búsqueda mediante el proveedor elegido.
- `GET /artist/<texto>` y `GET /album/<texto>`: rutas del proveedor ytmusicapi existente.
- `POST /playlist/create` y `POST /playlist/add`: requieren OAuth de YouTube Music.

## Límites

La YouTube Data API devuelve metadatos de vídeos, no URLs directas de audio. Para reproducir contenido usa un reproductor permitido por YouTube o una fuente que autorice la reproducción. El modo `revanced_mock` es solo demostrativo y no reproduce una canción real.
