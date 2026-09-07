# Brave TV – YouTube only (Google TV)

Wrapper estilo Brave para Google TV / Android TV. Solo YouTube. Sin otras páginas.
UI = `https://www.youtube.com/tv` (Leanback, misma que APK nativa) + User-Agent de Smart TV.
Motor = Android WebView del sistema (no fork Chromium completo). Adblock estilo Brave
simplificado en `shouldInterceptRequest` + allowlist estricta.

## Estructura
```
brave-tv-youtube/
  settings.gradle.kts
  build.gradle.kts
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/brave/tv/youtube/MainActivity.kt
  app/src/main/java/com/brave/tv/youtube/YouTubeWebViewClient.kt
  app/src/main/res/layout/activity_main.xml
```

## Requisitos (pendientes en tu PC)
- Windows 10/11 64-bit — OK (tienes D: 285 GB libres)
- Android Studio Ladybug+ → https://developer.android.com/studio
- Android SDK: platform android-34/35 + Android TV image + platform-tools (adb)
- JDK 17 (Temurin o el embebido de Android Studio). Tienes Java 1.8 → ACTUALIZAR
- Kotlin 2.x, AGP 8.5+, minSdk 23 (TV 6.0+), targetSdk 34/35
- Dispositivo Google TV / Android TV con WebView actualizado

## Abrir y compilar
1. Instala Android Studio + SDK + crea emulador "Android TV 1080p API 34".
2. Abre la carpeta `brave-tv-youtube/` en Android Studio.
3. Sync Gradle → Run en TV/emulador, o `adb install app-debug.apk`.
4. Al arrancar carga `youtube.com/tv`. D-pad = navegación nativa Leanback.
   BACK = historial WebView, si no hay más → no sale (kiosko).

## Reglas del kiosko
- Allowlist: `*.youtube.com`, `*.googlevideo.com`, `*.gstatic.com`, `*.googleapis.com`.
  Todo lo demás → bloqueado (`ERR_BLOCKED`).
- `onCreateWindow` (popups) bloqueado.
- Fullscreen video vía `onShowCustomView/onHideCustomView`.
- User-Agent Smart TV para forzar Leanback:
  `Mozilla/5.0 (Linux; Tizen 6.0; SmartHub) AppleWebKit/537.36 … Chrome/… Safari/537.36 SmartTV`
  (ver `MainActivity.TV_USER_AGENT`).

## Adblock estilo Brave (v1 simple)
`YouTubeWebViewClient.shouldInterceptRequest` bloquea por substrings
(doubleclick, googlesyndication, googleadservices, etc.). No toca anuncios
inyectados del propio player de YouTube (igual que Brave real en TV).
Evolución: usar `brave/adblock-rust` (clonado en `_ref-adblock-rust/`) vía
JNI o lista EasyList local + `WebViewAssetLoader`.

## Referencias descargadas
- `_ref-mrowser/` → `m-salehi-v/mrowser`: browser TV open-source con D-pad,
  SniffingWebViewClient, ExoPlayer. Base para cursor virtual y handoff HLS.
- `_ref-adblock-rust/` → `brave/adblock-rust`: motor adblock real de Brave.

## NO es fork de brave-core
Fork completo = Linux + 100 GB + depot_tools + `pnpm run init/build`
(60 GB, 240 repos, horas de compilación). Inviable en este PC Windows
y sobredimensionado para "solo YouTube". Este wrapper logra el objetivo:
icono Brave, solo YouTube, UI idéntica a APK nativa, mando simple.
Si luego quieres motor Brave real: compila en Linux o usa Custom Tabs
con paquete `com.brave.browser` como fallback.
