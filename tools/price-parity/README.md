# Price parity tool

This tool makes the test fixtures for `core:pricing`. It runs the Tuk web app's own
price code on the recorded live menus and on generated baskets. The Kotlin tests must
give the same numbers. See `docs/pricing.md` for the formulas.

Run it by hand, like the API probe. The Gradle build and the unit tests do not run it.
The tests only read the output file.

## Run

Node.js 22. No npm packages.

```
node tools/price-parity/generate.js
```

It reads `core/model/src/test/resources/fixtures/live/` and writes
`core/pricing/src/test/resources/parity/cases.json`. The output is the same on each run
(seeded random numbers, fixed clock). Run it again after you record new live fixtures
(`tools/api-probe`), and commit the new `cases.json`.

## Files

| File | What it does |
|---|---|
| `web-pricing.js` | Part 1: a text copy of the web price functions (each with bundle file, line and name). Part 2: a small wrapper that runs them on plain inputs, with a fake Vue instance and a fixed clock. |
| `generate.js` | Loads the fixtures, makes the cases (seed 20260927) and writes `cases.json`. |
| `refresh-verbatim.js` | Writes part 1 of `web-pricing.js` again from the beautified web bundles. |

## When Tuk changes the web app

1. Download the new bundles and beautify them (for example with `js-beautify`).
2. Change the file names in `BUNDLES` in `refresh-verbatim.js`.
3. Run `node tools/price-parity/refresh-verbatim.js <bundle dir> --check`. It shows the lines
   that changed.
4. Read the changes. Update `docs/pricing.md` and the Kotlin code.
5. Run it without `--check`, then run `generate.js`.

The copy in `web-pricing.js` is not changed by hand. Do not "fix" the web code there.

We did not run the bundles in a headless browser. The wrapper runs the same function text
in Node. `refresh-verbatim.js --check` proves that the text is the same as the bundles.

## `cases.json`

```
{
  "header": {
    "version": 1,
    "generated_by": "tools/price-parity/generate.js",
    "seed": 20260927,
    "fixtures": "core/model/src/test/resources/fixtures/live",
    "web_bundles": [String],
    "counts": {kind: Int},
    "live_package_codes": {code: Int},          // shops per workflow.data.fruit in eateries.json
    "fleets": {"fleet1": Fleet, …}              // delivery fleets, used by key in the cases
  },
  "cases": [Case]
}

Fleet = {"state": String, "data": {"pricing_array": [Int], "surge": Int?, "remit_amount": Int?,
                                   "peak_hour_max_distance": Int?}}
Case  = {"id": String, "kind": String, "tags": [String],
         "clock": {"hour": Int, "minute": Int, "epoch_ms": Long}?,   // local time in Chiang Mai
         "input": {…}, "expected": {…}}
```

JSON rules: a missing key is the same as "not set" in the web code (JavaScript `undefined`).
`null` in `expected` is JavaScript `null` or `NaN`. Numbers are JSON numbers (can have a
fraction, for example `0.07` or `22.5`). Price fields in inputs are text, as in the API.

### kind `checkout` (815 cases)

The whole flow: basket → order type → address and fare → "Place order" → `POST transactions`.

Input:

| Key | Type | Notes |
|---|---|---|
| `workflow` | `{id, data}` | `data` has only the keys that the price code reads: `fruit`, `vat`, `min_order`, `takeaway_discount`, `dinein_discount`, `max_remit`, `free_delivery`, `free_delivery_over`, `free_delivery_distance`, `free_delivery_polygon`, `free_delivery_to`, `max_distance`, `visual_discount`, `express`, `fallback_fleet`, `delivery_options {type, delay_duration, remit_amount, extra_cash, extra_tukpay, reversed}`, `payment_options`, `menu_options`, `delivery_fee_per_km`, `min_delivery_fee` |
| `business` | `{id, lat, lon, country}` | pickup point |
| `delivery_workflows` | `{express: key?, fallback: key?}` or `null` | keys into `header.fleets` |
| `user` | `{delivery_options: {remit_amount?, extra_distance?}}` or `null` | `user.data` |
| `basket` | `{items: [{item, quantity: Int}]}` | `item`: `id`, `id2`?, `name`, `price` (text), `discount`?, `discount_type`?, `by_weight`?, `actual_weight`?, `estimated_weight`?, `vatable`?, `free_gift`?, `comment`?, `options: [{menu: {id, name}, option: {id, price, discounted_price?, quantity?}}]` |
| `fulfilment_type` | `"delivery"`, `"take-away"`, `"dine-in"` | |
| `address` | `{lat, lon}` | delivery only |
| `route` | `{distance: Int (m), source: "google"\|"graphhopper"}` or `null` | delivery only; `null` = no route, the web code uses the straight line × 1.25 |
| `fulfilment_time` | `"HH:MM"` | optional (scheduled order) |
| `payment_method` | String | optional, default `"cash"` |
| `menu_items` | `[item]` | only for free gift cases (the web code finds the gift in the menu) |

