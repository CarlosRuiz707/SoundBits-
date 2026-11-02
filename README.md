# SoundBits v0.1.8

Reproductor musical Android con biblioteca local y búsqueda integrada de un catálogo online de audio con licencias Creative Commons compatibles.

## Cambios de esta versión
- Eliminada la búsqueda y reproducción de YouTube y el campo de API key.
- La búsqueda consulta primero la biblioteca local. Solo cuando no hay coincidencias, consulta Internet Archive.
- Se limita el catálogo online a elementos con licencias CC BY, CC BY-SA, CC0 o Public Domain Mark identificadas en sus metadatos.
- Reproducción online mediante el mismo elemento de audio y controles multimedia existentes.
- Proxy de audio del mismo origen con soporte de peticiones HTTP Range para búsqueda de posición.
- Gesto horizontal sobre la portada del reproductor: izquierda = siguiente; derecha = anterior.
- Se conserva la firma de beta existente para no romper la actualización de instalaciones de prueba. No publiques el archivo de firma ni sus credenciales; migra a secretos de CI antes de abrir el repositorio.

## Consideraciones del catálogo
Internet Archive es un catálogo de archivo, no un sustituto del catálogo comercial de Spotify. La disponibilidad, calidad y metadatos dependen de cada elemento. Revisa la licencia del elemento antes de distribuir comercialmente la app o integrar monetización.

## Novedades 1.9
- Diseño estilo Spotify (verde, fondo oscuro, navegación Inicio / Buscar / Tu biblioteca / Podcasts, ajustes con el engranaje de arriba).
- Guardar sin conexión: botón ⤓ en canciones (Internet Archive y podcasts) y «Guardar todo» en playlists. Pestaña «Descargas» en la biblioteca.
- Caché temporal (Ajustes > Sin conexión): lo que escuchas en línea con Wi‑Fi se guarda N días y se borra solo; al reproducirlo de nuevo con Wi‑Fi se vuelve a descargar.
- Podcasts: búsqueda (directorio de Apple) y feeds RSS, episodios con descarga sin conexión.
- Fuentes en línea: Internet Archive, YouTube (API oficial + reproductor incrustado), Mixcloud y Audiomack (reproductores oficiales, sin descargas).
- Se quitó la pantalla de inicio animada.

