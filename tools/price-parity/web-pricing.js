// The Tuk web app's price code, copied as text from the web bundles, with a small
// wrapper that runs it on plain inputs (no browser, no Vue, no network).
//
// Part 1 (between BEGIN VERBATIM and END VERBATIM) is a text copy of the web code.
// Do not edit it by hand. refresh-verbatim.js writes it from the beautified bundles.
// Each function has a comment: bundle file, line in the beautified bundle, and name.
//
// Part 2 (after END VERBATIM) is our wrapper. It makes a fake Vue instance ("vm"):
// data() values, props, computed values as getters and methods bound to the vm, and the
// global mixin. Then it runs the same steps as the web UI: option sheet -> basket ->
// order type -> delivery address and fare -> "Place order" -> POST transactions body.
//
// No "use strict" at file level: module 3d9a (geojson-utils) needs sloppy mode `this`.

// BEGIN VERBATIM
var VERBATIM = {
  // shop-profile~21833f8f.83f1e1e0.js:121 module 84f5: package codes (workflow.data.fruit)
  module_84f5: function(t, e, s) {
      "use strict";
      s("28a5"), s("f559");
      var i = "r_",
          n = {
              hasFreeDelivery: function(t) {
                  return !!t && (t.startsWith("p_") || t.startsWith("f_") || t.startsWith("thai") || t.startsWith(i))
              },
              deliverySubsidy: function(t) {
                  if (!t) return 0;
                  if (t.startsWith(i) && t.split(i).length > 1) {
                      var e = t.split(i)[1].split("_");
                      return 2 !== e.length ? 0 : parseInt(e[1]) / 100
                  }
                  if (t.startsWith("p_") && t.split("p_").length > 1) {
                      var s = t.split("p_")[1].split("_");
                      return parseInt(s[0]) / 100
                  }
                  if (t.startsWith("f_") && t.split("f_").length > 1) {
                      var n = t.split("f_")[1].split("_");
                      return parseInt(n[0]) / 100
                  }
                  switch (t) {
                      case "thai0":
                      case "thai0_10":
                      case "thai0_15":
                      case "thai0_20":
                      case "thai0_25":
                          return 0;
                      case "thai10":
                      case "thai10_5":
                      case "thai1010":
                      case "thai10_10":
                      case "thai10_0":
                          return .1;
                      case "thai15":
                      case "thai15_5":
                      case "thai15_10":
                          return .15;
                      case "thai20":
                      case "thai20_0":
                      case "thai20_5":
                          return .2;
                      case "thai25":
                      case "thai25_0":
                          return .25;
                      default:
                          return 0
                  }
              },
              billing: function(t) {
                  if (!t) return 0;
                  if (t.startsWith("p_") && t.split("p_").length > 1) {
                      var e = t.split("p_")[1].split("_");
                      if (2 === e.length) return parseInt(e[1]) / 100
                  }
                  return t.startsWith("f_") && t.split("f_").length > 1 && (e = t.split("f_")[1].split("_"), 2 === e.length) || t.startsWith("thai") && t.split("thai").length > 1 && (e = t.split("thai")[1].split("_"), 2 === e.length) ? parseInt(e[1]) / 100 : 0
              },
              remit: function(t) {
                  if (!t) return 0;
                  if (t.startsWith(i) && t.split(i).length > 1) {
                      var e = t.split(i)[1].split("_");
                      return parseInt(e[0]) / 100
                  }
                  return 0
              }
          };
      e["a"] = n
  },
  // app~b07b7304.0c8b3ad8.js:6 module 2c95: distance helpers
  module_2c95: function(e, o, r) {
      "use strict";
      r("7f7f"), r("55dd");
      var t = [{
              name: "Bangkok",
              lat: 13.73114,
              lon: 100.57529
          }, {
              name: "Bagan",
              lat: 21.1717,
              lon: 94.8585
          }, {
              name: "Chiang Mai",
              lat: 18.796143,
              lon: 98.979263
          }, {
              name: "Singapore",
              lat: 1.29027,
              lon: 103.851959
          }, {
              name: "Phitsanulok",
              lat: 16.819044,
              lon: 100.266008
          }, {
              name: "Hua Hin",
              lat: 12.5684,
              lon: 99.9577
          }, {
              name: "Songkhla",
              lat: 7.1988,
              lon: 100.5951
          }, {
              name: "Hat Yai",
              lat: 7.0086,
              lon: 100.4747
          }, {
              name: "Krabi",
              lat: 8.0578,
              lon: 98.9152
          }, {
              name: "Phuket",
              lat: 7.9712,
              lon: 98.3455
          }, {
              name: "Koh Yao",
              lat: 8.0393,
              lon: 98.589
          }, {
              name: "Mae Hong Son",
              lat: 19.3004,
              lon: 97.9658
          }, {
              name: "Pai",
              lat: 19.3585,
              lon: 98.4422
          }, {
              name: "Chiang Rai",
              lat: 19.9102,
              lon: 99.835
          }, {
              name: "Lisbon",
              lat: 38.71709,
              lon: -9.13926
          }],
          i = {
              geos: function() {
                  return t
              },
              prettyDistance: function(e, o) {
                  if (o || (o = 10), e >= 1e3 * o) return "∞";
                  if (e <= 999) return "".concat(e, "m");
                  var r = e / 1e3;
                  return "".concat(e < 1e4 ? r.toFixed(1) : r.toFixed(0), "km")
              },
              distance: function(e, o) {
                  if (!e || !o) return 999e3;
                  e.lon || (e.lon = e.lng), o.lon || (o.lon = o.lng);
                  var r = this.getDistanceFromLatLonInKm(e.lat, e.lon, o.lat, o.lon);
                  return Math.round(1e3 * r)
              },
              distanceToUserInMeters: function(e, o) {
                  if (!e || !o) return 999e3;
                  e.lon || (e.lon = e.lng), o.lon || (o.lon = o.lng);
                  var r = this.getDistanceFromLatLonInKm(e.lat, e.lon, o.lat, o.lon);
                  return Math.round(1e3 * r)
              },
              getDistanceFromLatLonInKm: function(e, o, r, t) {
                  var i = 6371,
                      a = this.deg2rad(r - e),
                      s = this.deg2rad(t - o),
                      n = Math.sin(a / 2) * Math.sin(a / 2) + Math.cos(this.deg2rad(e)) * Math.cos(this.deg2rad(r)) * Math.sin(s / 2) * Math.sin(s / 2),
                      l = 2 * Math.atan2(Math.sqrt(n), Math.sqrt(1 - n)),
                      d = i * l;
                  return d
              },
              deg2rad: function(e) {
                  return e * (Math.PI / 180)
              },
              distKM: function(e, o) {
                  var r = this.distanceToUserInMeters(e, o);
                  return r / 1e3
              },
              getRegion: function(e) {
                  var o = this;
                  return t.forEach((function(r) {
                      r.distance = o.distanceToUserInMeters(r, e)
                  })), t.sort((function(e, o) {
                      return e.distance - o.distance
                  })), t[0].name
              }
          };
      o["a"] = i
  },
  // app~a97bfcba.4d7f22cd.js:200 module f17f: $date helpers
  module_f17f: function(t, n, e) {
      "use strict";
      var o = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"],
          i = {
              getShortDay: function(t) {
                  return t ? o[t.getDay()] : o[(new Date).getDay()]
              },
              getHHMM: function() {
                  var t = this.padZero((new Date).getHours()),
                      n = this.padZero((new Date).getMinutes());
                  return "".concat(t).concat(n)
              },
              padZero: function(t) {
                  return t < 10 && (t = "0" + t), t
              }
          };
      n["a"] = i
  },
  // ride~shop-profile~31ecd969.b76bf2b8.js:86 module 3d9a: geojson-utils
  module_3d9a: function(t, n, o) {
      (function() {
          var n = this.gju = {};
  
          function o(t) {
              for (var n = [], o = [], a = 0; a < t[0].length; a++) n.push(t[0][a][1]), o.push(t[0][a][0]);
              return n = n.sort((function(t, n) {
                  return t - n
              })), o = o.sort((function(t, n) {
                  return t - n
              })), [
                  [n[0], o[0]],
                  [n[n.length - 1], o[o.length - 1]]
              ]
          }
  
          function a(t, n, o) {
              for (var a = [
                      [0, 0]
                  ], r = 0; r < o.length; r++) {
                  for (var e = 0; e < o[r].length; e++) a.push(o[r][e]);
                  a.push(o[r][0]), a.push([0, 0])
              }
              var i = !1;
              for (r = 0, e = a.length - 1; r < a.length; e = r++) a[r][0] > n != a[e][0] > n && t < (a[e][1] - a[r][1]) * (n - a[r][0]) / (a[e][0] - a[r][0]) + a[r][1] && (i = !i);
              return i
          }
          t.exports && (t.exports = n), n.lineStringsIntersect = function(t, n) {
              for (var o = [], a = 0; a <= t.coordinates.length - 2; ++a)
                  for (var r = 0; r <= n.coordinates.length - 2; ++r) {
                      var e = {
                              x: t.coordinates[a][1],
                              y: t.coordinates[a][0]
                          },
                          i = {
                              x: t.coordinates[a + 1][1],
                              y: t.coordinates[a + 1][0]
                          },
                          s = {
                              x: n.coordinates[r][1],
                              y: n.coordinates[r][0]
                          },
                          c = {
                              x: n.coordinates[r + 1][1],
                              y: n.coordinates[r + 1][0]
                          },
                          u = (c.x - s.x) * (e.y - s.y) - (c.y - s.y) * (e.x - s.x),
                          h = (i.x - e.x) * (e.y - s.y) - (i.y - e.y) * (e.x - s.x),
                          l = (c.y - s.y) * (i.x - e.x) - (c.x - s.x) * (i.y - e.y);
                      if (0 != l) {
                          var d = u / l,
                              f = h / l;
                          0 <= d && d <= 1 && 0 <= f && f <= 1 && o.push({
                              type: "Point",
                              coordinates: [e.x + d * (i.x - e.x), e.y + d * (i.y - e.y)]
                          })
                      }
                  }
              return 0 == o.length && (o = !1), o
          }, n.pointInBoundingBox = function(t, n) {
              return !(t.coordinates[1] < n[0][0] || t.coordinates[1] > n[1][0] || t.coordinates[0] < n[0][1] || t.coordinates[0] > n[1][1])
          }, n.pointInPolygon = function(t, r) {
              for (var e = "Polygon" == r.type ? [r.coordinates] : r.coordinates, i = !1, s = 0; s < e.length; s++) n.pointInBoundingBox(t, o(e[s])) && (i = !0);
              if (!i) return !1;
              var c = !1;
              for (s = 0; s < e.length; s++) a(t.coordinates[1], t.coordinates[0], e[s]) && (c = !0);
              return c
          }, n.pointInMultiPolygon = function(t, r) {
              for (var e = "MultiPolygon" == r.type ? [r.coordinates] : r.coordinates, i = !1, s = !1, c = 0; c < e.length; c++) {
                  for (var u = e[c], h = 0; h < u.length; h++) i || n.pointInBoundingBox(t, o(u[h])) && (i = !0);
                  if (!i) return !1;
                  for (h = 0; h < u.length; h++) s || a(t.coordinates[1], t.coordinates[0], u[h]) && (s = !0)
              }
              return s
          }, n.numberToRadius = function(t) {
              return t * Math.PI / 180
          }, n.numberToDegree = function(t) {
              return 180 * t / Math.PI
          }, n.drawCircle = function(t, o, a) {
              for (var r = [o.coordinates[1], o.coordinates[0]], e = t / 1e3 / 6371, i = [n.numberToRadius(r[0]), n.numberToRadius(r[1])], s = (a = a || 15, [
                      [r[0], r[1]]
                  ]), c = 0; c < a; c++) {
                  var u = 2 * Math.PI * c / a,
                      h = Math.asin(Math.sin(i[0]) * Math.cos(e) + Math.cos(i[0]) * Math.sin(e) * Math.cos(u)),
                      l = i[1] + Math.atan2(Math.sin(u) * Math.sin(e) * Math.cos(i[0]), Math.cos(e) - Math.sin(i[0]) * Math.sin(h));
                  s[c] = [], s[c][1] = n.numberToDegree(h), s[c][0] = n.numberToDegree(l)
              }
              return {
                  type: "Polygon",
                  coordinates: [s]
              }
          }, n.rectangleCentroid = function(t) {
              var n = t.coordinates[0],
                  o = n[0][0],
                  a = n[0][1],
                  r = n[2][0],
                  e = n[2][1],
                  i = r - o,
                  s = e - a;
              return {
                  type: "Point",
                  coordinates: [o + i / 2, a + s / 2]
              }
          }, n.pointDistance = function(t, o) {
              var a = t.coordinates[0],
                  r = t.coordinates[1],
                  e = o.coordinates[0],
                  i = o.coordinates[1],
                  s = n.numberToRadius(i - r),
                  c = n.numberToRadius(e - a),
                  u = Math.pow(Math.sin(s / 2), 2) + Math.cos(n.numberToRadius(r)) * Math.cos(n.numberToRadius(i)) * Math.pow(Math.sin(c / 2), 2),
                  h = 2 * Math.atan2(Math.sqrt(u), Math.sqrt(1 - u));
              return 6371 * h * 1e3
          }, n.geometryWithinRadius = function(t, o, a) {
              if ("Point" == t.type) return n.pointDistance(t, o) <= a;
              if ("LineString" == t.type || "Polygon" == t.type) {
                  var r, e = {};
                  for (var i in r = "Polygon" == t.type ? t.coordinates[0] : t.coordinates, r)
                      if (e.coordinates = r[i], n.pointDistance(e, o) > a) return !1
              }
              return !0
          }, n.area = function(t) {
              for (var n = 0, o = t.coordinates[0], a = o.length - 1, r = 0; r < o.length; a = r++) {
                  var e = {
                          x: o[r][1],
                          y: o[r][0]
                      },
                      i = {
                          x: o[a][1],
                          y: o[a][0]
                      };
                  n += e.x * i.y, n -= e.y * i.x
              }
              return n /= 2, n
          }, n.centroid = function(t) {
              for (var o, a = 0, r = 0, e = t.coordinates[0], i = e.length - 1, s = 0; s < e.length; i = s++) {
                  var c = {
                          x: e[s][1],
                          y: e[s][0]
                      },
                      u = {
                          x: e[i][1],
                          y: e[i][0]
                      };
                  o = c.x * u.y - u.x * c.y, a += (c.x + u.x) * o, r += (c.y + u.y) * o
              }
              return o = 6 * n.area(t), {
                  type: "Point",
                  coordinates: [r / o, a / o]
              }
          }, n.simplify = function(t, n) {
              var o, a, r, e, i, s, c, u, h, l, d, f, g, y, p, M, v, x;
              n = n || 20, t = t.map((function(t) {
                  return {
                      lng: t.coordinates[0],
                      lat: t.coordinates[1]
                  }
              }));
              var b = Math.PI / 180 * .5,
                  m = new Array,
                  P = new Array,
                  w = new Array;
              if (t.length < 3) return t;
              o = t.length, h = 360 * n / (2 * Math.PI * 6378137), h *= h, r = 0, P[0] = 0, w[0] = o - 1, a = 1;
              while (a > 0)
                  if (e = P[a - 1], i = w[a - 1], a--, i - e > 1) {
                      for (l = t[i].lng() - t[e].lng(), d = t[i].lat() - t[e].lat(), Math.abs(l) > 180 && (l = 360 - Math.abs(l)), l *= Math.cos(b * (t[i].lat() + t[e].lat())), f = l * l + d * d, R = e + 1, s = e, u = -1; R < i; R++) g = t[R].lng() - t[e].lng(), y = t[R].lat() - t[e].lat(), Math.abs(g) > 180 && (g = 360 - Math.abs(g)), g *= Math.cos(b * (t[R].lat() + t[e].lat())), p = g * g + y * y, M = t[R].lng() - t[i].lng(), v = t[R].lat() - t[i].lat(), Math.abs(M) > 180 && (M = 360 - Math.abs(M)), M *= Math.cos(b * (t[R].lat() + t[i].lat())), x = M * M + v * v, c = p >= f + x ? x : x >= f + p ? p : (g * d - y * l) * (g * d - y * l) / f, c > u && (s = R, u = c);
                      u < h ? (m[r] = e, r++) : (a++, P[a - 1] = s, w[a - 1] = i, a++, P[a - 1] = e, w[a - 1] = s)
                  } else m[r] = e, r++;
              m[r] = o - 1, r++;
              for (var k = new Array, R = 0; R < r; R++) k.push(t[m[R]]);
              return k.map((function(t) {
                  return {
                      type: "Point",
                      coordinates: [t.lng, t.lat]
                  }
              }))
          }, n.destinationPoint = function(t, o, a) {
              a /= 6371, o = n.numberToRadius(o);
              var r = n.numberToRadius(t.coordinates[0]),
                  e = n.numberToRadius(t.coordinates[1]),
                  i = Math.asin(Math.sin(e) * Math.cos(a) + Math.cos(e) * Math.sin(a) * Math.cos(o)),
                  s = r + Math.atan2(Math.sin(o) * Math.sin(a) * Math.cos(e), Math.cos(a) - Math.sin(e) * Math.sin(i));
              return s = (s + 3 * Math.PI) % (2 * Math.PI) - Math.PI, {
                  type: "Point",
                  coordinates: [n.numberToDegree(s), n.numberToDegree(i)]
              }
          }
      })()
  },
  mixin: {
    // app~50b71177.bdb8089a.js:1151 mixin deepCopy
    deepCopy: function(t) {
        return JSON.parse(JSON.stringify(t))
    },
    // app~50b71177.bdb8089a.js:1154 mixin weightPrice
    weightPrice: function(t) {
        if (!t.by_weight || !t.actual_weight) return t.price;
        var e = parseInt(t.actual_weight);
        switch (t.by_weight) {
            case "per gram":
                return e * t.price;
            case "per 100 grams":
                return e * t.price / 100;
            case "per kg":
                return e * t.price / 1e3;
            default:
                return t.price
        }
    },
    // app~50b71177.bdb8089a.js:1168 mixin discountedPrice
    discountedPrice: function(t) {
        var e = this.weightPrice(t);
        if (!t.discount) return parseInt(e);
        var o = parseInt(e) * (1 - t.discount / 100);
        return "number" === t.discount_type && (o = parseInt(e) - t.discount), o < 0 ? 0 : Math.round(o)
    },
    // app~50b71177.bdb8089a.js:1174 mixin discountedTotalPrice
    discountedTotalPrice: function(t) {
        var e = this,
            o = 0;
        return t.options && t.options.forEach((function(t) {
            t.option && (o += e.optionPrice(t.option))
        })), this.discountedPrice(t) + o
    },
    // app~50b71177.bdb8089a.js:1181 mixin optionPrice
    optionPrice: function(t) {
        var e = t.quantity || 1;
        return parseInt(t.discounted_price || t.price) * e
    },
    // app~50b71177.bdb8089a.js:1185 mixin actualQuantity
    actualQuantity: function(t) {
        var e = t.out_of_stock || 0,
            o = t.quantity || 1;
        return o - e
    }
  },
  // ShopPromosTab. Imports as in module f09f: s = require, o = event bus (0c12),
  // a = toConsumableArray (75fc), w = geo (2c95), x = package codes (84f5), T = shortid (8dee), S = object spread.
  shopPromosTab: function (s, o, a, w, x, T, S, C, setTimeout, clearTimeout) {
    return {
      // shop-profile~1c39816d.ec3a39e1.js:1881 ShopPromosTab data
      data: function() {
          return {
              songkranMinOrder: 0,
              hideOutOfStock: !0,
              showOrderOptions: !1,
              giftDetails: null,
              paymentMethod: null,
              showFees: !1,
              showPreface: !0,
              filterMemberItems: !1,
              filterFlashDeals: !1,
              lalamoveQuote: null,
              setAddressInitialised: !1,
              itemForRemoval: null,
              showRemoval: !1,
              whoPaysDelivery: null,
              agentModeData: null,
              showAgentModeWall: !1,
              showAgentModeOptions: !1,
              showPics: !1,
              showTimeSelector: !1,
              showSetAddress: !1,
              variesTime: 0,
              deliveryPromoCode: null,
              width: 1e3,
              showHowTo: !1,
              showDineInItems: !1,
              lang: this.$store.state.language,
              basket: null,
              showBasket: !1,
              fulfilmentType: null,
              manualItems: [],
              manualItemName: null,
              manualItemPrice: null,
              menuImg: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAOEAAADhCAMAAAAJbSJIAAAAflBMVEX///8CAgIAAADk5ORQUFDV1dVcXFxYWFiSkpIrKyvp6elGRkbz8/O9vb3v7+9+fn7b29swMDB4eHj5+fnNzc3GxsZsbGy2trY3NzdycnJmZmampqacnJytra2zs7OkpKSXl5dJSUmLi4s1NTUiIiI/Pz8SEhIZGRklJSWEhIR+9H6tAAAKpUlEQVR4nO2d2ZajIBCG28raSUyMS8y+dZbJ+7/gCIjRNIIgKn0O/8XMxHHhE6SKosSvLysrKysrKysrKysrKysrKysrKysrKyt96oXucX/1V9tEq81ivIvdwazrQunRPDz5owew9bN9xYOuS1hDwWC/pSwOS+n/fS/iXtdlVZB36pej/eZ8+u686yLLKHx9V8TLU/Z3k64LXk3e6ylFl6fsH4Kuiy/S7LRUoctBbsOuGXjyNjXwMsjbztSKDPu1+WhFXk3sdqK7Fj4KuTCNMdTIlzK+TGqr3kUrHmU8d81FNfP18xHGh9s1G9ZBb/ssMva79+cm08b4COO5Y8BTo3wYcel1yNdwBVLGfWeAbgt8GLHfkXF8tQOIGJ0unNXZvTXAblrqoEU+jLhtGTBuFxAx3lt9GM+tAyaIjxat/6IpQO5pAVqLy2318xWCjOU7Re0AjrQC0pDi8BUPgq95dOb00QCtuOJDTYC0xtbDcezlx4LhjYMYNw+oo4nSetue4x4jth+WX6KFhrqqB0jZ/HPklY7hJ5xrADTs3viqgLRNXvx9JOr1eYTJiRoda4wVAFO0W98/RNWMNpcwOV+DoXE5T4ZW2+3yOoRzibgSn9CBf43FqKr7ohRudD148ndcQOjApQE4pHnlmSSA5/YaK0+BiggdWGgFy7QWPB5E9+3YrdkXCAkdOOpBKmrBscNIj/tq7/Z0PCIVCJvoUMtDFvBY+6doou/xFxM68K3talTlDyE4P9P7fb1cfmtTled9o5vwwrwqI/ugAbGvrNkJ37Muk1z80m9cyzJErdkqPdZVANqZk/ambEStkZs7E7C1IXefjaixnR6YbfSl7wICTZgNFRxtTWjGvkCL8wkl/Zw214Y5ZAJoMfTFjitos/seuwq7J9TmgrMjTyYQOnpiGlGZPTKBcKnj7CVDCiMIHR2xt5IqNIVQQyWWjQrNINRQiaWxS1MIa1ci22Eyh7B2d8q2hUYR1rSJG07oQolQLWLNI6zn2LA90hqEA1DylnmzQfW80xPv3qkQrtSiD1xCtZuWihMxUSKcg9qokjujBweFM6biBbmVCM/gwFqhIHzCGn3Ni9s4KhEW+nIcsIPVPnZDj0RZKj5DfMIaUzW8MH5Fwn9n8ncvGg/TsBkJoOFSzSpmAvHnndXTiThzsdUI59EVYLVAMONCWBAcNM/m+v+SjT8VOgoB4V2VkNdIKxFOlpAlbOVj5rQGk4LDrUoTExAqOx/lGQOVT3tP6ij9p/eE7NDUSAcPgGGVkgiyI+CkBuhpuHHLxyjrBwLq42bPzRyu40qBXRGhYm/KMfdVCYNz0sdk+9GgJIzSDTNUl1XstYhQMf7NzwxSaPxofg71NwCyR4pyeNQGGAF/yleBEJuLCyj4NULCq2xZkAailiFNmMCt5l+eL58PKyRUGgfzH0MFwsBZkroL9rKTKsJMM+l2X+Gs8oQzdQ9ZTKgy7uRaQ3PG+GlpFBw39nyPsYQKw06uU2oe4Y/4JJ9izmubS6jQ1ZSnz7AJJ4kKnkWAtiR/zyYFBV/p9txx83SvoLA1t5M4a1chHsWa2OYQesiQ9/NbtqlpvxYTK454zxXdC8U10LDfR39nHeIM/bpJEcpPeYuS2D4IQ1z83KYJ3hClI8O34h4BzRNOcfr/J2HOilcglB5eCHy2X4Qu2h389wY8uERBIpyTWiTMHY0CG4Qwb9RQFFOSMHfpaioPdrMJj4QjexLJHUJmagyFzCJCmAWktBFWGmfmJfBKfxGSrvf9xueO/B6nhPFXkIreuzSVQxvhVJYwkiQkEQ940N9kQI/aDibM9QOUkIx4tBFKp/LtJAnTHH7ag7jpz1EJIR4lBjoJpQfBwpz1D8J0uEx9i+n7J4sQHuiPvlZC2VfbuHE2BuGFVkxIKPAvB5yUMDsqJIQ+ytFB/VB3hCKX5pMQxQ1vP5DGYDaomu4kXlG0FinhBg1dkEOgi9CRDnwLXx0pEgaPpEiXHZDNuNjjDXnWCu2dEq7wnEjitsy0Ecq6bZKEuEijL9wAkaOGnnyf8BbrMKKEpPWuvrQRysZ+yid/mYRz0vReuNoCBzPgHx4h2fVSBRkh9nxh//gjhLhrGeNMWzgdCdqZFJrZlyLCCe6ZHEJ4BSf3jgE+Ty7M2wShZCtFDgJyftFMxPKbWIIjQSslTHYA0uNO051fhdOt3scYQBgDcbMHtNBRWswdhzBzE6bUvtB9RlAcLRjQ0+woBjH12IcKScsdQzEmnSNMo13Yp0Q2k6ZuYycXpEbAjnTIQdLiozLhdkLcNfxE4WpZEMI7zbyfRr0cIZlGx4R4UgNu4+Nxj9cTKQwWqhDKWvyrHCHuCvFNX2bNbQ44BsYaH2bP2DkjxF4R3e/TR2nCpxEFoj4It0CvgdvrGW8E7HoXPdwPQmIyMGF+oY3PqY0mPO+jHOEa3XlyjeK/nuw4TRbWp3EaclczbYrlrUD4kM2qEYVLP62Fm4j8c+C6qeV28UbPzSvuzWI32yMR+u+sH4qu2+Fwu4g/66MCoXQSi/B9UbPipU4xzldFoqC+cYTy7wj9NUL5OdLnHyOUf29WOO1qGKH8TL4oUGMaofwKRCKDaBahytyTKOhtGKG0seAmQDMI3ZF29XMjLjGhSio0f4GBD8Jv0K9cFFtMqPJiiSCeWCQcjPUr53uLCVUeGUFXY9RzqNLRCP02swjV3ute/iFCtQRTfqjGJELV92bcv0P4EJ+CJf5UvlGE0pP4qbgptEYRqr6hx50HNohQfRGQ+V8hVF8DhJcXZRKh+hIgzBU/jCOs83oeb3xhEKFqT4rEWejSIMI66+Rw19k0hLDm++rlvqk5hLtapy43iaYQ1nsNmOe5GUNYdzGn0nlEQwjrr2ZaajBMIVyJjxaoLFxjBqGOUpQtmGgIYf0qLK1EIwj1FKKkEs0grOOwvXU2llDb6pDMqUQjCM+azs8MSRlAmH+rpqZYSyl1T6hzjX1W+NsAQumXSDhidDadE8rneXH1e6qte0K9a3r/nhHumjBbmEGXfmXydUyoeaFkpM/+tFvCJr448zmM6piwiUWMwyJip4Ty7+JVUvFDXV0SajYUb20NIWxwJfH8PEZ3hFBnCTqBZg8wgbDJpdJ7+WTzjgi1BC7K9U6ObpUwNxutksEmpffCyW19yA7pvUKl+upzlZWtLNeQTWIpfgOqrCspqzdiQ98k+qW3rwH3Vr6o8UZcH8JB44reHzaHaUtflM/uaQMpl0y11cm8JVw0owm1+8HcSZUPFukGbO+bKEjBpeVqbNJVK1FjX8wtAezgy9zH9hAB1tpjFlXUu7XE2PYjmNOmDURo7UPHLJV/UE8jYL+TFko1GzbM2EEf+inXaZAxsfKdViBR8IKGGAFuHT6BeXn9JhCT+3bumuyt6Fs3Y8LnNxQyVFSs1TgmfKsWgyQVdfzR9Twm59mYx4cU9TUwovHgq8Evb9cUWsm6FmNy+PJggIHgKDjeQZUSHei39unPGprsVSDRISv9k4JNaXLqQ3VKvOs/P2opzKRLs+i6LgaSSuHgOdy1+FFTnZoP9qvnZ8Qsj5bo/jqaaRmqK/Ci/WK7hg89L5vrIfzrcEUFE28wCMNw4PXMtgdWVlZWVlZWVlZWVlZWVlZWVlZK+g9GVJzXGDVB4gAAAABJRU5ErkJggg==",
              deliveryFee: 0,
              totalDeliveryFee: 0,
              deliveryFare: null,
              drivingRoute: null,
              address: null,
              contactlessNote: null,
              isInit: !0,
              loginCheckoutCallback: "SHOP_LOGIN_CHECKOUT_CALLBACK",
              scrolled: !1,
              categoryDebounce: !1,
              categoryTimer: null,
              searchTerm: "",
              showSubmit: !1,
              showOptions: !1,
              showPreorder: !1,
              selectedItem: null,
              clamped: !0,
              scrollTopPos: 0,
              fulfilmentTime: null,
              cashback: 0,
              promoQuery: null,
              imgFallback: {
                  loading: this.$store.state.menuItemPlaceholderPic,
                  error: this.$store.state.errorProfilePic
              },
              pulseAgentMode: !1,
              testFleet: "Test Express Delivery",
              z: 0
          }
      },
      computed: {
      // shop-profile~1c39816d.ec3a39e1.js:1954 ShopPromosTab computed extraDistance
      extraDistance: function() {
          var e = this.$store.state.user;
          return e && e.data.delivery_options && e.data.delivery_options.extra_distance || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:1965 ShopPromosTab computed isFreeGiftOrder
      isFreeGiftOrder: function() {
          return this.basketItems.find((function(e) {
              return e.item.free_gift
          }))
      },
      // shop-profile~1c39816d.ec3a39e1.js:1970 ShopPromosTab computed freeGiftOver
      freeGiftOver: function() {
          return this.freeGift ? parseInt(this.freeGift.free_gift) : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:1973 ShopPromosTab computed isPeakHour
      isPeakHour: function() {
          var e = (new Date).getHours();
          return [17, 18].includes(e)
      },
      // shop-profile~1c39816d.ec3a39e1.js:1992 ShopPromosTab computed isTestFleet
      isTestFleet: function() {
          return !!this.commerce.data.test || "Test Express Delivery" === this.commerce.data.express
      },
      // shop-profile~1c39816d.ec3a39e1.js:1995 ShopPromosTab computed isTest
      isTest: function() {
          return !!this.agentModeData && this.agentModeData.isTest
      },
      // shop-profile~1c39816d.ec3a39e1.js:1986 ShopPromosTab computed businessNoteForDriver
      businessNoteForDriver: function() {
          return this.commerce.data.delivery_options ? this.commerce.data.delivery_options.driver_note : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:1989 ShopPromosTab computed businessSignName
      businessSignName: function() {
          return this.commerce.data.delivery_options ? this.commerce.data.delivery_options.sign_name : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2008 ShopPromosTab computed isContactlessDelivery
      isContactlessDelivery: function() {
          return this.contactlessNote
      },
      // shop-profile~1c39816d.ec3a39e1.js:2047 ShopPromosTab computed takeawayDiscount
      takeawayDiscount: function() {
          return this.commerce.data.takeaway_discount
      },
      // shop-profile~1c39816d.ec3a39e1.js:2050 ShopPromosTab computed dineinDiscount
      dineinDiscount: function() {
          return this.commerce.data.dinein_discount
      },
      // shop-profile~1c39816d.ec3a39e1.js:2053 ShopPromosTab computed fulfilmentDiscount
      fulfilmentDiscount: function() {
          return !(!this.isTakeaway || !this.takeawayDiscount) || !(!this.isDinein || !this.dineinDiscount)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2056 ShopPromosTab computed fulfilmentDiscountAmount
      fulfilmentDiscountAmount: function() {
          return this.fulfilmentDiscountPercent ? Math.round(this.fulfilmentDiscountPercent * this.basketValue) : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2059 ShopPromosTab computed fulfilmentDiscountPercent
      fulfilmentDiscountPercent: function() {
          var e = 0;
          return this.isTakeaway && this.takeawayDiscount && (e = this.takeawayDiscount), this.isDinein && this.dineinDiscount && (e = this.dineinDiscount), e / 100
      },
      // shop-profile~1c39816d.ec3a39e1.js:2105 ShopPromosTab computed business
      business: function() {
          return this.$store.state.selectedBusiness
      },
      // shop-profile~1c39816d.ec3a39e1.js:2111 ShopPromosTab computed commerce
      commerce: function() {
          return this.business && this.business.workflows ? this.business.workflows.find((function(e) {
              return "Commerce" === e.name
          })) : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2116 ShopPromosTab computed locations
      locations: function() {
          return this.commerce ? this.commerce.data.locations : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2140 ShopPromosTab computed promos
      promos: function() {
          if (!this.commerce) return !1;
          if (!this.commerce.blobs) return !1;
          var e = this.commerce.blobs.find((function(e) {
                  return "digital_menu" === e.blob_type
              })),
              t = S({}, e);
          return t.data.sort((function(e, t) {
              return (e.out_of_stock || !1) == (t.out_of_stock || !1) ? 0 : e.out_of_stock ? 1 : -1
          })), t
      },
      // shop-profile~1c39816d.ec3a39e1.js:2151 ShopPromosTab computed freeGift
      freeGift: function() {
          return this.items.find((function(e) {
              return e.free_gift && !e.out_of_stock && !e.hidden
          }))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2183 ShopPromosTab computed items
      items: function() {
          var e = this;
          if (!this.promos && !this.manualItems) return [];
          if (!this.promos && this.manualItems) return this.manualItems;
          var t = this.promos.data.concat(this.manualItems),
              s = this.$date.getShortDay();
          return t = t.filter((function(e) {
              return !e.schedule || !e.schedule.length || e.schedule.includes(s.toLowerCase())
          })), t = t.filter((function(e) {
              return !e.hidden && (e.tags, !0)
          })), t = t.filter((function(t) {
              return !t.tags || e.validTimeTag(t.tags)
          })), t = t.filter((function(t) {
              return !e.hideOutOfStock || !t.out_of_stock
          })), t = t.filter((function(t) {
              return !e.invalidPrice(t)
          })), this.filterFlashDeals ? t.filter((function(e) {
              return e.flash_deal
          })) : this.filterMemberItems ? t.filter((function(t) {
              return e.isMembersOnly(t)
          })) : t.filter((function(t) {
              return JSON.stringify(t).toLowerCase().includes(e.searchTerm)
          }))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2207 ShopPromosTab computed languages
      languages: function() {
          return this.commerce ? this.commerce.data.languages || ["en", "th"] : []
      },
      // shop-profile~1c39816d.ec3a39e1.js:2216 ShopPromosTab computed basketItems
      basketItems: function() {
          return this.basket ? this.basket.items : []
      },
      // shop-profile~1c39816d.ec3a39e1.js:2219 ShopPromosTab computed basketItemCount
      basketItemCount: function() {
          if (!this.basket) return 0;
          var e = 0;
          return this.basket.items.forEach((function(t) {
              e += t.quantity
          })), e
      },
      // shop-profile~1c39816d.ec3a39e1.js:2226 ShopPromosTab computed basketValue
      basketValue: function() {
          var e = this;
          if (!this.basket) return 0;
          var t = 0;
          return this.basket.items.forEach((function(s) {
              t += e.discountedTotalPrice(s.item) * s.quantity
          })), t
      },
      // shop-profile~1c39816d.ec3a39e1.js:2234 ShopPromosTab computed vatableBasketValue
      vatableBasketValue: function() {
          var e = this;
          if (!this.basket) return 0;
          var t = 0;
          return this.basket.items.forEach((function(s) {
              s.item.hasOwnProperty("vatable") && !s.item.vatable || (t += e.discountedTotalPrice(s.item) * s.quantity)
          })), t
      },
      // shop-profile~1c39816d.ec3a39e1.js:2242 ShopPromosTab computed totalValue
      totalValue: function() {
          var e = this.vat,
              t = this.fulfilmentDiscountAmount,
              s = this.basketValue + e - t;
          return this.isDelivery ? s + this.actualDeliveryFee : s
      },
      // shop-profile~1c39816d.ec3a39e1.js:2257 ShopPromosTab computed isDelivery
      isDelivery: function() {
          return "delivery" === this.fulfilmentType
      },
      // shop-profile~1c39816d.ec3a39e1.js:2260 ShopPromosTab computed isTakeaway
      isTakeaway: function() {
          return "take-away" === this.fulfilmentType
      },
      // shop-profile~1c39816d.ec3a39e1.js:2263 ShopPromosTab computed isDinein
      isDinein: function() {
          return "dine-in" === this.fulfilmentType
      },
      // shop-profile~1c39816d.ec3a39e1.js:2272 ShopPromosTab computed fulfilmentOptions
      fulfilmentOptions: function() {
          return this.commerce.data.fulfilment_options || []
      },
      // shop-profile~1c39816d.ec3a39e1.js:2284 ShopPromosTab computed delayedDelivery
      delayedDelivery: function() {
          if (this.fulfilmentTime) return !0;
          var e = this.commerce.data;
          return !!e.delivery_options && "delayed" === e.delivery_options.type
      },
      // shop-profile~1c39816d.ec3a39e1.js:2289 ShopPromosTab computed delayDuration
      delayDuration: function() {
          var e = this.commerce.data;
          return e.delivery_options ? e.delivery_options.delay_duration : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2293 ShopPromosTab computed thirdPartyDelivery
      thirdPartyDelivery: function() {
          var e = this.commerce.data;
          return !!e.delivery_options && "third-party" === e.delivery_options.type
      },
      // shop-profile~1c39816d.ec3a39e1.js:2297 ShopPromosTab computed autoconfirm
      autoconfirm: function() {
          var e = this.commerce.data;
          return !!e.order_options && e.order_options.autoconfirm
      },
      // shop-profile~1c39816d.ec3a39e1.js:2301 ShopPromosTab computed reversedDelivery
      reversedDelivery: function() {
          if (this.agentModeData) return "receive" === this.agentModeData.mode;
          var e = this.commerce.data;
          return !!e.delivery_options && e.delivery_options.reversed
      },
      // shop-profile~1c39816d.ec3a39e1.js:2306 ShopPromosTab computed extraTukpay
      extraTukpay: function() {
          var e = this.commerce.data;
          return e.delivery_options && e.delivery_options.extra_tukpay || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2310 ShopPromosTab computed extraCash
      extraCash: function() {
          var e = this.commerce.data;
          return e.delivery_options && e.delivery_options.extra_cash || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2314 ShopPromosTab computed deliveryRemit
      deliveryRemit: function() {
          var e = this.fleetRemit + this.customerRemit + this.shopRemit;
          return e || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2318 ShopPromosTab computed fleetRemit
      fleetRemit: function() {
          return this.deliveryWorkflows ? this.deliveryWorkflows.express && this.deliveryWorkflows.express.data.remit_amount ? this.deliveryWorkflows.express.data.remit_amount || 0 : this.deliveryWorkflows.fallback && this.deliveryWorkflows.fallback.data.remit_amount || 0 : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2321 ShopPromosTab computed customerRemit
      customerRemit: function() {
          var e = this.$store.state.user;
          return e && e.data && e.data.delivery_options && e.data.delivery_options.remit_amount || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2325 ShopPromosTab computed shopRemit
      shopRemit: function() {
          return this.commerce.data.delivery_options && this.commerce.data.delivery_options.remit_amount || 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2331 ShopPromosTab computed shopDeliveryFee
      shopDeliveryFee: function() {
          return this.tukExpressFreeDelivery ? this.deliveryFare.client : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2334 ShopPromosTab computed tukExpressFreeDelivery
      tukExpressFreeDelivery: function() {
          return "shop" === this.whoPaysDelivery
      },
      // shop-profile~1c39816d.ec3a39e1.js:2337 ShopPromosTab computed freeDelivery
      freeDelivery: function() {
          return !!this.tukExpressFreeDelivery || (!!this.deliverySubsidyPercent || (!!this.freeDeliveryOver || this.commerce.data.free_delivery))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2340 ShopPromosTab computed dynamicFreeDeliveryOver
      dynamicFreeDeliveryOver: function() {
          if (!this.deliveryFare) return null;
          if (!this.deliverySubsidyPercent) return null;
          var e = this.deliveryFare.client / this.deliverySubsidyPercent;
          return Math.ceil(e)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2346 ShopPromosTab computed fixedFreeDeliveryOver
      fixedFreeDeliveryOver: function() {
          if (!this.deliveryFare) return null;
          var e = this.commerce.data;
          return e.free_delivery_over ? e.free_delivery_distance && this.deliveryFare.distance > e.free_delivery_distance ? null : e.free_delivery_over : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2351 ShopPromosTab computed freeDeliveryOver
      freeDeliveryOver: function() {
          var e = this.fixedFreeDeliveryOver,
              t = this.dynamicFreeDeliveryOver;
          return e && t ? t < e ? t : e : t || (this.deliverySubsidyPercent ? null : e)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2356 ShopPromosTab computed freeDeliveryTo
      freeDeliveryTo: function() {
          return this.commerce.data.free_delivery_to
      },
      // shop-profile~1c39816d.ec3a39e1.js:2359 ShopPromosTab computed isLegacyFreeDelivery
      isLegacyFreeDelivery: function() {
          return !!this.insideFreeDeliveryPolygon() || !!this.freeDeliveryOver && this.basketValue >= this.freeDeliveryOver
      },
      // shop-profile~1c39816d.ec3a39e1.js:2362 ShopPromosTab computed isFreeDelivery
      isFreeDelivery: function() {
          return !!this.freeDelivery && (!!this.tukExpressFreeDelivery || (this.basketValue >= this.freeDeliveryOver || (0 === this.actualDeliveryFee || !!this.insideFreeDeliveryPolygon())))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2365 ShopPromosTab computed freeDeliveryRemainder
      freeDeliveryRemainder: function() {
          return this.freeDeliveryOver ? this.freeDeliveryOver - this.basketValue : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2368 ShopPromosTab computed package
      package: function() {
          return this.commerce.data.fruit
      },
      // shop-profile~1c39816d.ec3a39e1.js:2371 ShopPromosTab computed billingAmount
      billingAmount: function() {
          var e = x["a"].billing(this.package);
          this.agentModeData && (e = 0);
          var t = this.basketValue * e;
          return t.toFixed(2)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2377 ShopPromosTab computed remitAmount
      remitAmount: function() {
          var e = x["a"].remit(this.package);
          this.agentModeData && (e = 0);
          var t = Math.round(this.basketValue * e);
          return this.maxRemit && t > this.maxRemit ? this.maxRemit : t
      },
      // shop-profile~1c39816d.ec3a39e1.js:2383 ShopPromosTab computed actualRemitAmount
      actualRemitAmount: function() {
          if (!this.isDelivery) return 0;
          if (!this.remitAmount) return 0;
          var e = this.remitAmount - this.actualDeliverySubsidy;
          return e < 0 ? 0 : e
      },
      // shop-profile~1c39816d.ec3a39e1.js:2389 ShopPromosTab computed deliverySubsidyPercent
      deliverySubsidyPercent: function() {
          return this.agentModeData ? 0 : x["a"].deliverySubsidy(this.package)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2392 ShopPromosTab computed deliverySubsidy
      deliverySubsidy: function() {
          return Math.round(this.basketValue * this.deliverySubsidyPercent)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2395 ShopPromosTab computed actualDeliverySubsidy
      actualDeliverySubsidy: function() {
          return this.deliveryFare ? this.isFreeDelivery || this.deliveryFare.client < this.deliverySubsidy ? this.deliveryFare.client : this.deliverySubsidy : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2398 ShopPromosTab computed actualDeliveryFee
      actualDeliveryFee: function() {
          if (!this.deliveryFare) return null;
          var e = this.deliveryFare.client;
          return this.isLegacyFreeDelivery ? 0 : this.deliverySubsidy ? this.deliverySubsidy > e ? 0 : e - this.deliverySubsidy : e
      },
      // shop-profile~1c39816d.ec3a39e1.js:2403 ShopPromosTab computed specialRemit
      specialRemit: function() {
          if (this.agentModeData || !this.applySpecialRemit) return 0;
          if (this.deliveryFare && this.isFreeDelivery) {
              var e = this.deliverySubsidy - this.deliveryFare.client;
              return e < 0 ? 0 : e > 30 ? 30 : e
          }
          return 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2411 ShopPromosTab computed applySpecialRemit
      applySpecialRemit: function() {
          return !!this.package && (!this.isLalamove && this.package.startsWith("p_"))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2414 ShopPromosTab computed specialDiscount
      specialDiscount: function() {
          return this.agentModeData || !this.applySpecialDiscount ? 0 : this.deliveryFare && this.isFreeDelivery ? this.deliverySubsidy - this.deliveryFare.client : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2417 ShopPromosTab computed applySpecialDiscount
      applySpecialDiscount: function() {
          return !!this.package && this.package.startsWith("f_")
      },
      // shop-profile~1c39816d.ec3a39e1.js:2427 ShopPromosTab computed freeDeliveryPolygon
      freeDeliveryPolygon: function() {
          return this.commerce.data.free_delivery_polygon
      },
      // shop-profile~1c39816d.ec3a39e1.js:2430 ShopPromosTab computed maxDeliveryDistance
      maxDeliveryDistance: function() {
          var e = [this.commerce.data.max_distance, this.peakHourMaxDeliveryDistance];
          return this.deliveryPricingArray && e.push(this.deliveryPricingArray.length), e = e.filter((function(e) {
              return e
          })), e.length ? Math.min.apply(Math, Object(a["a"])(e)) : 14
      },
      // shop-profile~1c39816d.ec3a39e1.js:2436 ShopPromosTab computed maxDistKM
      maxDistKM: function() {
          return Math.round(.8 * this.maxDeliveryDistance)
      },
      // shop-profile~1c39816d.ec3a39e1.js:2439 ShopPromosTab computed deliveryFeePerKM
      deliveryFeePerKM: function() {
          return this.commerce.data.delivery_fee_per_km
      },
      // shop-profile~1c39816d.ec3a39e1.js:2442 ShopPromosTab computed minDeliveryFee
      minDeliveryFee: function() {
          return this.commerce.data.min_delivery_fee
      },
      // shop-profile~1c39816d.ec3a39e1.js:2445 ShopPromosTab computed isSelfDelivery
      isSelfDelivery: function() {
          return "self" === this.commerce.data.express
      },
      // shop-profile~1c39816d.ec3a39e1.js:2448 ShopPromosTab computed peakHourMaxDeliveryDistance
      peakHourMaxDeliveryDistance: function() {
          return this.isPeakHour && this.deliveryWorkflows ? this.deliveryWorkflows.fallback ? this.deliveryWorkflows.fallback.data.peak_hour_max_distance : this.deliveryWorkflows.express ? this.deliveryWorkflows.express.data.peak_hour_max_distance : null : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2451 ShopPromosTab computed deliveryPricingArray
      deliveryPricingArray: function() {
          return this.deliveryWorkflows ? this.deliveryWorkflows.fallback ? this.deliveryWorkflows.fallback.data.pricing_array : this.deliveryWorkflows.express ? this.deliveryWorkflows.express.data.pricing_array : null : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2457 ShopPromosTab computed surgeFee
      surgeFee: function() {
          return this.deliveryWorkflows ? this.deliveryWorkflows.fallback ? this.deliveryWorkflows.fallback.data.surge || 0 : this.deliveryWorkflows.express && this.deliveryWorkflows.express.data.surge || 0 : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2460 ShopPromosTab computed fallbackFleet
      fallbackFleet: function() {
          return this.commerce.data.fallback_fleet
      },
      // shop-profile~1c39816d.ec3a39e1.js:2463 ShopPromosTab computed isLalamove
      isLalamove: function() {
          var e = this.commerce.data.express || "",
              t = this.commerce.data.fallback_fleet || "";
          return e.startsWith("Lalamove") || t.startsWith("Lalamove")
      },
      // shop-profile~1c39816d.ec3a39e1.js:2468 ShopPromosTab computed maxRemit
      maxRemit: function() {
          return this.commerce.data.max_remit
      },
      // shop-profile~1c39816d.ec3a39e1.js:2471 ShopPromosTab computed minOrder
      minOrder: function() {
          return this.commerce.data.min_order
      },
      // shop-profile~1c39816d.ec3a39e1.js:2487 ShopPromosTab computed closedUntil
      closedUntil: function() {
          return this.commerce.data.closed_until ? this.isFutureTime(this.commerce.data.closed_until) : null
      },
      // shop-profile~1c39816d.ec3a39e1.js:2490 ShopPromosTab computed isPaused
      isPaused: function() {
          return this.commerce.data.paused
      },
      // shop-profile~1c39816d.ec3a39e1.js:2493 ShopPromosTab computed isBlocked
      isBlocked: function() {
          return this.commerce.data.blocked
      },
      // shop-profile~1c39816d.ec3a39e1.js:2496 ShopPromosTab computed isOpen
      isOpen: function() {
          if (this.isPaused) return !1;
          if (this.isBlocked) return !1;
          if (this.closedUntil) return !1;
          if ("always-open" === this.business.data.hours_type) return !0;
          if (!this.business.data.hours) return !0;
          var e = this.$date.getShortDay(),
              t = this.business.data.hours[e.toLowerCase()];
          return !(!t || !this.openNow(t))
      },
      // shop-profile~1c39816d.ec3a39e1.js:2529 ShopPromosTab computed vat
      vat: function() {
          return this.agentModeData ? 0 : this.commerce && this.commerce.data.vat ? Math.round(this.commerce.data.vat * this.vatableBasketValue) : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2532 ShopPromosTab computed vatPercent
      vatPercent: function() {
          return this.commerce && this.commerce.data.vat ? this.commerce.data.vat : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2535 ShopPromosTab computed billingPercent
      billingPercent: function() {
          return this.agentModeData ? 0 : this.isDelivery ? x["a"].billing(this.package) : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2538 ShopPromosTab computed remitPercent
      remitPercent: function() {
          return this.agentModeData ? 0 : this.isDelivery ? x["a"].remit(this.package) : 0
      },
      // shop-profile~1c39816d.ec3a39e1.js:2541 ShopPromosTab computed requiresLargeBox
      requiresLargeBox: function() {
          return !!this.basket.items.find((function(e) {
              return JSON.stringify(e).toLowerCase().includes("pizza")
          }))
      }
      },
      methods: {
      // shop-profile~1c39816d.ec3a39e1.js:2727 ShopPromosTab methods hasOptions
      hasOptions: function(e) {
          return e.item.options && e.item.options.length
      },
      // shop-profile~1c39816d.ec3a39e1.js:2730 ShopPromosTab methods invalidPrice
      invalidPrice: function(e) {
          return e.price < 9 && !this.hasOptions({
              item: e
          }) && !e.free_gift
      },
      // shop-profile~1c39816d.ec3a39e1.js:2592 ShopPromosTab methods isMembersOnly
      isMembersOnly: function(e) {
          return !!e.tags && e.tags.includes("member")
      },
      // shop-profile~1c39816d.ec3a39e1.js:2630 ShopPromosTab methods validTimeTag
      validTimeTag: function(e) {
          console.log("validTimeTag", e);
          var t = e.find((function(e) {
              return e.startsWith("*")
          }));
          if (!t) return !0;
          var s = t.replace("*", "").split("-");
          if (2 !== s.length) return !0;
          console.log("times", s[0], s[1]);
          var i = (new Date).getHours();
          return i >= parseInt(s[0]) && i < parseInt(s[1])
      },
      // shop-profile~1c39816d.ec3a39e1.js:2758 ShopPromosTab methods handleVisualDiscount
      handleVisualDiscount: function() {
          if (this.promos) {
              var e = this.commerce.data.visual_discount;
              if (e) {
                  var t = 100 * e;
                  this.promos.data.forEach((function(s) {
                      s.discount = Math.round(parseInt(s.discount || 0) + t), s.price = Math.round(parseFloat(s.price) / (1 - e))
                  })), this.z += 1
              }
          }
      },
      // shop-profile~1c39816d.ec3a39e1.js:2775 ShopPromosTab methods closestPickupLocation
      closestPickupLocation: function() {
          var e = this;
          return this.locations ? (this.locations.forEach((function(t) {
              t.distance = w["a"].distanceToUserInMeters(t, e.address)
          })), this.locations.sort((function(e, t) {
              return e.distance - t.distance
          })), this.locations[0]) : this.business
      },
      // shop-profile~1c39816d.ec3a39e1.js:2996 ShopPromosTab methods insideFreeDeliveryPolygon
      insideFreeDeliveryPolygon: function() {
          if (!this.freeDeliveryPolygon) return !1;
          if (!this.address) return !1;
          var e = s("3d9a"),
              t = this.address.lat,
              i = this.address.lon,
              n = {
                  type: "Point",
                  coordinates: [i, t]
              },
              a = {
                  type: "Polygon",
                  coordinates: this.freeDeliveryPolygon
              };
          return !!e.pointInPolygon(n, a)
      },
      // shop-profile~1c39816d.ec3a39e1.js:3068 ShopPromosTab methods validFareDistance
      validFareDistance: function(e) {
          var t = "".concat(this.$store.getters.realName, " ").concat(this.$store.getters.phoneNumber),
              s = this.business.name;
          if (e <= 50) {
              var i = "🦦 Too short delivery ".concat(e, "m 🤹🏼‍♂️").concat(t, " @ ").concat(s);
              this.sendSuperuserNotification(i);
              var n = "Please enter a valid delivery address.<br><br>The address you gave is the same as the shop.";
              return this.warningAlert(n), this.fulfilmentType = null, this.deliveryFare = null, this.address = null, !1
          }
          if (e > 1e3 * this.maxDeliveryDistance) {
              console.log("delivery distance", e);
              var a = Math.round(e / 1e3),
                  r = "🐆 Too far delivery ".concat(a, "km 🤦🏻‍♂️").concat(t, " @ ").concat(s);
              return this.isCrazyCustomer || this.sendSuperuserNotification(r), this.deliveryTooFarAlert(), this.fulfilmentType = null, this.deliveryFare = null, this.address = null, !1
          }
          return !0
      },
      // shop-profile~1c39816d.ec3a39e1.js:3085 ShopPromosTab methods setDeliveryFare
      setDeliveryFare: function() {
          var e = this.getFare(this.business, this.address);
          this.validFareDistance(e.distance) && (this.deliveryFare = e, this.isLalamove && this.getFareFromLalamove(e))
      },
      // shop-profile~1c39816d.ec3a39e1.js:3110 ShopPromosTab methods addRemitToFare
      addRemitToFare: function() {
          this.deliveryFare.remit = this.deliveryRemit, this.deliveryFare.client = this.deliveryFare.cash + this.deliveryRemit
      },
      // shop-profile~1c39816d.ec3a39e1.js:3113 ShopPromosTab methods updateDeliveryFee
      updateDeliveryFee: function() {
          this.deliveryFare && (this.addRemitToFare(), this.isFreeDelivery ? this.deliveryFee = 0 : (this.deliveryFee = this.deliveryFare.client, this.deliverySubsidy && (this.deliverySubsidy > this.deliveryFee ? this.deliveryFee = 0 : this.deliveryFee = this.deliveryFee - this.deliverySubsidy)))
      },
      // shop-profile~1c39816d.ec3a39e1.js:3116 ShopPromosTab methods selfDeliveryPrice
      selfDeliveryPrice: function(e) {
          var t = e / 1e3,
              s = Math.round(t * this.deliveryFeePerKM);
          return s < this.minDeliveryFee ? this.minDeliveryFee : s
      },
      // shop-profile~1c39816d.ec3a39e1.js:3121 ShopPromosTab methods getFare
      getFare: function(e, t) {
          var s = this.tripDistance(e, t),
              i = "estimate";
          if (this.drivingRoute && (s = this.drivingRoute.distance), this.drivingRoute && (i = this.drivingRoute.source), this.isLalamove) return {
              distance: s
          };
          var n = null;
          return n = this.deliveryPricingArray ? this.getPriceFromPricingArray(s) : this.isSelfDelivery && this.deliveryFeePerKM ? this.selfDeliveryPrice(s) : this.getDefaultDeliveryPrice(s), {
              version: "v1",
              price: n + this.surgeFee + this.extraTukpay + this.extraCash,
              discount: 0,
              cash: n + this.extraCash + this.surgeFee,
              tukpay: this.extraTukpay,
              bonus: this.extraCash + this.extraTukpay + this.surgeFee,
              distance: s,
              source: i
          }
      },
      // shop-profile~1c39816d.ec3a39e1.js:3170 ShopPromosTab methods getDefaultDeliveryPrice
      getDefaultDeliveryPrice: function(e) {
          var t = 80;
          return e < 1e4 && (t = 60), e < 5e3 && (t = 40), e < 3e3 && (t = 30), t
      },
      // shop-profile~1c39816d.ec3a39e1.js:3174 ShopPromosTab methods getPriceFromPricingArray
      getPriceFromPricingArray: function(e) {
          var t = Math.round(e / 1e3);
          t && (t -= 1);
          var s = this.deliveryPricingArray[t];
          return s
      },
      // shop-profile~1c39816d.ec3a39e1.js:3196 ShopPromosTab methods tripDistance
      tripDistance: function(e, t) {
          var s = w["a"].distanceToUserInMeters(e, t),
              i = Math.round(1.25 * s);
          return this.console("tripDistance ".concat(s, " (").concat(i, " [+ ").concat(this.extraDistance, "])")), i + this.extraDistance
      },
      // shop-profile~1c39816d.ec3a39e1.js:3233 ShopPromosTab methods preSubmit
      preSubmit: function() {
          var e = {
              order_value: this.basketValue,
              fulfilment_type: this.fulfilmentType
          };
          if (this.log("submit_order_click", e), !this.invalidBasketForShop()) {
              if (!this.isOpen && !this.agentModeData) return this.$emit("shopWhenOpen");
              if (this.minOrder && this.basketValue < this.minOrder && this.isDelivery && !this.agentModeData) return this.promptMinOrder();
              if (this.songkranMinOrder && this.basketValue < this.songkranMinOrder && this.isDelivery) return this.promptMinOrderSongkran();
              if (this.isFreeGiftOrder && this.basketValue < this.freeGiftOver) return this.promptFreeGiftOverRemoval();
              if (!this.fulfilmentType) return this.promptFulfilmentType();
              if (this.isDelivery && !this.address) return this.promptFulfilmentType();
              if (!this.isDelivery || this.deliveryFare)
                  if (this.isDelivery && this.isLalamove && !this.lalamoveQuote) {
                      this.getFareFromLalamove(this.deliveryFare);
                      var t = "<div style='text-align:left'>😢 Delivery service is<br>temporarily unavailable.<br>Try again in a few minutes.</div>";
                      this.errorToast(t, "bottom", 9e3)
                  } else {
                      this.updateDeliveryFee();
                      var s = this.$t("menu.one-last-thing");
                      this.smsLoginOkay(s, this.loginCheckoutCallback) && this.checkMobileNumber() && (this.showSubmit = !0)
                  }
          }
      },
      // shop-profile~1c39816d.ec3a39e1.js:3288 ShopPromosTab methods submit
      submit: function(e) {
          var t = this;
          this.paymentMethod = e.method, (e.isTest || this.isTest) && this.prefaceTestOrderNotes(), e.addressNotes && (this.address.notes = e.addressNotes, this.saveDeliveryAddress(this.address)), e.fulfilmentTime && (this.fulfilmentTime = e.fulfilmentTime);
          var s = {
              id: this.business.id,
              country: this.business.country,
              type: this.business.business_type,
              name: this.business.name,
              lat: this.business.lat,
              lon: this.business.lon,
              phone_number: this.business.data.phone_number
          };
          this.locations && (s.lat = this.closestPickupLocation().lat, s.lon = this.closestPickupLocation().lon, s.name = this.business.name + "*");
          var i = {
              type: this.business.business_type,
              business_name: this.business.name,
              order_value: this.basketValue,
              payment_method: e.method,
              delivery_subsidy: this.actualDeliverySubsidy,
              remit: this.actualRemitAmount + this.specialDiscount + this.specialRemit,
              fulfilment_discount: this.fulfilmentDiscountAmount,
              special_discount: 0,
              vat: this.vat,
              basket: {
                  items: this.basket.items,
                  notes: this.basket.notes,
                  languages: this.languages
              }
          };
          e.changeFor && (i.change_for = e.changeFor), e.localContact && (i.local_contact = e.localContact), this.fulfilmentTime && (i.fulfilment_time = this.fulfilmentTime), e.id && (i.payment_id = e.id);
          var n = {
                  vat_percent: this.vatPercent,
                  billing_percent: this.billingPercent,
                  remit_percent: this.remitPercent,
                  delivery_subsidy_percent: this.deliverySubsidyPercent,
                  fulfilment_discount_percent: this.fulfilmentDiscountPercent
              },
              a = {
                  type: this.fulfilmentType,
                  business: s,
                  order: i,
                  settings: n,
                  agent: !!this.isSuperUser(),
                  uuid: this.$store.getters.getUUID,
                  start_url: this.$store.getters.getDeviceStartURL,
                  ad_tag: this.$store.getters.getAdTag,
                  short_id: T.generate(),
                  ref_prefix: this.commerce.data.ref_prefix
              };
          if (this.autoconfirm && (a.autoconfirm = !0), this.commerce.data.alternative_shop && (a.alternative_shop = this.commerce.data.alternative_shop), this.isDelivery) {
              var r = this.commerce.data.express;
              (e.isTest || this.isTest) && (r = this.testFleet);
              var o = {
                  workflow_id: r,
                  express_fleet: r,
                  is_free_delivery: this.isFreeDelivery,
                  promo_code: this.deliveryPromoCode,
                  contactless_note: this.contactlessNote,
                  business_note: this.businessNoteForDriver,
                  sign_name: this.businessSignName
              };
              if ("cash" === this.paymentMethod && (o.contactless_note = null), this.delayedDelivery && (o.type = "delayed"), this.delayDuration && (o.delay_duration = this.delayDuration), !this.delayedDelivery && this.basketValue >= 1e3) {
                  o.type = "delayed", o.delay_duration = 26;
                  var l = this.$store.getters.realName || "Unknown",
                      c = this.business.name,
                      u = "🤑💰".concat(this.basketValue, " Big Order Delay Delivery (").concat(l, " @ ").concat(c, ")");
                  this.sendSuperuserNotification(u)
              }
              if (this.thirdPartyDelivery && (o.type = "third-party"), this.reversedDelivery && (o.reversed = !0), !this.fallbackFleet || e.isTest || this.isTest || (o.fallback_fleet = this.fallbackFleet), (this.commerce.data.is_mart || this.commerce.data.require_receipt) && (o.require_receipt = !0), this.commerce.data.is_mart && (o.is_flexible_pickup_location = !0), this.requiresLargeBox && (o.is_large = !0), this.lalamoveQuote && (o.lalamove = S(S({}, this.lalamoveQuote), this.lalamoveRequest), "card" === this.paymentMethod || 0 === this.basketValue)) {
                  o.lalamove.total_price = 0;
                  var h = parseInt(o.lalamove.totalFee) - 15;
                  o.lalamove.totalFee = h.toString()
              }
              this.drivingRoute && (o.predicted_route = this.drivingRoute.encoded), a.coords_created = this.$store.getters.getUserLatLon, a.delivery = o, a.address = this.address, a.fare = this.deliveryFare, e.driverTip && (a.fare.client += e.driverTip, a.fare.cash += e.driverTip, a.fare.price += e.driverTip, a.fare.tip += e.driverTip), parseFloat(this.billingAmount) > 0 && (a.billing = this.billingAmount), this.giftDetails && (a.gift_details = this.giftDetails)
          }
          var d = this.userID();
          this.agentModeData && (d = this.agentModeData.mobile, a.autoconfirm = !0, a.is_agent_order = !0), console.log("createTransaction", a);
          var m = this.loadingDots(this.bulmaColor("info"));
          this.$store.dispatch("createTransaction", {
              workflow_type_name: "Commerce",
              workflow_id: this.commerce.id,
              business_id: this.business.id,
              created_by: d,
              data: a
          }).then((function() {
              if (t.newBasket(), t.agentModeData) t.$swal({
                  html: "<div style='text-align:left'>\n              <p style='font-size:1.2em; margin-bottom:12px'>\n              <i class='far fa-thumbs-up'></i>&nbsp;Order Created\n              </p>\n              <p style='margin-bottom:10px'>Confirm the order now to request the driver.</p>\n              <p>Click <strong>Share to customer&nbsp;<i class='fa fa-paper-plane'></i></strong> \n              &nbsp;to share the order link to the customer.</p>\n              </div>",
                  confirmButtonText: t.$t("okay"),
                  showCloseButton: !0
              }), t.goto("ecommerce");
              else {
                  var e = t.bulmaColor("success"),
                      s = t.$t("menu.confirmed-1"),
                      i = t.$t("menu.confirmed-2");
                  t.$swal({
                      title: "<i class='fa fa-thumbs-up' style='color:".concat(e, "'></i>"),
                      html: "".concat(s, "<br><br>").concat(i),
                      showConfirmButton: !0,
                      confirmButtonColor: e,
                      confirmButtonText: t.$t("okay")
                  }).then((function() {
                      var e = t.$t("menu.see-your-ongoing-orders") + " 👆🏻";
                      setTimeout((function() {
                          return t.successToast(e)
                      }), 1e3)
                  })), t.goto("orders?tab=ongoing")
              }
              t.log("submit_order_success", a)
          })).catch((function(e) {
              return t.apiError(e)
          })).finally((function() {
              return m.hide()
          }))
      },
      // shop-profile~1c39816d.ec3a39e1.js:3452 ShopPromosTab methods initBasket
      initBasket: function() {
          if (console.log("INIT basket"), this.$store.state.basket) {
              var e = this.$store.state.basket,
                  t = new Date(e.created_at).getTime(),
                  s = ((new Date).getTime() - t) / 6e4;
              console.log("basket age ".concat(s, " minutes")), s > 60 && e.shop_id !== this.business.id ? this.newBasket() : this.basket = this.$store.state.basket
          } else this.newBasket()
      },
      // shop-profile~1c39816d.ec3a39e1.js:3460 ShopPromosTab methods invalidBasketForShop
      invalidBasketForShop: function(e) {
          var t = this;
          if (this.basket || this.newBasket(), this.basket.shop_id === this.business.id) return !1;
          if (0 === this.basketItemCount) return this.newBasket(), !1;
          var s = this.bulmaColor("primary"),
              i = this.$t("menu.different-shop"),
              n = this.$t("menu.shop-here?"),
              a = this.$t("menu.shop-here");
          return this.$swal({
              html: "<div style='text-align:left'>\n          <p style=\"font-size:1.2em; margin-bottom:16px\">\n          <i class='fa fa-shopping-basket'></i>&nbsp;".concat(a, "?\n          </p>\n          <p style='margin-bottom:10px'>").concat(i, "</p>\n          <p>").concat(n, "</p>\n          </div>"),
              showCancelButton: !0,
              showCloseButton: !0,
              confirmButtonText: this.$t("menu.yes-shop-here"),
              cancelButtonText: this.$t("menu.go-to-other"),
              confirmButtonColor: s,
              cancelButtonColor: "orange"
          }).then((function(s) {
              s.value && (t.newBasket(), e && t.addToBasket(e, !0)), "cancel" === s.dismiss && (t.goto("shop/".concat(t.basket.shop_id, "?tab=promos")), t.$nextTick((function() {
                  return t.$emit("reactivateShopProfile")
              })))
          })), !0
      },
      // shop-profile~1c39816d.ec3a39e1.js:3482 ShopPromosTab methods newBasket
      newBasket: function() {
          this.basket = {
              shop_id: this.business.id,
              created_at: new Date,
              items: [],
              notes: null
          }, this.saveBasket(), this.showBasket = !1
      },
      // shop-profile~1c39816d.ec3a39e1.js:3504 ShopPromosTab methods saveBasket
      saveBasket: function() {
          this.sortBasket(), this.$store.commit("setBasket", this.basket)
      },
      // shop-profile~1c39816d.ec3a39e1.js:3530 ShopPromosTab methods sortBasket
      sortBasket: function() {
          this.basket.items.sort((function(e, t) {
              return e.item.name.localeCompare(t.item.name)
          }))
      },
      // shop-profile~1c39816d.ec3a39e1.js:3535 ShopPromosTab methods submitOptions
      submitOptions: function(e) {
          if (this.log("add_to_basket", e), console.log("submitOptions", e), e.comment) {
              var t = this.promos.data.find((function(t) {
                  return t.id === e.id
              }));
              t && (t.comment = e.comment)
          }
          if (e.id2) this.basket.items.push({
              item: e,
              quantity: 1
          });
          else {
              var s = this.basket.items.find((function(t) {
                  return t.item.id === e.id
              }));
              s ? (s.quantity += 1, s.item.comment = e.comment) : this.basket.items.push({
                  item: e,
                  quantity: 1
              })
          }
          this.saveBasket(), this.updateDeliveryFee(), this.$refs[e.id][0].forceAnimate("add"), this.$refs["special" + e.id] && this.$refs["special" + e.id][0].forceAnimate("add")
      },
      // shop-profile~1c39816d.ec3a39e1.js:3557 ShopPromosTab methods addToBasket
      addToBasket: function(e, t) {
          if (o["a"].$emit("BREAK_SHOP_CACHE"), this.log("view_menu_item", e), !e.hasOwnProperty("isTrusted") && (this.basket || this.initBasket(), !this.invalidBasketForShop(e)))
              if (this.isOpen || this.$emit("shopWhenOpen"), t) this.triggerOptions(e);
              else {
                  var s = this.basket.items.find((function(t) {
                      return t.item.id2 === e.id2
                  }));
                  s && (s.quantity += 1), this.finishAddition(t)
              }
      },
      // shop-profile~1c39816d.ec3a39e1.js:3567 ShopPromosTab methods finishAddition
      finishAddition: function(e) {
          this.saveBasket(), this.updateDeliveryFee();
          var t = this.$t("menu.added-to-basket"),
              s = "<i class='fa fa-plus'></i>&nbsp;".concat(t);
          e || this.$toast.open({
              message: s,
              duration: 500
          })
      },
      // shop-profile~1c39816d.ec3a39e1.js:3576 ShopPromosTab methods removeZeros
      removeZeros: function() {
          for (var e = this.basket.items, t = 0; t < e.length; t++) 0 === e[t].quantity && e.splice(t, 1)
      },
      // shop-profile~1c39816d.ec3a39e1.js:3579 ShopPromosTab methods advancedRemoval
      advancedRemoval: function(e) {
          if (this.showOptions = !1, 1 === this.itemQuantity(e)) {
              var t = this.basket.items.find((function(t) {
                  return t.item.id === e.id
              }));
              t && (t.quantity -= 1), this.finishRemoval(!0), this.$refs[e.id][0].forceAnimate("remove")
          } else this.itemForRemoval = e, this.showRemoval = !0
      },
      // shop-profile~1c39816d.ec3a39e1.js:3587 ShopPromosTab methods removeFromBasket
      removeFromBasket: function(e, t) {
          if (e.options && e.options.length && this.itemQuantity(e) > 1) this.itemForRemoval = e, this.showRemoval = !0;
          else {
              var s = this.basket.items.find((function(t) {
                  return t.item.id === e.id
              }));
              s && (s.quantity -= 1)
          }
          this.finishRemoval(t)
      },
      // shop-profile~1c39816d.ec3a39e1.js:3597 ShopPromosTab methods newRemoveFromBasket
      newRemoveFromBasket: function(e) {
          var t = this.basket.items.find((function(t) {
              return t.item.id2 === e.id2
          }));
          t && (t.quantity -= 1), this.finishRemoval(!1), this.$refs[e.id][0].forceAnimate("remove")
      },
      // shop-profile~1c39816d.ec3a39e1.js:3603 ShopPromosTab methods finishRemoval
      finishRemoval: function(e) {
          if (!e || this.showBasket) {
              var t = this.$t("menu.removed-from-basket"),
                  s = "<i class='fa fa-minus'></i>&nbsp;".concat(t);
              this.warningToast(s)
          }
          this.removeZeros(), this.saveBasket(), this.updateDeliveryFee(), console.log("finishRemoval", this.basket.items.length), this.basket.items.length || this.minimise()
      },
      // shop-profile~1c39816d.ec3a39e1.js:3611 ShopPromosTab methods itemQuantity
      itemQuantity: function(e) {
          if (!this.basket) return null;
          var t = this.basket.items.filter((function(t) {
                  return t.item.id === e.id
              })),
              s = 0;
          return t.forEach((function(e) {
              return s += e.quantity
          })), s
      },
      // shop-profile~1c39816d.ec3a39e1.js:2929 ShopPromosTab methods minimise
      minimise: function() {
          console.log("hide basket"), this.log("minimise_basket"), this.showBasket = !1
      },
      // shop-profile~1c39816d.ec3a39e1.js:3525 ShopPromosTab methods triggerOptions
      triggerOptions: function(e) {
          this.selectedItem = this.items.find((function(t) {
              return t.id === e.id
          })), this.showOptions = !0
      }
      }
    };
  },
  // ShopMenuOptions. Imports as in module 5811: n = event bus (0c12), o = shortid (8dee).
  shopMenuOptions: function (n, o, setTimeout, clearTimeout) {
    return {
      // shop-profile~f04d431b.1f6f733e.js:1726 ShopMenuOptions data
      data: function() {
          return {
              optionsMenusCopy: [],
              invalidMenus: [],
              triggerTimeout: null,
              selectingID: null,
              invalid: [],
              selected: [],
              comment: null,
              expandLength: 4,
              tallerImage: !1,
              isTallWrapper: !1,
              email: null,
              loginCallback: "SHOP_MENU_OPTIONS_LOGIN_MEMBER_CALLBACK",
              z: 0,
              y: 0
          }
      },
      // shop-profile~f04d431b.1f6f733e.js:1828 ShopMenuOptions mounted
      mounted: function() {
          var t = this;
          this.optionsMenusCopy = this.deepCopy(this.optionsMenus), setTimeout((function() {
              return t.isTallWrapper = !0
          }), 50), this.hasOptionsMenus || (this.comment = this.item.comment), n["a"].$on(this.loginCallback, (function() {
              return t.submitMember()
          })), this.propagateDiscounts && this.updateOptionMenuPrices()
      },
      computed: {
      // shop-profile~f04d431b.1f6f733e.js:1745 ShopMenuOptions computed isFreeGift
      isFreeGift: function() {
          return this.item.free_gift
      },
      // shop-profile~f04d431b.1f6f733e.js:1748 ShopMenuOptions computed propagateDiscounts
      propagateDiscounts: function() {
          return !!this.w.data.menu_options && this.w.data.menu_options.propagate_discounts
      },
      // shop-profile~f04d431b.1f6f733e.js:1769 ShopMenuOptions computed hasOptionsMenus
      hasOptionsMenus: function() {
          return this.item.options && this.item.options.length
      },
      // shop-profile~f04d431b.1f6f733e.js:1772 ShopMenuOptions computed filteredOptionsMenus
      filteredOptionsMenus: function() {
          var t = this;
          if (!this.hasOptionsMenus) return [];
          if (!this.optionsMenus) return [];
          var s = [];
          return this.item.options.forEach((function(e) {
              var i = t.optionsMenusCopy.find((function(t) {
                  return e === t.id
              }));
              if (i && i.data.items.length) {
                  if (i.data.condition) {
                      var a = t.trueCondition(i.data.condition);
                      if (!a) return
                  }
                  i && s.push(i)
              }
          })), s.sort((function(t, s) {
              var e = t.data.required,
                  i = s.data.required;
              return e === i ? 0 : e ? -1 : 1
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1794 ShopMenuOptions computed requiredOptions
      requiredOptions: function() {
          return this.filteredOptionsMenus.filter((function(t) {
              return t.data.required
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1799 ShopMenuOptions computed multipleOptions
      multipleOptions: function() {
          var t = this.filteredOptionsMenus;
          return t.filter((function(t) {
              return "multiple" === t.data.select
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1811 ShopMenuOptions computed totalPrice
      totalPrice: function() {
          var t = this;
          console.log("y", this.y);
          var s = 0;
          return this.selected.forEach((function(e) {
              return s += t.optionPrice(e.option)
          })), this.discountedPrice(this.item) + s
      },
      // shop-profile~f04d431b.1f6f733e.js:1819 ShopMenuOptions computed requiredInvalid
      requiredInvalid: function() {
          var t = this.selected.map((function(t) {
              return t.menu.id
          }));
          return this.requiredOptions.filter((function(s) {
              return !t.includes(s.id)
          })).length
      }
      },
      methods: {
      // shop-profile~f04d431b.1f6f733e.js:1840 ShopMenuOptions methods close
      close: function() {
          this.$emit("close")
      },
      // shop-profile~f04d431b.1f6f733e.js:1850 ShopMenuOptions methods updateOptionMenuPrices
      updateOptionMenuPrices: function() {
          if (!this.item.discount_type || "percent" === this.item.discount_type) {
              var t = this.item.discount,
                  s = 1 - parseInt(t) / 100;
              this.filteredOptionsMenus.forEach((function(t) {
                  t.data.items.forEach((function(t) {
                      t.discounted_price = parseInt(t.price) * s
                  }))
              }))
          }
      },
      // shop-profile~f04d431b.1f6f733e.js:1861 ShopMenuOptions methods triggerSelecting
      triggerSelecting: function(t) {
          var s = this;
          this.triggerTimeout && clearTimeout(this.triggerTimeout), this.selectingID = t.id, this.triggerTimeout = setTimeout((function() {
              return s.selectingID = null
          }), 4e3)
      },
      // shop-profile~f04d431b.1f6f733e.js:1871 ShopMenuOptions methods invalidOptionsMenu
      invalidOptionsMenu: function(t) {
          var s = t.data.multiple_constraint,
              e = t.data.multiple_n;
          if ("multiple" === t.data.select && "none" !== s) {
              var i = 0;
              if (this.selected.filter((function(s) {
                      return s.menu.id === t.id
                  })).forEach((function(t) {
                      i += t.option.quantity
                  })), i > e) return !0
          }
          return !1
      },
      // shop-profile~f04d431b.1f6f733e.js:1884 ShopMenuOptions methods invalidOptionsMenuStrict
      invalidOptionsMenuStrict: function(t) {
          if ("multiple" !== t.data.select) return !1;
          var s = t.data.multiple_constraint,
              e = parseInt(t.data.multiple_n),
              i = 0;
          return this.selected.filter((function(s) {
              return s.menu.id === t.id
          })).forEach((function(t) {
              i += t.option.quantity
          })), "up_to" === s && i > e || "exactly" === s && i !== e
      },
      // shop-profile~f04d431b.1f6f733e.js:1895 ShopMenuOptions methods checkRequirements
      checkRequirements: function() {
          var t = this;
          this.invalidMenus = [];
          var s = this.selected.map((function(t) {
              return t.menu.id
          }));
          this.requiredOptions.forEach((function(e) {
              s.includes(e.id) || t.invalidMenus.push(e.id)
          })), this.multipleOptions.forEach((function(s) {
              t.invalidOptionsMenuStrict(s) && t.invalidMenus.push(s.id)
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1969 ShopMenuOptions methods optionSelected
      optionSelected: function(t) {
          return this.selected.find((function(s) {
              return s.option.id === t.id
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1974 ShopMenuOptions methods trueCondition
      trueCondition: function(t) {
          return this.selected.find((function(s) {
              var e = JSON.stringify(s.option).toLowerCase();
              return e.includes(t)
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1980 ShopMenuOptions methods clearConditionalOptions
      clearConditionalOptions: function() {
          var t = this;
          this.selected.forEach((function(s) {
              var e = s.menu.condition;
              e && (t.trueCondition(e) || t.remove(s.option))
          }))
      },
      // shop-profile~f04d431b.1f6f733e.js:1987 ShopMenuOptions methods remove
      remove: function(t) {
          var s = this.selected.findIndex((function(s) {
              return s.option.id === t.id
          }));
          this.selected[s].option.quantity = null, this.selected.splice(s, 1)
      },
      // shop-profile~f04d431b.1f6f733e.js:1993 ShopMenuOptions methods removeSingleOption
      removeSingleOption: function(t) {
          var s = this.selected.findIndex((function(s) {
              return s.menu.id === t.id
          })); - 1 !== s && (console.log("removeSingleOption", s, t, this.selected[s]), this.selected[s].option.quantity = null, this.selected.splice(s, 1))
      },
      // shop-profile~f04d431b.1f6f733e.js:1998 ShopMenuOptions methods allowMultiple
      allowMultiple: function(t) {
          return "multiple" === t.data.select && t.data.allow_multiple
      },
      // shop-profile~f04d431b.1f6f733e.js:2001 ShopMenuOptions methods select
      select: function(t, s) {
          var e = this;
          if (s.out_of_stock) {
              var i = "Option Out of Stock";
              this.infoToast(i, "top")
          } else if (this.triggerSelecting(s), !this.optionSelected(s) || !this.allowMultiple(t))
              if (this.$nextTick((function() {
                      return e.clearConditionalOptions()
                  })), this.optionSelected(s)) this.remove(s);
              else {
                  "single" === t.data.select && this.removeSingleOption(t);
                  var a = this.deepCopy(t.data);
                  a.id = t.id, delete a.items, delete a.required, delete a.select, s.quantity ? s.quantity += 1 : s.quantity = 1, this.selected.push({
                      menu: a,
                      option: s
                  })
              }
      },
      // shop-profile~f04d431b.1f6f733e.js:2022 ShopMenuOptions methods removeQuantity
      removeQuantity: function(t) {
          t.quantity -= 1, 0 === t.quantity && this.remove(t), this.y += 1
      },
      // shop-profile~f04d431b.1f6f733e.js:2025 ShopMenuOptions methods addQuantity
      addQuantity: function(t) {
          t.quantity += 1, this.y += 1
      },
      // shop-profile~f04d431b.1f6f733e.js:2028 ShopMenuOptions methods submit
      submit: function() {
          if (this.item.max_count && this.qty >= this.item.max_count) {
              var t = this.$t("menu.you-have-max-allowed") + this.item.max_count;
              this.errorToast(t)
          } else if (this.item.free_gift && this.qty >= 1) {
              var s = "You can only order 1 Free Gift";
              this.errorToast(s)
          } else {
              if (this.checkRequirements(), this.requiredInvalid || this.invalidMenus.length) {
                  var e = this.$t("menu.select-required-option");
                  return this.warningToast(e, "top"), void this.scrollToBottom(500)
              }
              var i = this.deepCopy(this.item);
              this.hasOptionsMenus && (i.id2 = o.generate()), i.options = this.selected, i.comment = this.comment, this.$emit("submit", i), this.close()
          }
      }
      }
    };
  },
  // ShopSubmitOrder (module 9e1e4): cash rules.
  shopSubmitOrder: function () {
    return {
      computed: {
      // shop-profile~21833f8f.83f1e1e0.js:894 ShopSubmitOrder computed isLateDeliveryNotAllowCash
      isLateDeliveryNotAllowCash: function() {
          if (!this.isDelivery) return !1;
          var t = parseInt(this.$date.getHHMM());
          return t > 2220
      },
      // shop-profile~21833f8f.83f1e1e0.js:899 ShopSubmitOrder computed isEarlyDeliveryNotAllowCash
      isEarlyDeliveryNotAllowCash: function() {
          if (!this.isDelivery) return !1;
          var t = parseInt(this.$date.getHHMM());
          return t < 900
      },
      // shop-profile~21833f8f.83f1e1e0.js:904 ShopSubmitOrder computed hasCash
      hasCash: function() {
          return !this.isEarlyDeliveryNotAllowCash && (!this.isLateDeliveryNotAllowCash && (!this.commerce.payment_options || this.commerce.payment_options.includes("cash")))
      },
      // shop-profile~21833f8f.83f1e1e0.js:928 ShopSubmitOrder computed isDelivery
      isDelivery: function() {
          return "delivery" === this.fulfilment
      }
      }
    };
  },
  // CommerceOrder (module d252): total on the order page.
  commerceOrder: function () {
    return {
      computed: {
      // commerce-order~31ecd969.c07a3a3f.js:2086 CommerceOrder computed t
      t: function() {
          return this.transaction
      },
      // commerce-order~31ecd969.c07a3a3f.js:2117 CommerceOrder computed orderValue
      orderValue: function() {
          var t = this.vat,
              i = this.specialDiscount + this.fulfilmentDiscount + this.tukDiscount,
              e = this.t.data.order.order_value + t - i;
          if ("delivery" !== this.t.data.type) return e;
          if (this.t.data.delivery.is_free_delivery) return e;
          var a = this.subsidisedDeliveryFee;
          return e + a
      },
      // commerce-order~31ecd969.c07a3a3f.js:2129 CommerceOrder computed vat
      vat: function() {
          return this.t.data.order.vat || 0
      },
      // commerce-order~31ecd969.c07a3a3f.js:2132 CommerceOrder computed fulfilmentDiscount
      fulfilmentDiscount: function() {
          return this.t.data.order.fulfilment_discount || 0
      },
      // commerce-order~31ecd969.c07a3a3f.js:2135 CommerceOrder computed specialDiscount
      specialDiscount: function() {
          return this.t.data.order.special_discount || 0
      },
      // commerce-order~31ecd969.c07a3a3f.js:2138 CommerceOrder computed tukDiscount
      tukDiscount: function() {
          return this.t.data.order.tuk_discount || 0
      },
      // commerce-order~31ecd969.c07a3a3f.js:2141 CommerceOrder computed clientCash
      clientCash: function() {
          return this.t.data.fare.client || this.t.data.fare.cash
      },
      // commerce-order~31ecd969.c07a3a3f.js:2144 CommerceOrder computed subsidisedDeliveryFee
      subsidisedDeliveryFee: function() {
          return "delivery" !== this.t.data.type ? 0 : this.clientCash - this.t.data.order.delivery_subsidy
      },
      // commerce-order~31ecd969.c07a3a3f.js:2147 CommerceOrder computed deliveryFee
      deliveryFee: function() {
          if ("delivery" !== this.t.data.type) return 0;
          var t = this.subsidisedDeliveryFee;
          return this.t.data.delivery.is_free_delivery ? 0 : t
      }
      }
    };
  }
};
// END VERBATIM

// ================================================================= wrapper (ours)

// Fixed clock. The web code calls `new Date` for the hour (peak hours, cash rules) and for
// the basket age. We replace the global Date while the web code runs.
function withClock(clock, fn) {
  const realLog = console.log;
  console.log = () => {};
  const RealDate = Date;
  const nowMs = clock.epoch_ms;
  class FakeDate extends RealDate {
    constructor(...args) {
      if (args.length === 0) super(nowMs);
      else super(...args);
    }
    // Local time of the fake clock is given by the case, not by the machine time zone.
    getHours() {
      return this.getTime() === nowMs ? clock.hour : super.getHours();
    }
    getMinutes() {
      return this.getTime() === nowMs ? clock.minute : super.getMinutes();
    }
    static now() {
      return nowMs;
    }
  }
  globalThis.Date = FakeDate;
  try {
    return fn();
  } finally {
    globalThis.Date = RealDate;
    console.log = realLog;
  }
}

// Runs a webpack module function and returns its exports.
function loadModule(fn) {
  const module = { exports: {} };
  const require = () => ({});
  require.d = (exp, name, getter) => Object.defineProperty(exp, name, { enumerable: true, get: getter });
  require.r = () => {};
  require.n = (m) => () => m;
  fn.call(module.exports, module, module.exports, require);
  return module.exports;
}

function makeParts(V) {
  const pkg = loadModule(V.module_84f5).a; // package codes
  const geo = loadModule(V.module_2c95).a; // distance helpers
  const date = loadModule(V.module_f17f).a; // $date
  const gju = loadModule(V.module_3d9a); // geojson-utils
  return { V, pkg, geo, date, gju };
}

// Records UI calls (toasts, alerts) instead of showing them.
function uiStubs(log) {
  const rec = (name) => (...args) => {
    log.push({ ui: name, args: args.map((x) => (typeof x === 'string' ? x.slice(0, 120) : x)) });
  };
  const loading = () => ({ hide() {} });
  return {
    $t: (k) => k,
    $emit: (name, payload) => log.push({ emit: name, payload }),
    $nextTick: null, // set per vm
    $swal: () => new Promise(() => {}),
    $toast: { open: rec('toast'), clear() {} },
    $refs: new Proxy({}, { get: () => [{ forceAnimate() {}, reset() {}, scrollTo() {}, scrollIntoView() {} }] }),
    $el: { querySelectorAll: () => [] },
    log: rec('log'),
    console: () => {},
    infoToast: rec('infoToast'),
    errorToast: rec('errorToast'),
    warningToast: rec('warningToast'),
    successToast: rec('successToast'),
    warningAlert: rec('warningAlert'),
    sendSuperuserNotification: () => {},
    sendCommerceAgentNotification: () => {},
    loadingDots: loading,
    bulmaColor: () => '#000',
    currencySymbol: () => '฿',
    isSuperUser: () => false,
    isMember: () => false,
    smsLoginOkay: () => true,
    checkMobileNumber: () => true,
    userID: () => 'user-1',
    goto: () => {},
    apiError: () => {},
    isFutureTime: (t) => !!t && new Date(t) > new Date(),
    openNow: () => true,
    scrollToBottom: () => {},
    updateRoles: () => {},
    updateLocation: () => {},
  };
}

// Builds a fake Vue instance for a component definition.
function makeVm(def, parts, env, props) {
  const vm = {};
  const ticks = [];
  Object.assign(vm, uiStubs(env.log));
  vm.$nextTick = (fn) => ticks.push(fn);
  vm.$flush = () => {
    while (ticks.length) ticks.shift().call(vm);
  };
  vm.$store = env.store;
  vm.$date = parts.date;
  for (const [k, f] of Object.entries(parts.V.mixin)) vm[k] = f.bind(vm);
  Object.assign(vm, props || {});
  if (def.data) Object.assign(vm, def.data.call(vm));
  Object.assign(vm, props || {});
  for (const [k, f] of Object.entries(def.computed || {})) {
    Object.defineProperty(vm, k, { get: () => f.call(vm), configurable: true, enumerable: false });
  }
  for (const [k, f] of Object.entries(def.methods || {})) vm[k] = f.bind(vm);
  return vm;
}

function makeStore(input) {
  return {
    state: {
      user: input.user ? { id: 'user-1', data: input.user } : null,
      basket: input.stored_basket || null,
      selectedBusiness: null,
      roles: [],
      language: 'en',
      menuItemPlaceholderPic: '',
      errorProfilePic: '',
    },
    getters: {
      isLoggedIn: true,
      phoneNumber: '+66800000000',
      realName: 'Test',
      getUUID: 'uuid-1',
      getDeviceStartURL: null,
      getAdTag: null,
      getUserLatLon: { lat: 0, lon: 0 },
      getNameAndNumber: 'Test',
      isSuperUser: false,
      isSafari: false,
    },
    commit(name, value) {
      if (name === 'setBasket') this.state.basket = value;
    },
    dispatch(name, value) {
      this.dispatched.push({ name, value: JSON.parse(JSON.stringify(value)) });
      return new Promise(() => {});
    },
    dispatched: [],
  };
}

let shortIdCounter = 0;
const shortid = { generate: () => 'sid' + (++shortIdCounter).toString(36) };
function resetShortIds() {
  shortIdCounter = 0;
}

// Object spread helper (S in module f09f). Only the Lalamove path uses it.
function objectSpread(target, ...sources) {
  return Object.assign(target, ...sources);
}

function shopTabDef(parts) {
  const require = (id) => (id === '3d9a' ? parts.gju : {});
  const bus = { a: { $emit() {}, $on() {}, $off() {} } };
  const toConsumableArray = { a: (arr) => Array.from(arr) };
  const promo = { a: { lookup: () => null } };
  const noTimer = () => 0;
  return parts.V.shopPromosTab(
    require, bus, toConsumableArray, { a: parts.geo }, { a: parts.pkg }, shortid, objectSpread, promo, noTimer, noTimer,
  );
}

function optionsDef(parts) {
  const bus = { a: { $emit() {}, $on() {}, $off() {} } };
  const noTimer = () => 0;
  return parts.V.shopMenuOptions(bus, shortid, noTimer, noTimer);
}

// Builds the business object the shop page has in $store.state.selectedBusiness.
function makeBusiness(input) {
  const commerce = {
    id: input.workflow.id,
    name: 'Commerce',
    data: JSON.parse(JSON.stringify(input.workflow.data)),
    blobs: [
      { blob_type: 'digital_menu', data: JSON.parse(JSON.stringify(input.menu_items || [])) },
      ...(input.options_menus || []).map((m) => JSON.parse(JSON.stringify(m))),
    ],
  };
  return {
    id: input.business.id,
    name: input.business.name || 'Shop',
    business_type: 'Restaurant',
    country: input.business.country || 'th',
    lat: input.business.lat,
    lon: input.business.lon,
    data: { phone_number: '0800000000', hours_type: 'always-open' },
    workflows: [commerce],
  };
}

function plain(x) {
  return x === undefined ? null : JSON.parse(JSON.stringify(x));
}

// ---------------------------------------------------------------- 1. option sheet

// input: {workflow:{id,data}, business, menu_items:[the item], options_menus:[blobs {id, blob_type, data}],
//         item_id, qty, actions:[{op:"select"|"add_quantity"|"remove_quantity", menu_id, option_id}], comment}
function runOptionSheet(parts, input, clock) {
  return withClock(clock, () => {
    const log = [];
    const store = makeStore(input);
    const business = makeBusiness(input);
    store.state.selectedBusiness = business;
    const commerce = business.workflows[0];
    const item = commerce.blobs[0].data.find((i) => i.id === input.item_id);
    const def = optionsDef(parts);
    const vm = makeVm(def, parts, { log, store }, {
      optionsMenus: commerce.blobs.filter((b) => b.blob_type === 'options_menu'),
      item,
      lang: 'en',
      currency: '฿',
      qty: input.qty || 0,
      w: commerce,
      isOpen: true,
    });
    def.mounted.call(vm);
    const trace = [];
    for (const act of input.actions || []) {
      const menu = vm.filteredOptionsMenus.find((m) => m.id === act.menu_id);
      const optInMenu = menu ? menu.data.items.find((x) => x.id === act.option_id) : null;
      const selected = vm.selected.find((x) => x.option.id === act.option_id);
      if (act.op === 'select') {
        if (!menu || !optInMenu) {
          trace.push({ op: act.op, skipped: 'menu not shown' });
          continue;
        }
        vm.select(menu, optInMenu);
      } else if (act.op === 'add_quantity') {
        if (!selected) {
          trace.push({ op: act.op, skipped: 'not selected' });
          continue;
        }
        vm.addQuantity(selected.option);
      } else if (act.op === 'remove_quantity') {
        if (!selected) {
          trace.push({ op: act.op, skipped: 'not selected' });
          continue;
        }
        vm.removeQuantity(selected.option);
      }
      vm.$flush();
      trace.push({ op: act.op, total_price: vm.totalPrice });
    }
    if (input.comment !== undefined) vm.comment = input.comment;
    const shown = vm.filteredOptionsMenus.map((m) => m.id);
    const invalidNow = vm.filteredOptionsMenus.filter((m) => vm.invalidOptionsMenu(m)).map((m) => m.id);
    const totalPrice = vm.totalPrice;
    const before = log.length;
    vm.submit();
    const out = log.slice(before);
    const emitted = out.find((l) => l.emit === 'submit');
    const blocked = out.find((l) => l.ui === 'warningToast' || l.ui === 'errorToast');
    return {
      shown_menus: shown,
      invalid_menus_live: invalidNow,
      invalid_menus: plain(vm.invalidMenus),
      required_invalid: vm.requiredInvalid,
      total_price: totalPrice,
      selected: vm.selected.map((x) => ({ menu_id: x.menu.id, option_id: x.option.id, quantity: x.option.quantity })),
      submitted: !!emitted,
      blocked_reason: emitted ? null : blocked ? blocked.args[0] : null,
      basket_item: emitted ? plain(emitted.payload) : null,
      trace_total_prices: trace.map((t) => (t.skipped ? null : t.total_price)),
    };
  });
}

// ---------------------------------------------------------------- 2. basket

// input: {workflow, business, stored_basket|null, basket_age_minutes, steps:[{op, item|index}]}
// ops: "init" (initBasket), "add" (menu + button, then the option sheet returns `item`),
//      "submit_options" (only the sheet result, no shop check), "remove" (removeFromBasket from the menu),
//      "remove_line" (newRemoveFromBasket from the removal sheet)
function runBasket(parts, input, clock) {
  return withClock(clock, () => {
    const log = [];
    const store = makeStore(input);
    const business = makeBusiness(input);
    store.state.selectedBusiness = business;
    const vm = makeVm(shopTabDef(parts), parts, { log, store }, { deliveryWorkflows: null });
    const states = [];
    for (const step of input.steps) {
      let otherShopPrompt;
      if (step.op === 'init') vm.initBasket();
      else if (step.op === 'add') {
        // Menu "+" button: addToBasket(item, true) opens the option sheet, unless the basket is
        // for another shop (then a prompt opens). The sheet then calls submitOptions.
        vm.showOptions = !1;
        vm.addToBasket(JSON.parse(JSON.stringify(step.item)), !0);
        otherShopPrompt = !vm.showOptions;
        if (vm.showOptions) vm.submitOptions(JSON.parse(JSON.stringify(step.item)));
      } else if (step.op === 'submit_options') vm.submitOptions(JSON.parse(JSON.stringify(step.item)));
      else if (step.op === 'remove') vm.removeFromBasket(JSON.parse(JSON.stringify(step.item)), !0);
      else if (step.op === 'remove_line') vm.newRemoveFromBasket(JSON.parse(JSON.stringify(step.item)));
      const removalSheet = vm.showRemoval;
      states.push({
        op: step.op,
        other_shop_prompt: otherShopPrompt,
        kept_stored_basket: step.op === 'init' ? vm.basket === store.state.basket && !!input.stored_basket && vm.basket.created_at === input.stored_basket.created_at : undefined,
        shop_id: vm.basket ? vm.basket.shop_id : null,
        lines: vm.basket
          ? vm.basket.items.map((l) => ({ id: l.item.id, id2: l.item.id2 || null, name: l.item.name, quantity: l.quantity, comment: l.item.comment || null }))
          : [],
        removal_sheet: !!removalSheet,
        basket_value: vm.basketValue,
        item_count: vm.basketItemCount,
      });
      vm.showRemoval = !1;
    }
    return plain({ states });
  });
}

// ---------------------------------------------------------------- 3. checkout

// input: see README (cases.json schema, kind "checkout").
function runCheckout(parts, input, clock) {
  return withClock(clock, () => {
    const log = [];
    const store = makeStore(input);
    const business = makeBusiness(input);
    store.state.selectedBusiness = business;
    const vm = makeVm(shopTabDef(parts), parts, { log, store }, {
      deliveryWorkflows: input.delivery_workflows ? JSON.parse(JSON.stringify(input.delivery_workflows)) : null,
    });
    // Spies: which check stops "Place order".
    const stops = [];
    for (const name of ['promptMinOrder', 'promptMinOrderSongkran', 'promptFreeGiftOverRemoval', 'promptFulfilmentType', 'deliveryTooFarAlert']) {
      vm[name] = () => stops.push(name);
    }
    vm.basket = {
      shop_id: business.id,
      created_at: new Date(),
      items: JSON.parse(JSON.stringify(input.basket.items)),
      notes: input.basket.notes || null,
    };
    vm.fulfilmentType = input.fulfilment_type;
    if (input.fulfilment_time) vm.fulfilmentTime = input.fulfilment_time;
    let distanceCheck = null;
    if (input.fulfilment_type === 'delivery') {
      vm.address = JSON.parse(JSON.stringify(input.address));
      // getDrivingRoute (module f09f): then-branch, then finally-branch.
      if (input.route) {
        vm.drivingRoute = { distance: input.route.distance, source: input.route.source, encoded: 'poly' };
        vm.extraDistance && (vm.drivingRoute.distance += vm.extraDistance);
      }
      const logBefore = log.length;
      vm.setDeliveryFare();
      vm.deliveryFare && vm.updateDeliveryFee();
      if (vm.deliveryFare) distanceCheck = 'ok';
      else if (stops.includes('deliveryTooFarAlert')) distanceCheck = 'too_far';
      else if (log.slice(logBefore).some((l) => l.ui === 'warningAlert')) distanceCheck = 'too_short';
      else distanceCheck = 'no_fare';
    }
    const base = {
      line_totals: vm.basketItems.map((l) => vm.discountedTotalPrice(l.item)),
      basket_value: vm.basketValue,
      vatable_basket_value: vm.vatableBasketValue,
      item_count: vm.basketItemCount,
      vat: vm.vat,
      distance_check: distanceCheck,
    };
    if (distanceCheck && distanceCheck !== 'ok') {
      return plain(Object.assign(base, { max_delivery_distance: vm.maxDeliveryDistance, is_peak_hour: vm.isPeakHour }));
    }
    const stopsBefore = stops.length;
    vm.preSubmit();
    const blockedBy = stops.slice(stopsBefore)[0] || null;
    const snapshot = {
      fulfilment_discount_percent: vm.fulfilmentDiscountPercent,
      fulfilment_discount_amount: vm.fulfilmentDiscountAmount,
      max_delivery_distance: vm.maxDeliveryDistance,
      is_peak_hour: vm.isPeakHour,
      fare: plain(vm.deliveryFare),
      delivery_subsidy_percent: vm.deliverySubsidyPercent,
      delivery_subsidy: vm.deliverySubsidy,
      actual_delivery_subsidy: vm.actualDeliverySubsidy,
      remit_percent: vm.remitPercent,
      remit_amount: vm.remitAmount,
      actual_remit_amount: vm.actualRemitAmount,
      special_remit: vm.specialRemit,
      special_discount: vm.specialDiscount,
      billing_percent: vm.billingPercent,
      billing_amount: vm.billingAmount,
      free_delivery: plain(vm.freeDelivery),
      fixed_free_delivery_over: plain(vm.fixedFreeDeliveryOver),
      dynamic_free_delivery_over: plain(vm.dynamicFreeDeliveryOver),
      free_delivery_over: plain(vm.freeDeliveryOver),
      free_delivery_remainder: plain(vm.freeDeliveryRemainder),
      inside_free_delivery_polygon: vm.insideFreeDeliveryPolygon(),
      is_legacy_free_delivery: vm.isLegacyFreeDelivery,
      is_free_delivery: vm.isFreeDelivery,
      actual_delivery_fee: plain(vm.actualDeliveryFee),
      delivery_fee: vm.deliveryFee,
      total_value: vm.totalValue,
      blocked_by: blockedBy,
    };
    // ShopSubmitOrder: cash rules.
    const so = makeVm(parts.V.shopSubmitOrder(), parts, { log, store }, {
      commerce: business.workflows[0].data,
      fulfilment: vm.fulfilmentType,
    });
    snapshot.cash_allowed = so.hasCash;
    let order = null;
    let orderPageTotal = null;
    if (vm.showSubmit) {
      const n = store.dispatched.length;
      vm.submit({ method: input.payment_method || 'cash', isTest: !1, fulfilmentTime: vm.fulfilmentTime, driverTip: null });
      const call = store.dispatched.slice(n).find((d) => d.name === 'createTransaction');
      if (call) {
        const d = call.value.data;
        order = plain({
          type: d.type,
          order: {
            order_value: d.order.order_value,
            payment_method: d.order.payment_method,
            delivery_subsidy: d.order.delivery_subsidy,
            remit: d.order.remit,
            fulfilment_discount: d.order.fulfilment_discount,
            special_discount: d.order.special_discount,
            vat: d.order.vat,
            fulfilment_time: d.order.fulfilment_time,
          },
          settings: d.settings,
          delivery: d.delivery
            ? {
                workflow_id: d.delivery.workflow_id,
                express_fleet: d.delivery.express_fleet,
                fallback_fleet: d.delivery.fallback_fleet,
                is_free_delivery: d.delivery.is_free_delivery,
                type: d.delivery.type,
                delay_duration: d.delivery.delay_duration,
              }
            : null,
          // data.fare is the same object as expected.fare.
          fare_equals_expected_fare: d.fare === undefined ? null : JSON.stringify(d.fare) === JSON.stringify(plain(vm.deliveryFare)),
          billing: d.billing,
        });
        const op = makeVm(parts.V.commerceOrder(), parts, { log, store }, { transaction: call.value });
        orderPageTotal = op.orderValue;
      }
    }
    return plain(Object.assign(base, snapshot, { order, order_page_total: orderPageTotal }));
  });
}

// ---------------------------------------------------------------- 4. small functions

function runPackage(parts, code) {
  const p = parts.pkg;
  return plain({
    has_free_delivery: p.hasFreeDelivery(code),
    delivery_subsidy: p.deliverySubsidy(code),
    billing: p.billing(code),
    remit: p.remit(code),
  });
}

// input: {item, visual_discount?}. Returns the mixin values for one item, after the
// visual discount (handleVisualDiscount) when the case has one.
function runItemPrice(parts, input) {
  const log = [];
  const store = makeStore({});
  const item = JSON.parse(JSON.stringify(input.item));
  let after = item;
  if (input.visual_discount !== undefined) {
    const business = makeBusiness({
      workflow: { id: 'w', data: { visual_discount: input.visual_discount } },
      business: { id: 'b', lat: 0, lon: 0 },
      menu_items: [item],
    });
    store.state.selectedBusiness = business;
    const vm = makeVm(shopTabDef(parts), parts, { log, store }, {});
    vm.handleVisualDiscount();
    after = business.workflows[0].blobs[0].data[0];
  }
  const vm = makeVm({}, parts, { log, store }, {});
  return plain({
    item_after_visual_discount: input.visual_discount !== undefined ? { price: after.price, discount: after.discount } : null,
    weight_price: vm.weightPrice(after),
    discounted_price: vm.discountedPrice(after),
    option_prices: (after.options || []).map((o) => (o && o.option ? vm.optionPrice(o.option) : null)),
    discounted_total_price: vm.discountedTotalPrice(after),
  });
}

// input: {lat1, lon1, lat2, lon2, extra_distance?}: tripDistance (straight line x 1.25).
function runTripDistance(parts, input) {
  const log = [];
  const store = makeStore({ user: input.extra_distance ? { delivery_options: { extra_distance: input.extra_distance } } : null });
  const vm = makeVm(shopTabDef(parts), parts, { log, store }, {});
  return {
    straight_m: parts.geo.distanceToUserInMeters({ lat: input.lat1, lon: input.lon1 }, { lat: input.lat2, lon: input.lon2 }),
    trip_m: vm.tripDistance({ lat: input.lat1, lon: input.lon1 }, { lat: input.lat2, lon: input.lon2 }),
  };
}

let defaultParts = null;
function parts() {
  if (!defaultParts) defaultParts = makeParts(VERBATIM);
  return defaultParts;
}

module.exports = {
  VERBATIM,
  makeParts,
  resetShortIds,
  parts,
  runOptionSheet: (input, clock) => runOptionSheet(parts(), input, clock),
  runBasket: (input, clock) => runBasket(parts(), input, clock),
  runCheckout: (input, clock) => runCheckout(parts(), input, clock),
  runPackage: (code) => runPackage(parts(), code),
  runItemPrice: (input) => runItemPrice(parts(), input),
  runTripDistance: (input) => runTripDistance(parts(), input),
};