Expected:

| Key | Type | Web name |
|---|---|---|
| `line_totals` | `[Int?]` | `discountedTotalPrice(line.item)` per line |
| `basket_value` | `Int?` | `basketValue` = `order_value` |
| `vatable_basket_value` | `Int?` | `vatableBasketValue` |
| `item_count` | Int | `basketItemCount` |
| `vat` | `Int?` | `vat` |
| `distance_check` | `"ok"`, `"too_short"`, `"too_far"` or `null` (not delivery) | `validFareDistance` |
| `max_delivery_distance` | Number (km) | `maxDeliveryDistance` |
| `is_peak_hour` | Boolean | `isPeakHour` |

When `distance_check` is `"too_short"` or `"too_far"`, the web app clears the order type.
Then `expected` has only the keys above. Else it also has:

| Key | Type | Web name |
|---|---|---|
| `fulfilment_discount_percent` | Number | `fulfilmentDiscountPercent` (fraction) |
| `fulfilment_discount_amount` | Int | `fulfilmentDiscountAmount` |
| `fare` | `{version, price, discount, cash, tukpay, bonus, distance, source, remit, client}` or `null` | `deliveryFare` after `addRemitToFare` |
| `delivery_subsidy_percent` | Number | `deliverySubsidyPercent` |
| `delivery_subsidy` | `Int?` | `deliverySubsidy` |
| `actual_delivery_subsidy` | `Int?` | `actualDeliverySubsidy` |
| `remit_percent` | Number | `remitPercent` (0 unless delivery) |
| `remit_amount` | `Int?` | `remitAmount` |
| `actual_remit_amount` | Int | `actualRemitAmount` |
| `special_remit` | Int | `specialRemit` |
| `special_discount` | Int | `specialDiscount` |
| `billing_percent` | Number | `billingPercent` |
| `billing_amount` | String | `billingAmount` (`toFixed(2)`) |
| `free_delivery` | Boolean or Number or null | `freeDelivery` (JS value, see pricing.md §10) |
| `fixed_free_delivery_over` | `Int?` | `fixedFreeDeliveryOver` |
| `dynamic_free_delivery_over` | `Int?` | `dynamicFreeDeliveryOver` |
| `free_delivery_over` | `Int?` | `freeDeliveryOver` |
| `free_delivery_remainder` | `Int?` | `freeDeliveryRemainder` |
| `inside_free_delivery_polygon` | Boolean | `insideFreeDeliveryPolygon()` |
| `is_legacy_free_delivery` | Boolean | `isLegacyFreeDelivery` |
| `is_free_delivery` | Boolean | `isFreeDelivery` |
| `actual_delivery_fee` | `Int?` | `actualDeliveryFee` (basket row and total) |
| `delivery_fee` | Int | `deliveryFee` ("Fees" sheet) |
| `total_value` | `Int?` | `totalValue` |
| `blocked_by` | `null`, `"promptMinOrder"`, `"promptFreeGiftOverRemoval"`, `"promptFulfilmentType"` | the check in `preSubmit` that stopped the order |
| `cash_allowed` | Boolean | `ShopSubmitOrder.hasCash` |
| `order` | object or `null` (blocked) | amounts from the `POST transactions` body, see below |
| `order_page_total` | `Int?` | `CommerceOrder.orderValue` on the sent body |

`order`:

