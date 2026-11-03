# Tunebox — servidor de YouTube Music (ytmusicapi)

Servidor Flask (`ytmusic_api_server.py`) que usa [ytmusicapi](https://github.com/sigma67/ytmusicapi).
La app lo usa para **buscar en YouTube Music sin clave de Google** y para **guardar canciones en tus playlists**.

- Buscar, artistas y álbumes: funcionan **sin iniciar sesión**.
- Ver/crear playlists y añadir canciones: requieren OAuth de tu cuenta.

## 1. Instalar y probar la búsqueda

```bash
pip install -r requirements.txt
python ytmusic_api_server.py        # http://localhost:3000/search?q=oasis%20wonderwall&type=songs
```

## 2. Activar las playlists (OAuth)

Desde noviembre de 2024 YouTube Music exige tus propias credenciales de Google:

1. En [Google Cloud Console](https://console.cloud.google.com/) crea un proyecto y habilita **YouTube Data API v3**.
2. Credenciales → **ID de cliente de OAuth** → tipo **TVs y dispositivos de entrada limitada**.
   Copia el *client id* y el *client secret*. En la pantalla de consentimiento añade tu cuenta como usuario de prueba.
3. Genera `oauth.json`:

```bash
ytmusicapi oauth --client-id TU_ID --client-secret TU_SECRET --file oauth.json
```

4. Variables de entorno (ver `.env.example`):

```bash
export YTM_CLIENT_ID=TU_ID
export YTM_CLIENT_SECRET=TU_SECRET
export TUNEBOX_API_KEY=una-clave-larga-y-aleatoria
python ytmusic_api_server.py
```

Importante: ya **no** basta con `YTMusic('oauth.json')`. El código correcto es:

```python
from ytmusicapi import YTMusic, OAuthCredentials

yt = YTMusic('oauth.json', oauth_credentials=OAuthCredentials(client_id=ID, client_secret=SECRET))
playlist_id = yt.create_playlist('test', 'test description')
results = yt.search('Oasis Wonderwall', filter='songs')
yt.add_playlist_items(playlist_id, [results[0]['videoId']])
```

No subas `oauth.json` ni las claves a un repositorio público. En hostings sin disco persistente pega el contenido de `oauth.json` en `YTM_OAUTH_JSON`.

## 3. Conectar la app

Despliega el servidor con **HTTPS** (el `Procfile` ya usa gunicorn). En la app: *Ajustes → YouTube Music (servidor)*:
la dirección (`https://tu-servidor`) y la `TUNEBOX_API_KEY`. Desde ese momento la búsqueda de YouTube usa ytmusicapi y cada resultado
tiene un botón ＋ para guardarlo en una de tus playlists o en una nueva.

## Endpoints

| Ruta | Descripción | Requiere clave |
|---|---|---|
| `GET /health` | Estado (búsqueda, OAuth, protección) | no |
| `GET /search?q=…&type=songs\|all&limit=10` | Búsqueda normalizada | no |
| `GET /artist/<texto>` · `GET /album/<texto>` | Artista / álbum | no |
| `GET /playlists` | Tus playlists de la biblioteca | OAuth |
| `POST /playlist/create` `{title, description}` | Crea una playlist | `X-Tunebox-Key` |
| `POST /playlist/add` `{playlistId, videoId \| videoIds \| query}` | Añade canciones | `X-Tunebox-Key` |
| `POST /playlist/create-and-add` `{title, query}` | Crea y añade la primera coincidencia | `X-Tunebox-Key` |

Las acciones que modifican tu cuenta quedan bloqueadas mientras no definas `TUNEBOX_API_KEY`, para que nadie más pueda tocar tus playlists.
