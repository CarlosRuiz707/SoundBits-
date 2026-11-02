# Activar el inicio de sesión con Google en TuneBox

El código Android ya integra Credential Manager + Firebase Authentication. Para que el inicio de sesión funcione con cuentas reales, falta conectar el proyecto Firebase propietario de TuneBox. No se puede completar esa vinculación sin acceso a tu proyecto de Google/Firebase.

## 1. Crear o elegir el proyecto Firebase

1. Abre https://console.firebase.google.com/ y crea un proyecto para TuneBox.
2. Añade una aplicación Android con este identificador exacto: `app.tunebox`.
3. En la configuración de la aplicación Android, añade las huellas SHA-1 y SHA-256 del certificado con el que firmas el APK que vas a instalar.
4. Descarga el archivo `google-services.json` actualizado.
5. Colócalo en `app/google-services.json` en la raíz del repositorio. No cambies su nombre.

> Si compilas desde GitHub Actions, conserva `app/google-services.json` en esa ruta antes de ejecutar el workflow. El workflow crea las carpetas `app/src/...` sin borrar ese archivo.

## 2. Habilitar Google como proveedor

1. En Firebase Console, entra en **Authentication → Sign-in method**.
2. Activa **Google** y guarda los cambios.
3. Completa el nombre público y el correo de soporte del proyecto.
4. Verifica la pantalla de consentimiento de Google si vas a distribuir TuneBox públicamente.

## 3. Compilar e instalar

Vuelve a ejecutar el workflow **Build APK**. Cuando exista `app/google-services.json`, Gradle aplica automáticamente el plugin de Google Services y genera el recurso `default_web_client_id` que necesita Credential Manager.

## 4. Comprobar

1. Abre TuneBox → Ajustes → Cuenta TuneBox.
2. Pulsa **Continuar con Google** y elige una cuenta.
3. Al terminar, la tarjeta mostrará nombre y correo. Cierra y vuelve a abrir la app: Firebase debe restaurar la sesión.
4. Pulsa **Cerrar sesión** para salir y limpiar el estado de credenciales del proveedor.

## Seguridad y alcance actual

- El selector de cuentas se abre mediante Credential Manager nativo de Android; no se usa un WebView para autenticar con Google.
- El ID token de Google se intercambia directamente con Firebase Authentication dentro de Android y nunca se entrega al JavaScript de la página.
- No se solicitan ni almacenan contraseñas de Google.
- Firebase Authentication identifica al usuario mediante un UID estable.
- Este cambio implementa inicio/cierre de sesión y persistencia de identidad. La música y biblioteca siguen siendo locales; la sincronización entre dispositivos requiere una fase posterior con Firestore/Realtime Database y reglas de seguridad basadas en `request.auth.uid`.
- No publiques claves privadas de servicio ni tokens. El archivo `google-services.json` contiene identificadores de cliente de la aplicación, no una clave privada de administrador.

## Si Google no aparece o falla

Comprueba que el paquete sea `app.tunebox`, que el SHA-1/SHA-256 corresponda al certificado real del APK instalado, que Google esté habilitado en Firebase Authentication y que hayas descargado de nuevo `google-services.json` después de habilitar el proveedor.
