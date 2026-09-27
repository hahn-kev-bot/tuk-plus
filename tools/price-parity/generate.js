#!/usr/bin/env node
// Makes the price parity fixtures for core:pricing.
//
//   node tools/price-parity/generate.js
//
// Reads the recorded live data (core/model/src/test/resources/fixtures/live), makes a
// fixed set of test cases with a seeded random generator, runs the web app's own price
// code (web-pricing.js) on each case and writes
// core/pricing/src/test/resources/parity/cases.json.
//
// The output is the same on each run for the same fixtures (seeded, fixed clock).
'use strict';
const fs = require('fs');
const path = require('path');
const web = require('./web-pricing.js');

const ROOT = path.resolve(__dirname, '..', '..');
const LIVE = path.join(ROOT, 'core/model/src/test/resources/fixtures/live');
const OUT = path.join(ROOT, 'core/pricing/src/test/resources/parity/cases.json');
const SEED = 20260927;

// ---------------------------------------------------------------- random

function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const chance = (p) => rnd() < p;
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
const clone = (x) => JSON.parse(JSON.stringify(x));

// ---------------------------------------------------------------- clock

// Local time in Chiang Mai (UTC+7) on the recording day.
function clock(hour, minute) {
  return { epoch_ms: Date.UTC(2026, 8, 27, hour - 7, minute), hour, minute };
}
const CLOCKS = [clock(12, 0), clock(12, 0), clock(12, 0), clock(17, 30), clock(18, 59), clock(16, 59), clock(19, 0), clock(22, 21), clock(8, 59), clock(9, 0)];

// ---------------------------------------------------------------- live data

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));

// Only the workflow.data keys that the price code reads (and delivery_options keys).
const WORKFLOW_KEYS = [
  'fruit', 'vat', 'min_order', 'takeaway_discount', 'dinein_discount', 'max_remit', 'free_delivery',
  'free_delivery_over', 'free_delivery_distance', 'free_delivery_polygon', 'free_delivery_to',
  'max_distance', 'visual_discount', 'express', 'fallback_fleet', 'delivery_options',
  'payment_options', 'menu_options', 'delivery_fee_per_km', 'min_delivery_fee',
];
const DELIVERY_OPTION_KEYS = ['type', 'delay_duration', 'remit_amount', 'extra_cash', 'extra_tukpay', 'reversed'];

function trimWorkflowData(d) {
  const out = {};
  for (const k of WORKFLOW_KEYS) {
    if (!(k in d)) continue;
    if (k === 'delivery_options') {
      if (!d[k]) {
        out[k] = d[k];
        continue;
      }
      const o = {};
      for (const j of DELIVERY_OPTION_KEYS) if (j in d[k]) o[j] = d[k][j];
      out[k] = o;
    } else out[k] = clone(d[k]);
  }
  return out;
}

function trimFleet(f) {
  if (!f) return null;
  const d = {};
  for (const k of ['pricing_array', 'surge', 'remit_amount', 'peak_hour_max_distance']) if (f.data && k in f.data) d[k] = f.data[k];
  return { state: f.state, data: d };
}

// Menu item fields that the price code or the option sheet reads.
const ITEM_KEYS = ['id', 'name', 'price', 'discount', 'discount_type', 'options', 'by_weight', 'actual_weight', 'estimated_weight', 'vatable', 'free_gift', 'max_count', 'out_of_stock', 'comment'];
function trimItem(i) {
  const o = {};
  for (const k of ITEM_KEYS) if (k in i) o[k] = clone(i[k]);
  return o;
}
// Option group blob: keep the rule fields and the option id/name/price. With a `condition`,
// the web code matches the text against the whole option JSON, so keep all option fields then.
function trimOptionBlob(b, keepFull) {
  const d = {};
  for (const k of ['name', 'required', 'select', 'allow_multiple', 'multiple_constraint', 'multiple_n', 'condition', 'items']) if (k in b.data) d[k] = clone(b.data[k]);
  if (!keepFull) d.items = (d.items || []).map((x) => {
    const o = { id: x.id, name: x.name, price: x.price };
    if ('out_of_stock' in x) o.out_of_stock = x.out_of_stock;
    return o;
  });
  return { id: b.id, blob_type: 'options_menu', data: d };
}

function loadShops() {
  const eateries = readJson(path.join(LIVE, 'eateries.json'));
  const fleetByName = {};
  const menuShops = [];
  for (const f of fs.readdirSync(path.join(LIVE, 'workflows')).sort()) {
    const businessId = f.replace('.json', '');
    const wfs = readJson(path.join(LIVE, 'workflows', f));
    const w = wfs.find((x) => x.name === 'Commerce');
    if (!w) continue;
    const cdPath = path.join(LIVE, 'commerce_delivery', w.id + '.json');
    const cd = fs.existsSync(cdPath) ? readJson(cdPath) : null;
    if (cd) {
      if (cd.express && w.data.express) fleetByName[w.data.express] = trimFleet(cd.express);
      if (cd.fallback && w.data.fallback_fleet) fleetByName[w.data.fallback_fleet] = trimFleet(cd.fallback);
    }
    const b = eateries.find((x) => x.id === businessId) || { id: businessId, lat: 18.79, lon: 98.98, country: 'th' };
    const blobs = w.blobs || [];
    const dm = blobs.find((x) => x.blob_type === 'digital_menu');
    menuShops.push({
      business: { id: b.id, lat: b.lat, lon: b.lon, country: b.country || 'th' },
      workflow: { id: w.id, data: trimWorkflowData(w.data) },
      fleets: cd ? { express: trimFleet(cd.express), fallback: trimFleet(cd.fallback) } : null,
      fulfilmentOptions: w.data.fulfilment_options,
      items: dm ? dm.data : [],
      optionBlobs: blobs.filter((x) => x.blob_type === 'options_menu'),
    });
  }
  const menuIds = new Set(menuShops.map((s) => s.business.id));
  const otherShops = [];
  for (const b of eateries) {
    const w = (b.workflows || []).find((x) => x.name === 'Commerce');
    if (!w || menuIds.has(b.id)) continue;
    const d = w.data || {};
    // Fleets as the server sends them: express by name, fallback when the shop has a fallback fleet.
    const express = d.express && fleetByName[d.express] ? fleetByName[d.express] : null;
    const fallback = d.fallback_fleet && fleetByName[d.fallback_fleet] ? fleetByName[d.fallback_fleet] : null;
    otherShops.push({
      business: { id: b.id, lat: b.lat, lon: b.lon, country: b.country || 'th' },
      workflow: { id: w.id, data: trimWorkflowData(d) },
      fleets: express || fallback ? { express, fallback } : null,
      fulfilmentOptions: d.fulfilment_options,
    });
  }
  return { menuShops, otherShops, fleetByName, eateries };
}

