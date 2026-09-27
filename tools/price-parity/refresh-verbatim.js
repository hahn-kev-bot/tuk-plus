#!/usr/bin/env node
// Copies the web app's price functions, as text, from the beautified web bundles
// into web-pricing.js (between the BEGIN VERBATIM and END VERBATIM markers).
//
// Usage:
//   node tools/price-parity/refresh-verbatim.js <dir with beautified bundles>          (write)
//   node tools/price-parity/refresh-verbatim.js <dir with beautified bundles> --check  (compare only)
//
// The bundle file names include a content hash. When Tuk deploys a new web app, the
// names change. Update BUNDLES below, run with --check, read the diff, then write.
'use strict';
const fs = require('fs');
const path = require('path');

const BUNDLES = {
  shopTab: 'shop-profile~1c39816d.ec3a39e1.js', // module f09f, ShopPromosTab (menu, basket, fees, submit)
  options: 'shop-profile~f04d431b.1f6f733e.js', // module 5811, ShopMenuOptions (option sheet)
  submitOrder: 'shop-profile~21833f8f.83f1e1e0.js', // module 9e1e4 ShopSubmitOrder; module 84f5 package codes
  orderPage: 'commerce-order~31ecd969.c07a3a3f.js', // module d252, CommerceOrder (order page totals)
  mixin: 'app~50b71177.bdb8089a.js', // module 56d7, global Vue mixin (item and option prices)
  geo: 'app~b07b7304.0c8b3ad8.js', // module 2c95, distance helpers
  date: 'app~a97bfcba.4d7f22cd.js', // module f17f, $date helpers
  gju: 'ride~shop-profile~31ecd969.b76bf2b8.js', // module 3d9a, geojson-utils (point in polygon)
};

