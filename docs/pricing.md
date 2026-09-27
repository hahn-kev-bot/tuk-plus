# Tuk pricing (web app price code, traced)

Status: traced from the web bundles of 2026-09-27 (backend `3.7058f`).
The parity tool `tools/price-parity/` runs this code. See §18.

## 1. Overview

The Tuk server does not calculate prices. The web app calculates all amounts in the
browser. Then it sends them in `POST transactions` (api-reference §8). The shop, the
driver and the Tuk back office use these amounts. Thus the native app must calculate
the same numbers as the web app, to the baht.

This document gives each formula, the source in the web bundle, and the JavaScript
behaviour that a Kotlin port must copy. Each formula is written as pseudocode with
JavaScript semantics, unless the text says otherwise.

The main code is one Vue component, `ShopPromosTab` (the shop menu, the basket, the fees
and "Place order"). It uses a global mixin for item and option prices, a module for
package codes, and the option sheet component `ShopMenuOptions`.

Sources (file names of the web bundles; line numbers are for the beautified copy):

| Code | Bundle | Module |
|---|---|---|
| Item and option price (mixin) | `app~50b71177.bdb8089a.js` | `56d7`, lines 1151–1188 |
| Menu, basket, fees, order body (`ShopPromosTab`) | `shop-profile~1c39816d.ec3a39e1.js` | `f09f`, lines 1855–3639 |
| Option sheet (`ShopMenuOptions`) | `shop-profile~f04d431b.1f6f733e.js` | `5811`, lines 1715–2086 |
| Package codes | `shop-profile~21833f8f.83f1e1e0.js` (same copy in `e-commerce~31ecd969`, `eat~31ecd969`) | `84f5`, line 121 |
| Cash rules (`ShopSubmitOrder`) | `shop-profile~21833f8f.83f1e1e0.js` | `9e1e4`, lines 894–930 |
| Order page total (`CommerceOrder`) | `commerce-order~31ecd969.c07a3a3f.js` | `d252`, lines 2086–2151 |
| Straight-line distance | `app~b07b7304.0c8b3ad8.js` | `2c95` |
| Point in polygon (geojson-utils) | `ride~shop-profile~31ecd969.b76bf2b8.js` | `3d9a` |
| Driving route | `app~c714bc7b.ec8f9abe.js` | Vuex action `drivingRoute`, line 1918 |
| Fleet paused alert | `shop-profile~6222e40c.e40abe54.js` | `checkDeliveryWorkflows`, line 765 |

In this document, `bv` is the basket value (`order_value`), `w` is `workflow.data` of the
Commerce workflow, `fare` is the delivery fare object and `pkg` is `w.fruit`.

## 2. Item price

Source: mixin `weightPrice` (line 1154) and `discountedPrice` (line 1168).

```
weightPrice(item):
  if !item.by_weight || !item.actual_weight: return item.price      // the raw value, often a string
  wt = parseInt(item.actual_weight)
  "per gram":      return wt * item.price
  "per 100 grams": return wt * item.price / 100
  "per kg":        return wt * item.price / 1000
  other:           return item.price

discountedPrice(item):
  p = weightPrice(item)
  if !item.discount: return parseInt(p)                             // no rounding, truncation
  x = parseInt(p) * (1 - item.discount / 100)
  if item.discount_type == "number": x = parseInt(p) - item.discount
  return x < 0 ? 0 : Math.round(x)
```

Rules:

- `discount_type` is `"percent"` (or empty) or `"number"`. Any other value is percent.
- The `"number"` discount is in baht.
- A discount over 100 % gives 0. A number discount over the price gives 0.
- `by_weight` has an effect only after the shop sets `actual_weight` on the order.
  In the basket, the item has only `estimated_weight`. Then the price is `item.price`.

JavaScript behaviour to copy:

- `parseInt` truncates: `parseInt("12.5") = 12`, `parseInt("12.9") = 12`.
- `parseInt` reads leading digits: `parseInt("  45") = 45`, `parseInt("1e3") = 1`,
  `parseInt("-30") = -30`, `parseInt("") = NaN`, `parseInt("abc") = NaN`, `parseInt(null) = NaN`.
- `NaN` spreads to all sums. `JSON.stringify(NaN)` gives `null`. The web app then sends
  `null` in the order.
- `!item.discount` is true for `0`, `""`, `null` and a missing value. It is false for the
  string `"0"`. The result is the same for `"0"`, because `x = parseInt(p) * 1`.
- A string discount works: `"10" / 100 = 0.1`, `parseInt(p) - "10"` is a number.
- `discount / 100` and `1 - …` are IEEE double operations. The rounding can go down
  where exact maths goes up. Examples: `15 * (1 - 90/100) = 1.4999999999999996`, so the
  price is 1, not 2. `25 * (1 - 34/100) = 16.499999999999996`, so 16. Use the same
  double operations in the same order. Do not use `BigDecimal` or integer maths.
- `Math.round` rounds .5 up (to +∞). It is `floor(x + 0.5)`. Kotlin `Math.round(x)` and
  `x.roundToLong()` do the same. `kotlin.math.round(x)` rounds half to even. Do not use it.
  The item code never rounds a negative number (the result is 0 below 0).
- In the weight formula, `wt * item.price` changes the string to a number. Then
  `parseInt(226.95) = 226` truncates the result.

### 2.1 Visual discount

