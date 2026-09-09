package com.tubetv.youtube

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Kiosko YouTube-only + anti-ads integrado.
 *
 * Red: [AdblockEngine] con EasyList (assets/easylist.txt). Sin engine o con
 * engine vacío, usa lista dura mínima de respaldo.
 * Kiosko: allowlist estricta (YouTube + Google video + login Google).
 * Player: [adJs] (assets/adblock.js) hace auto-skip, mute en anuncios,
 * oculta overlays y salta sponsors vía SponsorBlock.
 */
class YouTubeWebViewClient(
    private val engine: AdblockEngine? = null,
    private val adJs: String = ""
) : WebViewClient() {

    private val allowedHosts = listOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtu.be", "googlevideo.com",
        "gstatic.com", "googleapis.com", "ggpht.com", "ytimg.com",
        "youtube-nocookie.com",
        // Login Google / YouTube (OAuth). Sin esto no hay sesión persistente.
        "accounts.google.com", "myaccount.google.com", "accounts.youtube.com",
        // SponsorBlock (saltar sponsors dentro del video)
        "sponsor.ajay.app"
    )

    private val fallbackBlocked = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "adservice.google",
        "ads.yahoo", "facebook.net/tr", "criteo.com", "outbrain.com", "taboola.com"
    )

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun hostAllowed(url: String): Boolean {
        val host = hostOf(url)
        if (host.isEmpty()) return false
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 204, "No Content", null, ByteArrayInputStream(ByteArray(0)))

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString().orEmpty()
        // Toda navegación fuera de YouTube se bloquea (sin otras páginas).
        if (!hostAllowed(url)) return true
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString().orEmpty()
        if (url.isEmpty()) return null
        val lower = url.lowercase()
        val docHost = runCatching { Uri.parse(view?.url.orEmpty()).host?.lowercase() }.getOrNull()
        val eng = engine
        if (eng != null && eng.ruleCount > 0) {
            if (eng.isBlocked(lower, docHost)) return emptyResponse()
        } else if (fallbackBlocked.any { lower.contains(it) }) {
            return emptyResponse()
        }
        if (!hostAllowed(url)) return emptyResponse()
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        inject(view)
    }

    // youtube.com/tv es SPA: las navegaciones no recargan página.
    // Se reinyecta en cada cambio de historial.
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        inject(view)
    }

    fun inject(view: WebView?) {
        if (adJs.isNotEmpty() && view != null) {
            runCatching { view.evaluateJavascript(adJs, null) }
        }
    }
}
