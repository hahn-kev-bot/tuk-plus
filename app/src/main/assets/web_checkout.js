// Tuk plus web checkout fallback (docs/PLAN.md §8a). Runs at document start on https://tukapp.co only.
// 1. Merges the cart from Tuk plus into the web app's saved state, one time per hand-off.
// 2. Copies the web app's POST transactions request and response to Tuk plus.
//    It does not change or stop any request.
(function () {
  if (window.__tukplus) return;
  window.__tukplus = true;
  var H = __HANDOFF__;

  function post(m) {
    try { TukPlus.postMessage(JSON.stringify(m)); } catch (e) { /* no listener */ }
  }

  try {
    if (localStorage.getItem('tukplus_handoff') !== H.token) {
      var s = {};
      try { s = JSON.parse(localStorage.getItem('store') || '{}') || {}; } catch (e) { s = {}; }
      for (var k in H.values) s[k] = H.values[k];
      localStorage.setItem('store', JSON.stringify(s));
      localStorage.setItem('tukplus_handoff', H.token);
      post({ type: 'injected', keys: Object.keys(H.values) });
    } else {
      post({ type: 'inject_skipped' });
    }
  } catch (e) {
    post({ type: 'inject_error', error: String(e) });
  }

  // The web app's "Checkout" bar is position:absolute, bottom:0 inside #app. #app has
  // height: -webkit-fill-available. Chrome makes that the screen height, but the Android WebView
  // makes it 0 (owner's log 2026-10-01: app=0, bar=[-66,0,66]), so the bar was above the screen.
  // We give #app the visible height, and keep the web app's --vh (1% of the height) equal to
  // 1% of the visible height. The style rule uses !important, so the web app's own values
  // cannot override it. We log the sizes too.
  var vhStyle = null;
  function fixHeight() {
    try {
      var h = (window.visualViewport && window.visualViewport.height) || window.innerHeight;
      if (!h) return;
      if (!vhStyle) {
        vhStyle = document.createElement('style');
        vhStyle.id = 'tukplus-vh';
        (document.head || document.documentElement).appendChild(vhStyle);
      }
      var css = 'html{--vh:' + (h / 100) + 'px !important}' +
        '#app{height:' + h + 'px !important;min-height:' + h + 'px !important;max-height:' + h + 'px !important}';
      if (vhStyle.textContent !== css) vhStyle.textContent = css;
    } catch (e) { /* ignore */ }
  }
  function layout(reason) {
    try {
      var bar = document.querySelector('.bottom-checkout-wrapper');
      var r = bar ? bar.getBoundingClientRect() : null;
      var app = document.getElementById('app');
      post({
        type: 'layout', reason: reason, path: location.pathname,
        inner: [window.innerWidth, window.innerHeight],
        client: document.documentElement.clientHeight,
        visual: window.visualViewport ? Math.round(window.visualViewport.height) : null,
        vh: getComputedStyle(document.documentElement).getPropertyValue('--vh'),
        app: app ? Math.round(app.getBoundingClientRect().height) : null,
        dpr: window.devicePixelRatio,
        bar: r ? [Math.round(r.top), Math.round(r.bottom), Math.round(r.height)] : null
      });
    } catch (e) { /* ignore */ }
  }
  var resizeTimer = null;
  function onResize() {
    fixHeight();
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { layout('resize'); }, 500);
  }
  window.addEventListener('resize', onResize);
  if (window.visualViewport) window.visualViewport.addEventListener('resize', onResize);
  document.addEventListener('DOMContentLoaded', function () {
    fixHeight();
    setTimeout(function () { layout('load+3s'); }, 3000);
    setTimeout(function () { layout('load+10s'); }, 10000);
  });
  var clicks = 0;
  document.addEventListener('click', function () {
    if (clicks++ < 10) setTimeout(function () { layout('click'); }, 800);
  }, true);

  function isOrder(method, url) {
    return /^post$/i.test(method || '') && /\/transactions\/?(\?|$)/.test(String(url || ''));
  }

  var open = XMLHttpRequest.prototype.open;
  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__tukplus = { method: method, url: String(url) };
    return open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (body) {
    var xhr = this;
    var info = xhr.__tukplus;
    if (info && isOrder(info.method, info.url)) {
      var request = typeof body === 'string' ? body : null;
      post({ type: 'transaction_sent', url: info.url, request: request });
      xhr.addEventListener('loadend', function () {
        var response = null;
        try {
          response = (xhr.responseType === '' || xhr.responseType === 'text') ? xhr.responseText : JSON.stringify(xhr.response);
        } catch (e) { response = null; }
        post({ type: 'transaction', url: info.url, status: xhr.status, request: request, response: response });
      });
    }
    return send.apply(this, arguments);
  };

  if (window.fetch) {
    var originalFetch = window.fetch;
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || (input && input.method) || 'GET';
      if (!isOrder(method, url)) return originalFetch.apply(this, arguments);
      var request = init && typeof init.body === 'string' ? init.body : null;
      post({ type: 'transaction_sent', url: url, request: request });
      return originalFetch.apply(this, arguments).then(function (r) {
        r.clone().text().then(function (t) {
          post({ type: 'transaction', url: url, status: r.status, request: request, response: t });
        }, function () {});
        return r;
      });
    };
  }
})();
