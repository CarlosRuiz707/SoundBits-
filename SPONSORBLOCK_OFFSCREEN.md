# SponsorBlock y reproducción offscreen en TuneBox

## SponsorBlock incluido

El reproductor YouTube incrustado (YouTube IFrame Player API) consulta `https://sponsor.ajay.app/api/skipSegments` al cargar cada vídeo. Actualmente se aplica la categoría `sponsor`; cuando el tiempo de reproducción entra en un intervalo válido, el reproductor salta al final del segmento y muestra un aviso breve en TuneBox. Si no hay segmentos o la API no responde, la reproducción continúa normalmente.

La consulta se realiza en un hilo de fondo, se valida el ID de vídeo y se limita la respuesta procesada. El módulo no altera canciones ni podcasts que no proceden del reproductor YouTube.

## Reproducción con la pantalla apagada

TuneBox ya usa `PlayService` como servicio multimedia en primer plano y publica una notificación con controles. Se añadió un `PARTIAL_WAKE_LOCK` administrado por ese servicio mientras el estado reportado es de reproducción, y se libera al pausar o destruir el servicio. Esto ayuda a evitar que el procesador entre en suspensión durante la reproducción de audio directa y del reproductor incrustado, siempre que la actividad/WebView siga viva.

**Límite importante:** la reproducción sigue originándose en el elemento de audio HTML o en el reproductor IFrame de YouTube dentro de `MainActivity`; `PlayService` todavía reenvía los comandos a JavaScript. Por tanto, esta modificación mejora el funcionamiento con la pantalla apagada, pero no convierte el servicio en un motor autónomo si Android destruye la actividad/WebView. Para garantizar continuidad tras la destrucción del proceso de interfaz, el siguiente paso sería migrar la cola de audio directo a Media3 `ExoPlayer` dentro de `MediaSessionService`. El reproductor de YouTube mantiene sus propias restricciones de disponibilidad y reproducción en segundo plano.

## Compilación

El flujo de GitHub se ha unificado para usar una sola versión canónica de `MainActivity.java` e `index.html`, e incluir `PlayWidget.java` y `YouTubeActivity.java` en el código Android. No se pudo ejecutar `gradle assembleDebug` en el entorno de edición porque no hay ejecutable Gradle/Android SDK disponible; hay que confirmar el build en CI o en un entorno Android configurado.
