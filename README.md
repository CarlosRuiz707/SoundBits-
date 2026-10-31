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
