"""SoundBits API  ·  FastAPI + ytmusicapi (metadatos) + yt-dlp (enlace de audio directo).

    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000

Contrato (compatible con tu ytmusic_api_server.py para /search):
    GET /search?q=...&type=songs&limit=20 -> [{id,title,artist,album,art,duration}]
    GET /stream/{video_id}                -> {url, mime, expires_in}
"""
import re
import time
from urllib.parse import parse_qs, urlparse

import yt_dlp
from cachetools import TTLCache
from fastapi import FastAPI, HTTPException, Query
from ytmusicapi import YTMusic

app = FastAPI(title="SoundBits API")
ytm = YTMusic()

VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
_streams: TTLCache = TTLCache(maxsize=512, ttl=20 * 60)  # evita re-extraer la misma pista

YDL_OPTS = {
    "format": "bestaudio[ext=m4a]/bestaudio/best",  # m4a (AAC) primero: ExoPlayer lo reproduce en todo Android
    "quiet": True,
    "no_warnings": True,
    "noplaylist": True,
    "skip_download": True,
}
MIME = {"m4a": "audio/mp4", "mp4": "audio/mp4", "webm": "audio/webm", "opus": "audio/ogg"}


def _normalize(item: dict) -> dict | None:
    vid = item.get("videoId")
    if not vid:
        return None
    artists = item.get("artists") or []
    thumbs = item.get("thumbnails") or []
    return {
        "id": vid,
        "title": item.get("title") or "Sin título",
        "artist": (artists[0].get("name") if artists else None) or "YouTube",
        "album": (item.get("album") or {}).get("name") or "",
        "art": thumbs[-1].get("url", "") if thumbs else "",
        "duration": item.get("duration_seconds") or 0,
    }


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/search")
def search(q: str = Query(..., min_length=1), type: str = "songs", limit: int = Query(20, ge=1, le=30)):
    # `def` (no async): FastAPI lo ejecuta en un threadpool, así no bloquea el event loop.
    results = ytm.search(q, filter="songs", limit=limit)
    return [t for t in map(_normalize, results) if t]


@app.get("/stream/{video_id}")
def stream(video_id: str):
    if not VIDEO_ID.match(video_id):
        raise HTTPException(400, "video_id inválido")
    if (hit := _streams.get(video_id)) is not None:
        return hit
    try:
        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            info = ydl.extract_info(f"https://music.youtube.com/watch?v={video_id}", download=False)
    except Exception as exc:  # yt-dlp lanza DownloadError variados
        raise HTTPException(502, f"No se pudo extraer el audio: {exc}")

    url = info.get("url")
    if not url:
        raise HTTPException(502, "yt-dlp no devolvió un enlace directo")
    expire = parse_qs(urlparse(url).query).get("expire", [None])[0]
    expires_in = max(int(expire) - int(time.time()), 60) if expire and expire.isdigit() else 3600
    payload = {"url": url, "mime": MIME.get(info.get("ext", ""), "audio/mp4"), "expires_in": expires_in}
    _streams[video_id] = payload
    return payload
