/* Brave-TV anti-ads para youtube.com/tv (Leanback). Idempotente. */
(function () {
  if (window.__btvAntiAds) return;
  window.__btvAntiAds = true;

  function css() {
    var s = document.getElementById('btv-adcss');
    if (s) return;
    s = document.createElement('style');
    s.id = 'btv-adcss';
    s.textContent = [
      '.ytp-ad-overlay-container,.ytp-ad-overlay-slot,',
      '.ytd-display-ad-renderer,.ytd-promoted-sparkles-text-search-renderer,',
      '.ytd-in-feed-ad-layout-renderer,.ytd-ad-slot-renderer,',
      '[id*="masthead-ad"],.yt-mealbar-promo-renderer { display:none !important; }'
    ].join('');
    (document.head || document.documentElement).appendChild(s);
  }

  function skipBtn() {
    var sels = ['.ytp-skip-ad-button', '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button-modern'];
    for (var i = 0; i < sels.length; i++) {
      var b = document.querySelector(sels[i]);
      if (b && b.offsetParent !== null) { try { b.click(); } catch (e) {} return true; }
    }
    // Fallback por texto (Omitir / Skip), genérico para Leanback y desktop.
    var btns = document.querySelectorAll('button');
    for (var j = 0; j < btns.length; j++) {
      var t = (btns[j].innerText || '').trim().toLowerCase();
      if ((t === 'omitir' || t === 'omitir anuncios' || t === 'skip' || t === 'skip ads' || t.indexOf('omitir') === 0) && btns[j].offsetParent !== null) {
        try { btns[j].click(); } catch (e) {} return true;
      }
    }
    return false;
  }

  var userMuted = false,
    sbId = null,
    sbSegs = [],
    sbMuted = false;

  function video() { return document.querySelector('video'); }

  function adShowing() {
    return !!document.querySelector('.ad-showing, .ytp-ad-player-overlay, .ytp-ad-player-overlay-layout');
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

  function sbTick(v, id) {
    if (!sbSegs.length || v.paused) { if (sbMuted && !adShowing()) { try { v.muted = userMuted; } catch (e) {} sbMuted = false; } return; }
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

  function tick() {
    try {
      css();
      var v = video();
      if (!v) return;
      var ad = adShowing();
      if (ad) {
        if (!v.muted) { userMuted = false; try { v.muted = true; v.playbackRate = 16; } catch (e) {} }
        skipBtn();
      } else {
        if (v.muted && !sbMuted) { try { v.muted = userMuted; if (v.playbackRate !== 1) v.playbackRate = 1; } catch (e) {} }
      }
      var id = videoId();
      if (id && id !== sbId) loadSB(id);
      if (id) sbTick(v, id);
    } catch (e) {}
  }

  setInterval(tick, 800);
  tick();
})();