// ---------------------------------------------------------------- geometry

// A point `m` metres (straight line) from (lat, lon) in a random direction.
function offset(lat, lon, m) {
  const bearing = rnd() * 2 * Math.PI;
  const dLat = (m * Math.cos(bearing)) / 111320;
  const dLon = (m * Math.sin(bearing)) / (111320 * Math.cos((lat * Math.PI) / 180));
  return { lat: +(lat + dLat).toFixed(6), lon: +(lon + dLon).toFixed(6) };
}

// ---------------------------------------------------------------- option sheet and basket

function eligibleItems(shop) {
  return shop.items.filter((i) => !i.hidden && !i.out_of_stock && !(i.price < 9 && !(i.options && i.options.length) && !i.free_gift) && !i.free_gift);
}

function sheetInput(shop, item, actions, extra) {
  const referenced = new Set(item.options || []);
  const blobs = shop.optionBlobs.filter((b) => referenced.has(b.id));
  const keepFull = blobs.some((b) => b.data && b.data.condition);
  const wf = extra && extra.workflowData ? extra.workflowData : shop.workflow.data;
  return Object.assign(
    {
      workflow: { id: shop.workflow.id, data: wf },
      business: shop.business,
      menu_items: [trimItem(item)],
      options_menus: blobs.map((b) => trimOptionBlob(b, keepFull)),
      item_id: item.id,
      qty: 0,
      actions,
    },
    extra && extra.input ? extra.input : {},
  );
}

// Makes a random list of sheet actions. `valid` tries to meet the group rules.
function randomActions(shop, item, valid) {
  const referenced = new Set(item.options || []);
  const groups = shop.optionBlobs.filter((b) => referenced.has(b.id) && b.data && (b.data.items || []).length);
  const acts = [];
  for (const g of groups) {
    const d = g.data;
    const inStock = d.items.filter((x) => !x.out_of_stock);
    const any = d.items;
    if (!any.length) continue;
    const pool = valid ? inStock : any;
    if (!pool.length) continue;
    if (d.select === 'single') {
      if (d.required || chance(0.5) || !valid) acts.push({ op: 'select', menu_id: g.id, option_id: pick(pool).id });
      if (!valid && chance(0.3)) acts.push({ op: 'select', menu_id: g.id, option_id: pick(pool).id });
      continue;
    }
    // multiple
    const n = parseInt(d.multiple_n) || 1;
    let target;
    if (d.multiple_constraint === 'exactly') target = valid ? n : int(0, n + 2);
    else if (d.multiple_constraint === 'up_to') target = valid ? int(d.required ? 1 : 0, n) : int(0, n + 2);
    else target = int(d.required ? 1 : 0, 3);
    if (d.required && target === 0 && valid) target = 1;
    let total = 0;
    const chosen = shuffle(pool);
    let k = 0;
    while (total < target && k < chosen.length) {
      const o = chosen[k++];
      acts.push({ op: 'select', menu_id: g.id, option_id: o.id });
      total++;
      if (d.allow_multiple) {
        while (total < target && chance(0.5)) {
          acts.push({ op: 'add_quantity', menu_id: g.id, option_id: o.id });
          total++;
        }
      }
    }
    if (d.allow_multiple && total < target && k > 0) {
      const o = chosen[0];
      while (total < target) {
        acts.push({ op: 'add_quantity', menu_id: g.id, option_id: o.id });
        total++;
      }
    }
    if (!valid && chance(0.3) && k > 0) acts.push({ op: d.allow_multiple ? 'remove_quantity' : 'select', menu_id: g.id, option_id: chosen[0].id });
  }
  return acts;
}

// The basket item that the sheet makes for `item`, or null.
function sheetItem(shop, item, clk, workflowData) {
  for (let tries = 0; tries < 4; tries++) {
    const input = sheetInput(shop, item, randomActions(shop, item, true), { workflowData });
    if (chance(0.1)) input.comment = pick(['no ice', 'extra spicy', '']);
    const r = web.runOptionSheet(input, clk);
    if (r.submitted) return { item: r.basket_item, sheet: { input, result: r } };
  }
  return null;
}

// Keeps only the item fields the price code reads, and the option fields.
function trimBasketItem(it) {
  const o = trimItem(it);
  delete o.out_of_stock;
  delete o.max_count;
  if (it.id2) o.id2 = it.id2;
  if (Array.isArray(it.options)) {
    o.options = it.options.map((x) => {
      if (!x || typeof x !== 'object' || !x.option) return x;
      const opt = { id: x.option.id, price: x.option.price };
      if ('discounted_price' in x.option) opt.discounted_price = x.option.discounted_price;
      if ('quantity' in x.option) opt.quantity = x.option.quantity;
      return { menu: { id: x.menu.id, name: x.menu.name }, option: opt };
    });
  }
  return o;
}

// ---------------------------------------------------------------- cases

const cases = [];
let nextId = 1;
function add(kind, tags, input, clk, expected) {
  cases.push({ id: `${kind}-${String(nextId++).padStart(4, '0')}`, kind, tags, clock: clk ? { hour: clk.hour, minute: clk.minute, epoch_ms: clk.epoch_ms } : undefined, input, expected });
}

function checkoutInput(shop, basketItems, opts) {
  const input = {
    workflow: { id: shop.workflow.id, data: opts.workflowData || shop.workflow.data },
    business: shop.business,
    delivery_workflows: opts.fleets !== undefined ? opts.fleets : shop.fleets,
    user: opts.user || null,
    basket: { items: basketItems },
    fulfilment_type: opts.type,
  };
  if (opts.fulfilment_time) input.fulfilment_time = opts.fulfilment_time;
  if (opts.payment_method) input.payment_method = opts.payment_method;
  if (opts.menu_items) input.menu_items = opts.menu_items;
  if (opts.type === 'delivery') {
    const m = opts.distance;
    if (opts.route === false) {
      // No route: the web code uses the straight line x 1.25.
      input.address = offset(shop.business.lat, shop.business.lon, Math.round(m / 1.25));
      input.route = null;
    } else {
      input.address = clone(opts.address || offset(shop.business.lat, shop.business.lon, Math.round(m / 1.3)));
      input.route = { distance: m, source: opts.source || pick(['google', 'graphhopper']) };
    }
  }
  return input;
}

function runCheckoutCase(tags, input, clk) {
  web.resetShortIds();
  const expected = web.runCheckout(input, clk);
  add('checkout', tags, input, clk, expected);
  return expected;
}

