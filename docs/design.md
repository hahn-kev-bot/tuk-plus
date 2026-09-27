# Tuk plus design

The app does not copy the Tuk web app's look. It uses Material You (Material 3,
with the Expressive shapes). The mockup is the "Tuk plus redesign" design page
(private to the owner): https://claude.ai/artifact/NLToekJrXxshZxYSi2RSsy

Status: mockup under review. The phase 1 screens still use the default Material 3
theme. The new design goes into the app after the owner approves the mockup.

## Decisions

| Topic | Decision |
|---|---|
| Colors | A vibrant tropical-bird palette. The owner picks one of three options on the design page (A electric kingfisher, B rainbow lorikeet, C toucan). Not green (too much like Grab), not Tuk yellow. The tokens below are the earlier, calmer kingfisher palette and will be replaced. |
| Dynamic color | Off. The app always uses its own palette, not the Android wallpaper colors. |
| Shapes | A mix of shapes: round-cornered squares, circles, "leaf" (two large and two small corners) and "arch" (round top). The shapes vary on cuisine tiles, shop pictures and menu item pictures. |
| Type | Bricolage Grotesque for headings, Figtree for text. |
| Navigation | Bottom navigation bar: Home, Shops, Orders, Account. |
| Order type | Delivery / take-away / dine-in is chosen in the cart, not on the shop menu. The shop page only says which types the shop offers. |
| Menu layout | The user switches between a list view and a photo grid view on the shop page. The app remembers the choice. |
| Data age | Each screen shows the age of its data in a small chip ("Updated 3 min ago"). A tap refreshes. |

## Color tokens (light)

| Role | Color |
|---|---|
| primary / onPrimary | `#0B5FA8` / `#FFFFFF` |
| primaryContainer / onPrimaryContainer | `#D3E4FF` / `#001C38` |
| secondaryContainer / onSecondaryContainer | `#D7E3F8` / `#101C2B` |
| tertiary (accent) | `#9C4400` |
| tertiaryContainer / onTertiaryContainer | `#FFDBC8` / `#331200` |
| surface / onSurface | `#F8F9FF` / `#191C20` |
| surfaceContainerLow / Container / High / Highest | `#F2F3FA` / `#ECEEF4` / `#E6E8EE` / `#E1E2E8` |
| onSurfaceVariant | `#43474E` |
| outline / outlineVariant | `#73777F` / `#C3C6CF` |
| error | `#BA1A1A` |

The dark scheme comes from the same seed when the design goes into the app.

## Next

1. The owner picks the palette. Then the design is locked.
2. Build the design into the app (Home, Shop menu with list and grid views, Item sheet).
3. Other screens (Shops list, cart, checkout, orders, account, dark theme) are designed later.