Source: `ShopPromosTab.handleVisualDiscount` (line 2758). It runs when the user opens the
menu tab (`reInit`, from `ShopProfile.setTab`).

```
if w.visual_discount (e.g. 0.1):
  for each menu item s:
    s.discount = Math.round(parseInt(s.discount || 0) + 100 * w.visual_discount)
    s.price    = Math.round(parseFloat(s.price) / (1 - w.visual_discount))
```

- It changes the menu items in place, before the user adds them. The basket item keeps
  the new `price` and `discount`.
- The shop shows a higher price with a bigger discount. The customer pays about the old
  price. Example: price 90, 10 % → price 100, discount 10 → pays 90.
- Web bug: each new visit of the menu tab applies it again (100 → 111, discount 20).
  Apply it once.
- With `discount_type: "number"`, the code adds the percent value to the baht discount.
- One live shop has it (`visual_discount: 0.1`).

## 3. Option price

Source: mixin `optionPrice` (line 1181) and `discountedTotalPrice` (line 1174).

```
optionPrice(option) = parseInt(option.discounted_price || option.price) * (option.quantity || 1)
```

- `option.quantity` is the quantity of this option (see §15). `0`, `null` and a missing
  value count as 1.
- `discounted_price || price`: a `discounted_price` of 0 (or `NaN`) falls back to `price`.
- `parseInt` truncates `discounted_price`: `parseInt(22.5) = 22`, `parseInt("21.25") = 21`.
- Option prices can be negative (live: `"-20"`). The sheet shows "−฿20".
- An option with `price: ""` or `null` gives `NaN`. The whole line becomes `NaN`.

### 3.1 Item discount on options (`propagate_discounts`)

Source: `ShopMenuOptions.updateOptionMenuPrices` (line 1850), called from `mounted` (line 1828)
when `w.menu_options.propagate_discounts` is true. No live shop has it.

```
if !item.discount_type || item.discount_type == "percent":
  f = 1 - parseInt(item.discount) / 100
  for each group shown when the sheet opens, for each option:
    option.discounted_price = parseInt(option.price) * f          // a float, not rounded
```

- It runs once, when the sheet opens. A group with a `condition` is not shown yet (nothing
  is selected), so its options do not get the discount.
- With no item discount, `parseInt(undefined) = NaN`, so `discounted_price = NaN`.
  Then `optionPrice` uses `price`.
- With a 100 % discount, `discounted_price = 0`. Then `optionPrice` uses the full `price`.
- A `"number"` item discount does not change options.
- Without `propagate_discounts`, options have no discount. This is the live case.

## 4. Line total and basket value

Source: mixin `discountedTotalPrice` (line 1174), `ShopPromosTab.basketValue` (line 2226),
`basketItemCount` (line 2219).

```
lineUnit(item) = discountedPrice(item) + Σ optionPrice(o.option) for o in item.options if o.option
bv (order_value) = Σ lineUnit(line.item) * line.quantity
itemCount = Σ line.quantity
```

- `item.options` on a basket item is a list of `{menu, option}`. On a menu item it is a
  list of group ids (strings). The `if o.option` skips strings.
- There is no rounding after the item price. Option sums are integers (parseInt).
  `bv` is an integer, unless an input is `NaN`.

## 5. VAT

Source: `vatableBasketValue` (line 2234), `vat` (line 2529), `vatPercent` (line 2532).

```
vatable = Σ lineUnit(item) * quantity  for lines where NOT (item has own key "vatable" and !item.vatable)
vat = w.vat ? Math.round(w.vat * vatable) : 0          // w.vat is a fraction, e.g. 0.07
```

- VAT is added on top of the basket value (§11). It is not included in the prices.
- An item with `vatable: false` is not in the VAT base. A missing `vatable` counts.
- Float: `0.07 * 150 = 10.500000000000002` → 11. `0.07 * 50 = 3.5000000000000004` → 4.
- 11 live shops have `vat: 0.07`.
- The order sends `order.vat = vat` and `settings.vat_percent = w.vat || 0`.

## 6. Take-away and dine-in discount

Source: `fulfilmentDiscountPercent` (line 2059), `fulfilmentDiscountAmount` (line 2056).

```
pct = 0
if type == "take-away" && w.takeaway_discount: pct = w.takeaway_discount
if type == "dine-in"   && w.dinein_discount:   pct = w.dinein_discount
pct = pct / 100
fulfilment_discount = pct ? Math.round(pct * bv) : 0
```

- The values are percent numbers (live: 5, 10, 15, 20, 50). `null` and 0 mean none.
- The discount does not apply to delivery.
- The base is `bv` (without VAT).
- `takeaway_options` has only a `blurb` text. `order_options` has only `autoconfirm`.
  Neither changes a price.

## 7. Delivery distance

Source: `getDrivingRoute` (line 2783), Vuex `drivingRoute` (app~c714bc7b line 1918),
`tripDistance` (line 3196), `validFareDistance` (line 3068), `maxDeliveryDistance` (line 2430).

Distance in metres:

1. Route from GraphHopper: `Math.round(paths[0].distance)`, `source = "graphhopper"`.
2. If that fails: `GET directions` → `distance` (not rounded), `source = "google"`.
3. If both fail: `tripDistance = Math.round(1.25 * haversine) + extra_distance`,
   `source = "estimate"`. Haversine: earth radius 6371 km, `Math.round(1000 * km)`.