function randomDistance(shop) {
  const fleet = shop.fleets ? shop.fleets.fallback || shop.fleets.express : null;
  const len = fleet && fleet.data.pricing_array ? fleet.data.pricing_array.length : 14;
  return pick([0, 20, 50, 51, 300, 499, 500, 1499, 1500, 2499, 2500, 4423, int(600, 6000), int(600, 6000), int(6000, 15000), int(15000, 30000), len * 1000, len * 1000 + 1, 27000, 27001, 27500, 35000]);
}

function typesFor(shop) {
  const fo = shop.fulfilmentOptions;
  return fo && fo.length ? fo : ['delivery', 'take-away', 'dine-in'];
}

function randomOrderType(shop) {
  const types = typesFor(shop);
  if (types.includes('delivery') && chance(0.6)) return 'delivery';
  return pick(types);
}

function randomUser() {
  if (!chance(0.1)) return null;
  const o = {};
  if (chance(0.7)) o.remit_amount = pick([2, 5, 10]);
  if (chance(0.5)) o.extra_distance = pick([200, 500, 1500]);
  return { delivery_options: o };
}

function randomCheckout(shop, basketItems, tags) {
  const type = randomOrderType(shop);
  const clk = pick(CLOCKS);
  const opts = { type, user: randomUser() };
  if (type === 'delivery') {
    opts.distance = randomDistance(shop);
    opts.route = chance(0.15) ? false : true;
  }
  if (chance(0.08)) opts.fulfilment_time = '19:30';
  if (chance(0.1)) opts.payment_method = 'promptpay';
  return runCheckoutCase(tags, checkoutInput(shop, basketItems, opts), clk);
}

// ---------------------------------------------------------------- A. shops with menus

function buildBasket(shop, clk, workflowData) {
  const items = eligibleItems(shop);
  if (!items.length) return [];
  const count = int(1, Math.min(5, items.length));
  const lines = [];
  for (const it of shuffle(items).slice(0, count)) {
    const r = sheetItem(shop, it, clk, workflowData);
    if (!r) continue;
    const hasId2 = !!r.item.id2;
    // The web app adds an item with options as a new line (quantity 1) each time.
    // Items without options merge into one line. We also use quantity > 1 on some option
    // lines: the formula is the same, and the native cart may allow it.
    const q = hasId2 ? (chance(0.8) ? 1 : int(2, 3)) : pick([1, 1, 1, 2, 3, 5]);
    lines.push({ item: trimBasketItem(r.item), quantity: q });
    if (hasId2 && chance(0.25)) {
      const r2 = sheetItem(shop, it, clk, workflowData);
      if (r2) lines.push({ item: trimBasketItem(r2.item), quantity: 1 });
    }
  }
  return lines;
}

function menuShopCases(shops) {
  for (const shop of shops) {
    const n = eligibleItems(shop).length ? 8 : 0;
    for (let k = 0; k < n; k++) {
      const clk = pick(CLOCKS);
      const basket = buildBasket(shop, clk);
      if (!basket.length) continue;
      randomCheckout(shop, basket, ['live-menu', `fruit:${shop.workflow.data.fruit || ''}`]);
    }
  }
}

// ---------------------------------------------------------------- B. shops without menus

function syntheticItem(k, pool) {
  // A live item without options, or a made-up one with a discount.
  if (pool.length && chance(0.6)) {
    const it = trimItem(pick(pool));
    delete it.out_of_stock;
    delete it.max_count;
    it.options = [];
    return it;
  }
  const it = { id: `syn-${k}`, name: `Item ${k}`, price: String(pick([25, 35, 45, 59, 60, 79, 89, 99, 120, 145, 150, 199, 250, 390, 450])), options: [] };
  const r = rnd();
  if (r < 0.25) it.discount = pick([5, 10, 15, 20, 25, 30, 33, 50]);
  else if (r < 0.3) {
    it.discount = pick([5, 10, 20, 50]);
    it.discount_type = 'number';
  } else if (r < 0.35) it.discount = pick(['10', '', '0']);
  return it;
}

function otherShopCases(shops, menuShops) {
  const pool = [];
  for (const s of menuShops) for (const i of eligibleItems(s)) if (!(i.options && i.options.length)) pool.push(i);
  let k = 0;
  for (const shop of shops) {
    const n = 1;
    for (let j = 0; j < n; j++) {
      const lines = [];
      const count = int(1, 4);
      for (let m = 0; m < count; m++) lines.push({ item: syntheticItem(k++, pool), quantity: pick([1, 1, 2, 3]) });
      randomCheckout(shop, lines, ['live-workflow', 'synthetic-basket', `fruit:${shop.workflow.data.fruit || ''}`]);
    }
  }
}

// ---------------------------------------------------------------- C. hand-picked checkout cases

