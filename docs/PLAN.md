# Tuk+ Android app – plan

Tuk+ is a native Android client for the Tuk food ordering service
(https://tukapp.co). It uses the same backend as the web app. We found out how
that backend works by reading the web app. See [api-reference.md](api-reference.md).

## 1. Goals

1. Browse shops and menus fast, also on a slow network.
2. Order food: cart, options, delivery or pickup, place the order.
3. Track orders and show payment details. Payment itself happens outside the app.
4. Always show how old the data on the screen is.

Not in scope (first release): rides, marts, card payment (Stripe), shop and
driver screens, LINE/Facebook/Apple login.

## 2. What we learned about the backend

| Fact | Effect on the app |
|---|---|
| Every call takes about 1 s. The server sends no cache headers, no ETag, no gzip. | We must cache all data in the app. We cannot use HTTP caching. |
| The web shop page makes 5 calls **in series** (about 5 s). Only 2–3 are needed, and they can run in parallel. | Shop page from network: about 1.3 s. From cache: at once. |
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
| Network | OkHttp + Retrofit + kotlinx.serialization (lenient: prices are strings, `null` lists, plain-text bodies) |
| Local cache | Room (SQLite) |
| Settings / session | DataStore; user id in EncryptedSharedPreferences or Tink |
| Images | Coil 3 with a large disk cache |
| Background work | WorkManager |
| Maps | osmdroid or MapLibre (the web app uses OpenStreetMap tiles) – no Google Maps key needed |
| Tests | JUnit, Turbine, MockWebServer, Paparazzi or Roborazzi for screenshots |
| Min SDK | 26 (Android 8). Target the latest SDK. |

## 4. Module layout

```
app/                      Application, navigation, DI setup
core/model/               Plain data classes (Shop, MenuItem, OptionGroup, Order, …)
core/network/             Retrofit API, JSON adapters, error mapping, auth interceptor
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
feature/account/          Login, profile, addresses, language
tools/api-probe/          JVM command-line tool: calls the live API and checks our models
```

`core/pricing` has no Android code. We can test it fast on the JVM.

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
| Home page blobs | region + page | 30 min | All 13 pages in parallel. Skip pages that were `null` last time, check them once a day. |
| Eat chips | region | 6 h | |
| Eatery list | region | 15 min | Open status is computed locally, so old data still shows correct open/closed state from hours. |
| "For you" scores | user | 6 h | |
| Shop menu (workflow + blobs) | business id | 1 h, and when `workflow.updated_at` in the eatery list is newer | Prefetch (see 5.4). |
| Delivery fleet pricing | commerce workflow id | 30 min | Needed for the fee at once. |
| Route distance | origin + destination (rounded to ~50 m) | 7 days | Only needed for the fee. |
| Search results | query | 10 min | Shop search also runs locally over the cached eatery list, so results show at once. |
| Saved addresses | user | 1 day | |
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
- App in background with an ongoing order: WorkManager every 15 min (the minimum),
  plus an optional ongoing notification with a short foreground poll. Show a
  notification when the state changes.
- If Tuk later registers our app in their Firebase project, use FCM to start a
  refresh at once.

### 5.6 Slow and failing requests

- Timeouts: connect 10 s, read 30 s (the eatery list is big).
- GET requests: retry 2 times with backoff (1 s, 3 s) on network errors and 5xx.
- `POST transactions` (place order) is **never** retried by itself. On a timeout,
  the app checks the ongoing orders for our `short_id`. If it finds none, the app
  asks the user before it sends again. This prevents double orders.
- Map plain-text 500 bodies to user messages. Keep the raw text in debug logs.
- Parse the large eatery list on a background thread, with streaming JSON.
- Show skeleton screens, not spinners, when there is no cache.

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
   total, place order.
7. **Orders** – ongoing and history tabs.
8. **Order detail** – status timeline, items, totals, driver on map when
   `enroute`, **payment details panel** (see §7), cancel while `initiated`,
   call shop.
9. **Account** – phone login (SMS code), name, phone, language (en/th),
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
- Optional (phase 5+): upload a payment slip photo, the same as the web app.

## 8. Phases

Each phase ends with a build that works.

| Phase | Content | Done when |
|---|---|---|
| 0. Foundations | Gradle project, modules, CI (build, lint, unit tests). `tools/api-probe` that calls the live read-only endpoints and checks our models. Record JSON fixtures. | The probe parses all Chiang Mai eateries and 25+ menus without errors. |
| 1. Browse (no login) | Home, Eat list, Search, Shop menu. Room cache, `Cached<T>`, age chip, prefetch. | Cold start shows Home in < 1 s from cache. Shop opens at once from cache. |
| 2. Cart and pricing | `core/pricing` with option rules, discounts, VAT, delivery fare, open hours. Cart saved in Room. | Unit tests match the web app results for every recorded menu (see §9). |
| 3. Account | SMS login, device uuid, session, profile, saved addresses, language. | Login works with a real phone. |
| 4. Checkout | Address picker with map, route distance, fees, payment method, validation, place order with idempotency check. | A test order is placed with a shop that agreed to help (§10). |
| 5. Orders and payment | Order list, detail, polling, notifications on state change, driver map, payment details panel, cancel. | We follow a real order from placed to delivered. |
| 6. Polish | Thai translation, accessibility, dark theme, offline mode, error reporting, release build, Play Store listing. | Beta testers use it for a week. |

## 9. Testing

- **Pricing parity:** run the web app's own price code in a headless browser
  (Playwright) against the recorded menus and baskets. Save the results as
  fixtures. The Kotlin tests must give the same numbers.
- **Contract tests:** `tools/api-probe` runs every day in CI against the live
  read-only endpoints. It fails when a field changes type or goes missing, or when
  `GET version` changes. This gives early warning of backend changes.
- **UI tests:** screenshot tests for main screens in light, dark, en and th.
- **Slow-network tests:** MockWebServer with 1–3 s delays and failures, to check
  that cached data stays on screen and the age chip is correct.
- **Order flow:** only with a real, agreed test order (§10). We do not send test
  orders to shops without permission.

## 10. Risks and open questions

| Risk / question | Plan |
|---|---|
| The API is not public. Tuk can change it or block us. | Ask Tuk for permission and, if possible, a contact. Contract tests warn us of changes. |
| Wrong totals or fees in an order. | Exact copy of the web formulas, parity tests, and show the same breakdown the web app shows. Compare with `delivery/price_check` in debug builds. |
| Double orders on a slow network. | `short_id` idempotency check (§5.6). |
| No push notifications without Tuk's help. | Polling first. Ask Tuk to add our package to their Firebase project. |
| Weak auth: the user id is the only secret. | Encrypted storage. No logging. No sharing. |
| Test orders affect real shops. | Find one shop that agrees to help, or ask Tuk for a test shop (`workflow.data.test` exists). |
| Packages other than `r_x_y` (`p_`, `f_`, `thai`) are not fully traced. | Read that code before phase 2 ends. Until then, show a "order on the website" link for those shops. |
| Route distance: the web app uses GraphHopper with its own key. | Use Tuk's `GET directions` first (same result source), then straight line × 1.25. |

Questions for the project owner:

1. Can we ask Tuk for permission, a test shop, and FCM access?
2. Languages for release 1: English and Thai only, or also Japanese, Chinese,
   Burmese and Khmer (the web app has all six)?
3. Regions: only Chiang Mai at first?
4. Is the payment-slip upload in scope?
5. App name and package id (for example `co.tukplus.app`)?
