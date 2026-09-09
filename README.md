# TubeTV – tu YouTube sin publicidad en la TV del salón

El kiosko solo-YouTube para Android TV / Google TV: la interfaz Leanback oficial
que ya conoces, sin navegador, sin distracciones, sin anuncios que te interrumpan.

- **Anti-ads real**: EasyList (78.000 reglas) + auto-skip de anuncios + mute
  inteligente + SponsorBlock (adiós sponsors, intros y outros).
- **Búsqueda por voz** con el micro del mando.
- **Sleep timer**: mantén BACK y programa el apagado.
- **Sesión Google persistente**, incluso tras reboot del TV.
- **Auto-update** desde GitHub, sin Play Store.

*Your noise-free YouTube for the living-room TV: official Leanback UI, EasyList
anti-ads, auto-skip + SponsorBlock, voice search, sleep timer, persistent login,
auto-updates. 100% open source.*

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

## Licencia
Dual: código original TubeTV → MIT; partes derivadas de Brave → MPL-2.0
(ver `LICENSE` y `LICENSE-UPSTREAM-MPL-2.0`). Proyecto independiente,
sin afiliación con Brave Software ni Google.