4. With a route, `extra_distance` is added to the route distance (1 and 2).

`extra_distance` is `user.data.delivery_options.extra_distance` (a per-user setting, usually
absent).

Pickup point: `business.lat/lon`. If `w.locations` is set, the closest location
(straight line) is the pickup point.

Limits (the web app rejects the address and clears the order type):

```
reject if metres <= 50                                   ("same as the shop")
reject if metres > 1000 * maxDeliveryDistance            ("too far")

maxDeliveryDistance = min of the truthy values of
  [ w.max_distance, peakHourMax, pricing_array.length ]  (all in km); 14 if none
peakHourMax = (local hour is 17 or 18) ? fleet.peak_hour_max_distance : null
```

- The fleet for `peak_hour_max_distance` and `pricing_array` is the fallback fleet if
  there is one, else the express fleet (§8.1).
- The hour is the device local hour (`new Date().getHours()`), 17:00–18:59.
- The alert text shows `Math.round(0.8 * maxDeliveryDistance)` km. This is display only.
- Live: `pricing_array` has 27 or 28 steps and `peak_hour_max_distance` is 27. So the peak
  limit changes only the 28-step fleet (28 km → 27 km).
- `inactive_max_distance` does not limit the distance. The web app only shows an alert when
  the express fleet is `inactive` ("Limited" if `0.75 * inactive_max_distance >= 1000`,
  else "Paused").

## 8. Delivery fare

Source: `getFare` (line 3121), `getPriceFromPricingArray` (line 3174), `getDefaultDeliveryPrice`
(line 3170), `selfDeliveryPrice` (line 3116), `addRemitToFare` (line 3110), `updateDeliveryFee`
(line 3113).

```
if pricingArray:                     base = getPriceFromPricingArray(metres)
else if w.express == "self" && w.delivery_fee_per_km: base = selfDeliveryPrice(metres)
else:                                base = getDefaultDeliveryPrice(metres)

getPriceFromPricingArray(m): km = Math.round(m / 1000); if km: km -= 1; return pricingArray[km]
getDefaultDeliveryPrice(m):  80; < 10000 → 60; < 5000 → 40; < 3000 → 30
selfDeliveryPrice(m):        s = Math.round(m / 1000 * w.delivery_fee_per_km); max(s, w.min_delivery_fee)
                             (the code is `s < min ? min : s`)

extraCash  = w.delivery_options.extra_cash  || 0
extraTukpay = w.delivery_options.extra_tukpay || 0
fare = { version: "v1",
         price:  base + surge + extraTukpay + extraCash,
         discount: 0,
         cash:   base + extraCash + surge,
         tukpay: extraTukpay,
         bonus:  extraCash + extraTukpay + surge,
         distance: metres,
         source: "graphhopper" | "google" | "estimate" }
then (addRemitToFare):
fare.remit  = deliveryRemit
fare.client = fare.cash + deliveryRemit
```

Index table: 0–1499 m → step 0; 1500–2499 m → step 1; 2500–3499 m → step 2; and so on.
`Math.round(m/1000)` is 0 for 0–499 m and 1 for 500–1499 m. Both give step 0.

`fare.client` is the delivery price for the customer before the subsidy.
`fare.cash` is the part for the driver.

### 8.1 Fleet choice

`deliveryWorkflows` is the response of `GET workflows?commerce_delivery=…` (`{express, fallback}`).

| Value | Rule |
|---|---|
| `pricing_array` | `fallback.data.pricing_array` if there is a fallback, else `express.data.pricing_array` |
| `surge` | `fallback.data.surge || 0` if there is a fallback, else `express.data.surge || 0` |
| `peak_hour_max_distance` | fallback first, else express |
| `remit_amount` (fleet remit) | **express first**: `express.data.remit_amount` if truthy, else `fallback.data.remit_amount || 0` |

The fallback fleet wins for price, surge and peak limit. The express fleet wins for the
remit. This is not symmetric. Live example: express "Chiang Mai South Express Delivery"
(27 steps, remit 11) with fallback "Chiang Mai Express Delivery" (28 steps, remit 10):
price from the 28-step array, remit 11.

If `deliveryWorkflows` is null (not loaded or no fleet): no pricing array, surge 0,
fleet remit `null` (the sum below gives 0), max distance 14 km unless `w.max_distance`.

### 8.2 Remit on the fare (`deliveryRemit`)

Source: `deliveryRemit` (line 2314), `fleetRemit` (line 2318), `customerRemit` (line 2321),
`shopRemit` (line 2325).

```
deliveryRemit = (fleetRemit + customerRemit + shopRemit) || 0
customerRemit = user.data.delivery_options.remit_amount || 0
shopRemit     = w.delivery_options.remit_amount || 0
```

The shop remit is common in live data (values 0–12). It is not "usually 0".

### 8.3 Other fare paths (not supported)

- Lalamove: if `w.express` or `w.fallback_fleet` starts with `"Lalamove"`. The fare comes
  from a Lalamove quote. No live shop uses it. Use the web checkout fallback.
- Promo code: `deliveryPromoCode` goes to `delivery.promo_code`. It does not change the
  fare (`handleFreeDeliveryPromo` is not called).
- Driver tip: the code exists (`e.driverTip`), but the UI has no button for it.
  If it were set, `fare.tip += tip` gives `NaN` (`fare.tip` is undefined).
