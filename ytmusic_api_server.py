import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
import urllib.error
from flask import Flask, jsonify, request
from ytmusicapi import YTMusic

app = Flask(__name__)

OAUTH_FILE = os.path.join(os.path.dirname(__file__), 'oauth.json')

# Adaptador intercambiable de API. Por defecto se conserva ytmusicapi.
# Para probar la ruta inventada: API_PROVIDER=revanced y API_BASE_URL=https://api.revanced.app/v4
API_PROVIDER = os.environ.get('API_PROVIDER', 'ytmusicapi').strip().lower()
API_BASE_URL = os.environ.get('API_BASE_URL', 'https://api.revanced.app/v4').rstrip('/')
YOUTUBE_DATA_API_KEY = os.environ.get('YOUTUBE_DATA_API_KEY', '').strip()
YOUTUBE_DATA_API_BASE_URL = os.environ.get(
    'YOUTUBE_DATA_API_BASE_URL', 'https://www.googleapis.com/youtube/v3'
).rstrip('/')


def external_search(query):
    """Busca con proveedor HTTP configurable y normaliza la respuesta."""
    if API_PROVIDER == 'revanced':
        url = API_BASE_URL + '/search?' + urllib.parse.urlencode({'q': query})
        req = urllib.request.Request(url, headers={'Accept': 'application/json', 'User-Agent': 'SoundBits/1.0'})
        with urllib.request.urlopen(req, timeout=12) as response:
            payload = json.loads(response.read().decode('utf-8'))
        items = payload.get('items', payload.get('results', [])) if isinstance(payload, dict) else payload
        normalized = []
        for item in items or []:
            if not isinstance(item, dict):
                continue
            video_id = item.get('videoId') or item.get('id')
            if isinstance(video_id, dict):
                video_id = video_id.get('videoId')
            normalized.append({
                'id': video_id,
                'title': item.get('title') or item.get('name') or 'Sin título',
                'artist': item.get('artist') or item.get('channelTitle') or item.get('channel') or 'YouTube',
                'album': item.get('album') or 'YouTube',
                'art': item.get('art') or item.get('thumbnail') or item.get('thumbnailUrl') or '',
                'type': item.get('type') or 'song',
                'provider': 'revanced',
                'source': 'youtube',
                'url': item.get('url') or (f"https://www.youtube.com/watch?v={video_id}" if video_id else ''),
                'duration': item.get('duration') or 0,
            })
        return normalized

    if API_PROVIDER == 'youtube_data_api':
        if not YOUTUBE_DATA_API_KEY:
            raise RuntimeError('Falta YOUTUBE_DATA_API_KEY en las variables de entorno.')
        params = urllib.parse.urlencode({
            'part': 'snippet', 'q': query, 'type': 'video', 'maxResults': 10,
            'videoEmbeddable': 'true', 'key': YOUTUBE_DATA_API_KEY,
        })
        url = YOUTUBE_DATA_API_BASE_URL + '/search?' + params
        req = urllib.request.Request(url, headers={'Accept': 'application/json'})
        with urllib.request.urlopen(req, timeout=12) as response:
            payload = json.loads(response.read().decode('utf-8'))
        normalized = []
        for item in payload.get('items', []):
            snippet = item.get('snippet') or {}
            video_id = (item.get('id') or {}).get('videoId')
            thumbnails = snippet.get('thumbnails') or {}
            thumb = thumbnails.get('high') or thumbnails.get('medium') or thumbnails.get('default') or {}
            normalized.append({
                'id': video_id,
                'title': snippet.get('title') or 'Sin título',
                'artist': snippet.get('channelTitle') or 'YouTube',
                'album': 'YouTube',
                'art': thumb.get('url', ''),
                'type': 'song',
                'provider': 'youtube_data_api',
                'source': 'youtube',
                'url': f'https://www.youtube.com/watch?v={video_id}' if video_id else '',
                'duration': 0,
            })
        return normalized

    raise RuntimeError(f'Proveedor HTTP no compatible: {API_PROVIDER}')


def get_yt(require_auth=False):
    # La búsqueda pública funciona sin OAuth. Las operaciones que modifican
    # la biblioteca del usuario sí necesitan oauth.json.
    if os.path.exists(OAUTH_FILE):
        return YTMusic(OAUTH_FILE)
    if require_auth:
        raise RuntimeError(
            'Esta operación requiere oauth.json. Configura OAuth de YouTube Music.'
        )
    return YTMusic()


def thumb_url(item):
    thumbs = item.get('thumbnails') or []
    if isinstance(thumbs, list):
        for thumb in reversed(thumbs):
            if isinstance(thumb, dict) and thumb.get('url'):
                return thumb['url']
    return ''


