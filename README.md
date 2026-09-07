# TubeTV – YouTube only (Google TV)

Kiosko solo-YouTube para Google TV / Android TV. Sin otras páginas.
UI = `https://www.youtube.com/tv` (Leanback, misma que APK nativa) + User-Agent de Smart TV.
Motor = WebView del sistema. Adblock integrado en `shouldInterceptRequest` + allowlist estricta.

Proyecto independiente, sin afiliación con Brave Software ni Google.

## Estructura
```
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/com/tubetv/youtube/MainActivity.kt
  src/main/java/com/tubetv/youtube/YouTubeWebViewClient.kt
  src/main/java/com/tubetv/youtube/AdblockEngine.kt
  src/main/assets/easylist.txt
  src/main/assets/adblock.js
```

## Requisitos
- Android Studio Ladybug+, JDK 17, Android SDK (platform 34, build-tools 34, platform-tools)
- Kotlin 2.x, AGP 8.5+, minSdk 23, targetSdk 34
- Google TV / Android TV con WebView actualizado

## Compilar
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
Tests: `./gradlew testDebugUnitTest`

## Reglas del kiosko
- Allowlist: `*.youtube.com`, `*.googlevideo.com`, `*.gstatic.com`,
  `*.googleapis.com`, login Google, SponsorBlock. Resto → bloqueado.
- `onCreateWindow` (popups) bloqueado.
- Fullscreen video vía `onShowCustomView/onHideCustomView`.
- User-Agent Smart TV fuerza Leanback (ver `MainActivity.TV_USER_AGENT`).
- BACK = fullscreen > historial WebView > nada.
- Sesión persistente: cookies + `flush()` en `onPause()`.

## Anti-ads (v2)
- Red: `AdblockEngine` (Kotlin) con EasyList real (sintaxis ABP: `||host^`,
  `@@`, `$third-party`, `$domain=`). 7 unit tests.
- Player (`assets/adblock.js`): auto-clic Omitir/Skip, mute + 16x en anuncios,
  oculta overlays, salta sponsors vía API SponsorBlock.
- Límite: anuncios in-stream no saltables de YouTube pueden verse segundos
  (muteados/acelerados); vienen del propio player oficial.