- Agent mode (`agentModeData`, TUK Express for shop staff): not in scope.

## 9. Package codes (`workflow.data.fruit`)

Source: module `84f5` (shop-profile~21833f8f line 121).

```
hasFreeDelivery(c) = !!c && (c starts with "p_" | "f_" | "thai" | "r_")

deliverySubsidy(c):
  if !c: 0
  "r_A_B": parts = c.split("r_")[1].split("_"); parts.length != 2 ? 0 : parseInt(B) / 100
  "p_A…":  parseInt(A) / 100
  "f_A…":  parseInt(A) / 100
  switch c:
    thai0, thai0_10, thai0_15, thai0_20, thai0_25 → 0
    thai10, thai10_5, thai1010, thai10_10, thai10_0 → 0.1
    thai15, thai15_5, thai15_10 → 0.15
    thai20, thai20_0, thai20_5 → 0.2
    thai25, thai25_0 → 0.25
    other → 0

billing(c):
  if !c: 0
  "p_A_B" (exactly 2 parts): parseInt(B) / 100
  "f_A_B" (exactly 2 parts) or "thaiA_B" (exactly 2 parts): parseInt(B) / 100
  other: 0

remit(c):
  "r_A…": parseInt(A) / 100
  other: 0
```

JavaScript behaviour:

- The prefix test is case sensitive. `"R_20_10"` has no rules (all 0).
- `"r_x_y"` gives `parseInt("y") / 100 = NaN` for the subsidy. Then `delivery_subsidy`
  is `null` in the order. `"r_"` gives remit `NaN`, and `actualRemitAmount` treats `NaN` as 0.
- `"r_20"` and `"r_20_10_5"` have remit 20 % but subsidy 0.
- `"p_10"` and `"f_10"` have a subsidy but billing 0.
- `"thai"` alone: `hasFreeDelivery` is true, but subsidy 0. The eat list shows the "free
  delivery" tag, but the checkout has no subsidy.
- `thai1010` is in the switch: subsidy 0.1. `"thai1010".split("thai")[1] = "1010"` has
  one part, so billing 0.

What each family does (at checkout, for delivery):

| Family | Tuk commission (`remit`) | Delivery subsidy | Billing (`data.billing`) | Extra |
|---|---|---|---|---|
| `r_A_B` | `round(bv × A%)` (max `w.max_remit`), minus the subsidy used | `round(bv × B%)` | none | none |
| `p_A_B` | 0 | `round(bv × A%)` | `bv × B%` (text) | special remit (below) |
| `f_A_B` | 0 | `round(bv × A%)` | `bv × B%` (text) | special discount (below) |
| `thaiX[_Y]` | 0 | from the fixed list | `bv × Y%` (text) | none |
| `apple`, `elderberry`, `fig`, `durian`, other, none | 0 | 0 | none | none |

For `apple`, `elderberry`, `fig`, `durian`: the web code has no rules. The customer pays
the full fare (`fare.client`). The free delivery rules of §10 still work
(`free_delivery_over`, polygon, `free_delivery`).

## 10. Subsidy, free delivery and the fee

Source: lines 2331–2425.

```
pct = deliverySubsidy(pkg)                            // deliverySubsidyPercent
deliverySubsidy = Math.round(bv * pct)

dynamicFreeDeliveryOver = fare && pct ? Math.ceil(fare.client / pct) : null
fixedFreeDeliveryOver   = fare && w.free_delivery_over
                            ? (w.free_delivery_distance && fare.distance > w.free_delivery_distance ? null : w.free_delivery_over)
                            : null
freeDeliveryOver (e = fixed, t = dynamic):
  e && t ? min(e, t) : (t || (pct ? null : e))

freeDelivery = whoPaysDelivery == "shop" || !!pct || !!freeDeliveryOver || w.free_delivery
insidePolygon = w.free_delivery_polygon && address && pointInPolygon([lon, lat], {type: "Polygon", coordinates: w.free_delivery_polygon})

isLegacyFreeDelivery = insidePolygon || (!!freeDeliveryOver && bv >= freeDeliveryOver)
isFreeDelivery = !!freeDelivery && (whoPaysDelivery == "shop" || bv >= freeDeliveryOver
                                    || actualDeliveryFee === 0 || insidePolygon)

actualDeliveryFee (shown in the basket, used in the total):
  !fare ? null
  : isLegacyFreeDelivery ? 0
  : deliverySubsidy ? (deliverySubsidy > fare.client ? 0 : fare.client - deliverySubsidy)
  : fare.client

deliveryFee (data field; shown in the "Fees" sheet; set by updateDeliveryFee):
  isFreeDelivery ? 0 : (deliverySubsidy > fare.client ? 0 : fare.client - deliverySubsidy)

actualDeliverySubsidy (sent as order.delivery_subsidy):
  !fare ? 0 : (isFreeDelivery || fare.client < deliverySubsidy) ? fare.client : deliverySubsidy

freeDeliveryRemainder = freeDeliveryOver ? freeDeliveryOver - bv : null   // "Only ฿X more!"
```

Notes:

- For an `r_A_B` shop, delivery is free when the subsidy covers the fare. The threshold is
  `Math.ceil(fare.client / pct)`. It uses a float division: `70 / 0.1 = 700` but other
  values can give `x.0000000001` and then one baht more. Use the same double division.