function edgeCases(ctx) {
  const cmExpress = ctx.fleetByName['Chiang Mai Express Delivery'];
  const south = ctx.fleetByName['Chiang Mai South Express Delivery'];
  const base = {
    business: { id: 'edge-shop', lat: 18.79, lon: 98.98, country: 'th' },
    workflow: { id: 'edge-wf', data: { fruit: 'r_20_10', express: 'Chiang Mai Express Delivery' } },
    fleets: { express: cmExpress, fallback: null },
  };
  const shopWith = (data, fleets) => ({
    business: base.business,
    workflow: { id: base.workflow.id, data: Object.assign(clone(base.workflow.data), data) },
    fleets: fleets === undefined ? base.fleets : fleets,
  });
  const item = (id, price, extra) => Object.assign({ id, name: `Edge ${id}`, price: String(price), options: [] }, extra || {});
  const noon = clock(12, 0);
  const basketOf = (value) => [{ item: item('v', value), quantity: 1 }];

  // Every package code, known and unknown, at two basket sizes and two distances, and take-away.
  const codes = ['r_20_10', 'r_15_9', 'r_10_0', 'r_14_8', 'r_20', 'r_', 'r_20_10_5', 'r_x_y', 'R_20_10', 'p_10_5', 'p_0_20', 'p_5_5', 'p_15_8', 'p_10', 'f_10_10', 'f_20_0', 'f_10', 'thai', 'thai10', 'thai10_5', 'thai1010', 'thai15_10', 'thai20', 'thai25_0', 'thai0_10', 'thai99_1', 'apple', 'elderberry', 'fig', 'durian', 'banana', '', null];
  for (const code of codes) {
    const data = code === null ? { fruit: undefined } : { fruit: code };
    const shop = shopWith(data);
    if (code === null) delete shop.workflow.data.fruit;
    for (const value of [150, 820]) {
      runCheckoutCase(['edge', 'package', `fruit:${code}`], checkoutInput(shop, basketOf(value), { type: 'delivery', distance: value === 150 ? 1200 : 6400, source: 'google' }), noon);
      runCheckoutCase(['edge', 'package', `fruit:${code}`], checkoutInput(shop, basketOf(value), { type: 'take-away' }), noon);
    }
  }
  // p_: special remit is clamped to 0..30. f_: special discount.
  for (const code of ['p_10_5', 'p_20_5', 'f_10_10', 'f_20_0']) {
    for (const value of [300, 600, 1500, 2500]) runCheckoutCase(['edge', 'special-remit', `fruit:${code}`], checkoutInput(shopWith({ fruit: code }), basketOf(value), { type: 'delivery', distance: 3000, source: 'google' }), noon);
  }
  // Dynamic free delivery threshold: ceil(client / subsidy percent), with float division.
  for (const [code, value] of [['r_20_10', 529], ['r_20_10', 530], ['r_20_10', 531], ['r_15_7', 757], ['r_15_7', 758], ['r_20_9', 588], ['r_20_9', 589]]) {
    runCheckoutCase(['edge', 'free-delivery-threshold'], checkoutInput(shopWith({ fruit: code, delivery_options: { remit_amount: 3 } }), basketOf(value), { type: 'delivery', distance: 4423, source: 'google' }), noon);
  }
  // VAT, vatable false, rounding of .5.
  for (const value of [50, 150, 151, 999]) {
    runCheckoutCase(['edge', 'vat'], checkoutInput(shopWith({ fruit: 'r_10_0', vat: 0.07 }), basketOf(value), { type: 'take-away' }), noon);
  }
  runCheckoutCase(['edge', 'vat', 'vatable-false'], checkoutInput(shopWith({ fruit: 'r_10_0', vat: 0.07 }), [{ item: item('a', 150), quantity: 2 }, { item: item('b', 80, { vatable: false }), quantity: 1 }, { item: item('c', 45, { vatable: true }), quantity: 1 }], { type: 'delivery', distance: 2600, source: 'google' }), noon);
  // Item discounts: percent, number, strings, over 100 %, float rounding.
  const discountItems = [
    item('p30', 35, { discount: 30 }), item('p10', 45, { discount: 10 }), item('p33', 99, { discount: 33 }),
    item('p150', 60, { discount: 150 }), item('n20', 50, { discount: 20, discount_type: 'number' }),
    item('n99', 50, { discount: 99, discount_type: 'number' }), item('s10', 80, { discount: '10' }),
    item('s0', 80, { discount: '0' }), item('sempty', 80, { discount: '' }), item('dot', '12.5', { discount: 10 }),
    item('pt', 230, { discount: 0, discount_type: 'percent' }), item('ns', 90, { discount: '15', discount_type: 'number' }),
  ];
  for (const it of discountItems) runCheckoutCase(['edge', 'item-discount'], checkoutInput(shopWith({}), [{ item: it, quantity: 3 }], { type: 'take-away' }), noon);
  // Take-away and dine-in discounts.
  for (const [data, type] of [[{ takeaway_discount: 15 }, 'take-away'], [{ takeaway_discount: 15 }, 'dine-in'], [{ takeaway_discount: 15 }, 'delivery'], [{ dinein_discount: 20 }, 'dine-in'], [{ takeaway_discount: 5, dinein_discount: 10 }, 'dine-in'], [{ takeaway_discount: 50, min_order: 150 }, 'take-away'], [{ takeaway_discount: 0 }, 'take-away'], [{ takeaway_discount: null }, 'take-away'], [{ takeaway_discount: 12.5 }, 'take-away']]) {
    runCheckoutCase(['edge', 'fulfilment-discount'], checkoutInput(shopWith(data), [{ item: item('t', 133), quantity: 1 }, { item: item('u', 45), quantity: 2 }], { type, distance: 2000, source: 'google' }), noon);
  }
  // Fixed free delivery over, distance limit, polygon (live shop 15efa4ee has all three).
  const polyShop = ctx.eateries.find((b) => b.id.startsWith('15efa4ee'));
  if (polyShop) {
    const w = polyShop.workflows.find((x) => x.name === 'Commerce');
    const shop = { business: { id: polyShop.id, lat: polyShop.lat, lon: polyShop.lon, country: 'th' }, workflow: { id: w.id, data: trimWorkflowData(w.data) }, fleets: { express: cmExpress, fallback: null } };
    const inside = { lat: 18.795, lon: 98.985 };
    const outside = { lat: 18.83, lon: 99.03 };
    for (const [addr, dist] of [[inside, 1500], [outside, 7000], [outside, 9000]]) {
      for (const value of [150, 249, 260]) runCheckoutCase(['edge', 'free-delivery-over', 'polygon'], checkoutInput(shop, basketOf(value), { type: 'delivery', distance: dist, source: 'google', address: addr }), noon);
    }
  }
  for (const [data, value] of [[{ fruit: 'apple', free_delivery: true }, 200], [{ fruit: 'apple', free_delivery_over: 300 }, 250], [{ fruit: 'apple', free_delivery_over: 300 }, 300], [{ fruit: 'apple', free_delivery_over: -1 }, 100], [{ fruit: 'r_20_10', free_delivery_over: 300 }, 320], [{ fruit: 'r_20_10', free_delivery_over: 800 }, 700], [{ fruit: 'r_20_10', free_delivery_over: 300, free_delivery_distance: 3000 }, 320]]) {
    runCheckoutCase(['edge', 'free-delivery'], checkoutInput(shopWith(data), basketOf(value), { type: 'delivery', distance: 4000, source: 'google' }), noon);
  }
  // max_remit.
  for (const value of [100, 400, 2000]) runCheckoutCase(['edge', 'max-remit'], checkoutInput(shopWith({ max_remit: 30 }), basketOf(value), { type: 'delivery', distance: 2500, source: 'google' }), noon);
  // min_order: delivery only.
  for (const type of ['delivery', 'take-away']) for (const value of [99, 100]) runCheckoutCase(['edge', 'min-order'], checkoutInput(shopWith({ min_order: 100 }), basketOf(value), { type, distance: 2500, source: 'google' }), noon);
  // Delivery type and the 1000 rule.
  for (const [dopt, value, ft] of [[{ type: 'normal', delay_duration: 0 }, 999], [{ type: 'normal', delay_duration: 0 }, 1000], [{ type: 'delayed', delay_duration: 15 }, 1200], [{ type: 'immediate' }, 1500], [undefined, 1000], [{ type: 'third-party' }, 1200], [{ type: 'normal', delay_duration: 5 }, 400, '19:30'], [{ type: 'delayed', delay_duration: 20, reversed: true }, 300]]) {
    const data = dopt ? { delivery_options: dopt } : {};
    runCheckoutCase(['edge', 'delivery-type'], checkoutInput(shopWith(data), basketOf(value), { type: 'delivery', distance: 3000, source: 'google', fulfilment_time: ft }), noon);
  }
  // Fleet choice: fallback wins for price, surge and peak; express wins for remit.
  const fl = (arr, remit, surge, peak) => {
    const d = { pricing_array: arr, surge };
    if (remit !== undefined) d.remit_amount = remit;
    if (peak !== undefined) d.peak_hour_max_distance = peak;
    return { state: 'active', data: d };
  };
  const arrA = cmExpress.data.pricing_array;
  const arrB = arrA.map((x) => x + 5).slice(0, 20);
  const fleetSets = [
    { express: fl(arrA, 10, 0, 27), fallback: fl(arrB, 7, 15, 8) },
    { express: fl(arrA, undefined, 5, 27), fallback: fl(arrB, 7, 0, 12) },
    { express: fl(arrA, 0, 5, 27), fallback: null },
    { express: fl(arrA, 12, 20, 6), fallback: null },
    { express: null, fallback: fl(arrB, 9, 0, 27) },
    null,
  ];
  for (const fleets of fleetSets) {
    for (const clk of [noon, clock(17, 30)]) {
      for (const dist of [1400, 7000, 9000, 19000, 21000]) {
        runCheckoutCase(['edge', 'fleet'], checkoutInput(shopWith({}, fleets), basketOf(260), { type: 'delivery', distance: dist, source: 'graphhopper' }, fleets), clk);
      }
    }
  }
  // Self delivery and the default price model, max_distance of the shop.
  for (const data of [{ express: 'self', delivery_fee_per_km: 12, min_delivery_fee: 40 }, { express: 'self' }, { max_distance: 5 }, { max_distance: 5, express: 'self', delivery_fee_per_km: 9, min_delivery_fee: 35 }]) {
    for (const dist of [1000, 2999, 3000, 4999, 5000, 9999, 12000]) runCheckoutCase(['edge', 'no-fleet'], checkoutInput(shopWith(data, null), basketOf(300), { type: 'delivery', distance: dist, source: 'google' }), noon);
  }
  // Extra cash, extra tukpay, shop remit, user remit and extra distance.
  for (const [dopt, user] of [[{ extra_cash: 20 }, null], [{ extra_cash: 10, extra_tukpay: 5, remit_amount: 4 }, null], [{ remit_amount: 3 }, { delivery_options: { remit_amount: 5 } }], [{}, { delivery_options: { extra_distance: 800 } }]]) {
    for (const route of [true, false]) runCheckoutCase(['edge', 'extras'], checkoutInput(shopWith({ delivery_options: dopt }), basketOf(420), { type: 'delivery', distance: 4700, source: 'google', user, route }), noon);
  }
  // Distance limits: 50 m, array length, peak hour.
  for (const dist of [0, 50, 51, 499, 500, 1499, 1500, 27000, 27001, 28000, 28001]) {
    for (const clk of [noon, clock(18, 59), clock(19, 0)]) runCheckoutCase(['edge', 'distance'], checkoutInput(shopWith({}, { express: cmExpress, fallback: null }), basketOf(300), { type: 'delivery', distance: dist, source: 'google' }), clk);
  }
  if (south) for (const dist of [26500, 27000, 27001]) runCheckoutCase(['edge', 'distance'], checkoutInput(shopWith({}, { express: cmExpress, fallback: south }), basketOf(300), { type: 'delivery', distance: dist, source: 'google' }), noon);
  // Cash hours.
  for (const clk of [clock(22, 20), clock(22, 21), clock(8, 59), clock(9, 0), clock(23, 30)]) {
    for (const type of ['delivery', 'take-away']) runCheckoutCase(['edge', 'cash'], checkoutInput(shopWith({ payment_options: ['cash'] }), basketOf(300), { type, distance: 2000, source: 'google' }), clk);
  }
  runCheckoutCase(['edge', 'cash'], checkoutInput(shopWith({ payment_options: ['card'] }), basketOf(300), { type: 'take-away' }), noon);
  // Options in the basket: discounted_price, quantity, negative price, quantity 0 and null.
  const withOpts = item('o', 120, { discount: 10, id2: 'line1', options: [
    { menu: { id: 'm1', name: 'Size' }, option: { id: 'L', price: '20', discounted_price: 18, quantity: 1 } },
    { menu: { id: 'm2', name: 'Add' }, option: { id: 'egg', price: '15', quantity: 3 } },
    { menu: { id: 'm3', name: 'No' }, option: { id: 'less', price: '-20', quantity: 1 } },
    { menu: { id: 'm4', name: 'Zero' }, option: { id: 'z', price: '10', quantity: 0 } },
    { menu: { id: 'm5', name: 'Null' }, option: { id: 'n', price: '10', quantity: null } },
    { menu: { id: 'm6', name: 'Float' }, option: { id: 'f', price: '25', discounted_price: 22.5 } },
    { menu: { id: 'm7', name: 'Zero disc' }, option: { id: 'zd', price: '25', discounted_price: 0 } },
  ] });
  runCheckoutCase(['edge', 'options'], checkoutInput(shopWith({}), [{ item: withOpts, quantity: 2 }], { type: 'delivery', distance: 3500, source: 'google' }), noon);
  // by_weight: price is per weight when the shop has set actual_weight.
  for (const bw of [{ by_weight: 'per 100 grams', estimated_weight: 250 }, { by_weight: 'per 100 grams', actual_weight: '255' }, { by_weight: 'per kg', actual_weight: '1250' }, { by_weight: 'per gram', actual_weight: '3' }]) {
    runCheckoutCase(['edge', 'by-weight'], checkoutInput(shopWith({}), [{ item: item('w', 89, bw), quantity: 1 }], { type: 'take-away' }), noon);
  }
  // Free gift: the order is stopped when the basket is below the gift threshold.
  const gift = item('gift', 0, { free_gift: '500' });
  for (const value of [300, 500]) {
    runCheckoutCase(['edge', 'free-gift'], checkoutInput(shopWith({}), [{ item: item('v', value), quantity: 1 }, { item: gift, quantity: 1 }], { type: 'take-away', menu_items: [item('v', value), gift] }), noon);
  }
}

