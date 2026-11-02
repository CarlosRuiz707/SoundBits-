# TuneBox

Reproductor musical Android con biblioteca local, catálogo online y diseño oscuro inspirado en reproductores modernos.

## Incluye
- Biblioteca local con reproducción, cola, búsquedas y listas.
- Catálogo online y reproductor integrado.
- Interfaz oscura, acento verde, DM Sans y SVG de trazo libre.
- Inicio de sesión Android con Google mediante Credential Manager + Firebase Authentication.
- Persistencia de sesión, perfil básico y cierre de sesión.

## Configurar Google Sign-In
Sigue todos los pasos de [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md). Es necesario conectar tu propio proyecto Firebase, registrar el paquete `app.tunebox`, añadir las huellas SHA del certificado, habilitar Google y colocar `app/google-services.json`.

Sin ese archivo de configuración, la aplicación compila sin activar el proveedor y la pantalla de cuenta indica que falta configurar Firebase. No se simula un inicio de sesión.

## Tecnología
Android Java + WebView + Firebase Authentication + Android Credential Manager.