- `bv >= freeDeliveryOver` with `freeDeliveryOver = null` is `bv >= 0` in JavaScript.
  This is true. So a shop with only `free_delivery: true` (no subsidy, no threshold) has
  `isFreeDelivery = true`.
- Web inconsistency for that case: the basket shows "Free" (template uses `isFreeDelivery`)
  and `deliveryFee` is 0, but `actualDeliveryFee = fare.client` is added to `totalValue`.
  The order has `is_free_delivery: true` and `delivery_subsidy = fare.client`, so the order
  page shows no fee. No live shop has `free_delivery: true` now (one has `false`).
  The parity cases with the tag `free-delivery` show it.
- `free_delivery_over: -1` means free delivery at any basket value.
- `free_delivery_to` is only a text for the menu ("Free delivery to …").
- The polygon test is geojson-utils `pointInPolygon` (bounding box, then ray casting).
  `w.free_delivery_polygon` is a list of rings: `[[[lon, lat], …]]`. One live shop has it
  (`15efa4ee`, with `free_delivery_over: 249` and `free_delivery_distance: 8000`).
- `whoPaysDelivery` is only set in agent mode. It is null for customers.

## 11. Commission, special amounts and billing

Source: `remitAmount` (line 2377), `actualRemitAmount` (line 2383), `specialRemit` (line 2403),
`specialDiscount` (line 2414), `billingAmount` (line 2371), `maxRemit` (line 2468).

```
remitAmount = min(Math.round(bv * remit(pkg)), w.max_remit)      // cap only if w.max_remit is truthy
actualRemitAmount = !isDelivery || !remitAmount ? 0 : max(0, remitAmount - actualDeliverySubsidy)

specialRemit    (p_ codes, not Lalamove) = fare && isFreeDelivery ? clamp(deliverySubsidy - fare.client, 0, 30) : 0
specialDiscount (f_ codes)               = fare && isFreeDelivery ? deliverySubsidy - fare.client : 0

order.remit = actualRemitAmount + specialDiscount + specialRemit

billingAmount = (bv * billing(pkg)).toFixed(2)                   // a string, e.g. "38.00"
```

- `order.remit` is Tuk's commission after it pays the delivery subsidy. For `r_A_B` it is
  not `bv × A%`. It is `bv × A%` minus the subsidy that was used.
- For `p_`: when delivery is free, the unused subsidy (up to ฿30) is added to `order.remit`.
- For `f_`: when delivery is free, all of the unused subsidy is added to `order.remit`.
  The name is `specialDiscount`, but the order sends `special_discount: 0` always.
  `specialDiscount` can be negative when the polygon makes delivery free with a small subsidy.
- `max_remit` (one live shop has `null`) caps `remitAmount` before the subsidy.
- `billingAmount` goes to `data.billing` (top level of `data`) only for delivery and only
  when `parseFloat(billingAmount) > 0`. `toFixed(2)` rounds the double. Copy
  `Number.prototype.toFixed` (it can differ from `String.format("%.2f")` for values
  like `1.005`).

## 12. Total

Source: `totalValue` (line 2242).

```
total = bv + vat - fulfilment_discount + (isDelivery ? actualDeliveryFee : 0)
```

- If there is no fare yet, `actualDeliveryFee` is null and `x + null = x`.
- There is no service or platform fee. The "Fees" sheet shows "Platform Fee ฿0" and
  `deliveryFee`.

The order page (`CommerceOrder.orderValue`, line 2117) calculates the total again from the
order:

```
orderPageTotal = order.order_value + (order.vat || 0)
                 - ((order.special_discount || 0) + (order.fulfilment_discount || 0) + (order.tuk_discount || 0))
                 + (data.type == "delivery" && !delivery.is_free_delivery ? (fare.client || fare.cash) - order.delivery_subsidy : 0)
```

It is the same as the checkout total, except in the `free_delivery` case of §10.

## 13. Checks before the order

Source: `preSubmit` (line 3233), `ShopMenuOptions.submit` (line 2028), `ShopSubmitOrder`.

In this order:

1. The basket must be for this shop (§16).
2. The shop must be open (`paused`, `blocked`, `closed_until`, hours).
3. `min_order`: if `w.min_order && bv < w.min_order && isDelivery` → stop ("minimum order").
   Take-away and dine-in have no minimum.
4. Free gift: if a basket item has `free_gift` and `bv < parseInt(freeGift.free_gift)` → stop.
   `freeGift` is the first menu item with `free_gift`, not out of stock, not hidden.
5. An order type must be set. For delivery, an address and a fare must exist.
6. Login with a phone number.

In the "Place order" sheet (`ShopSubmitOrder`):

- Cash for delivery is not offered when `parseInt(HHMM) > 2220` or `< 900` (device time).
  So cash is not offered from 22:21 to 08:59. At 22:20 and 09:00 cash is offered.
  Take-away and dine-in always offer cash (if `payment_options` has `cash` or is missing).
- If no method is selected, the method is `"cash"` (for Thai shops).
- `change_for`: must be more than `totalValue`.

Delivery type in the order (`submit`, line 3288):

```
delayedDelivery = fulfilment_time set || w.delivery_options.type == "delayed"
if delayedDelivery:            delivery.type = "delayed"
if w.delivery_options.delay_duration (truthy): delivery.delay_duration = it
if !delayedDelivery && bv >= 1000: delivery.type = "delayed"; delivery.delay_duration = 26
if w.delivery_options.type == "third-party": delivery.type = "third-party"
if w.delivery_options.reversed: delivery.reversed = true
```

