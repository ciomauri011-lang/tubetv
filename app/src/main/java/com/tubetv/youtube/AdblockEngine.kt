package com.tubetv.youtube

import java.net.URI
import java.util.LinkedHashMap

/**
 * Motor anti-ads, versión ligera en Kotlin puro (testeable en JVM).
 *
 * Lee sintaxis Adblock Plus (EasyList) y soporta el subconjunto útil para red:
 *  - Reglas de bloqueo y excepción (@@)
 *  - Ancla de host: ||dominio^  (con o sin resto de path)
 *  - Substrings de URL, comodín * y separador ^
 *  - Opciones: $third-party, $~third-party, $domain=a|~b
 * Ignora: reglas cosméticas (##), comentarios, $generichide y tipos de recurso
 * (se tratan como "aplica a todo" salvo third-party/domain).
 *
 * Nota: la determinación de third-party usa eTLD+1 ingenuo (últimas 2
 * etiquetas). Suficiente para TV; documentado como limitación.
 */
class AdblockEngine {

    private data class HostRule(
        val suffix: String,
        val pathPart: String?,
        val thirdParty: Int, // 0 = cualquiera, 1 = solo third, -1 = solo first
        val domains: List<Pair<String, Boolean>> // (dominio, incluido)
    )

    private val blockHosts = ArrayList<HostRule>()
    private val allowHosts = ArrayList<HostRule>()
    private val blockSubs = ArrayList<Triple<String, Int, List<Pair<String, Boolean>>>>()
    private val allowSubs = ArrayList<Triple<String, Int, List<Pair<String, Boolean>>>>()

    private val cache = object : LinkedHashMap<String, Boolean>(4096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean = size > 4096
    }

    var ruleCount: Int = 0
        private set

    fun load(lines: List<String>) {
        blockHosts.clear(); allowHosts.clear(); blockSubs.clear(); allowSubs.clear()
        synchronized(cache) { cache.clear() }
        var n = 0
        for (raw in lines) {
            if (parseRule(raw.trim())) n++
        }
        ruleCount = n
    }

    /** true si la regla aplica y matchea; respeta third-party y domain=. */
    private fun hostMatch(rule: HostRule, host: String, url: String, third: Boolean, doc: String): Boolean {
        if (!host.endsWith(rule.suffix)) return false
        // límite: el sufijo debe empezar en borde de etiqueta
        val i = host.length - rule.suffix.length
        if (i > 0 && host[i - 1] != '.') return false
        if (rule.pathPart != null && !url.contains(rule.pathPart)) return false
        if (rule.thirdParty == 1 && !third) return false
        if (rule.thirdParty == -1 && third) return false
        if (rule.domains.isNotEmpty() && !domainMatch(rule.domains, doc)) return false
        return true
    }

    private fun subMatch(rule: Triple<String, Int, List<Pair<String, Boolean>>>, url: String, third: Boolean, doc: String): Boolean {
        if (!url.contains(rule.first)) return false
        if (rule.second == 1 && !third) return false
        if (rule.second == -1 && third) return false
        if (rule.third.isNotEmpty() && !domainMatch(rule.third, doc)) return false
        return true
    }

    private fun domainMatch(domains: List<Pair<String, Boolean>>, doc: String): Boolean {
        var included = false
        var hasInclude = false
        for ((d, inc) in domains) {
            if (inc) {
                hasInclude = true
                if (doc == d || doc.endsWith(".$d")) included = true
            } else {
                if (doc == d || doc.endsWith(".$d")) return false
            }
        }
        return if (hasInclude) included else true
    }

    fun isBlocked(urlRaw: String, docHostRaw: String? = null): Boolean {
        val url = urlRaw.lowercase()
        val key = url + "|" + (docHostRaw ?: "")
        synchronized(cache) { cache[key]?.let { return it } }
        val host = runCatching { URI(url).host?.lowercase() ?: "" }.getOrDefault("")
        val doc = (docHostRaw?.lowercase() ?: host)
        val third = isThirdParty(host, doc)
        // Excepciones primero
        for (r in allowHosts) if (hostMatch(r, host, url, third, doc)) return cache(key, false)
        for (r in allowSubs) if (subMatch(r, url, third, doc)) return cache(key, false)
        for (r in blockHosts) if (hostMatch(r, host, url, third, doc)) return cache(key, true)
        for (r in blockSubs) if (subMatch(r, url, third, doc)) return cache(key, true)
        return cache(key, false)
    }