```
{"type": String,
 "order": {"order_value", "payment_method", "delivery_subsidy", "remit", "fulfilment_discount",
           "special_discount", "vat", "fulfilment_time"?},
 "settings": {"vat_percent", "billing_percent", "remit_percent", "delivery_subsidy_percent",
              "fulfilment_discount_percent"},
 "delivery": {"workflow_id", "express_fleet", "fallback_fleet"?, "is_free_delivery", "type"?, "delay_duration"?} | null,
 "fare_equals_expected_fare": Boolean | null,    // data.fare is expected.fare (null: no fare sent)
 "billing"?: String}                             // data.billing, only when sent
```

A key that is missing in `order.delivery` is not sent by the web app (for example `type`
for a `normal` shop).

### kind `option_sheet` (131 cases)

The option sheet (`ShopMenuOptions`) for one item.

Input: `workflow`, `business`, `menu_items: [item]` (the menu item, with `options` = group ids),
`options_menus: [{id, blob_type: "options_menu", data: {name, required, select, allow_multiple,
multiple_constraint, multiple_n, condition, items: [{id, name, price, out_of_stock?, …}]}}]`,
`item_id`, `qty` (Int: quantity of this item already in the basket), `actions: [{op, menu_id,
option_id}]` with `op` = `"select"` (tap the option), `"add_quantity"` (+), `"remove_quantity"` (−),
and `comment`? (item note).

Groups with a `condition` keep all option fields, because the condition matches the option JSON.

Expected: `shown_menus` ([String], ids in display order after the actions),
`invalid_menus_live` ([String], red border), `invalid_menus` ([String] after "Add"),
`required_invalid` (Int), `total_price` (`Int?`), `selected` ([{menu_id, option_id, quantity}]),
`submitted` (Boolean), `blocked_reason` (String?: text key of the toast),
`basket_item` (the new basket item or `null`; `id2` is a test id `sidN`),
`trace_total_prices` ([`Int?`]: sheet total after each action, `null` = action skipped
because the group was not shown or the option was not selected).

### kind `basket` (17 cases)

Basket rules. Input: `workflow`, `business`, `stored_basket` (`{shop_id, created_at (ISO), notes,
items}` or `null`), `basket_age_minutes`?, `steps: [{op, item?}]`, `op` = `"init"` (`initBasket`),
`"add"` (menu + button, then the sheet returns `item`), `"submit_options"` (sheet result only),
`"remove"` (menu − button with the menu `item`), `"remove_line"` (removal sheet, basket line `item`).

Expected: `states`, one per step: `{op, other_shop_prompt?: Boolean, kept_stored_basket?: Boolean,
shop_id, lines: [{id, id2?, name, quantity, comment?}], removal_sheet: Boolean, basket_value, item_count}`.
The line order is the web order (`localeCompare` on the name). Compare it as a list or as a set.

### kind `package` (50 cases)

Input `{fruit: String?}`. Expected `{has_free_delivery: Boolean, delivery_subsidy: Number?,
billing: Number?, remit: Number?}` (fractions; `null` = `NaN`).

### kind `item_price` (65 cases)

Input `{item, visual_discount?: Number}`. Expected `{item_after_visual_discount: {price, discount}?,
weight_price: String|Number|null, discounted_price: Int?, option_prices: [Int?],
discounted_total_price: Int?}`. Tag `float-rounding`: prices and percents where double maths
rounds differently from exact maths.

### kind `trip_distance` (20 cases)

Input `{lat1, lon1, lat2, lon2, extra_distance?}`. Expected `{straight_m: Int, trip_m: Int}`
(haversine metres, and `round(1.25 × straight) + extra_distance`).

## Tags

- `live-menu`: a live shop with its menu; the basket comes from the web option sheet.
- `live-workflow`: a live shop without a recorded menu; the basket is made up.
- `edge`: hand-picked cases (`package`, `special-remit`, `free-delivery-threshold`, `vat`,
  `item-discount`, `fulfilment-discount`, `free-delivery-over`, `polygon`, `free-delivery`,
  `max-remit`, `min-order`, `delivery-type`, `fleet`, `no-fleet`, `extras`, `distance`,
  `cash`, `options`, `by-weight`, `free-gift`).
- `fruit:<code>`: the package code of the shop.