- For `normal` or `immediate` shops, `delivery.type` is not in the body (not `"normal"`).
- The ฿1000 rule applies only when the order is not already delayed. A delayed shop keeps
  its own `delay_duration` (for example 15).
- `delay_duration: 0` is falsy, so it is not sent.

## 14. Amounts in `POST transactions`

Source: `submit` (line 3288). Only the fields with amounts or amount rules are listed.
See api-reference §8 for the full body.

| Field | Value |
|---|---|
| `data.type` | `"delivery"`, `"take-away"` or `"dine-in"` |
| `data.order.type` | `business.business_type` (e.g. `"Restaurant"`) |
| `data.order.order_value` | `bv` (§4) |
| `data.order.payment_method` | e.g. `"cash"` |
| `data.order.delivery_subsidy` | `actualDeliverySubsidy` (§10). 0 when there is no fare. |
| `data.order.remit` | `actualRemitAmount + specialDiscount + specialRemit` (§11) |
| `data.order.fulfilment_discount` | §6 |
| `data.order.special_discount` | always 0 |
| `data.order.vat` | §5 |
| `data.order.basket` | `{items: basket.items, notes, languages}`; `items` is the list of `{item, quantity}` (§16) |
| `data.order.change_for`, `local_contact`, `fulfilment_time`, `payment_id` | only when set |
| `data.settings.vat_percent` | `w.vat` or 0 |
| `data.settings.billing_percent` | `billing(pkg)` for delivery, else 0 |
| `data.settings.remit_percent` | `remit(pkg)` for delivery, else 0 |
| `data.settings.delivery_subsidy_percent` | `deliverySubsidy(pkg)` for all order types |
| `data.settings.fulfilment_discount_percent` | §6 `pct` |
| `data.billing` | `billingAmount` text, delivery only, only if > 0 |
| `data.delivery.is_free_delivery` | `isFreeDelivery` (§10) |
| `data.delivery.type`, `delay_duration` | §13 |
| `data.delivery.workflow_id`, `express_fleet` | `w.express` (fleet name) |
| `data.delivery.fallback_fleet` | `w.fallback_fleet`, if set |
| `data.delivery.promo_code` | `null` unless the user entered a code |
| `data.fare` | the fare object of §8 with `remit` and `client` |
| `data.autoconfirm` | `true` if `w.order_options.autoconfirm` |

Worked example (live-like, `r_20_10`, express fleet remit 10, shop remit 3, 4423 m,
basket 2 × ฿120 + 1 × ฿140 −10 % with option ฿20 × 2):

```
bv = 240 + (126 + 40) = 406
fare: step round(4.423)-1 = 3 → 40; cash 40; remit 10+3 = 13; client 53
deliverySubsidy = round(40.6) = 41; fee = 53 - 41 = 12; free over ceil(53/0.1) = 530
remitAmount = round(81.2) = 81; order.remit = 81 - 41 = 40
total = 406 + 12 = 418
```

Stale fare (web bug): if the user sets a delivery address and then changes to take-away,
`deliveryFare` stays. Then a take-away order can send a non-zero `delivery_subsidy`
(and `specialRemit`/`specialDiscount`). The native app should use no fare for take-away and
dine-in (open question in §20).

## 15. Option rules (option sheet)

Source: `ShopMenuOptions` (shop-profile~f04d431b, module 5811).

Shown groups (`filteredOptionsMenus`, line 1772):

- The groups are the item's `options` ids, in that order, then sorted: required groups first
  (stable sort).
- A group is shown only if its blob exists and has at least one option.
- A group with `condition` is shown only if a selected option's JSON, lower case, contains
  the condition text: `JSON.stringify(option).toLowerCase().includes(condition)`. The
  condition itself is not changed to lower case. A condition with capital letters never
  matches. The JSON includes all option fields (name, translations, price, quantity).
- After each selection, selected options of groups whose condition is no longer true are
  removed (`clearConditionalOptions`, on the next tick).

Selecting (`select`, line 2001):

- An out-of-stock option cannot be selected (toast "Option Out of Stock").
- `single`: a new option replaces the old one in that group. A tap on the selected option
  removes it.
- `multiple` without `allow_multiple`: a tap toggles the option.
- `multiple` with `allow_multiple`: the first tap selects with quantity 1. More taps do
  nothing. The + and − buttons change the quantity. At 0 the option is removed.
  There is no upper limit on the + button.
- `allow_multiple` on a `single` group has no effect.
- The selected entry is `{menu, option}`: `menu` is a copy of the group data without
  `items`, `required` and `select`, plus `id` (the blob id). `option` is the option object
  with `quantity` (and `discounted_price` if §3.1 applies).

Rules checked at "Add" (`checkRequirements`, `invalidOptionsMenuStrict`, line 1884):

```
required group: at least one selected option in it
for each shown "multiple" group:
  n = parseInt(multiple_n); q = Σ option.quantity in this group
  "up_to":   invalid if q > n
  "exactly": invalid if q !== n
  "none" or missing: always valid
```

- `exactly` is checked also for an optional group with nothing selected. So an optional
  `exactly 3` group needs exactly 3. (Live: all `exactly` groups are required.)
