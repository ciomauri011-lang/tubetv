package com.brave.tv.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdblockEngineTest {

    private lateinit var engine: AdblockEngine

    @Before
    fun setup() {
        engine = AdblockEngine()
        engine.load(
            listOf(
                "||doubleclick.net^",
                "||googlesyndication.com^",
                "||ads.example.com/banner/*",
                "@@||ads.example.com/banner/allowed/",
                "/pagina-ads.",
                "||tracker.io^\$third-party",
                "||cdn.example.com^\$~third-party",
                "||promo.net^\$domain=youtube.com",
                "! comentario",
                "##.ad-slot",
                "||short^"
            )
        )
    }

    @Test
    fun bloqueaHostAnclado() {
        assertTrue(engine.isBlocked("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(engine.isBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
    }

    @Test
    fun respetaExcepcion() {
        assertTrue(engine.isBlocked("https://ads.example.com/banner/xyz.js"))
        assertFalse(engine.isBlocked("https://ads.example.com/banner/allowed/xyz.js"))
    }

    @Test
    fun substringSimple() {
        assertTrue(engine.isBlocked("https://www.youtube.com/pagina-ads.html"))
    }

    @Test
    fun thirdParty() {
        // tracker.io en documento youtube.com = third -> bloquea
        assertTrue(engine.isBlocked("https://tracker.io/x.js", "www.youtube.com"))
        // mismo sitio = first -> no bloquea
        assertFalse(engine.isBlocked("https://tracker.io/x.js", "tracker.io"))
    }

    @Test
    fun firstPartyOnly() {
        assertFalse(engine.isBlocked("https://cdn.example.com/a.js", "www.youtube.com"))
        assertTrue(engine.isBlocked("https://googlesyndication.com/pagead/js/adsbygoogle.js", "www.youtube.com"))
    }

    @Test
    fun domainOption() {
        assertTrue(engine.isBlocked("https://promo.net/o.js", "www.youtube.com"))
        assertFalse(engine.isBlocked("https://promo.net/o.js", "www.otra.com"))
    }

    @Test
    fun noFalsosPositivosYoutube() {
        assertFalse(engine.isBlocked("https://www.youtube.com/tv", "www.youtube.com"))
        assertFalse(engine.isBlocked("https://rr1---sn.googlevideo.com/videoplayback?x=1", "www.youtube.com"))
    }
}
