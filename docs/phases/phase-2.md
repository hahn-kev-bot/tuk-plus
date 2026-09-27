# Phase 2 – Cart and pricing

Status: **not started**.

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
  - saved in Room (first use of Room in the app);
  - each line keeps a copy of the item and option data and the menu version;
    after a menu refresh, the app checks each line (removed, sold out, price
    changed) and shows the changes.
- Screens (design first, then build; docs/design.md):
  - the item sheet's "Add to cart" works;
  - "View cart" bar on the shop menu;
  - cart screen with the order type choice (delivery / take-away / dine-in) and
    the price breakdown.
- Trace the other package codes in the web code (`p_`, `f_`, `thai`, and the new
  `apple`, `elderberry`, `fig`, `durian`). Until then, those shops offer only
  the web checkout fallback (phase 4).

## Parity tests

A manual tool runs the web app's own price functions (from the bundles
`shop-profile~1c39816d` and `app~50b71177`) in a headless browser on the
recorded menus and on generated baskets. It saves the results as fixtures. The
Kotlin tests must give the same numbers. The tool is run by hand, like the API
probe; the tests only read the fixtures.

## Decisions to make at the start

- Cart screen design (mockup for the owner, like phase 1).
- Room schema for the cart.

## Draft owner test list

1. Add items with and without options from one shop. The cart shows each line
   and the right subtotal.
2. Add an item from another shop. The app asks to start a new cart.
3. Change quantities and remove lines.
4. Close and open the app. The cart is still there.
5. Compare the cart total with tukapp.co for the same items (without ordering).
