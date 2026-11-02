SoundBits — paquete reconstruido

Qué contiene
- La interfaz index.html del ZIP SoundBits-rediseno-8 (diseño principal).
- La estructura Android, MainActivity, servicios, widget, manifiesto y configuración de compilación del ZIP Tunebox-v-0.1-main.
- Se mantienen los archivos Android originales para que el proyecto sea compilable mediante el flujo de GitHub Actions incluido.

Cambios
- Se sustituyó el index.html antiguo por el del rediseño más reciente.
- No se añadieron de nuevo los paneles de credenciales de Jamendo, Audiomack, Mixcloud o Internet Archive; no aparecen en el HTML del rediseño recibido.

Limitaciones importantes
- Esta es una reconstrucción de fuentes, NO un APK compilado ni una prueba en un dispositivo.
- La biblioteca ytmusicapi de Apis.zip es Python y no se ejecuta directamente en WebView. El backend Python incluido en el proyecto requiere ejecutarse en un entorno servidor; no se ha conectado automáticamente a la app.
- Como el HTML del rediseño y la actividad Android provienen de versiones diferentes, hace falta probar la comunicación entre JavaScript y MainActivity en una compilación real. La sintaxis JavaScript inline pasó node --check, pero eso no garantiza que toda la app funcione ni elimina por sí solo la pantalla negra.

Compilación
El proyecto incluye build.yml para GitHub Actions. Sube el contenido del ZIP a un repositorio y ejecuta el workflow Build APK. Revisa los logs si falla; no distribuyas el keystore de firma incluido en el ZIP.