// ---------------------------------------------------------------- D. option sheet cases

function optionCases(menuShops) {
  // Live groups: random valid and invalid selections.
  for (const shop of menuShops) {
    const withOptions = shop.items.filter((i) => i.options && i.options.length && !i.hidden);
    for (const it of shuffle(withOptions).slice(0, 3)) {
      const valid = chance(0.6);
      const input = sheetInput(shop, it, randomActions(shop, it, valid));
      add('option_sheet', ['live-menu', valid ? 'valid-try' : 'invalid-try'], input, clock(12, 0), web.runOptionSheet(input, clock(12, 0)));
    }
  }
  // Synthetic groups for rules that the live data does not have.
  const shop = { business: { id: 'opt-shop', lat: 18.79, lon: 98.98, country: 'th' }, workflow: { id: 'opt-wf', data: { fruit: 'r_20_10' } } };
  const opt = (id, price, extra) => Object.assign({ id, name: `Opt ${id}`, price: String(price) }, extra || {});
  const group = (id, data) => ({ id, blob_type: 'options_menu', data: Object.assign({ name: `Group ${id}`, required: false, select: 'multiple', items: [opt(id + 'a', 0), opt(id + 'b', 10), opt(id + 'c', 25), opt(id + 'd', 5, { out_of_stock: true })] }, data) });
  const groups = [
    group('ex2', { select: 'multiple', multiple_constraint: 'exactly', multiple_n: 2, allow_multiple: true, required: true }),
    group('ex3opt', { select: 'multiple', multiple_constraint: 'exactly', multiple_n: 3, allow_multiple: false, required: false }),
    group('up2', { select: 'multiple', multiple_constraint: 'up_to', multiple_n: 2, allow_multiple: true }),
    group('up1s', { select: 'multiple', multiple_constraint: 'up_to', multiple_n: '1', allow_multiple: false }),
    group('none', { select: 'multiple', multiple_constraint: 'none', multiple_n: 1, allow_multiple: true }),
    group('nocon', { select: 'multiple' }),
    group('exnan', { select: 'multiple', multiple_constraint: 'exactly' }),
    group('single', { select: 'single', required: true }),
    group('singleopt', { select: 'single', required: false, allow_multiple: true }),
    group('cond', { select: 'single', condition: 'opt singlec' }),
    group('condUp', { select: 'single', condition: 'Opt singleb' }),
  ];
  const itemsDef = [
    { id: 'it1', name: 'Rice box', price: '100', options: ['single', 'ex2', 'up2'] },
    { id: 'it2', name: 'Salad', price: '120', discount: 20, options: ['singleopt', 'none', 'up1s', 'nocon'] },
    { id: 'it3', name: 'Tea', price: '60', discount: 10, discount_type: 'number', options: ['ex3opt', 'exnan'] },
    { id: 'it4', name: 'Noodles', price: '80', options: ['single', 'cond', 'condUp'] },
    { id: 'it5', name: 'Limited', price: '50', max_count: 2, options: ['singleopt'] },
    { id: 'it6', name: 'Gift', price: '0', free_gift: '300', options: ['singleopt'] },
    { id: 'it7', name: 'Missing group', price: '70', options: ['nope'] },
    { id: 'it8', name: 'Percent 15', price: '99', discount: 15, options: ['ex2', 'none'] },
  ];
  const sshop = Object.assign({}, shop, { items: itemsDef, optionBlobs: groups });
  const scripted = [
    ['it1', [['select', 'single', 'singleb'], ['select', 'ex2', 'ex2b'], ['add_quantity', 'ex2', 'ex2b']]],
    ['it1', [['select', 'single', 'singleb'], ['select', 'ex2', 'ex2b']]],
    ['it1', [['select', 'single', 'singleb'], ['select', 'ex2', 'ex2b'], ['select', 'ex2', 'ex2c'], ['select', 'up2', 'up2c'], ['add_quantity', 'up2', 'up2c'], ['add_quantity', 'up2', 'up2c']]],
    ['it1', [['select', 'ex2', 'ex2b'], ['select', 'ex2', 'ex2c']]],
    ['it1', [['select', 'single', 'singleb'], ['select', 'single', 'singlec'], ['select', 'ex2', 'ex2a'], ['select', 'ex2', 'ex2b']]],
    ['it1', [['select', 'single', 'singled'], ['select', 'ex2', 'ex2d'], ['select', 'ex2', 'ex2b'], ['add_quantity', 'ex2', 'ex2b']]],
    ['it1', [['select', 'single', 'singleb'], ['select', 'ex2', 'ex2b'], ['add_quantity', 'ex2', 'ex2b'], ['add_quantity', 'ex2', 'ex2b'], ['remove_quantity', 'ex2', 'ex2b']]],
    ['it1', [['select', 'single', 'singleb'], ['select', 'ex2', 'ex2b'], ['remove_quantity', 'ex2', 'ex2b'], ['select', 'ex2', 'ex2c'], ['add_quantity', 'ex2', 'ex2c']]],
    ['it2', []],
    ['it2', [['select', 'singleopt', 'singleoptc'], ['select', 'singleopt', 'singleoptc']]],
    ['it2', [['select', 'none', 'nonea'], ['select', 'none', 'noneb'], ['add_quantity', 'none', 'noneb'], ['select', 'up1s', 'up1sb'], ['select', 'up1s', 'up1sc']]],
    ['it2', [['select', 'nocon', 'noconb'], ['select', 'nocon', 'noconc'], ['select', 'up1s', 'up1sc']]],
    ['it3', []],
    ['it3', [['select', 'ex3opt', 'ex3opta'], ['select', 'ex3opt', 'ex3optb'], ['select', 'ex3opt', 'ex3optc']]],
    ['it3', [['select', 'ex3opt', 'ex3opta'], ['select', 'ex3opt', 'ex3optb'], ['select', 'ex3opt', 'ex3optc'], ['select', 'exnan', 'exnanb']]],
    ['it4', [['select', 'single', 'singlec'], ['select', 'cond', 'condb']]],
    ['it4', [['select', 'single', 'singlec'], ['select', 'cond', 'condb'], ['select', 'single', 'singleb']]],
    ['it4', [['select', 'single', 'singleb'], ['select', 'condUp', 'condUpb']]],
    ['it5', [['select', 'singleopt', 'singleoptb']]],
    ['it6', [['select', 'singleopt', 'singleoptb']]],
    ['it7', []],
    ['it8', [['select', 'ex2', 'ex2c'], ['add_quantity', 'ex2', 'ex2c'], ['select', 'none', 'nonec'], ['add_quantity', 'none', 'nonec']]],
  ];
  const variants = [
    { tag: 'plain', wf: { fruit: 'r_20_10' }, qty: 0 },
    { tag: 'propagate-discounts', wf: { fruit: 'r_20_10', menu_options: { propagate_discounts: true } }, qty: 0 },
    { tag: 'qty-1', wf: { fruit: 'r_20_10' }, qty: 1 },
    { tag: 'qty-2', wf: { fruit: 'r_20_10' }, qty: 2 },
  ];
  for (const v of variants) {
    for (const [itemId, acts] of scripted) {
      if (v.tag.startsWith('qty') && !['it5', 'it6'].includes(itemId)) continue;
      const it = itemsDef.find((x) => x.id === itemId);
      const input = sheetInput(sshop, it, acts.map(([op, menu_id, option_id]) => ({ op, menu_id, option_id })), { workflowData: v.wf, input: { qty: v.qty } });
      // keep full option JSON: the condition groups match text against it.
      input.options_menus = groups.filter((g) => (it.options || []).includes(g.id)).map((g) => clone(g));
      add('option_sheet', ['synthetic', v.tag], input, clock(12, 0), web.runOptionSheet(input, clock(12, 0)));
    }
  }
  // Random tries on the synthetic groups, with and without propagate_discounts.
  for (let k = 0; k < 40; k++) {
    const it = pick(itemsDef.filter((x) => !['it5', 'it6', 'it7'].includes(x.id)));
    const wf = chance(0.5) ? { fruit: 'r_20_10', menu_options: { propagate_discounts: true } } : { fruit: 'r_20_10' };
    const input = sheetInput(sshop, it, randomActions(sshop, it, chance(0.5)), { workflowData: wf });
    input.options_menus = groups.filter((g) => (it.options || []).includes(g.id)).map((g) => clone(g));
    add('option_sheet', ['synthetic', 'random'], input, clock(12, 0), web.runOptionSheet(input, clock(12, 0)));
  }
  return { sshop, groups, itemsDef };
}