- `exactly` without `multiple_n` gives `n = NaN`, so it is always invalid.
- `multiple_n` can be a string (`parseInt`).
- The live warning (red group border, `invalidOptionsMenu`, line 1871) is only `q > multiple_n`
  for `multiple` groups whose constraint is not `"none"`.
- Single groups have no quantity rule.

Other "Add" checks:

- `max_count`: stop if the quantity of this item in the basket (all lines) `>= max_count`.
- `free_gift`: stop if the basket has one already.

The sheet total (`totalPrice`) is `discountedPrice(item) + Σ optionPrice(selected option)`.

The new basket item (`submit`):

```
i = deepCopy(menuItem)
if menuItem.options && menuItem.options.length: i.id2 = shortid()    // also if no group is shown
i.options = selected          // [{menu, option}], can be []
i.comment = comment           // the note for this item
```

## 16. Basket rules

Source: `initBasket` (line 3452), `invalidBasketForShop` (line 3460), `submitOptions` (line 3535),
`removeFromBasket` (line 3587), `newRemoveFromBasket` (line 3597), `sortBasket` (line 3530).

Shape (`state.basket`, saved in `localStorage.store`):

```
{shop_id, created_at, notes, items: [{item, quantity}]}
```

`item` is the item of §15: the whole menu item (all fields, translations, pic, tags,
`options` replaced by the selected list), plus `id2` and `comment`.

- `initBasket`: if the stored basket is older than 60 minutes **and** for another shop, make
  a new basket. Else keep it. A basket for this shop is kept at any age. A basket for another
  shop younger than 60 minutes is kept.
- `invalidBasketForShop` (on add and checkout): if the basket is for another shop and has
  items, ask "Shop here?". Yes → new basket. No → go to the other shop. An empty basket for
  another shop is replaced without a question.
- Add (`submitOptions`):
  - The item has `id2` (it has option groups): always a new line with quantity 1.
  - No `id2`: find the first line with the same `item.id`. If found, quantity + 1 and the
    line's `item.comment` becomes the new comment (the old item copy stays). Else a new line.
- The menu "+" button always opens the option sheet. So a line with `id2` always has
  quantity 1. Only lines without options get quantity > 1.
- Remove from the menu "−" (`removeFromBasket`): if the item has option groups and the total
  quantity is > 1, open the removal sheet (list of lines of this item). Else decrease the
  first line with this `item.id`. The removal sheet removes one line by `id2`.
- Lines with quantity 0 are removed.
- The basket is sorted by `item.name` with `localeCompare` after each change. Display only.
- Order notes: `basket.notes` (one text for the order). The note per item is `item.comment`.
- The basket in the store has no menu version. The web app does not check prices again.
  It uses the saved item copy.
- `basketWarning` text "This basket is for another shop." exists for the UI.

## 17. JavaScript behaviour summary for the port

| Topic | JavaScript | Kotlin |
|---|---|---|
| `parseInt("12.5")` | 12 | parse leading integer digits; stop at the first non-digit |
| `parseInt("1e3")`, `parseInt(" 45")`, `parseInt("-20")` | 1, 45, −20 | same parser: skip leading space, sign, digits |
| `parseInt(22.5)` (number) | 22 (via `"22.5"`) | truncate toward zero |
| `parseInt("")`, `parseInt(null)` | `NaN` | model as "no number"; the sum becomes `NaN` → `null` in JSON |
| `Math.round(x)` | `floor(x + 0.5)` | `Math.round(x)`, not `kotlin.math.round` |
| `a / 100`, `1 - a / 100`, `x * 0.07` | IEEE double | `Double`, same order of operations |
| `Math.ceil(client / pct)` | double division | `ceil(client.toDouble() / pct)` |
| `x || y` | 0, `""`, `null`, `NaN` fall to `y` | explicit checks |
| `!discount` | `"0"` is truthy | keep: result is the same |
| `bv >= null` | `bv >= 0` → true | free delivery quirk (§10) |
| `(v).toFixed(2)` | string, JS rounding | copy JS `toFixed` |
| missing key vs `null` | `undefined` keys are not sent | omit the key |
| string prices | `"120"` | the API sends text; keep the raw text |

## 18. Parity fixtures

`tools/price-parity/` has a copy of this web code (`web-pricing.js`) and a generator. The
generator writes `core/pricing/src/test/resources/parity/cases.json`. The Kotlin tests read
this file. See `tools/price-parity/README.md` for the schema.

## 19. Package codes in the live data (2026-09-27)

Counts from `fixtures/live/eateries.json`: 251 shops with a Commerce workflow
(of 256 Chiang Mai eateries).

