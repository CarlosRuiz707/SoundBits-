# SoundBits 2.0

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


## SoundBits 2.0 — evolución visual y eficiencia

- Se conserva la arquitectura Android existente (Java + WebView + servicio multimedia).
- Se incorpora una capa visual Liquid Glass moderada, con degradados translúcidos y bordes sutiles.
- El modo ligero elimina los efectos de transparencia y sombras para dispositivos modestos.
- El analizador visual ya no ejecuta un ciclo `requestAnimationFrame` permanente: solo se actualiza cuando el reproductor a pantalla completa está visible y la app está en primer plano.
- Se incrementa `versionCode` a 10 y `versionName` a 2.0 para distinguir esta entrega de la 1.8.

### Alcance de esta entrega

Esta versión es una primera integración segura de diseño y optimización. No afirma incorporar todavía funciones no presentes en el código base, como reproducción gapless/crossfade real, audio espacial con seguimiento de cabeza, FLAC lossless garantizado, sincronización con cuentas externas, traducción IA o SponsorBlock. Estas requieren implementación y validación separadas.

### Firma de Android

Para actualizar una instalación existente, conserva de forma privada la misma clave de firma que usaste para publicar SoundBits 1.8. No compartas `tunebox.jks` ni sus contraseñas. Antes de distribuir, migra las credenciales de firma a secretos locales o de CI; no cambies la clave de firma si necesitas mantener la ruta de actualización.
