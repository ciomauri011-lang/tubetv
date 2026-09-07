package com.tubetv.youtube

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_URL = "https://www.youtube.com/tv"
        // UA de Smart TV: obliga a YouTube a servir la UI Leanback (igual que APK nativa).
        const val TV_USER_AGENT =
            "Mozilla/5.0 (Linux; Tizen 6.0; SmartHub; SMART-TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 SmartTV"
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var fullscreenView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = TV_USER_AGENT
            // D-pad / foco TV: WebView debe ser enfocable, sin cursor táctil.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
            }
        }
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        // Sesión persistente: cookies aceptadas (incl. 3rd-party para login
        // Google) y guardadas en disco. Sobrevive reboot del TV salvo
        // "borrar datos" o cerrar sesión manual.
        with(CookieManager.getInstance()) {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = YouTubeWebViewClient()
        loadAdblockAsync()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                customViewCallback = callback
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view)
                webView.visibility = View.INVISIBLE
            }

            override fun onHideCustomView() {
                fullscreenView?.let { fullscreenContainer.removeView(it) }
                fullscreenView = null
                fullscreenContainer.visibility = View.GONE
                customViewCallback?.onCustomViewHidden()
                webView.visibility = View.VISIBLE
            }

            // Bloquea popups / ventanas nuevas: kiosko YouTube-only.
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(HOME_URL)
        }
        webView.requestFocus()
    }

    /**
     * Carga EasyList + JS anti-ads en hilo fondo y activa el cliente completo.
     * Mientras tanto opera el cliente básico (kiosko + lista dura).
     */
    private fun loadAdblockAsync() {
        Thread({
            var engine: AdblockEngine? = null
            var js = ""
            try {
                val lines = assets.open("easylist.txt").bufferedReader().readLines()
                engine = AdblockEngine().also { it.load(lines) }
            } catch (e: Exception) {
                engine = null
            }
            try {
                js = assets.open("adblock.js").bufferedReader().readText()
            } catch (e: Exception) {
                js = ""
            }
            val eng = engine
            runOnUiThread {
                webView.webViewClient = YouTubeWebViewClient(eng, js)
            }
        }, "tubetv-adblock").start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // BACK TV: cierra fullscreen > historial WebView > nada (kiosko, no sale a otra página).
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) {
                (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        // Fuerza escritura de cookies a disco: garantiza sesión tras reboot.
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
