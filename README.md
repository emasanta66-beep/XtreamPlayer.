# Xtream Player (Android)

Reproductor de listas **Xtream Codes** para el teléfono, en la línea de IPTVnator.
Kotlin + Jetpack Compose + Media3 (ExoPlayer). Minimo Android 8.0 (API 26).

## Funciones

- Varias cuentas Xtream (servidor, usuario, contraseña). También acepta pegar la URL
  `http://host:puerto/get.php?username=...&password=...` y extrae los datos solo.
- Pestañas **En vivo / Películas / Series / Favoritos**, con categorías y búsqueda.
- Canales en vivo con guía (ahora / después) vía `get_short_epg`.
- Series con temporadas y episodios.
- Reproductor a pantalla completa (HLS, TS, MP4, MKV...), botón vertical/horizontal.
- En vivo: si `.m3u8` falla, reintenta con `.ts` automáticamente (y viceversa). Se puede elegir el formato por defecto en el menú ⋮.
- Películas y episodios recuerdan por dónde ibas.
- Favoritos por cuenta.

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

## Notas

- Se permite tráfico HTTP sin cifrar porque casi todos los servidores IPTV lo usan.
- El APK sale firmado con la clave de debug: sirve para instalar y usar, no para publicar en Play Store.
- Las contraseñas se guardan en el almacenamiento privado de la app (sin copia de seguridad en la nube).
- ExoPlayer no reproduce `.avi`; el resto de contenedores habituales funcionan.
- Solo reproduce contenido de servicios que tengas contratados legítimamente.