// ---------------------------------------------------------------- E. basket cases

function basketCases(menuShops, synth) {
  const clk = clock(12, 0);
  const shop = { business: { id: 'shop-A', lat: 18.79, lon: 98.98, country: 'th' }, workflow: { id: 'wf-A', data: { fruit: 'r_20_10' } } };
  const iso = (minutesAgo) => new Date(clk.epoch_ms - minutesAgo * 60000).toISOString();
  const plainItem = (id, name, price) => ({ id, name, price: String(price), options: [] });
  const stored = (shopId, minutesAgo, items) => ({ shop_id: shopId, created_at: iso(minutesAgo), notes: null, items: items || [{ item: plainItem('x1', 'Stored item', 90), quantity: 2 }] });
  // 60-minute rule.
  for (const [shopId, age] of [['shop-A', 30], ['shop-A', 61], ['shop-A', 600], ['shop-B', 30], ['shop-B', 60], ['shop-B', 61], ['shop-B', 600]]) {
    const input = { workflow: shop.workflow, business: shop.business, stored_basket: stored(shopId, age), basket_age_minutes: age, steps: [{ op: 'init' }] };
    add('basket', ['init-basket'], input, clk, web.runBasket(input, clk));
  }
  // Stored basket from another shop, younger than 60 minutes: adding opens the "shop here?" prompt.
  {
    const input = { workflow: shop.workflow, business: shop.business, stored_basket: stored('shop-B', 20), basket_age_minutes: 20, steps: [{ op: 'init' }, { op: 'add', item: Object.assign(plainItem('a', 'Alpha', 50), { options: [] }) }] };
    add('basket', ['init-basket', 'other-shop'], input, clk, web.runBasket(input, clk));
  }
  // Empty stored basket from another shop.
  {
    const input = { workflow: shop.workflow, business: shop.business, stored_basket: stored('shop-B', 5, []), basket_age_minutes: 5, steps: [{ op: 'init' }, { op: 'add', item: Object.assign(plainItem('a', 'Alpha', 50), { options: [] }) }] };
    add('basket', ['init-basket', 'other-shop-empty'], input, clk, web.runBasket(input, clk));
  }
  // Merge rules: lines without id2 merge (and take the new comment), lines with id2 never merge.
  const a = Object.assign(plainItem('a', 'Alpha', 50), { options: [], comment: null });
  const aNote = Object.assign(clone(a), { comment: 'no ice' });
  const b = Object.assign(plainItem('b', 'bravo', 70), { options: [] });
  const withOpt = (id2, optPrice) => ({ id: 'c', name: 'Charlie', price: '80', id2, comment: null, options: [{ menu: { id: 'm', name: 'Size' }, option: { id: 'L', price: String(optPrice), quantity: 1 } }] });
  const seqs = [
    [a, a, b],
    [a, aNote, a],
    [withOpt('id2-1', 10), withOpt('id2-2', 10), a],
    [b, a, withOpt('id2-1', 20), b],
    [Object.assign(plainItem('z', 'zulu', 10), { options: [] }), Object.assign(plainItem('e', 'Écrevisse', 10), { options: [] }), Object.assign(plainItem('y', 'Yak', 10), { options: [] }), Object.assign(plainItem('t', 'ต้มยำ', 10), { options: [] })],
  ];
  for (const seq of seqs) {
    const input = { workflow: shop.workflow, business: shop.business, stored_basket: null, steps: [{ op: 'init' }, ...seq.map((item) => ({ op: 'add', item }))] };
    add('basket', ['merge'], input, clk, web.runBasket(input, clk));
  }
  // Removal from the menu (by item id) and from the removal sheet (by id2).
  const menuC = { id: 'c', name: 'Charlie', price: '80', options: ['m'] };
  const removals = [
    [{ op: 'add', item: a }, { op: 'add', item: a }, { op: 'remove', item: a }, { op: 'remove', item: a }],
    [{ op: 'add', item: withOpt('id2-1', 10) }, { op: 'remove', item: menuC }],
    [{ op: 'add', item: withOpt('id2-1', 10) }, { op: 'add', item: withOpt('id2-2', 20) }, { op: 'remove', item: menuC }, { op: 'remove_line', item: withOpt('id2-2', 20) }],
  ];
  for (const steps of removals) {
    const input = { workflow: shop.workflow, business: shop.business, stored_basket: null, steps: [{ op: 'init' }, ...steps] };
    add('basket', ['remove'], input, clk, web.runBasket(input, clk));
  }
}

