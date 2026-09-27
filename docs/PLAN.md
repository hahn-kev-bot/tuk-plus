# Tuk plus – Android app plan

Tuk plus is a native Android client for the Tuk food ordering service
(https://tukapp.co). It uses the same backend as the web app. We found out how
that backend works by reading the web app. See [api-reference.md](api-reference.md).

Tuk gave permission to build this app. Tuk does not supply test shops or push
notifications. The project owner tests orders manually with real orders.

## 1. Goals

1. Browse shops and menus fast, also on a slow network.
2. Order food: cart, options, delivery or pickup, place the order. The real Tuk
   web app, in a WebView, is a fallback for checkout (§8a).
3. Track orders and show payment details. Payment itself happens outside the app.
4. Always show how old the data on the screen is.

Release 1 decisions:

- App name: **Tuk plus**. Application id: **`app.hahn.tukplus`**.
- Language: **English only**. All UI text goes in string resources, so we can
  add Thai later. Menu and shop text uses the `en` field if present, else the
  default field.
- Region: **Chiang Mai only**. The app always uses the Chiang Mai region and
  coordinates `18.796143,98.979263` for list requests. It uses the device
  location only for distance, sort and the delivery address. There is no region picker.

Not in scope (first release): other languages, other regions, rides, marts,
card payment (Stripe), shop and driver screens, LINE/Facebook/Apple login.

## 2. What we learned about the backend

| Fact | Effect on the app |
|---|---|
| The web app waits about 1 s per call. Most of that is new TLS connections: with one reused HTTP/2 connection a call takes about 0.3 s (measured in phase 0). The server sends no cache headers, no ETag, no gzip. | One shared OkHttp client for the whole app. We must still cache all data in the app, because we cannot use HTTP caching. |
| The web shop page makes 5 calls **in series** (about 5 s). Only 2–3 are needed, and they can run in parallel. | Shop page from network: about 0.4 s. From cache: at once. |
| The eatery list is the whole region in one call (256 shops, 608 KB, about 2 s). All filters are client side. | Fetch it one time, store it, filter and search it locally. |
| Open status, distance, prices and delivery fees are calculated on the client. | We calculate them locally. No network wait. Open status is always current, also with old cached data. |
| The client sends the totals and fees in the order. | Our pricing code must match the web app exactly. We test it with many recorded menus. |
| No live updates. The web app polls every 60 s. | We poll too, faster while the order screen is open. |
| Auth is a static string plus a device UUID. The user id is the session. | Store the user id encrypted. Never log it. |
| Images are on S3 with 30-day caching and WebP versions. | Use a disk image cache and request `.webp`. |

## 3. Technology

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM + unidirectional data flow; repositories expose `Flow<Cached<T>>` |
| DI | Hilt |
| Network | OkHttp + kotlinx.serialization, with a small own client (`TukApi`). No Retrofit: the API mixes JSON, plain text, `null` bodies and HTTP 500 answers that are not errors, and a thin client handles this more simply. |
| Local cache | API responses: raw JSON files (`FileResponseCache`, see docs/phases/phase-1.md). Cart and orders: Room (phase 2+) |
| Settings / session | DataStore; user id in EncryptedSharedPreferences or Tink |
| Images | Coil 3 with a large disk cache |
| Background work | WorkManager |
| Maps | osmdroid or MapLibre (the web app uses OpenStreetMap tiles) – no Google Maps key needed |
| Tests | JUnit, Turbine, MockWebServer (logic only, no network) |
| Logs | Own file logger (JSON Lines, one file per day), see §9 |
| Min SDK | 26 (Android 8). Target the latest SDK. |

## 4. Module layout

```
app/                      Application, navigation, DI setup
core/model/               Plain data classes (Shop, MenuItem, OptionGroup, Order, …)
core/network/             TukApi client, auth, retries (GET only), HTTP logging, error mapping
core/database/            Room entities, DAOs
core/data/                Repositories, cache policy, sync workers
core/pricing/             Pure Kotlin: price, options, VAT, delivery fare, open hours
core/ui/                  Theme, common components (CacheAgeChip, skeletons, errors)
feature/home/             Home rows from page blobs
feature/eat/              Shop list, chips, filters, sort
feature/search/           Shop and menu search
feature/shop/             Shop header, menu, option picker
feature/cart/             Cart and checkout
feature/orders/           Order list, order detail, tracking, payment details
feature/account/          Login, profile, addresses, language, debug logs export
core/logging/             Session log writer, redaction, export
tools/api-probe/          JVM command-line tool, run manually: calls the live read-only API and checks our models
```

`core/model`, `core/network`, `core/logging` and `core/pricing` have no Android
code. We can test them fast on the JVM, and `tools/api-probe` can use them.

Phase 0 made `app`, `core/model`, `core/network`, `core/logging` and
`tools/api-probe`. Phase 1 added `core/domain` (browse logic: hours, filters,
search, menu rules) and `core/data` (cache and repositories). The screens are
packages in `app` (`ui/home`, `ui/eat`, `ui/search`, `ui/shop`) while the app is
small. Other modules come with the phase that needs them.

## 5. Speed and caching design

### 5.1 Rules

1. **Cache first.** Every screen shows cached data at once, if there is any.
2. **Revalidate in the background.** After the screen shows cached data, the
   repository fetches new data if the cache is older than its refresh age.
3. **Always show the age.** Each screen shows a small chip: "Updated 3 min ago",
   "Updating…", or "Offline – data from 2 h ago".
4. **Never block on the network**, except for actions that must be live (place
   order, login, final shop-open check).
5. **Parallel calls.** Never chain calls that do not depend on each other.

### 5.2 The `Cached<T>` type

```kotlin
data class Cached<T>(
    val data: T?,             // null when there is no cache yet
    val fetchedAt: Instant?,  // when the server gave us this data
    val status: Status,       // Fresh, Stale, Refreshing, Error(reason)
)
```

Each repository returns `Flow<Cached<T>>`. The flow first emits the cached row
from Room. Then it emits `Refreshing`. Then it emits the new data, or `Error` with
the old data kept. The UI never has an empty screen when old data exists.

### 5.3 Cache table

| Data | Key | Refresh age | Notes |
|---|---|---|---|
| Home page blobs | page (region is Chiang Mai) | 30 min | All 13 pages in parallel. Skip pages that were `null` last time, check them once a day. |
| Eat chips | – | 6 h | |
| Eatery list | – | 15 min | Open status is computed locally, so old data still shows correct open/closed state from hours. |
| "For you" scores | user | 6 h | |
| Shop menu (workflow + blobs) | business id | 1 h, and when `workflow.updated_at` in the eatery list is newer | Prefetch (see 5.4). |
| Delivery fleet pricing | commerce workflow id | 30 min | Needed for the fee at once. |
| Route distance | origin + destination (rounded to ~50 m) | 7 days | Only needed for the fee. |
| Search results | query | 10 min | Shop search also runs locally over the cached eatery list, so results show at once. |
| Saved addresses | user | 1 day | |
| Cart | device | never expires by time | Device only, like the web app (see 5.7). |
| Orders (ongoing) | user | 0 (always refresh) | Polling (see 5.5). |
| Orders (history) | user | 10 min | Paged. |
| Images | URL | 30 days | Coil disk cache, 250 MB. |

### 5.4 Prefetch

- **App start:** run these in parallel: eatery list, home blobs, chips, "for you",
  ongoing orders. First send `GET version` to open the HTTP/2 connection early.
- **Warm shops:** after the eatery list loads, prefetch menus in the background
  for favourite shops, recent shops, and shops shown on Home "What's HOT".
  Limit: 4 calls at a time, about 20 shops. Only on unmetered network or when
  the user allows it.
- **On scroll:** when a shop card is visible for about 1 s, prefetch its menu.
- **Periodic:** a WorkManager job refreshes the eatery list and home blobs every
  few hours, on unmetered network and while charging. So the app opens with
  recent data.
- **Images:** prefetch shop banners and the first menu photos with Coil.

### 5.5 Live data

- Order detail screen open: poll `commerce/transaction?id=` every 15 s while the
  order is ongoing. Stop when it ends.
- App in foreground: poll the ongoing list every 60 s.
- There are no push notifications (Tuk does not give us FCM access). All
  updates come from polling.
- App in background with an ongoing order: a foreground service with an
  ongoing notification ("Order MUR123 – being prepared") polls every 30 s. It
  stops when the order ends, or after 3 hours. The user can turn it off. Then
  WorkManager polls every 15 min (the Android minimum).
- Show a notification when the order state changes, and when the driver's
  payment details become available.

### 5.6 Slow and failing requests

- Timeouts: connect 10 s, read 30 s (the eatery list is big).
- GET requests: retry 2 times with backoff (1 s, 3 s) on network errors and 5xx.
- `POST transactions` (place order) is **never** retried by itself. On a timeout,
  the app checks the ongoing orders for our `short_id`. If it finds none, the app
  asks the user: "Try again" or "Finish on Tuk website" (§8a). This prevents
  double orders.
- Map plain-text 500 bodies to user messages. Keep the raw text in debug logs.
- Parse the large eatery list on a background thread, with streaming JSON.
- Show skeleton screens, not spinners, when there is no cache.

### 5.7 Cart

The web app keeps the cart **only on the device**. It is part of the app state
in `localStorage`. The server gets no cart until the order is placed. (The web
app sends cart events such as `add_to_basket` to `POST logs` as telemetry only.
We do not send these.)

Web app rules that we copy:

- One cart at a time, for one shop.
- If the user adds an item from a different shop: when the cart is more than
  60 minutes old, replace it; else ask "Start a new cart?".
- An item with options is always its own cart line. Items without options merge.

Our additions:

- Store the cart in Room, so it survives app restarts.
- Store a copy of the item and option data (name, price) in the cart line, with
  the menu version. When the menu refreshes, check each line again: removed item,
  out of stock, price changed, option group changed. Show the changes to the user
  before checkout.
- Keep one cart per shop (optional, later). The web app keeps only one.

## 6. Screens

1. **Home** – search bar, rows from page blobs (Specials, What's HOT, Shops &
   Services, Only on TUK, Cuisines), "New on TUK", "Top eats", recent shops.
2. **Eat list** – chips, filter sheet (distance, fulfilment, cuisine, free
   delivery, open now), sort (distance, name, new, for you). Open shops first.
3. **Search** – local results at once from the cached list; server results
   (`autocomplete?business=`, `search/menu_items`) added when they arrive.
4. **Shop** – banner, hours today, open state, fulfilment types, category tabs,
   menu list, item search. Cache age chip.
5. **Option picker** – bottom sheet with option groups and validation.
6. **Cart / checkout** – items, notes, fulfilment type, address (map pin +
   saved addresses), time (now or later today), payment method, fee breakdown,
   total, place order. A second button, "Finish on Tuk website", opens the web
   checkout fallback (§8a).
7. **Web checkout (fallback)** – the Tuk web app in a WebView, with our cart and
   login. The user finishes the order there.
8. **Orders** – ongoing and history tabs.
9. **Order detail** – status timeline, items, totals, driver on map when
   `enroute`, **payment details panel** (see §7), cancel while `initiated`,
   call shop, "Open on Tuk website" (§8a).
10. **Account** – phone login (SMS code), name, phone,
   saved addresses, logout.

## 7. Payment details panel

Payment happens outside the app. The app shows:

- The amount to pay, as the web app calculates it (api-reference §10).
- The order number to use as the reference.
- For PromptPay: the driver's QR image (`qr_pic_url`), large, with "Save image"
  and "Share" buttons. Many Thai bank apps can read a QR from an image in the gallery.
- For bank transfer: bank name, account number, account name. Each has a copy button.
- Before the details exist: the web app's text, "Payment details will show on the
  order after the shop has confirmed…".
- For cash: the amount and the "change for" value.
- For take-away / dine-in: "Pay at the shop".
- Payment slip upload (in scope). The user takes a photo or picks an image of
  the transfer slip. The app uploads it (the web app uses
  `POST …execute-api…/dev/uploadimage`; we must trace the request body before
  phase 5) and then sends
  `PATCH transactions/{id} {"data":{"payment_receipt_pic":"<url>"}}`.
  The order then shows "Paid – slip sent". The upload runs in WorkManager, so it
  continues on a slow network and after the app closes. The app logs each step.

## 8. Phases

Each phase ends with a build that works. Each phase has its own file in
[docs/phases/](phases/README.md).

| Phase | Content | Done when |
|---|---|---|
| 0. Foundations ✅ [spec](phases/phase-0.md) | Gradle project, modules, CI (build, lint, unit tests). Session logger (§9). `tools/api-probe` that calls the live read-only endpoints and checks our models. Record JSON fixtures. | The probe parses all Chiang Mai eateries and 25+ menus without errors. Logs export works. |
| 1. Browse (no login) ✅ built, [spec](phases/phase-1.md) | Home, Eat list, Search, Shop menu. Response cache, `Cached<T>`, age chip, prefetch. | Cold start shows Home in < 1 s from cache. Shop opens at once from cache. |
| 2. Cart and pricing [spec](phases/phase-2.md) | `core/pricing` with option rules, discounts, VAT, delivery fare, open hours. Cart saved in Room. | Unit tests match the web app results for every recorded menu (see §10). |
| 3. Account [spec](phases/phase-3.md) | SMS login, device uuid, session, profile, saved addresses, language. | Login works with a real phone. |
| 4. Checkout [spec](phases/phase-4.md) | Address picker with map, route distance, fees, payment method, validation, place order with idempotency check. Order preview screen in debug builds shows the exact JSON before it is sent. Web checkout fallback (§8a) with the background order check. | The owner places a real order in Tuk plus, and one with the fallback. The shop sees correct items and totals. The check logs its result. |
| 5. Orders and payment [spec](phases/phase-5.md) | Order list, detail, polling, foreground tracking notification, driver map, payment details panel, payment slip upload, cancel, "Open on Tuk website". | The owner follows a real order from placed to delivered and pays with the details shown. |
| 6. Polish [spec](phases/phase-6.md) | Accessibility, dark theme, offline mode, error reporting, release build, Play Store listing. | Beta testers use it for a week. |

## 8a. Web checkout fallback

Tuk plus places orders with its own checkout. The real Tuk web app is a
**fallback**: at checkout the user can select "Finish on Tuk website" instead of
"Place order". Then the web app, in an in-app **WebView**, does the rest of the
checkout and places the order.

The app offers the fallback:

- always, as a second button at checkout;
- when our checkout finds a problem before the order is sent (validation error,
  shop package that we do not support yet, pricing error, fee not available);
- when `POST transactions` fails and our idempotency check finds no order (§5.6).

The web app is the reference implementation, so a fallback order is correct even
if our code has a bug. In the background, Tuk plus calculates the same order and
compares the two. This finds bugs in our code with real orders.

The WebView is inside Tuk plus, not Chrome or a Custom Tab, because the app must
write into the web app's local storage.

### State injection (tested)

The web app saves its whole state in `localStorage["store"]` (JSON). At start it
reads the value back with `Object.assign(defaultState, stored)` and does no other
checks. We tested this on 2026-09-27 in a headless browser: we wrote a cart into
`store.basket` before the page loaded and opened `/shop/<id>`. The web app
showed "Checkout 2 Items ฿500" and its checkout started with our cart. No order
was sent.

In Android:

1. Before the page loads, a document-start script
   (`WebViewCompat.addDocumentStartJavaScript`, only for `https://tukapp.co`)
   reads `localStorage.store`, **merges** our values into it, and writes it back.
   It does this only once for each hand-off (a one-time token), so a page reload
   does not reset the web app's state.
2. Values that we write:
   - `basket` (checkout only): `{shop_id, created_at, notes, items:[{item, quantity}]}`.
     Each `item` is a copy of the menu item in the web app format, with `id2`,
     `comment` and `options:[{menu, option}]` (api-reference §8).
   - `user`, `userSource`, `roles`: the login bundle from our login. The web
     app then counts as logged in.
   - `uuid`: `{uuid, createdAt}`, the same device uuid as the app. The web app
     checks the pair with `users/{id}?uuid=&check=true`, so it must match.
   - `language: "en"`, `region: "Chiang Mai"`.
3. After the page loads, the script reads the state back and checks the cart
   item count and the user id. If they do not match, the app logs it and shows
   a message. (This catches a change of the web app's state format.)

### Checkout flow

1. The user fills the cart in Tuk plus, and at checkout taps "Finish on Tuk
   website". The choices already made in our checkout (fulfilment type, address,
   time, payment method) are saved for the check.
2. Tuk plus saves a **snapshot** of the cart and all pricing inputs it has
   (menu version, workflow settings, fleet pricing).
3. The WebView opens `https://tukapp.co/shop/<businessId>` with the cart and
   login injected. The web checkout opens.
4. The user selects the fulfilment type, address, time and payment method, and
   places the order in the web app.
5. The document-start script also wraps `XMLHttpRequest`. It does not change or
   stop any request. When the web app sends `POST transactions`, the script
   copies the request body and the response, and sends them to Tuk plus through
   `WebViewCompat.addWebMessageListener` (only for `https://tukapp.co`).
6. When the web app goes to `/orders?tab=ongoing`, Tuk plus closes the WebView,
   clears its own cart, and opens its own order screen for the new order.
7. If the capture fails, Tuk plus finds the order in
   `transactions?user_id=&type=ongoing` (newest order for this shop, created
   after the hand-off).

The delivery address cannot be pre-filled: it is a local value in the web
checkout screen. Addresses that the user saves in Tuk plus (`POST user_addresses`)
show in the web app's saved-address list.

### Background order check

After a fallback order is placed, a WorkManager job runs the check. It does not
block the user.

1. Input: our cart snapshot, the captured `POST transactions` body, and the order
   from `commerce/transaction?id=`.
2. Tuk plus builds its own order JSON with `core/pricing`, with the same choices
   that the user made in the web app (fulfilment type, address, time, payment
   method, distance from the captured `fare.distance`).
3. It compares field by field: items and options, `order_value`, `vat`,
   discounts, `delivery_subsidy`, `remit`, `fare.*`, `settings.*`, `delivery.*`,
   and the payment amount that our payment panel would show.
4. It also checks that the web app got our cart correctly (same items, options,
   quantities and notes).
5. Result: `match`, or a list of differences with both values. The app writes the
   result, and all the inputs, to the log (§9). With the inputs, each difference
   can become a unit test.
6. The check also follows the order until it ends. It compares our state
   mapping, amounts and payment details with what the order really shows.
7. Debug builds show a notification when there is a difference. Settings →
   Debug shows a list of recent checks.

Orders placed with our own checkout get a smaller check: the app reads the
order back with `commerce/transaction?id=` and checks that the server stored
what we sent (items, amounts, fare). It logs any difference.

### Open an order in the web app

- The order detail screen has "Open on Tuk website". It opens
  `https://tukapp.co/commerce_order/<orderId>` in the same WebView, with the
  login injected in the same way (no cart).
- The login is needed: the web order page shows the customer actions (for example
  cancel and slip upload) only when `order.created_by == user.id`.
- Use this when our order screen shows something wrong, or for a feature that we
  do not have yet.

### Risks

- The web app can change its state format. The read-back check (above) finds this.
- The web app in the WebView sends its own telemetry to Tuk. That is normal
  for the web app.
- The web app uses the device time zone for open hours. Phones in Thailand are
  correct.
- The WebView is slower than our own screens, because the web app loads its
  bundle and data again. This is acceptable for a fallback.

## 9. Logging

The owner tests the app with real orders. When there is a bug, the owner exports
the logs and gives them to the developer. So the logs must have enough detail
to find the bug without the device.

### 9.1 Files

- One file per **day**: `files/logs/2026-09-27.jsonl`. A new **session** starts
  at each app process start. Each session has an id, and each line has the
  session id. So one file holds all sessions of one day, and a tool can split
  them.
- Format: JSON Lines. One event per line:
  `{"t":"2026-09-27T08:31:48.017+07:00","s":"a1b2c3","lvl":"I","tag":"net","ev":"http","…":…}`
- The first line of each session: app version, build type, git commit, Android
  version, device model, locale, time zone, region, network type, device uuid
  (hashed), logged in or not, backend `version` string.
- Keep 14 days. Maximum 10 MB per day. When a file is full, keep writing to a
  second file (`2026-09-27.2.jsonl`).
- Write on a single background thread with a buffer. Flush at once on warnings,
  errors and when the app goes to the background.
- An uncaught-exception handler writes the crash with its stack trace before the
  app stops. On the next start, the app shows "The app crashed. Share logs?".

### 9.2 What to log

| Area | Events |
|---|---|
| Network | Every request: method, path, query, status, duration, response size, retry count. On errors and parse failures: the first 4 KB of the body. |
| Write calls | `POST transactions`, `PATCH transactions/*`, login, addresses, slip upload: the **full** request body and the full response. |
| Web checkout | Hand-off start, injected values (redacted), read-back result, web app URL changes, the captured `POST transactions` body and response, WebView errors, time to load. |
| Order check | Cart snapshot, all pricing inputs, our order JSON, the web app's order JSON, the list of differences or `match`. |
| Cache | Hit or miss, age of the data, refresh start, refresh result. |
| Pricing | Input (cart lines, options, workflow settings, fleet pricing, distance) and every output number. With this we can repeat a price calculation in a unit test. |
| Orders | Every state change seen by polling, with time. Polling start and stop. Idempotency checks. |
| UI | Screen opened, main actions (add to cart, checkout, place order, finish on website, cancel), validation errors shown to the user. |
| App | Start, foreground, background, workers, foreground service start and stop, permissions. |

### 9.3 Privacy

- Never log the `Authorization` header.
- Replace the user id and device uuid with a short hash. The same value always
  gives the same hash, so we can still follow one user in the logs.
- Mask phone numbers (`+66 8x xxx 5678`) and email addresses.
- Addresses and coordinates stay in the logs, because fee bugs need them. The
  logs stay on the device until the owner shares them.
- Payment account numbers: log only the last 4 digits.

### 9.4 Export

- Settings → Debug → "Share logs". The user selects today, one day, or the last
  7 days. The app makes a zip and opens the Android share sheet.
- Also "Share this session" for a quick report.
- The zip contains a `README.txt` with the app version and the device summary.
- A long press on the cache age chip opens a small log view for the current screen.
- A small script (`tools/logview`) splits a file by session and prints a
  timeline, for use when debugging.

## 10. Testing

All automatic tests are **logic only**. They do not use the network.

- **Unit tests:** pricing, option rules, open hours (with the `Asia/Bangkok` time
  zone and fixed clocks), tag parsing, cache policy, order state mapping,
  log redaction.
- **Parsing tests:** use JSON fixtures that we record from the live read-only
  API. They include odd cases: `null` lists, plain-text errors, prices as
  strings, empty `pic`.
- **Pricing parity:** a manual tool runs the web app's own price code in a
  headless browser on the recorded menus and baskets and saves the results as
  fixtures. The Kotlin unit tests must give the same numbers.
- **Repository tests:** MockWebServer with delays and failures, to check that
  cached data stays on screen and the cache age is correct.
- **Live API probe (manual only):** `./gradlew :tools:api-probe:run`, or a GitHub
  Actions job with a manual trigger (`workflow_dispatch`). It calls only
  read-only GET endpoints. It checks that our models still parse, and it records
  new fixtures. It prints the backend `version` string.
- **Write APIs:** tested only by the owner with real orders. We use the logs (§9)
  to find problems.
- **Bug fixes from logs:** for each bug, turn the logged input into a unit test
  first, then fix the code.

## 11. Risks and open questions

| Risk / question | Plan |
|---|---|
| The API is not public. Tuk can change it. | Tuk gave permission. Run the manual API probe before each release and when the backend `version` changes. The app logs the backend version at each session start. |
| Wrong totals or fees in an order. | Exact copy of the web formulas and parity tests. The web checkout fallback, with the background check (§8a), finds differences with real orders. Compare with `delivery/price_check` in debug builds. |
| Double orders on a slow network. | `short_id` idempotency check (§5.6). |
| No push notifications. | Polling, with a foreground service while an order is ongoing (§5.5). |
| Weak auth: the user id is the only secret. | Encrypted storage. No logging. No sharing. |
| No test shops. Test orders are real orders. | Only the owner places orders. Debug builds show the exact order JSON and ask for a second confirmation before sending. Use the fallback for new kinds of shops first, so the check can compare. |
| Packages other than `r_x_y` are not fully traced. Seen: `p` (12 shops), `f` (10), `apple` (5), `elderberry` (2), `fig` (1), `durian` (1); the web code also has `thai`. | For these shops, checkout offers only the web fallback until we trace that code. The background check collects data for them. |
| Route distance: the web app uses GraphHopper with its own key. | Use Tuk's `GET directions` first (same result source), then straight line × 1.25. |

There are no open questions for the project owner now.