    private fun cache(key: String, v: Boolean): Boolean {
        synchronized(cache) { cache[key] = v }
        return v
    }

    private fun isThirdParty(host: String, doc: String): Boolean {
        if (host.isEmpty() || doc.isEmpty() || host == doc) return false
        return baseDomain(host) != baseDomain(doc)
    }

    private fun baseDomain(host: String): String {
        val p = host.split(".")
        return if (p.size >= 2) p.takeLast(2).joinToString(".") else host
    }

    private fun parseRule(raw: String): Boolean {
        if (raw.isEmpty() || raw[0] == '!' || raw[0] == '[') return false
        if (raw.contains("##") || raw.contains("#@#") || raw.contains("#?#") || raw.contains("#\$#")) return false
        var rule = raw
        var allow = false
        if (rule.startsWith("@@")) {
            allow = true
            rule = rule.substring(2)
        }
        // Separa opciones $ (cuidando $ dentro de regex, raro en EasyList)
        var options = ""
        val di = rule.lastIndexOf('$')
        if (di > 0 && !rule.substring(di).contains(" ")) {
            options = rule.substring(di + 1)
            rule = rule.substring(0, di)
        }
        var third = 0
        val domains = ArrayList<Pair<String, Boolean>>()
        if (options.isNotEmpty()) {
            var genericOnly = false
            for (opt in options.split(",")) {
                when {
                    opt == "third-party" || opt == "3p" -> third = 1
                    opt == "~third-party" || opt == "~3p" -> third = -1
                    opt.startsWith("domain=") -> opt.removePrefix("domain=").split("|").forEach {
                        if (it.startsWith("~")) domains.add(it.substring(1).lowercase() to false)
                        else domains.add(it.lowercase() to true)
                    }
                    opt == "generichide" || opt == "genericblock" || opt == "elemhide" -> genericOnly = true
                }
            }
            // $generichide solas no bloquean red; si la regla solo trae eso, ignora
            if (genericOnly && third == 0 && domains.isEmpty() && options.split(",").all {
                    it == "generichide" || it == "genericblock" || it == "elemhide" ||
                        it in setOf("script", "image", "stylesheet", "xmlhttprequest", "subdocument", "media", "font", "object", "other", "ping", "websocket", "webrtc")
                }) {
                // son reglas de tipo de recurso sin ancla útil: solo sirven si hay patrón;
                // se conservan igual (el patrón manda). No se descartan.
            }
        }
        if (rule.isEmpty()) return false
        // Ancla de host ||dominio^...
        if (rule.startsWith("||")) {
            var rest = rule.substring(2)
            val end = rest.indexOfFirst { it == '^' || it == '/' }
            val domain = (if (end < 0) rest else rest.substring(0, end)).lowercase()
                .trimEnd('*')
            if (domain.isEmpty() || domain.contains("*")) return false
            var pathPart: String? = null
            if (end >= 0) {
                // Resto "^" solo = separador, sin restricción de path.
                val lit = abpToLiteral(rest.substring(end).replace("^", ""))
                if (lit != null && lit.isNotEmpty()) pathPart = lit
            }
            val r = HostRule(domain, pathPart, third, domains)
            (if (allow) allowHosts else blockHosts).add(r)
            return true
        }
        // Otros: convierte a substring literal si no hay regex real
        val literal = abpToLiteral(rule) ?: return false
        if (literal.length < 3) return false
        (if (allow) allowSubs else blockSubs).add(Triple(literal, third, domains))
        return true
    }

    /** Convierte patrón ABP a substring literal; null si requiere regex real. */
    private fun abpToLiteral(rule: String): String? {
        var s = rule
        if (s.startsWith("|")) s = s.substring(1)
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        if (s.contains("*")) {
            // Conserva solo si el * es cola (prefijo útil)
            if (s.indexOf('*') == s.length - 1) {
                s = s.dropLast(1)
            } else {
                // Parte literal más larga entre *
                val parts = s.split("*").filter { it.length >= 5 && !it.contains("^") }
                s = parts.maxByOrNull { it.length } ?: return null
            }
        }
        s = s.replace("^", "")
        if (s.length < 3) return null
        return s.lowercase()
    }
}