// ---------------------------------------------------------------- F. small functions

function smallCases() {
  const codes = ['r_20_10', 'r_15_9', 'r_10_0', 'r_20', 'r_', 'r_20_10_5', 'r_x_10', 'r_20_x', 'R_20_10', 'r_020_010', 'r_2.5_10', 'r_-5_10', 'p_10_5', 'p_0_20', 'p_10', 'p_', 'p_x_5', 'p_10_5_1', 'f_10_10', 'f_20_0', 'f_10', 'f_', 'thai', 'thai0', 'thai0_10', 'thai0_15', 'thai0_20', 'thai0_25', 'thai10', 'thai10_5', 'thai1010', 'thai10_10', 'thai10_0', 'thai15', 'thai15_5', 'thai15_10', 'thai20', 'thai20_0', 'thai20_5', 'thai25', 'thai25_0', 'thai30_5', 'thai_5', 'apple', 'elderberry', 'fig', 'durian', 'banana', '', null];
  for (const c of codes) add('package', ['package'], { fruit: c }, null, web.runPackage(c));

  const items = [
    { price: '100' }, { price: '100', discount: 10 }, { price: '35', discount: 30 }, { price: '99', discount: 33 },
    { price: '45', discount: 10 }, { price: '12.5' }, { price: '12.5', discount: 10 }, { price: '12.9', discount: 50 },
    { price: '60', discount: 150 }, { price: '50', discount: 20, discount_type: 'number' }, { price: '50', discount: 60, discount_type: 'number' },
    { price: '50', discount: '20', discount_type: 'number' }, { price: '80', discount: '' }, { price: '80', discount: '0' }, { price: '80', discount: 0 },
    { price: '80', discount: '10' }, { price: '80', discount: 12.5 }, { price: '' }, { price: 'abc' }, { price: null }, { price: 99 },
    { price: '  45' }, { price: '1e3' }, { price: '-30' }, { price: '89', by_weight: 'per 100 grams' },
    { price: '89', by_weight: 'per 100 grams', actual_weight: '255' }, { price: '400', by_weight: 'per kg', actual_weight: '1250' },
    { price: '2', by_weight: 'per gram', actual_weight: '3.9' }, { price: '89', by_weight: 'per piece', actual_weight: '3' },
    { price: '89', by_weight: 'per 100 grams', actual_weight: '255', discount: 10 },
    { price: '120', discount: 10, options: [
      { menu: { id: 'm' }, option: { id: 'a', price: '20' } }, { menu: { id: 'm' }, option: { id: 'b', price: '15', quantity: 3 } },
      { menu: { id: 'm' }, option: { id: 'c', price: '-20', quantity: 1 } }, { menu: { id: 'm' }, option: { id: 'd', price: '10', quantity: 0 } },
      { menu: { id: 'm' }, option: { id: 'e', price: '25', discounted_price: 22.5 } }, { menu: { id: 'm' }, option: { id: 'f', price: '25', discounted_price: 0 } },
      { menu: { id: 'm' }, option: { id: 'g', price: '25', discounted_price: '21.25', quantity: 2 } }, { menu: { id: 'm' }, option: { id: 'h', price: 0 } },
      { menu: { id: 'm' } }, 'raw-menu-id',
    ] },
    { price: '50', options: [{ menu: { id: 'm' }, option: { id: 'x', price: '' } }] },
    { price: '50', options: [{ menu: { id: 'm' }, option: { id: 'x', price: null } }] },
  ];
  for (const it of items) add('item_price', ['item'], { item: it }, null, web.runItemPrice({ item: it }));
  // Visual discount (handleVisualDiscount), applied once.
  for (const [it, vd] of [[{ price: '90' }, 0.1], [{ price: '90', discount: 10 }, 0.1], [{ price: '95', discount: '5' }, 0.1], [{ price: '99' }, 0.15], [{ price: '12.5' }, 0.2], [{ price: '100', discount: 20, discount_type: 'number' }, 0.1], [{ price: '100', discount: '' }, 0.25]]) {
    add('item_price', ['visual-discount'], { item: it, visual_discount: vd }, null, web.runItemPrice({ item: it, visual_discount: vd }));
  }
  // Float search: prices and percent discounts where p * (1 - d / 100) lands near .5.
  let found = 0;
  for (let p = 10; p <= 600 && found < 25; p++) {
    for (let d = 1; d < 100 && found < 25; d++) {
      const f = p * (1 - d / 100);
      const exact = (p * (100 - d)) / 100;
      if (Math.round(f) !== Math.round(exact)) {
        const it = { price: String(p), discount: d };
        add('item_price', ['float-rounding'], { item: it }, null, web.runItemPrice({ item: it }));
        found++;
      }
    }
  }
  // Straight-line distance (no route).
  for (let k = 0; k < 20; k++) {
    const lat1 = 18.7 + rnd() * 0.2;
    const lon1 = 98.9 + rnd() * 0.2;
    const lat2 = lat1 + (rnd() - 0.5) * 0.2;
    const lon2 = lon1 + (rnd() - 0.5) * 0.2;
    const input = { lat1: +lat1.toFixed(6), lon1: +lon1.toFixed(6), lat2: +lat2.toFixed(6), lon2: +lon2.toFixed(6) };
    if (k % 4 === 0) input.extra_distance = 500;
    add('trip_distance', ['haversine'], input, null, web.runTripDistance(input));
  }
}

