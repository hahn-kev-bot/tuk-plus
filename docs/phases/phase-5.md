# Phase 5 – Orders and payment

Status: **not started**.

## Goal

The user follows each order from placed to delivered, and sees the payment
details to pay outside the app.

**Done when** (PLAN.md §8): the owner follows a real order from placed to
delivered and pays with the details shown.

## Scope

- Orders tab: ongoing and history (`transactions?user_id=&type=ongoing|recent`,
  paged `type=relevant&offset=`), recently failed orders.
- Order detail (`commerce/transaction?id=`): status timeline (api-reference §9
  states), items, totals, order number (`{prefix-}{3 letters}{ref}`), call shop,
  cancel while `initiated` (`PATCH transactions/{id} {state:"cancelled_by_customer"}`).
- Live updates without push (Tuk gives no FCM access, PLAN.md §5.5):
  - order screen open: poll every 15 s;
  - app in the foreground: ongoing list every 60 s;
  - app in the background: a foreground service with an ongoing notification,
    poll every 30 s, stop when the order ends or after 3 hours (the user can turn
    it off); then WorkManager every 15 minutes;
  - a notification when the state changes and when payment details appear.
- Driver on a map when `delivery.state == "enroute"` (`delivery.driver.user_state`).
- Payment details panel (PLAN.md §7):
  - amount to pay: `order_value + vat − discounts + (fare.client − delivery_subsidy)`,
    or without the fare part for free delivery;
  - PromptPay QR from the driver (`qr_pic_url`) with Save and Share;
  - bank transfer: bank, account number, name, each with Copy;
  - before the details exist: the web app's text;
  - cash: amount and "change for"; take-away and dine-in: "Pay at the shop".
- Payment slip upload: trace the web app's `uploadimage` request first; upload in
  WorkManager; then `PATCH transactions/{id} {data:{payment_receipt_pic}}`.
- "Open on Tuk website": the order page in the WebView with the login injected.

## Risks

- The order states come from the web code. A real order may show states that we
  have not seen. The log keeps every state change for review.
- Account numbers are shown in full on screen, but the log keeps only the last 4 digits.

## Draft owner test list

1. Place an order (phase 4). The order screen changes state without a refresh.
2. Put the app in the background. The ongoing notification shows the state.
3. When the driver is on the way, the map shows the driver.
4. Pay with the QR or bank details shown. Upload the slip. The order shows "Paid".
5. Place an order and cancel it while it waits for the shop.
