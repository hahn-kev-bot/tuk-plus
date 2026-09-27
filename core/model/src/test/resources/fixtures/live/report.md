
# API probe report

- Checks: 114, failed: 0, time: 12.614 s

## Notes

- menu โครเก็ต Smullig Croquette (1ae438a5): menu has no items
- page Eat has no list_title

## Response times (ms)

| Endpoint | Calls | Median | Max |
|---|---|---|---|
| `autocomplete` | 2 | 283 | 283 |
| `blob/active (page)` | 14 | 292 | 533 |
| `businesses?type=eatery` | 1 | 358 | 358 |
| `businesses?type=new_shops` | 1 | 281 | 281 |
| `delivery/price_check` | 1 | 280 | 280 |
| `directions` | 1 | 437 | 437 |
| `helpers/shop_open` | 30 | 289 | 301 |
| `recommendations/foryou` | 1 | 281 | 281 |
| `search/menu_items` | 1 | 286 | 286 |
| `short_link/{handle}` | 1 | 284 | 284 |
| `version` | 1 | 1257 | 1257 |
| `workflows/{businessId}` | 30 | 292 | 328 |
| `workflows?commerce_delivery` | 30 | 291 | 315 |

## Fields that our models do not read

- business.data: desktop_banner_pic_url (130), normal_link (28), owes (2), price_info (1)
- menu item: languages (1593), zh (131), null (114), es (82), color (49), contain_image (21), notes (5), by_weight (2)
- option group: instruction (57), original_id (41), parent_item_id (16), es (5)
- workflow.data: cash (118), preferred_channels (61), delivery (51), commerce_agent_message (26), multiple_locations (24), menu (11), alternative_shop (10), order_options (8), agent_options (5), tuk_express (5), info (3), operating_hours (3), phone_number (3), stops (3), takeaway_options (2), ui (1), free_delivery_polygon (1), visual_discount (1), free_delivery (1), max_remit (1), digital_payment (1), free_delivery_distance (1), prep_time_selector (1), home_tab (1)

## Facts

- backend version: `3.7058f` (1)
- delivery fleet states: `active` (30)
- delivery_options.type: `delayed` (143), `(none)` (87), `normal` (19), `immediate` (2)
- express fleets: `Chiang Mai Express Delivery` (195), `Chiang Mai South Express Delivery` (43), `Chiang Mai North Express Delivery` (12), `self` (1)
- fulfilment_options: `delivery` (203), `take-away` (197), `dine-in` (167)
- has fallback fleet: `yes` (11)
- hours text shapes: `9999-9999` (887), `(null)` (357), `99:99-99:99` (188), `99.99-99.99` (151), `(empty)` (37), `9999-99.99` (35), `9999-99:99` (34), `99:99-99.99` (29), `9999-9999, 9999-9999` (12), `99:99-9999` (10), `9999-9999, 99.99-99.99` (5), `99.99-9999,99.99-99.99` (5), `99.99-99.99,99.99-99.99` (4), `99.99-9999` (3), `99.99-99:99` (3), `99:99 -99:99` (3), `99:99- 99:99` (1), `9999-9999,9999-9999` (1), `99.99 -99.99` (1), `9999-9999 ` (1), `99:99-99:99, 99:99-99.99` (1), `9999-9999 , 9999-9999` (1), `99:99-99:99, 99:99-99:99` (1), `99.99-99.99 ` (1)
- hours_type: `selected-hours` (252), `(none)` (3), `always-open` (1)
- item discount types: `(none)` (1588), `percent` (4), `number` (2)
- item schedule values: `["fri"]` (1)
- option multiple_constraint: `none` (97), `(none)` (34), `exactly` (9)
- option select: `single` (108), `multiple` (32)
- package (fruit) prefixes: `r` (220), `p` (12), `f` (10), `apple` (5), `elderberry` (2), `fig` (1), `durian` (1)
- page blob types: `list` (101), `list_title` (9)
- payment_options: `cash` (126)
- price text shapes: `999` (1047), `99` (476), `9` (50), `9999` (20), `(null)` (1)
- pricing_array lengths: `28` (25), `27` (5)
- shop_open answers: `Closed(reason=Shop is not currently open (Shop is closed today))` (14), `Open` (12), `Closed(reason=Shop is not currently open (Today's Open Hours: 9999-9999))` (2), `Closed(reason=Shop is closed for holiday (9999-99-99T99:99:99.999Z))` (1), `Closed(reason=Shop is not currently open (Today's Open Hours: 99:99-99.99))` (1)
- shops with VAT: `count` (11)
- shops with min_order: `count` (16)
- shops without Commerce workflow: `count` (5)
- tile tag kinds: `shop handle` (50), `eat filter` (25), `plain text` (19), `search` (3), `external link` (3), `preset *for-you` (1)
- workflow types per shop: `Commerce` (30)
