# Droxm Browser

Navegador Android liviano y personalizable. Usa el WebView del sistema (Chromium) como motor; todo lo demás (interfaz, pestañas, ajustes) es propio, en Kotlin + Jetpack Compose.

## Compilar con GitHub

Cada push a `main` corre el workflow **Build APK**: entrá a la pestaña *Actions*, abrí la última ejecución y bajá el artifact `droxm-browser` (un zip con el APK). Instalalo en el celular.

El APK es de tipo **release** (R8 + sin código de debug, bastante más fluido que un debug) y se firma con `app/debug.keystore` (versionada a propósito), así que cada build nuevo se instala encima del anterior sin desinstalar. Si venías de un APK debug anterior, también se instala encima (misma clave).

## Release firmado (para repartir el APK)

1. Generá una clave (una sola vez, guardala bien: si la perdés no se puede actualizar la app):
   `keytool -genkeypair -v -keystore release.jks -alias droxm -keyalg RSA -keysize 2048 -validity 10000`
2. Pasala a base64: `base64 -w0 release.jks`
3. En el repo: *Settings → Secrets and variables → Actions*, creá estos secrets:
   `KEYSTORE_BASE64` (el base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
4. Subí un tag: `git tag v0.1.0 && git push --tags`. El workflow **Release** compila el APK firmado y lo publica en *Releases*.

## Estructura

- `engine/` interfaz `BrowserEngine` + implementación con WebView. Si algún día se migra a GeckoView, solo se escribe otra implementación.
- `tabs/` pestañas, `TabManager` (descarte de pestañas inactivas, incógnito) y `TabStore` (guarda/restaura pestañas normales).
- `settings/` `AppSettings`: posición y tamaño de la barra, botones, tema, buscador, pestañas activas.
- `ui/` pantallas en Compose.
- `util/` utilidades.

## Estado

- [x] Paso 1: esqueleto, WebView, barra de direcciones, build en GitHub
- [x] Paso 2: pestañas con descarte automático + modo incógnito aislado
- [x] Pestañas guardadas entre sesiones (menos incógnito)
- [~] Paso 3: personalización. Hecho: ajustes de layout (barra arriba/abajo, tamaño, botones), tema, buscador. Falta: fondos con foto/animados y accesos rápidos
- [ ] Paso 4: bloqueo de anuncios, historial, favoritos, descargas
