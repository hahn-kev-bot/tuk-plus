# Phase 1 – Browse (no login)

## Goal

The user can browse Chiang Mai shops and menus fast, also on a slow network.
Each screen shows how old its data is.

**Done when** (PLAN.md §8):

1. A cold start shows Home in less than 1 s from the cache.
2. A shop opens at once from the cache.

The app logs two events to measure this: `perf home_ready`
(`ms_since_process_start`) and `perf shop_ready` (`ms`, `menu_age_s`).

## Scope

In scope:

- **Home**: search bar, rows from page blobs (Specials, What's HOT, Shops &
  Services, Only on TUK, Cuisines), Recent shops, New on TUK, Top eats.
  The sign-up row for shop owners (`Home_Business`) is not shown.
- **Shops list** ("All shops", and Home tiles such as a cuisine): chips from the
  Eat page, text filter, Open now, Delivery / Take-away / Dine-in, sort
  (For you, Nearest, A–Z, Newest), open shops first, then closed shops.
- **Search**: local results at once; Tuk's shop search and menu search from
  3 characters. A menu result opens the shop with the menu filtered.
- **Shop**: header, open state and hours today, fulfilment types, menu preface,
  category chips, sticky category titles, item search, item sheet with option groups.
  Prices show discounts. Sold-out items are dim and last in their category.
- **Cache age chip** on each screen. A tap refreshes. Pull-to-refresh also works.
- **Location** (coarse, optional): for distance and Nearest sort only.

Not in scope: cart, ordering, login (phases 2–4).

## Design decisions

| Decision | Why |
|---|---|
| Cache raw response bodies in files (`FileResponseCache`), not Room tables. | The shop list is one 600 KB response. Android cannot read SQLite rows larger than about 2 MB, and the list grows. The same parser reads cached and fresh bodies, so they cannot differ. Room comes in phase 2 for the cart. |
| Cache key without `ts`. | `ts` is only a cache buster. |
| `CachedResource`: disk first, then refresh when stale; only one request at a time per key; a failure keeps the old data. | PLAN.md §5.1. |
| All open states, distances, filters and search run on the device. | The server sends the full list; no network wait. Open state uses Chiang Mai time (`Asia/Bangkok`), not the phone's time zone. |
| One OkHttp client; the first call at start is `GET version`. | It opens the HTTP/2 connection early. Phase 0 logs showed 577–787 ms for a new connection and 171 ms for a reused one. |
| Prefetch at start: shop list, all Home pages, Eat chips, For you, New shops, in parallel. On Wi-Fi also the menus and fleets of recent and "What's HOT" shops (max 20, 4 at a time). A shop card that stays on screen 1 s loads its menu. | PLAN.md §5.4. |
| Background refresh every 4 h on Wi-Fi with a good battery (WorkManager). | The app opens with recent data. |
| Menu is outdated when the shop list shows a newer Commerce `updated_at`. | A menu change shows soon, without a short refresh age for all menus. |
| Home tiles with `shop_open` use the local open state, not one `helpers/shop_open` call per tile. | The web app makes 14 calls for this. |
| Images: Coil with a 250 MB disk cache; `.webp` first, the original if WebP fails. | api-reference §11. |
| Features are packages in `app`, not separate modules. | Fewer modules while the app is small. |

## Modules added

- `core:domain` (JVM): opening hours, categories and badges, tile actions, Home
  row rules, shop list filter and sort, local search, menu display rules.
- `core:data` (JVM): `FileResponseCache`, `Cached<T>`, `CachedResource`,
  `ResourceStore`, refresh ages (`Policies`), repositories, `RecentShops`, `Prefetcher`.

## Owner test list

Install the debug APK. For each step, note what is wrong. Then share the logs
(Settings → Debug → Share today).

1. **First start (no cache).** Home shows grey boxes, then the rows. The chip
   says "Loading…", then "Updated now".
2. **Second start.** Force-stop the app and open it again. Home shows at once.
   The chip shows the age. (Log: `home_ready` should be less than 1000 ms.)
3. **Tiles.** Tap a shop tile in What's HOT: the shop opens. Tap a cuisine tile:
   the shop list opens with that filter. Tap "Buy 1 Get 1": the list shows those shops.
4. **Shops list.** Try Open now, Delivery, the chips, and each sort. Tap
   "Use my location", allow it, and sort by Nearest. Distances show.
5. **Search.** Type "piz": local results show at once; "More shops" and
   "Menus with …" come after about 1 s. Tap a menu result: the shop opens with
   the menu filtered to "piz".
6. **Shop.** Scroll the menu. The category title stays at the top. Tap a
   category chip. Tap an item: the sheet shows options and prices. Check a shop
   that is closed: it shows "Closed – opens at …" or "Closed today".
7. **Offline.** Turn on flight mode. Open the app and a shop that you opened
   before: the data shows, and the chip says "Offline – data from … ago".
   Open a shop that you never opened: the chip says "Offline – no data yet".
8. **Pull to refresh** on Home, the list and a shop. The chip changes to
   "Updating…" and then "Updated now".
9. **Compare with the website.** For two shops, compare open state, hours today,
   menu items and prices with tukapp.co.
