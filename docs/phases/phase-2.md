# Phase 2 – Cart and pricing

Status: **built, waiting for the owner's device test**.

## Progress

- Done: the web price code is traced (docs/pricing.md), including all package codes
  (`r_`, `p_`, `f_`, `thai…`; `apple`, `elderberry`, `fig`, `durian` have no rules).
- Done: parity tool `tools/price-parity/` and fixtures `core/pricing/src/test/resources/parity/cases.json`
  (1098 cases).
- Done: `core:pricing` port (`Pricing.quote`, `CartPricing`). All parity cases pass.
- Done: web option rules in `core:domain` (`exactly` on optional groups, `condition`,
  `max_count`, free gift, a quantity per option). Owner decisions: docs/pricing.md §21.
- Done: cart (`core:domain` `CartRules`, `core:data` `CartStore`), menu-change check
  on each menu load, `CartQuotes` (the cart priced with `core:pricing` each minute).
- Done: screens: item sheet (conditional groups, option quantities, item limits,
  exact price), "View cart" bar (shop menu and main tabs), cart screen (lines,
  quantities, order type, order note, subtotal, discount, VAT, delivery, total,
  free-delivery hint, minimum order, shop closed, web-only note).

## Decisions made

| Decision | Choice | Why |
|---|---|---|
| Cart storage | One JSON file (`files/cart/cart.json`), atomic write. **Not Room.** | One small cart. A file needs no schema migrations. |
| Cart screen design | Built directly in the Lorikeet design, no mockup first. | The owner reviews on the device; changes are cheap. |
| Lines with options | A quantity > 1 is allowed (the web app always uses 1). | The amounts are linear (docs/pricing.md §21). |
| Lines with a note | Do not merge with other lines (the web app merges, last note wins). | The note belongs to its line. |
| Delivery fee in the cart | "At checkout", because there is no address before checkout (phase 4). | The fare depends on the distance. |
| Clock for peak and cash hours | Asia/Bangkok, not the device time zone. | The shops are in Chiang Mai. |

## Known gaps

- The menu list shows item prices without the shop's `visual_discount` (the web app shows a
  higher price with a larger discount; the net price is the same, rounding can differ by ฿1).
  The item sheet and the cart use `core:pricing`, so their amounts are exact.
- `UserDeliveryOptions` (the user's own remit and extra distance) are not read yet.
  They need the login (phase 3).

## Goal

The user can put items with options into a cart, and the app calculates every
amount exactly like the Tuk web app. Nothing is sent to Tuk in this phase.

**Done when** (PLAN.md §8): the unit tests give the same numbers as the web app's
own price code for every recorded menu and basket.

## Scope

- `core:pricing` (pure Kotlin, no Android):
  - item price with discount (percent or number), option prices (with the
    item's discount, like the web app), quantity;
  - subtotal (`order_value`), VAT for shops with `vat`, take-away and dine-in
    discounts;
  - delivery fare from the fleet `pricing_array`, `surge`, `remit_amount`,
    maximum distance (peak hours 17:00–18:59);
  - package code `r_x_y`: Tuk commission (`remit`) and delivery subsidy, free
    delivery threshold (api-reference §7);
  - `min_order` for delivery; the ฿1000 rule (`delayed`, 26 minutes).
- Option rules: `core:domain` `OptionChoice` exists (phase 1). Add
  `exactly` / `up_to` quantity rules with `allow_multiple` (a quantity per option).
- Cart (PLAN.md §5.7):
  - one cart for one shop; the 60-minute rule when the user adds from another shop;
  - a line per item with options (`id2`), lines without options merge;
  - saved in one JSON file (see "Decisions made");
  - each line keeps a copy of the item and option data and the menu version;
    after a menu refresh, the app checks each line (removed, sold out, price
    changed) and shows the changes.
- Screens (design first, then build; docs/design.md):
  - the item sheet's "Add to cart" works;
  - "View cart" bar on the shop menu;
  - cart screen with the order type choice (delivery / take-away / dine-in) and
    the price breakdown.
- Trace the other package codes in the web code (`p_`, `f_`, `thai`, and the new
  `apple`, `elderberry`, `fig`, `durian`). Done: all are supported.

## Parity tests

A manual tool (`tools/price-parity/`) runs the web app's own price functions (copied
as text from the bundles `shop-profile~1c39816d`, `app~50b71177` and others) in Node on the
recorded menus and on generated baskets. It saves the results as fixtures. The
Kotlin tests must give the same numbers. The tool is run by hand, like the API
probe; the tests only read the fixtures.

## Draft owner test list

1. Add items with and without options from one shop. The cart shows each line
   and the right subtotal.
2. Add an item from another shop. The app asks to start a new cart.
3. Change quantities and remove lines.
4. Close and open the app. The cart is still there.
5. Compare the cart total with tukapp.co for the same items (without ordering).
   Try a take-away order at a shop with a take-away discount, and a shop with VAT.
6. An item with a "choose up to N" group with quantities: the quantities stop at N.
7. Share the logs. Look for `cart add`, `cart quote` and `cart line_changed`.
