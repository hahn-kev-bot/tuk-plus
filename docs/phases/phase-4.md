# Phase 4 – Checkout

Status: **in progress**: the web checkout fallback is built first (owner request, 2026-10-01).
The own checkout is not started.

## Progress

- Done: "Finish on Tuk website" in the cart. It opens `https://tukapp.co/shop/<businessId>`
  in a WebView (`ui/webcheckout`). The document-start script `app/src/main/assets/web_checkout.js`
  (origin `https://tukapp.co` only) merges `basket`, `language` and `region` into
  `localStorage.store` one time per hand-off (token in `localStorage.tukplus_handoff`), and
  copies the web app's `POST transactions` request and response to the app (XHR and fetch).
  It does not change or stop any request.
- Done: `core:data` `WebCheckout` saves a snapshot per hand-off (`files/web_checkout/<token>.json`:
  cart, workflow data, fleets, shop location, then the captured request and response).
- Done: background order check `core:pricing` `OrderCheck`: our price code runs on the web
  order's own basket, address, fare distance, payment method and time, and each amount field
  is compared. Basket differences are reported too. Log: `web_checkout check` with
  `result=match` or the differences. Test: all 645 parity orders without a user remit match.
- Done: after the order is sent and the web app shows `/orders…`, the app clears the cart and
  goes to the Orders tab.
- Tested on 2026-10-01 in headless Chromium on the live site (every write call blocked, the
  order request answered by the test): the basket shows "Checkout 2 Items ฿500", a reload does
  not inject again, the capture gets the request and the response.
- Not yet: login hand-off (phase 3). Until then, the user logs in inside the WebView once; the
  WebView keeps that login. Lines with options and quantity n are sent as n lines (the web
  app uses quantity 1 for them).
- Not yet: the check does not know the user's own remit (`user.delivery_options`), so it can
  show a fare difference for users who have one.

## Goal

The user places an order in Tuk plus. As a fallback, the user can finish the
order in the real Tuk web app inside the app.

**Done when** (PLAN.md §8): the owner places one real order in Tuk plus and one
with the fallback. The shop sees correct items and totals. The background check
logs its result.

## Scope

### Own checkout

- Order type: delivery, take-away or dine-in (chosen in the cart, docs/design.md).
- Delivery address: saved addresses and a map pin (osmdroid or MapLibre, no
  Google key). Driver notes are required, like the web app.
- Distance: `GET directions`, else straight line × 1.25 (api-reference §7).
  Cached for 7 days per rounded place pair.
- Time: now, or later today (at least one hour ahead).
- Payment method: PromptPay, bank transfer, and cash when the shop allows it
  (not for delivery from 22:20 to 09:00).
- Checks before sending (api-reference §8): open shop and `helpers/shop_open`,
  required options, `min_order`, phone, distance limits, blocks.
- `POST transactions` with the body of api-reference §8.
  - A new `short_id` for each order is the idempotency key.
  - No automatic retry. After a time-out, look for the `short_id` in the ongoing
    orders; if none, ask "Try again" or "Finish on Tuk website".
- Debug builds: a preview of the exact JSON and a second confirmation before sending.
- After the order: read it back with `commerce/transaction?id=` and log any
  difference from what was sent.

### Web checkout fallback (PLAN.md §8a)

- A second button at checkout, and the offer after a failure or for shops with
  package codes that `core:pricing` does not support yet.
- WebView with a document-start script (only for `https://tukapp.co`) that merges
  the cart (`basket`), the login (`user`, `userSource`, `roles`), `uuid`,
  `language` and `region` into `localStorage.store`, one time per hand-off.
  Tested in a headless browser on 2026-09-27.
- The script copies the web app's `POST transactions` body and response (it does
  not change or stop them) and sends them to the app with `addWebMessageListener`.
- When the web app goes to `/orders?tab=ongoing`, the app closes the WebView,
  clears its cart and opens its own order screen.
- Background order check (WorkManager): builds our own order for the same choices
  and compares it field by field with the web app's order. Logs `match` or each
  difference with both values.

## Risks

- Test orders are real orders. Only the owner places them.
- The web app can change its state format; the script reads the state back and
  logs a mismatch.

## Draft owner test list

1. Delivery order in Tuk plus, paid by PromptPay. Check the shop's order on the
   shop side if possible, else the order on tukapp.co.
2. Take-away order with the web checkout fallback. Tuk plus shows the order after
   the web app places it.
3. Share the logs. Look for the order request, the read-back check and the
   fallback comparison.
4. Turn on flight mode just before "Place order". The app does not send the order
   twice.