// ---------------------------------------------------------------- main

function main() {
  const ctx = loadShops();
  edgeCases(ctx);
  menuShopCases(ctx.menuShops);
  otherShopCases(ctx.otherShops, ctx.menuShops);
  const synth = optionCases(ctx.menuShops);
  basketCases(ctx.menuShops, synth);
  smallCases();

  const counts = {};
  for (const c of cases) counts[c.kind] = (counts[c.kind] || 0) + 1;
  const fruitCounts = {};
  for (const b of ctx.eateries) {
    const w = (b.workflows || []).find((x) => x.name === 'Commerce');
    if (!w) continue;
    const f = w.data.fruit === undefined ? '(none)' : String(w.data.fruit);
    fruitCounts[f] = (fruitCounts[f] || 0) + 1;
  }
  const header = {
    version: 1,
    generated_by: 'tools/price-parity/generate.js',
    seed: SEED,
    fixtures: path.relative(ROOT, LIVE),
    web_bundles: ['shop-profile~1c39816d.ec3a39e1.js', 'shop-profile~f04d431b.1f6f733e.js', 'shop-profile~21833f8f.83f1e1e0.js', 'commerce-order~31ecd969.c07a3a3f.js', 'app~50b71177.bdb8089a.js', 'app~b07b7304.0c8b3ad8.js', 'app~a97bfcba.4d7f22cd.js', 'ride~shop-profile~31ecd969.b76bf2b8.js'],
    counts,
    live_package_codes: fruitCounts,
  };
  // Delivery fleets are stored once, in header.fleets, and cases refer to them by key.
  const fleets = {};
  const fleetKey = (f) => {
    if (!f) return null;
    const j = JSON.stringify(f);
    let k = Object.keys(fleets).find((x) => JSON.stringify(fleets[x]) === j);
    if (!k) {
      k = 'fleet' + (Object.keys(fleets).length + 1);
      fleets[k] = f;
    }
    return k;
  };
  for (const c of cases) {
    if (c.kind === 'checkout' && c.input.delivery_workflows) {
      c.input.delivery_workflows = { express: fleetKey(c.input.delivery_workflows.express), fallback: fleetKey(c.input.delivery_workflows.fallback) };
    }
  }
  header.fleets = fleets;
  const lines = cases.map((c) => '  ' + JSON.stringify(c));
  const text = '{\n"header": ' + JSON.stringify(header) + ',\n"cases": [\n' + lines.join(',\n') + '\n]}\n';
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, text);
  console.log(`Wrote ${cases.length} cases (${(text.length / 1024).toFixed(0)} KB) to ${path.relative(ROOT, OUT)}`);
  console.log(counts);
}

main();