def normalize_song(item):
    artists = item.get('artists') or [{'name': 'YouTube'}]
    album = item.get('album') or {}
    return {
        'id': item.get('videoId'),
        'title': item.get('title') or 'Sin título',
        'artist': artists[0].get('name') if artists else 'YouTube',
        'album': album.get('name') or 'YouTube',
        'art': thumb_url(item),
        'type': 'song',
        'provider': 'youtube',
        'source': 'youtube',
        'url': f"https://music.youtube.com/watch?v={item.get('videoId')}",
        'duration': item.get('duration') or 0,
    }


def normalize_album(item):
    return {
        'id': item.get('browseId'),
        'title': item.get('title') or 'Álbum',
        'artist': (item.get('artists') or [{}])[0].get('name') or 'YouTube',
        'album': item.get('title') or 'Álbum',
        'art': thumb_url(item),
        'type': 'album',
        'provider': 'youtube',
        'source': 'youtube',
        'url': f"https://music.youtube.com/browse/{item.get('browseId')}",
    }


def normalize_artist(item):
    return {
        'id': item.get('browseId'),
        'title': item.get('artist') or item.get('name') or 'Artista',
        'artist': item.get('artist') or item.get('name') or 'YouTube',
        'album': 'YouTube',
        'art': thumb_url(item),
        'type': 'artist',
        'provider': 'youtube',
        'source': 'youtube',
    }


def create_playlist_from_query(title, description, query, limit=1):
    yt = get_yt()
    playlist_id = yt.create_playlist(title, description)

    if not query:
        return {
            'playlistId': playlist_id,
            'title': title,
            'description': description,
            'added': []
        }

    search_results = yt.search(query, filter='songs', limit=limit)
    if not search_results:
        raise ValueError(f'No se encontraron resultados para: {query}')

    video_id = search_results[0].get('videoId')
    if not video_id:
        raise ValueError(f'No se pudo obtener videoId para: {query}')

    yt.add_playlist_items(playlist_id, [video_id])
    return {
        'playlistId': playlist_id,
        'title': title,
        'description': description,
        'videoId': video_id,
        'query': query,
        'added': [video_id]
    }


@app.get('/health')
def health():
    if API_PROVIDER in ('revanced', 'youtube_data_api'):
        return jsonify({
            'ok': True,
            'provider': API_PROVIDER,
            'baseUrl': API_BASE_URL if API_PROVIDER == 'revanced' else YOUTUBE_DATA_API_BASE_URL,
            'apiKeyConfigured': bool(YOUTUBE_DATA_API_KEY) if API_PROVIDER == 'youtube_data_api' else None,
            'note': 'La disponibilidad remota se confirma al ejecutar una búsqueda.'
        })
    try:
        get_yt()
        return jsonify({'ok': True, 'provider': 'ytmusicapi', 'message': 'YouTube Music disponible', 'oauthConfigured': os.path.exists(OAUTH_FILE)})
    except Exception as exc:
        return jsonify({'ok': False, 'provider': 'ytmusicapi', 'error': str(exc)}), 503


@app.get('/search')
def search():
    query = request.args.get('q', '').strip()
    if not query:
        return jsonify({'error': 'Falta el parámetro q'}), 400

    if API_PROVIDER in ('revanced', 'youtube_data_api'):
        try:
            return jsonify(external_search(query))
        except urllib.error.HTTPError as exc:
            return jsonify({'error': f'La API externa respondió HTTP {exc.code}', 'provider': API_PROVIDER}), exc.code
        except Exception as exc:
            return jsonify({'error': str(exc), 'provider': API_PROVIDER}), 502

    try:
        yt = get_yt()
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    results = []

    try:
        songs = yt.search(query, filter='songs', limit=10)
        results.extend(normalize_song(item) for item in songs)
    except Exception:
        pass

    try:
        albums = yt.search(query, filter='albums', limit=5)
        results.extend(normalize_album(item) for item in albums)
    except Exception:
        pass

    try:
        artists = yt.search(query, filter='artists', limit=5)
        results.extend(normalize_artist(item) for item in artists)
    except Exception:
        pass

    return jsonify(results[:20])


@app.post('/playlist/create')
def create_playlist():
    try:
        yt = get_yt(require_auth=True)
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    payload = request.get_json(silent=True) or {}
    title = (payload.get('title') or 'Tunebox Playlist').strip() or 'Tunebox Playlist'
    description = (payload.get('description') or 'Creada desde Tunebox').strip()

    try:
        playlist_id = yt.create_playlist(title, description)
        return jsonify({
            'playlistId': playlist_id,
            'title': title,
            'description': description,
        })
    except Exception as exc:
        return jsonify({'error': f'Error creando playlist: {exc}'}), 500


