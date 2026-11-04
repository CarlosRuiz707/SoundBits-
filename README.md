# Tunebox

Reproductor musical Android (Java + WebView) con biblioteca local, búsqueda en línea, podcasts, descargas offline,
letras sincronizadas, SponsorBlock y diseño inspirado en YouTube Music.

## Compilar el APK (GitHub Actions)
1. Sube el contenido de esta carpeta a la raíz de tu repositorio (incluida la carpeta oculta `.github/workflows/`).
2. En **Actions → Build APK → Run workflow**. El APK queda como artefacto y en *Releases*.

El workflow ordena los archivos planos de la raíz (`*.java`, `AndroidManifest.xml`, `index.html`, `res_*`, `app-build.gradle`)
dentro de `app/` antes de compilar. Si algún archivo aparece duplicado con nombre tipo `archivo (1).java`, el workflow lo borra.

## Estructura
- `index.html`: toda la interfaz.
- `MainActivity.java`, `NativeEngine.java`, `PlayService.java`, `PlayWidget.java`, `YouTubeActivity.java`: código Android.
- `app/google-services.json`: configuración de Firebase (inicio de sesión con Google).
- `tunebox.jks`: llave de firma. Mantén siempre la misma para poder actualizar la app.
- `backend/`: servidor opcional de ytmusicapi (no hace falta para compilar ni usar la app).
- `docs/`: guía de Firebase y notas de SponsorBlock.

## Documentación
- Inicio de sesión con Google: [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md)
- SponsorBlock y reproducción con pantalla apagada: [`docs/SPONSORBLOCK_OFFSCREEN.md`](docs/SPONSORBLOCK_OFFSCREEN.md)
- Servidor opcional de YouTube Music: [`backend/README-API.md`](backend/README-API.md)
