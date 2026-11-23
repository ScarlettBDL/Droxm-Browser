# Droxm Browser

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requisitos)
[![Versión](https://img.shields.io/badge/versi%C3%B3n-0.1.0-6C4DFF)](#)
[![Gratis](https://img.shields.io/badge/precio-gratis-success)](#)

Un navegador para Android **liviano, rápido y personalizable**, pensado para hacer lo
básico muy bien: abrir páginas, manejar pestañas sin comerse la memoria y dejar la
pantalla de inicio a tu gusto.

Sin cuentas, sin suscripciones, sin anuncios propios y sin recolección de datos. La app
pesa poco porque usa el motor que ya viene en tu teléfono (WebView/Chromium); lo que es
propio es toda la experiencia: pestañas, barra, ajustes y pantalla de inicio.

> **Estado:** versión 0.1.0. Es un proyecto joven y en desarrollo activo.

---

## Qué podés hacer con Droxm

### Navegar

- Abrir cualquier página web con el motor Chromium del sistema.
- Escribir una dirección o buscar directamente desde la barra: si no parece una URL,
  busca en el buscador que elijas.
- Ver solo el dominio en la barra (con candado si la conexión es HTTPS) y tocar para
  editar la dirección completa.
- Usar los botones **atrás/adelante** o el gesto del sistema.
- **Sugerencias** mientras escribís: favoritos e historial que coinciden (en incógnito, solo favoritos).
- **Buscar en la página:** resalta las coincidencias y salta entre ellas.
- Detener la carga o recargar con un toque.
- Elegir Droxm como **navegador predeterminado**: los links que abrís desde otras apps
  caen en una pestaña nueva.
- Los links de `mailto:`, `tel:` y similares se abren en la app que corresponda.

### Pestañas y grupos

- Pestañas ilimitadas, con tarjetas con **miniatura** en una grilla para verlas y cambiarte rápido.
- **Grupos de pestañas** (como en Chrome o Brave): mantené apretada una pestaña para
  agruparla. El grupo se ve como una sola tarjeta con nombre y color; al tocarla se abre con
  todas sus pestañas. Se pueden renombrar, cambiar de color, desagrupar o cerrar de una vez.
- **Mantené apretado un link** en una página para abrirlo en una pestaña nueva (en el mismo
  grupo), en incógnito, copiarlo o compartirlo.
- **Pestañas dormidas:** solo las que estás usando mantienen su página viva. Las demás se
  guardan y se recuperan al abrirlas, así el teléfono no se queda sin memoria.
- Elegís cuántas pestañas pueden estar activas a la vez (de 1 a 6).
- Al cerrar y volver a abrir la app, **se restauran tus pestañas normales**.
- Cerrar una pestaña o todas de una vez.

### Compatibilidad con las webs

- **Cámara, micrófono y ubicación:** la página pide permiso y Droxm te pregunta (Permitir /
  Bloquear, con "Recordar mi decisión"; en incógnito nunca se recuerda). Las decisiones se
  borran desde Ajustes.
- **Videos en pantalla completa**, con rotación libre.
- **Subir archivos y fotos** desde los botones de las páginas.
- **Visor de PDF propio:** los PDF se abren dentro de Droxm (páginas dibujadas a medida que
  se ven, zoom con − / + o doble toque) y se pueden guardar.
- **Contraseñas y autocompletar:** Droxm usa el servicio de autocompletar de Android (Google,
  Bitwarden, etc.).

### Favoritos, historial y descargas

- **Favoritos:** menú ⋮ → *Agregar a favoritos*. Con buscador.
- **Historial** (solo pestañas normales): hasta 3000 páginas, agrupadas por día, con
  buscador, borrado de a una o todo junto.
- **Descargas:** archivos e imágenes (mantené apretada una imagen) se guardan en la carpeta
  Descargas con el gestor del sistema.
- **Barra de direcciones** que muestra solo el dominio y, al tocarla, la URL completa en
  varias líneas.
- **Sitio de escritorio**, copiar enlace y compartir desde el menú.

### Modo incógnito

- Pestañas separadas del modo normal, con su propio color oscuro para que no te confundas.
- En equipos compatibles, cookies, caché y almacenamiento quedan **realmente aislados**:
  no se mezclan con tu sesión normal.
- No se guarda historial de lo que visitás en incógnito y todo se borra al cerrar esas
  pestañas (y también al abrir la app, por si el proceso murió antes).
- Si tu teléfono no permite ese aislamiento, la app te avisa y usa un modo liviano en
  lugar de fingir privacidad.

### Pantalla de inicio

- **Tu propio fondo:** una foto, un GIF/WebP animado o un video corto en loop y sin
  sonido. Se recorta solo para llenar la pantalla.
- **Control de oscuridad** para que el texto se lea bien sobre fondos claros.
- **Sitios más visitados** en una grilla; un sitio aparece recién después de **3 visitas**.
  Elegís cuántos mostrar (3, 4, 6, 8 o 12) y dónde: arriba, al centro o abajo. Mantené
  apretado un sitio para quitarlo de la lista.
- Podés ocultar el título "Droxm" o la grilla de sitios si preferís algo más limpio.

### Personalización

- Barra de direcciones **arriba o abajo** y en tres tamaños.
- Mostrar u ocultar los botones de atrás/adelante.
- Tema **claro, oscuro o automático**. En Android 12+ toma los colores de tu fondo de
  pantalla (Material You).
- Cuatro buscadores: **DuckDuckGo** (por defecto), Google, Bing y Brave.

### Privacidad

- **Bloqueador de anuncios y rastreadores** incluido, activado por defecto. Usa
  **EasyList y EasyPrivacy** con un motor escrito en **Rust** (ver más abajo), así que
  filtra por dominio, ruta, tipo de recurso y si el pedido es de un tercero. Si el motor
  nativo no estuviera disponible, usa una lista corta de más de 100 redes de publicidad.
- Las listas se descargan solas desde `easylist.to` (con Wi-Fi, cada 7 días) o con
  *Ajustes → Actualizar ahora*. Es la única conexión que hace la app además de tu
  navegación.
- **Todo queda en tu teléfono.** No hay cuentas, servidores propios, estadísticas ni
  telemetría.
- El navegador no puede leer tus archivos locales.
- Android no hace copia de seguridad de los datos de la app en la nube.

---

## Requisitos

| | Mínimo |
| --- | --- |
| **Android** | 8.0 (API 26) o superior |
| **WebView del sistema** | Una versión reciente (se actualiza desde Play Store) |
| **Permisos** | Internet y estado de la red (para actualizar listas solo con Wi-Fi). Cámara, micrófono y ubicación solo se piden cuando una página los usa y vos aceptás. Hasta Android 9, además, guardar descargas |
| **Conexión** | Wi-Fi o datos para navegar |
| **Espacio** | El del APK; el fondo de inicio es opcional (hasta 150 MB) |

No necesita root, ni cuentas, ni hardware especial, y funciona en cualquier arquitectura
de procesador.

### Para aprovechar todo

- **Android 9 o superior:** fondos con GIF/WebP animados.
- **Android 12 o superior:** colores dinámicos según tu fondo de pantalla.
- **WebView actualizado:** aislamiento completo del modo incógnito. En versiones viejas
  la app lo detecta y te avisa; igual podés navegar en incógnito, pero con menos
  garantías.

---

## Instalación

1. Entrá a [**Releases**](https://github.com/ScarlettBDL/Droxm-Browser/releases) y
   descargá el último `app-release.apk`.
2. Si todavía no hay releases publicadas, usá el artefacto **`droxm-browser`** de la
   pestaña [**Actions**](https://github.com/ScarlettBDL/Droxm-Browser/actions/workflows/build.yml).
3. Abrí el archivo en el teléfono y permití "instalar apps de origen desconocido"
   cuando Android lo pida.
4. Listo. Si querés, abrila y elegila como navegador predeterminado.

Las actualizaciones se instalan **encima de la versión anterior**, sin desinstalar ni
perder los ajustes.

---

## Primeros pasos

- **Buscar o navegar:** tocá la barra, escribí y dale a Ir. Un texto cualquiera se busca;
  `algo.com` se abre como página.
- **Pestañas:** el cuadradito con el número, a la derecha de la barra, abre la grilla.
- **Menú:** los tres puntos (⋮) tienen pestañas nuevas, recargar, favoritos, buscar en la
  página, copiar/compartir, sitio de escritorio, historial, descargas y ajustes.
- **Atrás:** el gesto o botón del sistema vuelve en la página; si ya no hay historial, no
  hace nada.
- **Inicio:** los sitios que más visitás aparecen solos. Mantené apretado uno para
  sacarlo.

---

## Ajustes

| Ajuste | Opciones | Por defecto |
| --- | --- | --- |
| Posición de la barra | Arriba / Abajo | Abajo |
| Tamaño de la barra | Compacta / Normal / Grande | Normal |
| Botones atrás y adelante | Mostrar / Ocultar | Mostrar |
| Fondo de inicio | Foto, GIF o video (máx. 150 MB) | Degradado |
| Oscuridad del fondo | 0–90 % | 35 % |
| Mostrar título "Droxm" | Sí / No | Sí |
| Sitios más visitados | Sí / No | Sí |
| Cantidad de sitios | 3 / 4 / 6 / 8 / 12 | 8 |
| Posición de los sitios | Arriba / Centro / Abajo | Centro |
| Tema | Sistema / Claro / Oscuro | Sistema |
| Buscador | DuckDuckGo / Google / Bing / Brave | DuckDuckGo |
| Bloquear anuncios | Sí / No | Sí |
| Listas de filtros | Actualizar ahora | Automático con Wi-Fi |
| Pestañas activas a la vez | 1–6 | 3 |

Los ajustes se guardan al instante: no hay botón de "guardar".

---

## Preguntas frecuentes

**¿Es gratis?**
Sí. No hay versión paga, cuentas ni anuncios dentro de la app.

**¿Bloquea los anuncios de YouTube?**
No. YouTube sirve sus anuncios desde su propio dominio y dentro del video, y el
bloqueador solo filtra pedidos de red. Sí bloquea la publicidad y el seguimiento de la
mayoría de los sitios.

**¿El modo incógnito es realmente privado?**
En equipos con WebView moderno, sí: usa un perfil aparte y borra sus datos al cerrar. Si
tu WebView es viejo, la app te muestra un aviso porque las cookies pueden compartirse con
el modo normal. En cualquier caso, tu proveedor de Internet y los sitios que visitás
siguen pudiendo verte.

**¿Se pierden las pestañas al cerrar la app?**
Las normales se restauran al volver a abrir. Las de incógnito nunca se guardan, a
propósito.

**¿Puedo descargar archivos o abrir PDFs?**
Sí. Los PDF se abren en el visor propio (con botón *Guardar*) y el resto de los archivos e
imágenes se descarga a la carpeta Descargas.

**¿Necesita permisos raros?**
No: Internet y estado de la red. Cámara, micrófono y ubicación están declarados, pero Android
solo te los pide cuando una página los usa y aceptás. Hasta Android 9, además, guardar
descargas. Para elegir un
fondo se usa el selector del sistema, así que la app no necesita acceso a tus fotos.

**¿Funciona sin Google Play?**
Sí, el APK se instala aparte. Eso sí: el motor WebView lo provee el sistema, así que
conviene mantenerlo actualizado.

**¿Por qué está en español?**
La interfaz está pensada en español y todavía no tiene otros idiomas.

---

## Limitaciones conocidas

- El visor de PDF no maneja PDF con contraseña (ofrece descargarlo), no selecciona texto ni
  busca dentro del documento.
- WebView no soporta notificaciones web ni push: las páginas que las piden no las reciben.
- Guardar y rellenar contraseñas depende del servicio de autocompletar que tengas activado
  en Android; Droxm no tiene gestor propio.
- El bloqueador solo filtra pedidos de red: no soporta reglas con expresiones regulares ni
  `$redirect`/`$csp`, y los selectores de ocultamiento (`##`) de EasyList no se aplican
  (solo un ocultamiento básico de anuncios de Google). El tipo de recurso se deduce de las
  cabeceras y la extensión, porque WebView no lo informa.
- Las miniaturas del selector de pestañas solo viven en memoria: tras reiniciar la app se
  ven las iniciales hasta volver a entrar a cada pestaña.
- La restauración de sesión guarda la dirección y el título de cada pestaña, no todo su
  historial.
- El modo incógnito depende del WebView del sistema.
- Solo en español.
- Sin sincronización entre dispositivos.

---

## Para desarrolladores

Kotlin + Jetpack Compose sobre WebView. Se compila **solo con GitHub Actions** (no hace
falta el SDK de Android local).

- `app/` — la aplicación. El motor del navegador está detrás de las interfaces
  `BrowserEngine`/`EngineSession`, así que cambiar WebView por otro motor es escribir otra
  implementación.
- `rust/core/` — núcleo del bloqueador, sin dependencias: parser de listas, índices por
  token y dominio, excepciones (`@@`, `$important`, `$badfilter`), clasificación de
  recursos y deduplicación. `cargo test` y `cargo run --release --example bench` funcionan
  en cualquier PC.
- `rust/jni/` — puente JNI. Una sola llamada nativa por pedido; las listas grandes pasan
  por archivos, no por la JVM.
- `.github/` — el CI prueba el núcleo, compila la librería para arm64, armv7 y x86_64 y arma
  el APK. Si la parte de Rust fallara, el APK sale igual con el bloqueador de reserva.

---

## ¿Encontraste un problema o tenés una idea?

Se reporta en [**Issues**](https://github.com/ScarlettBDL/Droxm-Browser/issues). Si es un
error, contá qué teléfono y versión de Android usás, qué esperabas que pasara y qué pasó.

## Licencia

El proyecto todavía no declara una licencia, así que el código queda con todos los
derechos reservados por su autor.
