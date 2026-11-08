# Reglas de R8 para la versión release.

# El decodificador de audio ffmpeg (AC3, DTS, etc.) lo crea ExoPlayer por reflexión y su código nativo (JNI)
# llama de vuelta a clases Java por nombre: si R8 las renombra o las quita, el audio deja de sonar.
-keep class androidx.media3.decoder.** { *; }
-keep class org.jellyfin.media3.** { *; }

# Dependencias opcionales de OkHttp que no existen en Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
