# Tuk API reference (reverse engineered)

This file records how the Tuk web app (https://tukapp.co) talks to its backend.
We got this information from the site's JavaScript bundles (web build `3.7058f`)
and from live read-only requests on 2026-09-27. The API is not documented or
supported by Tuk. It can change at any time.

The scope is the **customer** side: browse, menu, cart, checkout, orders.
The web app also has driver, dispatcher and shop-admin screens. We ignore them.

## 1. Transport

| Item | Value |
|---|---|
| Base URL | `https://api.tukbot.com/prod/tuk/` |
| Host | AWS API Gateway + Lambda, region ap-southeast-1 (Singapore), HTTP/2 |
| Auth header | `Authorization: Bearer helloworld\|<device-uuid>` (see §2) |
| Compression | None. The server ignores `Accept-Encoding: gzip`. |
| Cache headers | None. No `Cache-Control`, `ETag` or `Last-Modified`. |
| CORS | `*` |
| Latency | About 0.5 s server time per call. About 1 s end to end. The eatery list (608 KB) takes about 2 s. |

Response rules:

- Success is HTTP 200 with JSON.
- An empty result is HTTP 200 with the body `null`. Treat `null` as an empty list.
- A server error is HTTP 500 with a **plain-text** body, for example
  `sql: no rows in result set`.
- Some endpoints send HTTP 200 with a plain-text sentinel, for example `yes`,
  `user is deleted`, `successfully created device`.
- `HEAD` requests fail with 500. Use `GET`.
- Many GETs add `ts=<epoch ms>` as a cache buster. The server does not need it.

`GET version` returns a bare string such as `3.7058f`. A trailing `f` tells the
web app to force a reload. A native client can use this value to detect backend
releases (log it, and alert when it changes).

## 2. Identity and session

There is **no real authentication token**.

1. The client makes a v1 UUID one time and keeps it (the "device uuid").
2. Every request sends `Authorization: Bearer helloworld|<uuid>`.
   Without the header, the gateway returns 407.
3. After login, the client knows the `user.id` (a UUID). The web app writes the
   device uuid onto the user: `PATCH users/{userId} {"uuid": "<uuid>"}`.
4. The session is only "the client remembers `user.id`". Calls that need a user
   send the user id as a query parameter.

`GET users/{id}?uuid={uuid}&check=true` validates the pair. It returns the text
`user is deleted` if the user is not valid.

> Security note: the server accepts any uuid, and user ids are enough to read
> user data. We must treat user ids as secrets, store them encrypted, and never
> log them. We must not probe other users' data.

### 2.1 Phone login ("magic login", recommended)

| Step | Request | Result |
|---|---|---|
| Start | `POST magic_login` `{"source":"sms","source_user_id":"+66812345678","uuid":"<uuid>"}` | Server sends a 4-digit SMS code. Body is the magic-login record `{id, created_at, source_user_id, ...}`. |
| Verify | `GET magic_login/{id}?code=1234` | Body is the login bundle `{user, user_source, roles}`. |
| Resend | `GET magic_login/{id}?resend={notificationToken}` | Sends the code again. |
| Email code | `POST email/login` `{"mobile","email","uuid","magic_login_id"}` | Sends the same code by email. |

Phone format: E.164. The web app adds `+66` by default and removes a leading `0`.

Other login methods exist in the web app (LINE OAuth, Facebook, Apple). They
need the web app's OAuth client registrations. A native app cannot reuse them.
We skip them.

After login, the web app also does:

- `PATCH users/{id} {"uuid": ...}` (bind the device).
- `POST devices/{uuid}` `{"data":"<device JSON as a string>","user_id":"<id>"}`.
  Optional. It is an idempotent upsert.
- `PATCH users/{id} {"notification_token": ...}` for web push.

Logout: `PATCH logout/{id} {"notification_token": "..."}`.

### 2.2 User

- `GET users/{id}` and `GET users/{id}?info=true`.
- Fields: `id, name, phone_number, email, language, profile_pic_url,
  notification_token, uuid, created_at, updated_at, data{sms, email, sound,
  referral_code, is_blocked, ...}`.
- Edit fields: `PATCH users/{id}` with a partial object
  (`name`, `phone_number`, `language`, `email`, `profile_pic_url`).
- Edit settings: `PATCH users/{id}?action=settings {"data": {...}}`.

### 2.3 Saved addresses

| Action | Request |
|---|---|
| List | `GET user_addresses/{userId}` (`null` when empty) |
| Add | `POST user_addresses` |
| Edit | `PATCH user_addresses` (body includes `id`) |
| Delete | `DELETE user_addresses/{id}` |
| Past delivery drop-offs | `GET helpers/get_past_delivery_addresses?user_id={id}` |

Address: `{id, user_id, name, address, address_type (home|work|delivery|other), lat, lon, notes}`.

### 2.4 Telemetry (skip)

`POST logs` is client telemetry only. A native client does not need it.

## 3. Region and location

- The web app uses a region name, for example `Chiang Mai`. The default is Chiang Mai.
- The region is the nearest city in a fixed table. Known coordinates:
  Chiang Mai `18.796143,98.979263`, Bangkok `13.7321,100.5696`,
  Singapore `1.29027,103.851959`, Lisbon `38.71709,-9.13926`.
- Content today: Chiang Mai has 256 eateries. Bangkok has 8. Other regions are
  almost empty.
- Location goes to the server only as the query parameter `coords=lat,lon`.

## 4. Home and Eat pages (page blobs)

`GET blob/active?type=page&page={P}&region={R}`

- Home uses `P = Home_1 … Home_12` and `Home_Business`. The web app sends
  13 requests in parallel. Home_7 to Home_12 are `null` today.
- Eat uses `P = Eat` (category chips).
- The response is a list of blobs sorted by `data.rank`. The last one has
  `blob_type: "list_title"`.

List tile:

```json
{"id":"31b71e95-…","parent_id":"Home_1","blob_type":"list",
 "data":{"name":"Buy 1 Get 1","tag":"/eat?s=buy1get1&a=hp_tile_deals_buy1get1",
  "rank":4,"region":"Chiang Mai","page":"Home_1","show_name":false,"hidden":false,
  "pic":"https://tukapp.s3…/list/EFurgQApe.png","pic_en":"…","pic_th":"…"}}
```

Row title:

```json
{"blob_type":"list_title","data":{"page":"Home_2","title":{"en":"What’s HOT","th":"ฮอตฮิต"},
 "settings":{"randomise":false,"shop_open":true,"hidden":false,
  "time_slot":{"start":"0700","end":"2300"}}}}
```

Client rules:

- Remove tiles with `data.hidden`. Remove the row if `settings.hidden`.
- Hide the row when local time (HHMM) is outside `time_slot`. An end of `0` means `2400`.
- `randomise`: shuffle the tiles.
- `shop_open`: show a shop tile only if its shop is open. The web app calls
  `helpers/shop_open` once per tile. We compute this from the cached eatery list
  instead (§5.2).
- Picture: `pic_<lang>` if present, else `pic`.
- Show the tile name only if `show_name` is true.

Tile `tag` actions:

| Tag form | Action |
|---|---|
| contains `http` | Open an external link. |
| `@handle` or `/@handle` | Open a shop. Resolve with `data.premium_link` in the eatery list, else `GET short_link/@handle` → `{"url":"/shop/<businessId>"}`. |
| `/shop/<id>` | Open a shop. |
| `/eat?s=<text>` or plain text (`American`) | Eat list with a text filter. |
| `*for-you`, `*free-delivery`, `*open-now` | Eat list preset. |
| `/search/<text>` | Search screen. |
| `a=<id>` query parameter | Ad tracking. Remove it. |

Other Home sections:

- "New on TUK": `GET businesses?type=new_shops&coords=lat,lon` → `[{id,name,profile_pic_url,product_pic_url}]`.
- "Top eats": eateries sorted by the "for you" score.

## 5. Eateries

### 5.1 List

`GET businesses?type=eatery&coords=lat,lon` (also `type=mart`)

- Without `coords`, the server returns 500 "Please allow the app to know your
  location…".
- It returns the **whole region** (256 shops, 608 KB, about 2 s). It is not sorted
  and has no distance. All filters are client side.

Item (trimmed):

```json
{"id":"b5e18463-…","name":"Butter is Better Too","address":"…","business_type":"Restaurant",
 "is_active":true,"lat":18.751184,"lon":98.97347,"country":"th","updated_at":"…",
 "profile_pic_url":"…","banner_pic_url":"…",
 "data":{"hidden":false,"premium_link":"@buttertoo",
   "categories":"American, Diner, Breakfast, _butterisbetter, _pancakes",
   "search_terms":"Breakfast Sandwich","blurb":"…","phone_number":"…",
   "hours":{"mon":"","tue":"0800-1630","…":"…"},"hours_type":"selected-hours","product_pic_url":"…"},
 "workflows":[{"id":"faf8f62d-…","workflow_type_name":"Commerce","state":"active","updated_at":"…",
   "data":{"paused":false,"closed_until":"2026-08-12T17:00:00.000Z",
     "fulfilment_options":["delivery","take-away","dine-in"],"payment_options":["cash"],
     "fruit":"r_10_0","express":"Chiang Mai South Express Delivery","languages":["th","en"],
     "vat":0.07,"min_order":0,"free_delivery_over":0,"menu_style":"grid"}}]}
```

Derived fields (client side):

- `distance = round(1.3 × haversine metres)` from the user.
- Categories: split on `,`. An entry that starts with `_` is a hidden search keyword.
  An entry that ends with `*` is a badge (show it without `*` and `_`).

### 5.2 Open now

A shop is open when all of these are true:

- It has a Commerce workflow, and `workflow.data.paused` is not true.
- `closed_until` is empty or in the past.
- `hours_type == "always-open"`, or there are no hours, or the current time is in
  one of today's ranges.

Hours format: `data.hours.{sun..sat}` is a string. `null` or `""` means closed.
Remove spaces, `:` and `.`. Split on `,`. Each part is `HHMM-HHMM`. Open when
`start ≤ now < end`. An end of `0` means `2400`. Seen values: `0800-1600`,
`07:30-14:00`, `11.00-21.00`, `1100-1400, 1700-2100`.
Use the `Asia/Bangkok` time zone for Thai shops (not the device zone).

Server check (use before checkout only):
`GET helpers/shop_open?id={businessId}` or `?handle=@x`. It always returns HTTP 200
with plain text: `yes`, or a reason such as
`checkCommerceAllowTransaction: Shop is not currently open (Shop is closed today)`.

### 5.3 For you

`GET recommendations/foryou?page=eat&user_id={id or empty}` → `[{"key":"<businessId>","score":161}, …]` (20 items).

### 5.4 Search

| Search | Request | Response |
|---|---|---|
| Shops | `GET autocomplete?business={text}` | `{"businesses":[{"key":"Pizza Mania","value":"<businessId>","pic":"…","hidden":true}]}`. Drop entries that are hidden or have no `pic`. |
| Menu items | `GET search/menu_items?text={text}` | `[{"business_name","business_id","business_pic","count"}]`. Counts only. Open the shop and filter its menu locally. |

Search is global (no location). It is a substring match. No match returns `null`.
The web app starts to search at 3 characters, with a 1 s debounce.

## 6. Shop and menu

### 6.1 Calls

The web app loads a shop page in series: `short_link` → `businesses/{id}` →
`workflows/{businessId}` → (1 s later) `workflows?commerce_delivery=` →
(3 s later) `helpers/get_business_and_workflows`. That takes about 5 s.

What we need, all in parallel:

| Call | Why |
|---|---|
| `GET workflows/{businessId}` | Commerce workflow with the menu blobs (about 24 KB). |
| `GET workflows?commerce_delivery={commerceWorkflowId}` | Delivery fleet and pricing. |
| `GET businesses/{businessId}` | Only if the shop is not in the cached eatery list. |

`helpers/get_business_and_workflows` returns the same bytes as `businesses/{id}`.
Skip it. Use `workflow.updated_at` and `business.updated_at` as the menu version.

Menu only: `GET blob/{commerceWorkflowId}?type=workflow` (all blobs) or add
`&blob_type=digital_menu`.

### 6.2 Menu blobs (in `workflow.blobs`)

| `blob_type` | `data` |
|---|---|
| `menu_categories` | `[{"name":"Combos","en":"Combo","th":"…"}]` – category order |
| `digital_menu` | `[item]` – display order |
| `options_menu` | one option group (one blob per group) |
| `menu_preface` | `{preface, en, th}` – text at the top of the menu |

Menu item:

```json
{"id":"t8lp5D-zs","name":"Big Borscht Combo","description":"…","price":"380",
 "pic":"…/digital_menu/v5JsfIPaX.png","category":"Combos",
 "options":["691399e6-…","37a0afd5-…"],
 "en":{"name":"…","description":"…"},"th":{"name":"…"},
 "discount":"10","discount_type":"percent|number","out_of_stock":false,"hidden":false,
 "tags":["Vegan","member","*11-15"],"schedule":["mon","tue"],"max_count":6,
 "vatable":true,"price_from":"499","free_gift":null}
```

- `price` is a **string**. The web app uses `parseInt`, so decimals are dropped.
- `pic` is often empty. Show a placeholder.
- `options` holds ids of `options_menu` blobs.
- Hide an item when `hidden`, or when `schedule` is set and does not include
  today, or when a tag `*HH-HH` excludes the current hour. The web app also hides
  items with price < 9 that have no options and no `free_gift`.
- `out_of_stock`: show it dimmed. The user cannot add it.
- Localized text: `item[lang].name` if present, else `item.name`.

Option group (`options_menu.data`):

```json
{"name":"Soup Selection","en":"…","required":true,"select":"single|multiple",
 "allow_multiple":false,"multiple_constraint":"none|exactly|up_to","multiple_n":1,
 "condition":"<optional substring>","show_zero":true,
 "items":[{"id":"WtpSsafyU","name":"Chicken Soup","price":"0","out_of_stock":false,"en":{…}}]}
```

- Show the group only if the item lists its id. If `condition` is set, show the
  group only when a selected option name contains that text.
- `single`: a new choice replaces the old one. `multiple`: toggle. With
  `allow_multiple`, each option has a quantity.
- `required`: the user must select at least one.
- `exactly` / `up_to`: the total quantity must be `= multiple_n` / `≤ multiple_n`.

### 6.3 Delivery fleet

`GET workflows?commerce_delivery={commerceWorkflowId}`:

```json
{"express":{"id":"b7888320-…","name":"Delivery","state":"active",
  "data":{"pricing_array":[30,30,40,40,46,52,58,64,70,80,90,100,110,120],
   "surge":0,"remit_amount":10,"peak_hour_max_distance":27,"inactive_max_distance":30000,
   "inactive_message":"Due to high demand…"}},"fallback":null}
```

If `express.state` is `inactive`, delivery is paused. Show `inactive_message`.

## 7. Pricing (client side)

The server does **not** calculate prices for the client. The client calculates the
totals and the delivery fare and **sends them** in the order. The native app
must use the same formulas as the web app, or the shop sees wrong amounts.
These formulas come from `shop-profile~1c39816d.js` and `app~50b71177.js`.

```
discounted(p, item) = discount_type == "number" ? max(0, p - d) : round(p * (1 - d/100))
optionPrice          = parseInt(option.discounted_price || option.price) * option.quantity
lineUnit             = discounted(parseInt(item.price)) + Σ optionPrice
subtotal (order_value) = Σ lineUnit * quantity
vat                  = round(workflow.data.vat * vatable subtotal)
fulfilment_discount  = take-away / dine-in discount percent of subtotal (if the shop sets one)
```

Delivery fare:

```
metres  = route distance (GraphHopper, then GET directions, then straight line × 1.25)
km      = round(metres / 1000); if km > 0 then km = km - 1
base    = pricing_array[km]            (fallback fleet wins over express)
cash    = base + surge
client  = cash + remit_amount (+ shop and user remit, usually 0)
```

- Max distance (km) = min(`max_distance`, peak-hour max from 17:00 to 18:59,
  `pricing_array.length`). Default 14. Reject distances of 50 m or less.
- `GET directions?origin=lat,lon&destination=lat,lon` → `{"source":"google","distance":4423,"encoded_polyline":"…"}`.
- `GET delivery/price_check?pickup=lat,lon&dropoff=lat,lon&type=express` →
  `{"driving_distance":3.98,"price":40}`. The web UI does not call it, but it
  agrees with the formula. Use it as a check.

Shop package code `workflow.data.fruit`, for example `r_20_10`:

- First number (20) = Tuk commission percent. Send as `order.remit`. Do not show it.
- Second number (10) = delivery subsidy percent:
  `delivery_subsidy = round(0.10 × subtotal)`.
  Fee shown to the customer = `max(0, client − delivery_subsidy)`.
  So delivery is free when the subtotal is ≥ `ceil(client / 0.10)`.
- Other prefixes (`p_`, `f_`, `thai`) use a billing percent instead. We must
  read these paths in the code again before we support such shops.

Other rules:

- `min_order` applies to delivery only.
- Orders of ฿1000 or more use `delivery.type = "delayed"` with `delay_duration = 26`.
- There is no service fee ("Platform Fee ฿0").

## 7a. Cart

There is no cart API. The web app keeps the cart only in the browser, as
`state.basket` in the saved Vuex state (`localStorage.store`):
`{shop_id, created_at, notes, items:[{item, quantity}]}`. The server sees the
cart only when the order is placed (§8). The web app also sends cart events
(`add_to_basket`, `checkout_basket`, `set_order_notes`, …) to `POST logs` as
telemetry. The admin endpoint `analytics/active_baskets_count` probably counts
these events.

## 8. Placing an order

`POST transactions` (Vuex `createTransaction`, 5 s debounce).

```json
{"workflow_type_name":"Commerce","workflow_id":"<commerce workflow id>",
 "business_id":"<id>","created_by":"<user.id>",
 "data":{
  "type":"delivery|take-away|dine-in",
  "business":{"id","country","type","name","lat","lon","phone_number"},
  "order":{"type":"Restaurant","business_name":"…","order_value":380,"payment_method":"cash",
    "delivery_subsidy":38,"remit":76,"fulfilment_discount":0,"special_discount":0,"vat":0,
    "basket":{"items":[…],"notes":"…","languages":["en","th"]},
    "change_for":1000,"local_contact":"08…","fulfilment_time":"19:30"},
  "settings":{"vat_percent":0,"billing_percent":0,"remit_percent":0.2,
    "delivery_subsidy_percent":0.1,"fulfilment_discount_percent":0},
  "agent":false,"uuid":"<device uuid>","short_id":"<new shortid>","ref_prefix":"",
  "coords_created":{"lat":0,"lon":0},
  "delivery":{"workflow_id":"Chiang Mai Express Delivery","express_fleet":"Chiang Mai Express Delivery",
    "is_free_delivery":false,"promo_code":"","contactless_note":"","business_note":"",
    "type":"delayed","delay_duration":15,"predicted_route":"<polyline>"},
  "address":{"name","address","notes","lat","lon"},
  "fare":{"version":"v1","price":40,"discount":0,"cash":40,"tukpay":0,"bonus":0,
    "distance":4423,"source":"google","remit":10,"client":50}}}
```

- `delivery.workflow_id` is the fleet **name** (`workflow.data.express`), not a UUID.
- `basket.items[]` are copies of menu items plus `quantity`, `comment`, `id2`
  (a new id when the item has options), and
  `options: [{menu:{id,name,en,…}, option:{id,name,price,quantity}}]`.
- `short_id` is made by the client. We use it as an **idempotency key**: after
  a timeout, look for an ongoing order with this `short_id` before a retry.
- The web app ignores the response. It goes to the ongoing orders list.
- Scheduled order: `order.fulfilment_time = "HH:MM"`, same day, at least one hour ahead.
- The table number (`?table=` in the shop URL) is shown but not sent.

Checks the web app does before it sends:

- The user is logged in and has a phone number (7+ characters). A Thai
  local contact is needed if the phone is not `+66`.
- The shop is open (§5.2) and not paused. `helpers/shop_open` says `yes`.
- Required options are selected. `min_order` is met for delivery.
- For delivery: an address with lat/lon, a fare, a distance inside the maximum,
  and a driver note.
- No active block (`block_commerce_message`). If `block_multiple` is set, no
  other ongoing order.
- Cash is not offered for delivery from 22:20 to 09:00.

## 9. Orders and tracking

| Action | Request |
|---|---|
| Ongoing | `GET transactions?user_id={id}&type=ongoing` |
| Recent | `GET transactions?user_id={id}&type=recent` |
| History (paged) | `GET transactions?user_id={id}&type=relevant&offset={n}` (`offset` = items loaded, `null` = end) |
| Recently failed | `GET commerce/transactions?type=recently_failed&user_id={id}` |
| Order detail | `GET commerce/transaction?id={id}` |
| Delivery leg | `GET commerce/delivery?id={id}` |
| Cancel | `PATCH transactions/{id} {"state":"cancelled_by_customer"}` (only while `initiated`) |
| Payment slip | `PATCH transactions/{id} {"data":{"payment_receipt_pic":"<url>"}}` |

Order fields: `id, workflow_id, workflow_type_name (Commerce|Delivery|Ride),
business_id, state, is_ongoing, created_at, updated_at, business{…}, delivery{…},
data{type, order{…}, fare{…}, address{…}, short_id, ref}`.

Order number shown to users: `{ref_prefix-}{first 3 letters of shop name, upper case}{data.ref}`.
Currency comes from `business.country` (`th` = ฿).

Order states:

| State | Meaning |
|---|---|
| `initiated` | Placed. Waits for the shop. The customer can cancel. |
| `scheduled` | Future order. |
| `confirmed` | Shop accepted. Food is being prepared. |
| `ready` | Food is ready (pickup for take-away). |
| `completed` | Done. |
| `timed_out` | Shop did not answer. Show as failed. |
| `cancelled_by_shop`, `cancelled_by_customer`, `cancelled_by_driver`, `cancelled_by_agent`, `rejected` | Cancelled. |

Delivery states (`delivery.state`): `initiated` (finding a driver) →
`confirmed` (driver goes to the shop) → `driver_arrived` → `enroute`
(driver goes to the customer) → `alighted` (delivered).

Live updates: there is no websocket. The web app polls
`commerce/transaction?id=` every 60 s while `is_ongoing` or
`delivery.is_ongoing` is true, and the order list every 120 s. Web push (FCM)
only starts an early refresh. The driver position is
`delivery.driver.user_state {lat, lon, updated_at}`. The web app shows it only
when `delivery.state == "enroute"` and moves the marker by estimate between polls.

Push: the web app uses Firebase project `tuk-push-notifications` (sender id
`808714537831`). An Android app can get FCM messages from that project only if
Tuk registers our package name in it. Tuk will not do this, so the app polls.

## 10. Payment details (display only)

Payment happens outside the app. The app only shows the details.

Payment methods at checkout:

| Method | When offered |
|---|---|
| `cash` | If in `workflow.data.payment_options`. Not for delivery from 22:20 to 09:00. |
| `promptpay` | Always for Thai shops. |
| `bank-transfer` | Always for Thai shops. (The order page checks `bank_transfer` with an underscore.) |
| `paynow` | Singapore. The QR is built from `commerce.data.paynow`. |
| `card` | Stripe. **Out of scope** for us. |

Text the web app shows for PromptPay and bank transfer at checkout:
"Payment details will show on the order after the shop has confirmed. Please
double check the account number as it is different for each order."

On the order page, the details belong to the **assigned driver**:

- `delivery.driver.role.data.qr_pic_url` – a PromptPay QR image.
- `delivery.driver.role.data.bank_account` – `{bank_name, account_number, name_on_account}`.
- Amount to pay the driver = `order_value + vat − discounts + (fare.client − delivery_subsidy)`,
  or without the fare part if delivery is free.
- The reference is the order number. There is no other reference.
- The web app shows the panel only when the order has an ongoing or recent
  delivery and the driver has a QR or a bank account.
- For take-away and dine-in, the customer pays at the shop.

## 11. Images

- Public S3: `https://tukapp.s3.ap-southeast-1.amazonaws.com/staging/businesses/<id>/<kind>/<x>.png`.
- `Cache-Control: max-age=2592000` (30 days), with `ETag`.
- Most `.png` files are JPEGs. Change the extension to `.webp` to get a WebP
  that is 20–60 % smaller. If it fails, use the original URL.
- There are no resized versions. Menu photos are about 400 × 400.

## 12. Source map

| Topic | Web bundle file (beautified) |
|---|---|
| API client (all endpoints) | `app~3d685a12.*.js` module `068b` |
| Vuex store and actions | `app~c714bc7b.*.js` |
| Price and hours helpers | `app~50b71177.*.js` |
| Home | `home~31ecd969.*.js` |
| Eat list | `eat~31ecd969.*.js` |
| Shop, menu, basket, fees | `shop-profile~1c39816d.*.js`, `shop-profile~21833f8f.*.js`, `shop-profile~f04d431b.*.js` |
| Order page and payment panel | `commerce-order~31ecd969.*.js` |
| Search | `search~21833f8f.*.js` |