// Which functions to copy. Order is kept in the output.
const SHOP_TAB_COMPUTED = [
  'extraDistance', 'isFreeGiftOrder', 'freeGiftOver', 'isPeakHour', 'isTestFleet', 'isTest',
  'businessNoteForDriver', 'businessSignName', 'isContactlessDelivery',
  'takeawayDiscount', 'dineinDiscount', 'fulfilmentDiscount', 'fulfilmentDiscountAmount',
  'fulfilmentDiscountPercent', 'business', 'commerce', 'locations', 'promos', 'freeGift', 'items',
  'languages', 'basketItems', 'basketItemCount', 'basketValue', 'vatableBasketValue', 'totalValue',
  'isDelivery', 'isTakeaway', 'isDinein', 'fulfilmentOptions', 'delayedDelivery', 'delayDuration',
  'thirdPartyDelivery', 'autoconfirm', 'reversedDelivery', 'extraTukpay', 'extraCash',
  'deliveryRemit', 'fleetRemit', 'customerRemit', 'shopRemit', 'shopDeliveryFee',
  'tukExpressFreeDelivery', 'freeDelivery', 'dynamicFreeDeliveryOver', 'fixedFreeDeliveryOver',
  'freeDeliveryOver', 'freeDeliveryTo', 'isLegacyFreeDelivery', 'isFreeDelivery',
  'freeDeliveryRemainder', 'package', 'billingAmount', 'remitAmount', 'actualRemitAmount',
  'deliverySubsidyPercent', 'deliverySubsidy', 'actualDeliverySubsidy', 'actualDeliveryFee',
  'specialRemit', 'applySpecialRemit', 'specialDiscount', 'applySpecialDiscount',
  'freeDeliveryPolygon', 'maxDeliveryDistance', 'maxDistKM', 'deliveryFeePerKM', 'minDeliveryFee',
  'isSelfDelivery', 'peakHourMaxDeliveryDistance', 'deliveryPricingArray', 'surgeFee',
  'fallbackFleet', 'isLalamove', 'maxRemit', 'minOrder', 'closedUntil', 'isPaused', 'isBlocked',
  'isOpen', 'vat', 'vatPercent', 'billingPercent', 'remitPercent', 'requiresLargeBox',
];
const SHOP_TAB_METHODS = [
  'hasOptions', 'invalidPrice', 'isMembersOnly', 'validTimeTag', 'handleVisualDiscount',
  'closestPickupLocation', 'insideFreeDeliveryPolygon', 'validFareDistance', 'setDeliveryFare',
  'addRemitToFare', 'updateDeliveryFee', 'selfDeliveryPrice', 'getFare', 'getDefaultDeliveryPrice',
  'getPriceFromPricingArray', 'tripDistance', 'preSubmit', 'submit', 'initBasket',
  'invalidBasketForShop', 'newBasket', 'saveBasket', 'sortBasket', 'submitOptions', 'addToBasket',
  'finishAddition', 'removeZeros', 'advancedRemoval', 'removeFromBasket', 'newRemoveFromBasket',
  'finishRemoval', 'itemQuantity', 'minimise', 'triggerOptions',
];
const OPTIONS_COMPUTED = [
  'isFreeGift', 'propagateDiscounts', 'hasOptionsMenus', 'filteredOptionsMenus', 'requiredOptions',
  'multipleOptions', 'totalPrice', 'requiredInvalid',
];
const OPTIONS_METHODS = [
  'close', 'updateOptionMenuPrices', 'triggerSelecting', 'invalidOptionsMenu',
  'invalidOptionsMenuStrict', 'checkRequirements', 'optionSelected', 'trueCondition',
  'clearConditionalOptions', 'remove', 'removeSingleOption', 'allowMultiple', 'select',
  'removeQuantity', 'addQuantity', 'submit',
];
const SUBMIT_ORDER_COMPUTED = ['isLateDeliveryNotAllowCash', 'isEarlyDeliveryNotAllowCash', 'hasCash', 'isDelivery'];
const ORDER_PAGE_COMPUTED = [
  't', 'orderValue', 'vat', 'fulfilmentDiscount', 'specialDiscount', 'tukDiscount', 'clientCash',
  'subsidisedDeliveryFee', 'deliveryFee',
];
const MIXIN_METHODS = ['deepCopy', 'weightPrice', 'discountedPrice', 'discountedTotalPrice', 'optionPrice', 'actualQuantity'];

// ---------------------------------------------------------------- extractor

