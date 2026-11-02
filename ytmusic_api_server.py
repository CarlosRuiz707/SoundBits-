import os
from flask import Flask, jsonify, request
from ytmusicapi import YTMusic

app = Flask(__name__)

OAUTH_FILE = os.path.join(os.path.dirname(__file__), 'oauth.json')


def get_yt():
    if not os.path.exists(OAUTH_FILE):
        raise RuntimeError(
            'No existe oauth.json. Genera la autenticación con: python -m ytmusicapi oauth'
        )
    return YTMusic(OAUTH_FILE)


def thumb_url(item):
    for key in ('high', 'medium', 'default'):
        thumb = (item.get('thumbnails') or [{}])[-1]
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


@app.get('/health')
def health():
    try:
        get_yt()
        return jsonify({'ok': True, 'provider': 'youtube', 'message': 'OAuth de YouTube Music listo'})
    except Exception as exc:
        return jsonify({'ok': False, 'provider': 'youtube', 'error': str(exc)}), 503


@app.get('/search')
def search():
    try:
        yt = get_yt()
    except Exception as exc:
        return jsonify({'error': str(exc)}), 503

    query = request.args.get('q', '').strip()
    if not query:
        return jsonify({'error': 'Falta el parámetro q'}), 400

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


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=int(os.environ.get('PORT', 3000)), debug=False)