| Code | Shops | Commission | Subsidy | Billing | Behaviour |
|---|---|---|---|---|---|
| `r_20_10` | 77 | 20 % | 10 % | – | r family |
| `r_15_9` | 34 | 15 % | 9 % | – | r family |
| `r_15_10` | 22 | 15 % | 10 % | – | r family |
| `r_15_8` | 21 | 15 % | 8 % | – | r family |
| `r_20_8` | 14 | 20 % | 8 % | – | r family |
| `r_20_9` | 12 | 20 % | 9 % | – | r family |
| `r_20_7` | 9 | 20 % | 7 % | – | r family |
| `r_10_0` | 8 | 10 % | 0 % | – | commission only, full fare |
| `r_15_5` | 3 | 15 % | 5 % | – | r family |
| `r_18_9`, `r_14_8`, `r_20_6`, `r_10_5`, `r_14_7` | 2 each | | | – | r family |
| `r_20_5`, `r_15_4`, `r_18_10`, `r_16_8`, `r_14_10`, `r_13_7`, `r_15_7`, `r_17_8`, `r_19_7`, `r_14_9` | 1 each | | | – | r family |
| **all `r_`** | **220** | | | | |
| `p_10_5` | 5 | 0 | 10 % | 5 % | special remit ≤ ฿30 |
| `p_10_10` | 3 | 0 | 10 % | 10 % | special remit ≤ ฿30 |
| `p_5_5` | 2 | 0 | 5 % | 5 % | special remit ≤ ฿30 |
| `p_15_8` | 1 | 0 | 15 % | 8 % | special remit ≤ ฿30 |
| `p_0_20` | 1 | 0 | 0 % | 20 % | billing only, full fare |
| **all `p_`** | **12** | | | | |
| `f_10_10` | 8 | 0 | 10 % | 10 % | special discount (to `remit`) |
| `f_20_0` | 2 | 0 | 20 % | 0 % | special discount (to `remit`) |
| **all `f_`** | **10** | | | | |
| `apple` | 5 | 0 | 0 | – | no rules, full fare |
| `elderberry` | 2 | 0 | 0 | – | no rules, full fare |
| `fig` | 1 | 0 | 0 | – | no rules, full fare |
| `durian` | 1 | 0 | 0 | – | no rules, full fare |
| `thai…` | 0 | 0 | list | `_Y` | known to the code, not used |

## 20. Needed for the Kotlin port

Model (`core:model`):

- [ ] `WorkflowData`: add `max_remit`, `free_delivery`, `free_delivery_distance`,
      `free_delivery_polygon`, `free_delivery_to`, `max_distance`, `visual_discount`,
      `menu_options.propagate_discounts`, `delivery_fee_per_km`, `min_delivery_fee`,
      `locations`, `order_options.autoconfirm`. Keep `vat`, `takeaway_discount`,
      `dinein_discount` as `Double` (a percent can be 12.5).
- [ ] `DeliveryOptions`: add `remit_amount`, `extra_cash`, `extra_tukpay`, `reversed`,
      `driver_note`, `sign_name`.
- [ ] `MenuItem`: add `by_weight`, `actual_weight`, `estimated_weight`. Keep `price`,
      `discount` as raw text (JS `parseInt` rules). Keep `vatable` as "missing / true / false".
- [ ] `OptionItem`: keep `price` as raw text; add `discounted_price` for the cart line.
- [ ] `OptionGroup`: `multiple_n` can be text; keep the raw value.
- [ ] User data: `delivery_options.remit_amount` and `extra_distance`.

Pricing (`core:pricing`):

- [ ] `jsParseInt(String?)`, `jsRound(Double)`, JS `toFixed(2)`, `NaN` handling.
- [ ] Item price, visual discount (once), option price, `propagate_discounts`.
- [ ] Line total, `order_value`, item count, VAT with `vatable`.
- [ ] Take-away and dine-in discount.
- [ ] Fare: fleet choice (fallback for array/surge/peak, express for remit), index rule,
      default model, self delivery, extra cash and tukpay, remit sum, `client`.
- [ ] Distance: route + `extra_distance`; fallback straight line × 1.25; 50 m and max distance
      rules; peak hours 17–18 local time.
- [ ] Package codes: all branches of §9, including the `NaN` cases.
- [ ] Subsidy, free delivery (dynamic, fixed with distance, polygon, `free_delivery`,
      `-1`), `actualDeliveryFee`, `deliveryFee`, `actualDeliverySubsidy`.
- [ ] Commission with `max_remit`, special remit (p_), special discount (f_), billing text.
- [ ] Total and order page total.
- [ ] Checks: `min_order` (delivery), free gift, delivery type and ฿1000 rule, cash hours,
      `max_count`.
- [ ] Order body amounts of §14 (omit keys that the web app does not send).

Cart (`core:domain`, Room):

- [ ] Line id: `id2` for items with option groups (new line each add); merge by `item.id`
      otherwise; the last comment wins on merge.
- [ ] 60-minute rule (older than 60 minutes **and** other shop → new basket).
- [ ] Option sheet rules of §15, including `condition`, `exactly` on optional groups and
      `allow_multiple`.
- [ ] Keep a copy of the menu item and the selected options in each line (the web order
      sends this copy).

Tests:

- [ ] Read `core/pricing/src/test/resources/parity/cases.json`; one test per `kind`.
- [ ] Compare numbers exactly (`null` for `NaN`), and strings for `billing_amount`.

Open questions:

1. Stale fare on take-away (§14): copy the bug (send a subsidy) or send 0? We suggest 0.
2. The `free_delivery: true` case (§10): the web total includes the fee but the order page
   does not. Which total should the native app show? No live shop has it now.
3. Peak hours and cash hours use the device clock and time zone. Use Asia/Bangkok, or the
   device zone like the web app?
4. Lines with options: the web app never has quantity > 1 on them. Can the native cart allow
   it? The formulas are linear, so the amounts stay correct.
5. The fleet for a shop with `express: "self"`: the parity data uses the recorded fallback
   fleet. We did not record a live response for that shop.