function makeReader(dir) {
  const cache = {};
  const lines = (file) => {
    if (!cache[file]) cache[file] = fs.readFileSync(path.join(dir, file), 'utf8').split('\n');
    return cache[file];
  };
  // Returns the index just after the matching close brace. Knows strings and simple regex literals.
  function matchBrace(text, open) {
    let depth = 0;
    let i = open;
    let prev = '';
    while (i < text.length) {
      const c = text[i];
      if (c === '"' || c === "'" || c === '`') {
        i++;
        while (i < text.length && text[i] !== c) {
          if (text[i] === '\\') i++;
          i++;
        }
        i++;
        prev = 'x';
        continue;
      }
      if (c === '/' && /[=(,:!&|?{};]/.test(prev)) {
        i++;
        while (i < text.length && text[i] !== '/') {
          if (text[i] === '\\') i++;
          i++;
        }
        i++;
        while (/[gimsuy]/.test(text[i])) i++;
        prev = 'x';
        continue;
      }
      if (c === '{') depth++;
      if (c === '}') {
        depth--;
        if (depth === 0) return i + 1;
      }
      if (!/\s/.test(c)) prev = c;
      i++;
    }
    throw new Error('no closing brace');
  }
  function cut(file, n, indent) {
    const ls = lines(file);
    const rest = ls.slice(n).join('\n');
    const start = rest.indexOf('function');
    const open = rest.indexOf('{', start);
    const end = matchBrace(rest, open);
    return rest
      .slice(start, end)
      .split('\n')
      .map((l, k) => (k === 0 ? l : l.slice(Math.min(indent, l.match(/^\s*/)[0].length))))
      .join('\n');
  }
  // A module "id": function(...) {...}. Returns {line, text}.
  function moduleRange(file, id) {
    const ls = lines(file);
    const re = new RegExp('^        "?' + id + '"?: function');
    const n = ls.findIndex((l) => re.test(l));
    if (n < 0) throw new Error(`module ${id} not found in ${file}`);
    const text = cut(file, n, 8);
    return { line: n + 1, text, end: n + text.split('\n').length };
  }
  // A "name: function(...) {...}" inside a module and a "computed:" / "methods:" / "data:" block.
  function member(file, moduleId, block, name) {
    const ls = lines(file);
    const mod = moduleRange(file, moduleId);
    let from = mod.line - 1;
    if (block) {
      const b = ls.findIndex((l, k) => k >= from && k < mod.end && new RegExp('^\\s+' + block + ': \\{').test(l));
      if (b < 0) throw new Error(`${block} not found in ${moduleId}`);
      from = b;
    }
    const re = new RegExp('^\\s*"?' + name + '"?: function\\s*\\(');
    for (let n = from; n < mod.end; n++) {
      if (re.test(ls[n])) {
        const indent = ls[n].match(/^\s*/)[0].length;
        return { line: n + 1, text: cut(file, n, indent) };
      }
    }
    throw new Error(`${name} not found in ${file} ${moduleId} ${block}`);
  }
  return { moduleRange, member };
}

// Indents all lines but the first.
function ind(text, n) {
  const pad = ' '.repeat(n);
  return text.split('\n').map((l, k) => (k === 0 ? l : pad + l)).join('\n');
}

function build(dir) {
  const r = makeReader(dir);
  const out = [];
  const ref = (file, line, what) => `// ${file}:${line} ${what}`;

  const members = (file, mod, block, names, label) =>
    names
      .map((n) => {
        const m = r.member(file, mod, block, n);
        return `      ${ref(file, m.line, `${label} ${block} ${n}`)}\n      ${n}: ${ind(m.text, 6)}`;
      })
      .join(',\n');

  const whole = (file, id, what) => {
    const m = r.moduleRange(file, id);
    return `  ${ref(file, m.line, what)}\n  module_${id}: ${ind(m.text, 2)}`;
  };

  out.push('var VERBATIM = {');
  out.push(whole(BUNDLES.submitOrder, '84f5', 'module 84f5: package codes (workflow.data.fruit)') + ',');
  out.push(whole(BUNDLES.geo, '2c95', 'module 2c95: distance helpers') + ',');
  out.push(whole(BUNDLES.date, 'f17f', 'module f17f: $date helpers') + ',');
  out.push(whole(BUNDLES.gju, '3d9a', 'module 3d9a: geojson-utils') + ',');

  // Global mixin (app~50b71177 module 56d7, r["default"].mixin({methods: {...}})).
  out.push('  mixin: {');
  out.push(
    MIXIN_METHODS.map((n) => {
      const m = r.member(BUNDLES.mixin, '56d7', 'methods', n);
      return `    ${ref(BUNDLES.mixin, m.line, `mixin ${n}`)}\n    ${n}: ${ind(m.text, 4)}`;
    }).join(',\n'),
  );
  out.push('  },');

  // Components. Each factory gets the webpack imports that the functions use.
  const tabData = r.member(BUNDLES.shopTab, 'f09f', null, 'data');
  out.push('  // ShopPromosTab. Imports as in module f09f: s = require, o = event bus (0c12),');
  out.push('  // a = toConsumableArray (75fc), w = geo (2c95), x = package codes (84f5), T = shortid (8dee), S = object spread.');
  out.push('  shopPromosTab: function (s, o, a, w, x, T, S, C, setTimeout, clearTimeout) {');
  out.push('    return {');
  out.push(`      ${ref(BUNDLES.shopTab, tabData.line, 'ShopPromosTab data')}`);
  out.push(`      data: ${ind(tabData.text, 6)},`);
  out.push('      computed: {');
  out.push(members(BUNDLES.shopTab, 'f09f', 'computed', SHOP_TAB_COMPUTED, 'ShopPromosTab'));
  out.push('      },');
  out.push('      methods: {');
  out.push(members(BUNDLES.shopTab, 'f09f', 'methods', SHOP_TAB_METHODS, 'ShopPromosTab'));
  out.push('      }');
  out.push('    };');
  out.push('  },');

  const optData = r.member(BUNDLES.options, '5811', null, 'data');
  const optMounted = r.member(BUNDLES.options, '5811', null, 'mounted');
  out.push('  // ShopMenuOptions. Imports as in module 5811: n = event bus (0c12), o = shortid (8dee).');
  out.push('  shopMenuOptions: function (n, o, setTimeout, clearTimeout) {');
  out.push('    return {');
  out.push(`      ${ref(BUNDLES.options, optData.line, 'ShopMenuOptions data')}`);
  out.push(`      data: ${ind(optData.text, 6)},`);
  out.push(`      ${ref(BUNDLES.options, optMounted.line, 'ShopMenuOptions mounted')}`);
  out.push(`      mounted: ${ind(optMounted.text, 6)},`);
  out.push('      computed: {');
  out.push(members(BUNDLES.options, '5811', 'computed', OPTIONS_COMPUTED, 'ShopMenuOptions'));
  out.push('      },');
  out.push('      methods: {');
  out.push(members(BUNDLES.options, '5811', 'methods', OPTIONS_METHODS, 'ShopMenuOptions'));
  out.push('      }');
  out.push('    };');
  out.push('  },');

  out.push('  // ShopSubmitOrder (module 9e1e4): cash rules.');
  out.push('  shopSubmitOrder: function () {');
  out.push('    return {');
  out.push('      computed: {');
  out.push(members(BUNDLES.submitOrder, '9e1e4', 'computed', SUBMIT_ORDER_COMPUTED, 'ShopSubmitOrder'));
  out.push('      }');
  out.push('    };');
  out.push('  },');

  out.push('  // CommerceOrder (module d252): total on the order page.');
  out.push('  commerceOrder: function () {');
  out.push('    return {');
  out.push('      computed: {');
  out.push(members(BUNDLES.orderPage, 'd252', 'computed', ORDER_PAGE_COMPUTED, 'CommerceOrder'));
  out.push('      }');
  out.push('    };');
  out.push('  }');
  out.push('};');
  return out.join('\n');
}

function main() {
  const dir = process.argv[2];
  const check = process.argv.includes('--check');
  if (!dir) {
    console.error('Usage: node tools/price-parity/refresh-verbatim.js <beautified bundle dir> [--check]');
    process.exit(2);
  }
  const target = path.join(__dirname, 'web-pricing.js');
  const src = fs.readFileSync(target, 'utf8');
  const BEGIN = '// BEGIN VERBATIM\n';
  const END = '// END VERBATIM\n';
  const a = src.indexOf(BEGIN);
  const b = src.indexOf(END);
  if (a < 0 || b < 0) throw new Error('markers not found in web-pricing.js');
  const fresh = build(dir) + '\n';
  const old = src.slice(a + BEGIN.length, b);
  if (check) {
    if (old === fresh) {
      console.log('web-pricing.js is the same as the bundles.');
      return;
    }
    const ol = old.split('\n');
    const nl = fresh.split('\n');
    let shown = 0;
    for (let i = 0; i < Math.max(ol.length, nl.length) && shown < 40; i++) {
      if (ol[i] !== nl[i]) {
        console.log(`line ${i + 1}\n  old: ${ol[i]}\n  new: ${nl[i]}`);
        shown++;
      }
    }
    process.exitCode = 1;
    return;
  }
  fs.writeFileSync(target, src.slice(0, a + BEGIN.length) + fresh + src.slice(b));
  console.log('Wrote the verbatim section of web-pricing.js.');
}

if (require.main === module) main();
