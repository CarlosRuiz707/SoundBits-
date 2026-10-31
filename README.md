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


## SoundBits 2.1 — Audio, podcasts y descargas

- Añadida búsqueda de programas en el catálogo de Apple Podcasts/iTunes.
- Consulta de episodios desde el endpoint de episodios de iTunes cuando está disponible y lectura de RSS cuando el feed está expuesto por CORS.
- Reproducción web de episodios y guardado de posición en almacenamiento local; el botón de descarga solicita guardar el archivo cuando el servidor lo permite.
- Se conserva la apariencia visual existente; Podcasts se integra dentro de la pantalla Buscar.
- LRCLIB ya está integrado para letras sincronizadas y búsqueda por artista/título.
- El catálogo musical online continúa limitado a contenido con licencia identificada como compatible.

### Límites y siguientes integraciones

Spotify requiere OAuth/credenciales para endpoints que no sean públicos; no se simula una integración autenticada. SponsorBlock y Return YouTube Dislike solo tienen sentido para contenido de YouTube con identificadores de vídeo, y esta versión base no reproduce YouTube. No se afirma que esas integraciones estén activas. Las API de Voxtrona no se han podido identificar como endpoints públicos en el código fuente entregado; deben configurarse con sus rutas y autenticación oficiales antes de invocarlas.

### SoundBits 2.1 — streaming y podcasts en segundo plano
- La reproducción de episodios reutiliza el reproductor de audio principal, por lo que aprovecha la sesión multimedia/notificación y el servicio Android ya configurados.
- Se guarda periódicamente la posición de los episodios en `localStorage` y se intenta restaurar al volver a reproducirlos.
- El streaming online de música sigue usando el catálogo de Internet Archive filtrado por licencias compatibles y el proxy con soporte HTTP Range; no se presenta como acceso al catálogo comercial de Voxtrona.
- El ZIP de Voxtrona proporcionado contiene un sitio web de presentación y enlaces de descarga, pero no se encontraron endpoints de streaming ni documentación de API en el código inspeccionado. Para integrar Voxtrona directamente hace falta su endpoint oficial de búsqueda/resolución de audio y el mecanismo de autorización, si lo requiere. No se debe inventar una API ni extraer streams de fuentes protegidas.
