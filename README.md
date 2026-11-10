# Xtream Player (Android)

Reproductor de listas **Xtream Codes** para el teléfono, en la línea de IPTVnator.
Kotlin + Jetpack Compose + Media3 (ExoPlayer). Minimo Android 8.0 (API 26).

## Funciones

- Varias cuentas Xtream (servidor, usuario, contraseña). También acepta pegar la URL
  `http://host:puerto/get.php?username=...&password=...` y extrae los datos solo.
- Al abrir la app entra directamente en la última lista usada. Desde el menú **⋮** se puede
  cambiar de lista o añadir una nueva sin salir del reproductor.
- Pestañas **En vivo / Películas / Series / Favoritos**, con categorías y búsqueda.
- Canales en vivo con guía (ahora / después) vía `get_short_epg`.
- Series con temporadas y episodios.
- Reproductor a pantalla completa (HLS, TS, MP4, MKV...), botón vertical/horizontal,
  saltos de **±10 s** y selector de **velocidad** (0,5x a 2x).
- **Picture-in-picture**: al pulsar Inicio mientras se reproduce, el vídeo sigue en una
  ventana pequeña (también hay un botón **PiP** en el reproductor).
- En vivo: si `.m3u8` falla, reintenta con `.ts` automáticamente (y viceversa). Se puede elegir el formato por defecto en el menú ⋮.
- Películas y episodios recuerdan por dónde ibas.
- Favoritos por cuenta.
- **Continuar viendo**: pestaña con las películas y episodios que dejaste a medias, con vista previa y barra de avance. Al tocar uno se retoma justo donde lo dejaste (y se quita solo al terminarlo).
- Series con vista previa de cada capítulo (imagen, duración y avance) y botón para continuar el último episodio visto.
- **Audio y subtítulos**: en el reproductor, botón "Audio y subs" para cambiar el idioma del audio y activar o desactivar subtítulos (si el contenido trae esas pistas).

## Obtener el APK

### Opción A: GitHub Actions (sin instalar nada)

1. Crea un repositorio en GitHub y sube el contenido de esta carpeta.
2. Ve a la pestaña **Actions → Build APK → Run workflow** (también corre solo en cada push).
3. Al terminar, descarga el artefacto **XtreamPlayer-apk** (contiene `app-debug.apk`).
4. Pásalo al teléfono e instálalo (hay que permitir "instalar apps desconocidas").

### Opción B: Android Studio

1. Abre esta carpeta con Android Studio (Ladybug o más nuevo) y espera la sincronización de Gradle.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción C: línea de comandos

Con JDK 17, Android SDK (platform 35) y Gradle 8.14: `gradle assembleDebug`.

## Pruebas

- `gradle testDebugUnitTest` corre las pruebas unitarias: limpieza de servidores y lectura de
  URLs de lista, guardado del catálogo en el celular y textos del reproductor/descargas.
- GitHub Actions las ejecuta en cada push y en cada Pull Request; si fallan, no se compila ni se
  publica el APK.

## Notas

- Se permite tráfico HTTP sin cifrar porque casi todos los servidores IPTV lo usan.
- El APK sale firmado con la clave de debug: sirve para instalar y usar, no para publicar en Play Store.
- Las cuentas y las URL de descargas (que pueden incluir credenciales) se guardan cifradas con una clave de Android Keystore, dentro del almacenamiento privado de la app. Los datos existentes se migran automáticamente al actualizar (sin copia de seguridad en la nube).
- ExoPlayer no reproduce `.avi`; el resto de contenedores habituales funcionan.
- Solo reproduce contenido de servicios que tengas contratados legítimamente.

## Actualizaciones automáticas

- Cada vez que se sube código a `main`, GitHub Actions compila el APK firmado y lo publica como **Release**.
- La app busca una versión nueva al abrirse (como máximo cada 3 días) y desde el menú ⋮ → *Buscar actualización*.
- Si hay una nueva, la descarga y abre el instalador de Android (el sistema siempre pide confirmar).
- Para que funcione hay que crear dos *secrets* en el repo (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`.
- Cada APK publicado incluye una atestación de procedencia. Para verificarla con GitHub CLI: `gh attestation verify XtreamPlayer.apk -R emasanta66-beep/XtreamPlayer.`.
