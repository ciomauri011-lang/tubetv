/* TubeTV anti-ads v3 para youtube.com/tv (Leanback). Idempotente.
 * Detección por scoring multi-señal + MutationObserver instantáneo.
 * Seguro en vivo: jamás velocidad; restore blindado. */
(function () {
  if (window.__ttvAntiAds) return;
  window.__ttvAntiAds = true;

  var userMuted = false,
    sbId = null,
    sbSegs = [],
    sbMuted = false,
    boosted = false,
    lastAd = null,
    lastVideo = null,
    adSlotsCache = { id: null, has: false };

  function css() {
    var s = document.getElementById('ttv-adcss');
    if (s) return;
    s = document.createElement('style');
    s.id = 'ttv-adcss';
    s.textContent = [
      '.ytp-ad-overlay-container,.ytp-ad-overlay-slot,',
      '.ytd-display-ad-renderer,.ytd-promoted-sparkles-text-search-renderer,',
      '.ytd-in-feed-ad-layout-renderer,.ytd-ad-slot-renderer,',
      '[id*="masthead-ad"],.yt-mealbar-promo-renderer { display:none !important; }'
    ].join('');
    (document.head || document.documentElement).appendChild(s);
  }

  function video() { return document.querySelector('video'); }

  function skipVisible() {
    var sels = ['.ytp-skip-ad-button', '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button-modern'];
    for (var i = 0; i < sels.length; i++) {
      var b = document.querySelector(sels[i]);
      if (b && b.offsetParent !== null) return true;
    }
    return false;
  }

  function skipBtn() {
    var sels = ['.ytp-skip-ad-button', '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button-modern'];
    for (var i = 0; i < sels.length; i++) {
      var b = document.querySelector(sels[i]);
      if (b && b.offsetParent !== null) { try { b.click(); } catch (e) {} return true; }
    }
    var btns = document.querySelectorAll('button');
    for (var j = 0; j < btns.length; j++) {
      var t = (btns[j].innerText || '').trim().toLowerCase();
      if ((t === 'omitir' || t === 'omitir anuncios' || t === 'skip' || t === 'skip ads' || t.indexOf('omitir') === 0) && btns[j].offsetParent !== null) {
        try { btns[j].click(); } catch (e) {} return true;
      }
    }
    return false;
  }

  function isLive(v) {
    try {
      if (!isFinite(v.duration)) return true;
      var b = document.querySelector('.ytp-live-badge');
      if (b && b.offsetParent !== null) return true;
    } catch (e) {}
    return false;
  }

  function videoId() {
    try {
      var q = new URLSearchParams(location.search).get('v');
      if (q) return q;
      var h = location.hash || '';
      var m = h.match(/[?&]v=([^&#]+)/);
      if (m) return decodeURIComponent(m[1]);
      var p = location.pathname.match(/\/embed\/([^\/?#]+)/);
      if (p) return p[1];
    } catch (e) {}
    return null;
  }

  // Señal fuerte: playerResponse trae adPlacements no vacíos.
  // Se evalúa 1 vez por video y se cachea (parse acotado).
  function hasAdSlots(id) {
    if (!id) return false;
    if (adSlotsCache.id === id) return adSlotsCache.has;
    var has = false;
    try {
      var html = document.documentElement.innerHTML;
      var probe = html.length > 600000 ? html.substring(0, 600000) : html;
      var i = probe.indexOf('"adPlacements":');
      if (i >= 0) {
        var chunk = probe.substring(i, i + 200);
        has = chunk.indexOf('"adPlacements":[]') !== 0;
      }
    } catch (e) { has = false; }
    adSlotsCache = { id: id, has: has };
    return has;
  }

  // Texto "Anuncio/Advertisement" dentro del player (barato, acotado).
  function adTextInPlayer() {
    try {
      var p = document.querySelector('.html5-video-player') || document.body;
      var t = (p.innerText || '').substring(0, 4000).toLowerCase();
      return t.indexOf('anuncio') >= 0 || t.indexOf('advertisement') >= 0 ||
        t.indexOf('se omit') >= 0 || t.indexOf('skip in') >= 0;
    } catch (e) { return false; }
  }

  // Scoring: >=2 = anuncio. Devuelve razones para diagnóstico.
  function scoreAd(id) {
    var score = 0, why = [];
    if (skipVisible()) { score += 3; why.push('skip'); }
    if (document.querySelector('.ad-showing, .ytp-ad-player-overlay, .ytp-ad-player-overlay-layout')) { score += 2; why.push('cls'); }
    if (document.querySelector('.ytp-ad-text, .ytp-ad-preview-text, .ytp-ad-message-container, .ytp-ad-skip-button-slot, [class*="ad-badge"]')) { score += 2; why.push('badge'); }
    if (adTextInPlayer()) { score += 2; why.push('txt'); }
    if (id && hasAdSlots(id)) { score += 1; why.push('slots'); }
    return { ad: score >= 2, score: score, why: why.join('+') };
  }

  function loadSB(id) {
    sbId = id; sbSegs = [];
    try {
      fetch('https://sponsor.ajay.app/api/skipSegments?videoID=' + encodeURIComponent(id) +
        '&categories=[%22sponsor%22,%22intro%22,%22outro%22,%22selfpromo%22,%22interaction%22,%22music_offtopic%22]' +
        '&actionTypes=[%22skip%22,%22mute%22]')
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (j) { sbSegs = Array.isArray(j) ? j : []; })
        .catch(function () { sbSegs = []; });
    } catch (e) { sbSegs = []; }
  }

  function sbTick(v) {
    if (!sbSegs.length || v.paused) { if (sbMuted) { try { v.muted = userMuted; } catch (e) {} sbMuted = false; } return; }
    var t = v.currentTime;
    for (var i = 0; i < sbSegs.length; i++) {
      var s = sbSegs[i], seg = s.segment;
      if (!seg) continue;
      if (s.actionType === 'mute') {
        if (t >= seg[0] && t < seg[1]) { if (!v.muted) { userMuted = v.muted; try { v.muted = true; } catch (e) {} sbMuted = true; } }
      } else if (t >= seg[0] && t < seg[1] - 0.3) {
        try { v.currentTime = seg[1]; } catch (e) {}
      }
    }
  }

  function check() {
    try {
      css();
      var v = video();
      if (!v) return;
      if (v !== lastVideo) { lastVideo = v; boosted = false; }
      var live = isLive(v);
      var id = videoId();
      var r = scoreAd(id);
      var ad = r.ad;
      if (live) {
        if (v.playbackRate !== 1) { try { v.playbackRate = 1; } catch (e) {} }
        if (ad) {
          if (!v.muted) { userMuted = v.muted; }
          try { v.muted = true; } catch (e) {}
          boosted = true;
          skipBtn();
        } else if (boosted) {
          boosted = false;
          try { if (!userMuted) v.muted = false; } catch (e) {}
        }
      } else if (ad) {
        if (!v.muted) { userMuted = v.muted; }
        boosted = true;
        try { v.muted = true; v.playbackRate = 16; } catch (e) {}
        skipBtn();
      } else {
        if (boosted) {
          boosted = false;
          try { if (!userMuted) v.muted = false; v.playbackRate = 1; } catch (e) {}
        } else if (v.playbackRate !== 1 && !v.paused) {
          try { v.playbackRate = 1; } catch (e) {}
        }
      }
      if (id && id !== sbId) loadSB(id);
      if (id && !live) sbTick(v);
      if (ad !== lastAd) {
        lastAd = ad;
        try { console.log('TTV ad=' + (ad ? 1 : 0) + ' live=' + (live ? 1 : 0) + ' why=' + r.why + ' score=' + r.score); } catch (e) {}
      }
    } catch (e) {}
  }

  window.__ttvState = function () {
    try {
      var v = video();
      var id = videoId();
      var r = id ? scoreAd(id) : { ad: false, score: 0, why: '' };
      return JSON.stringify({
        js: 'v3', video: !!v, ad: r.ad, score: r.score, why: r.why, skip: skipVisible(),
        live: v ? isLive(v) : null, rate: v ? v.playbackRate : null,
        muted: v ? v.muted : null, boosted: boosted, sb: sbSegs.length,
        url: location.href.substring(0, 120)
      });
    } catch (e) { return '{"js":false}'; }
  };

  // Reacción instantánea a cambios del player + intervalo de respaldo.
  var scheduled = false;
  try {
    new MutationObserver(function () {
      if (scheduled) return;
      scheduled = true;
      setTimeout(function () { scheduled = false; check(); }, 300);
    }).observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
  setInterval(check, 2000);
  check();
})();
