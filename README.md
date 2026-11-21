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
- Detener la carga o recargar con un toque.
- Elegir Droxm como **navegador predeterminado**: los links que abrís desde otras apps
  caen en una pestaña nueva.
- Los links de `mailto:`, `tel:` y similares se abren en la app que corresponda.

### Pestañas

- Pestañas ilimitadas, con tarjetas en una grilla para verlas y cambiarte rápido.
- **Pestañas dormidas:** solo las que estás usando mantienen su página viva. Las demás se
  guardan y se recuperan al abrirlas, así el teléfono no se queda sin memoria.
- Elegís cuántas pestañas pueden estar activas a la vez (de 1 a 6).
- Al cerrar y volver a abrir la app, **se restauran tus pestañas normales**.
- Cerrar una pestaña o todas de una vez.

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
- **Sitios más visitados** en una grilla: aprendé tus accesos de un toque. Mantené
  apretado un sitio para quitarlo de la lista.
- Podés ocultar el título "Droxm" o la grilla de sitios si preferís algo más limpio.

### Personalización

- Barra de direcciones **arriba o abajo** y en tres tamaños.
- Mostrar u ocultar los botones de atrás/adelante.
- Tema **claro, oscuro o automático**. En Android 12+ toma los colores de tu fondo de
  pantalla (Material You).
- Cuatro buscadores: **DuckDuckGo** (por defecto), Google, Bing y Brave.

### Privacidad

- **Bloqueador de anuncios y rastreadores** incluido, activado por defecto. Bloquea más
  de 100 redes de publicidad y seguimiento antes de que salgan a la red, y esconde los
  huecos que dejan.
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
| **Permisos** | Solo acceso a Internet |
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
- **Menú:** los tres puntos (⋮) tienen nueva pestaña, nueva pestaña incógnito, recargar,
  cerrar y ajustes.
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
| Tema | Sistema / Claro / Oscuro | Sistema |
| Buscador | DuckDuckGo / Google / Bing / Brave | DuckDuckGo |
| Bloquear anuncios | Sí / No | Sí |
| Pestañas activas a la vez | 1–6 | 3 |

Los ajustes se guardan al instante: no hay botón de "guardar".

---

## Preguntas frecuentes

**¿Es gratis?**
Sí. No hay versión paga, cuentas ni anuncios dentro de la app.

**¿Bloquea los anuncios de YouTube?**
No. El bloqueador trabaja por dominio y YouTube sirve sus anuncios desde su propio
dominio. Sí bloquea la publicidad y el seguimiento de la mayoría de los sitios.

**¿El modo incógnito es realmente privado?**
En equipos con WebView moderno, sí: usa un perfil aparte y borra sus datos al cerrar. Si
tu WebView es viejo, la app te muestra un aviso porque las cookies pueden compartirse con
el modo normal. En cualquier caso, tu proveedor de Internet y los sitios que visitás
siguen pudiendo verte.

**¿Se pierden las pestañas al cerrar la app?**
Las normales se restauran al volver a abrir. Las de incógnito nunca se guardan, a
propósito.

**¿Puedo descargar archivos o abrir PDFs?**
Todavía no. Esta versión no incluye gestor de descargas ni visor de PDF.

**¿Necesita permisos raros?**
Solo Internet. Para elegir un fondo se usa el selector del sistema, así que la app no
necesita acceso a tus fotos.

**¿Funciona sin Google Play?**
Sí, el APK se instala aparte. Eso sí: el motor WebView lo provee el sistema, así que
conviene mantenerlo actualizado.

**¿Por qué está en español?**
La interfaz está pensada en español y todavía no tiene otros idiomas.

---

## Limitaciones conocidas

- Sin historial ni marcadores en la interfaz.
- Sin gestor de descargas ni lector de PDF.
- El bloqueador es una lista fija de dominios: no usa listas comunitarias tipo EasyList
  ni filtra por ruta.
- La restauración de sesión guarda la dirección y el título de cada pestaña, no todo su
  historial.
- El modo incógnito depende del WebView del sistema.
- Solo en español.
- Sin sincronización entre dispositivos.

---

## ¿Encontraste un problema o tenés una idea?

Se reporta en [**Issues**](https://github.com/ScarlettBDL/Droxm-Browser/issues). Si es un
error, contá qué teléfono y versión de Android usás, qué esperabas que pasara y qué pasó.

## Licencia

El proyecto todavía no declara una licencia, así que el código queda con todos los
derechos reservados por su autor.