@app.post('/playlist/add')
def add_playlist_items():
    try:
        yt = get_yt(require_auth=True)
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    payload = request.get_json(silent=True) or {}
    playlist_id = payload.get('playlistId') or payload.get('playlist_id')
    video_id = payload.get('videoId') or payload.get('video_id')
    query = (payload.get('query') or payload.get('q') or '').strip()

    if not playlist_id:
        return jsonify({'error': 'Falta playlistId'}), 400

    try:
        if video_id:
            ids = [video_id]
        elif query:
            search_results = yt.search(query, filter='songs', limit=1)
            if not search_results:
                return jsonify({'error': f'No se encontraron resultados para: {query}'}), 404
            ids = [search_results[0].get('videoId')]
            if not ids[0]:
                return jsonify({'error': f'No se pudo obtener videoId para: {query}'}), 400
        else:
            return jsonify({'error': 'Falta videoId o query'}), 400

        yt.add_playlist_items(playlist_id, ids)
        return jsonify({
            'playlistId': playlist_id,
            'videoId': ids[0],
            'added': ids,
        })
    except Exception as exc:
        return jsonify({'error': f'Error agregando a la playlist: {exc}'}), 500


@app.post('/playlist/create-and-add')
def create_and_add_playlist():
    payload = request.get_json(silent=True) or {}
    title = (payload.get('title') or 'Tunebox Playlist').strip() or 'Tunebox Playlist'
    description = (payload.get('description') or 'Creada desde Tunebox').strip()
    query = (payload.get('query') or payload.get('q') or '').strip()

    if not query:
        return jsonify({'error': 'Falta el parámetro query'}), 400

    try:
        result = create_playlist_from_query(title, description, query)
        return jsonify(result)
    except Exception as exc:
        return jsonify({'error': str(exc)}), 500


@app.get('/artist/<path:query>')
def artist(query):
    try:
        yt = get_yt()
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    try:
        matches = yt.search(query, filter='artists')
        if not matches:
            return jsonify({'error': 'No se encontró el artista'}), 404

        artist_id = matches[0].get('browseId')
        artist_data = yt.get_artist(artist_id)

        songs = []
        for track in artist_data.get('songs', [])[:10]:
            songs.append(normalize_song(track))

        albums = []
        for album in artist_data.get('albums', [])[:10]:
            albums.append(normalize_album(album))

        return jsonify({
            'id': artist_id,
            'title': artist_data.get('name') or matches[0].get('artist') or query,
            'artist': artist_data.get('name') or query,
            'album': 'YouTube',
            'art': artist_data.get('header', {}).get('image', [{}])[-1].get('url') or '',
            'type': 'artist',
            'provider': 'youtube',
            'source': 'youtube',
            'songs': songs,
            'albums': albums,
        })
    except Exception as exc:
        return jsonify({'error': f'Error buscando artista: {exc}'}), 500


@app.get('/album/<path:query>')
def album(query):
    try:
        yt = get_yt()
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    try:
        matches = yt.search(query, filter='albums')
        if not matches:
            return jsonify({'error': 'No se encontró el álbum'}), 404

        album_id = matches[0].get('browseId')
        album_data = yt.get_album(album_id)

        tracks = []
        for track in album_data.get('tracks', []):
            tracks.append(normalize_song(track))

        return jsonify({
            'id': album_id,
            'title': album_data.get('title') or matches[0].get('title') or query,
            'artist': (album_data.get('artists') or [{}])[0].get('name') or 'YouTube',
            'album': album_data.get('title') or query,
            'art': thumb_url(album_data) or '',
            'type': 'album',
            'provider': 'youtube',
            'source': 'youtube',
            'tracks': tracks,
        })
    except Exception as exc:
        return jsonify({'error': f'Error buscando álbum: {exc}'}), 500


def run_cli():
    parser = argparse.ArgumentParser(description='Busca una canción y la añade a una playlist de YouTube Music.')
    parser.add_argument('--query', '-q', help='Texto a buscar, por ejemplo: "Oasis Wonderwall"')
    parser.add_argument('--title', default='Tunebox Playlist', help='Nombre de la playlist')
    parser.add_argument('--description', default='Creada desde Tunebox', help='Descripción de la playlist')
    parser.add_argument('--limit', type=int, default=1, help='Número de resultados a probar (1 por defecto)')
    args = parser.parse_args()

    if not args.query:
        parser.print_help()
        return 1

    try:
        result = create_playlist_from_query(args.title, args.description, args.query, args.limit)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(f'Error: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    if any(arg in sys.argv[1:] for arg in ('--query', '-q')):
        raise SystemExit(run_cli())
    app.run(host='0.0.0.0', port=int(os.environ.get('PORT', 3000)), debug=False)
