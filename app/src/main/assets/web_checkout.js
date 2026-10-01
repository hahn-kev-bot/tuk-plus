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
