# Tuk plus design

The app does not copy the Tuk web app's look. It uses Material You (Material 3,
with the Expressive shapes). The mockup is the "Tuk plus redesign" design page
(private to the owner): https://claude.ai/artifact/NLToekJrXxshZxYSi2RSsy

Status: locked and built into the app (Home, Shops list, Search, Shop menu with
list and grid views, Item sheet, bottom navigation). The four phone screens on the
design page still show the earlier Kingfisher colors; the palette board shows the
chosen Lorikeet colors.

## Decisions

| Topic | Decision |
|---|---|
| Colors | "Rainbow lorikeet": electric violet-blue main color, lime for selected chips and the navigation indicator, orange for badges. Chosen from three vibrant tropical-bird options. Not green (too much like Grab), not Tuk yellow. |
| Dynamic color | Off. The app always uses its own palette, not the Android wallpaper colors. |
| Shapes | A mix of shapes: round-cornered squares, circles, "leaf" (two large and two small corners) and "arch" (round top). The shapes vary on cuisine tiles, shop pictures and menu item pictures. |
| Type | Bricolage Grotesque for headings, Figtree for text. |
| Navigation | Bottom navigation bar: Home, Shops, Orders, Account. |
| Order type | Delivery / take-away / dine-in is chosen in the cart, not on the shop menu. The shop page only says which types the shop offers. |
| Menu layout | The user switches between a list view and a photo grid view on the shop page. The app remembers the choice. |
| Data age | Each screen shows the age of its data in a small chip ("Updated 3 min ago"). A tap refreshes. |

## Color tokens

Source: `app/src/main/kotlin/app/hahn/tukplus/ui/theme/Theme.kt`.

| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | `#4B2BE8` / `#FFFFFF` | `#C6BFFF` / `#2A0B9E` |
| primaryContainer / onPrimaryContainer | `#E4DFFF` / `#170065` | `#4B2BE8` / `#FFFFFF` |
| secondaryContainer / onSecondaryContainer (lime) | `#B8F23A` / `#1B2600` | `#B8F23A` / `#1B2600` |
| tertiary (orange text) | `#A33A00` | `#FFB596` |
| tertiaryContainer / onTertiaryContainer (orange badges) | `#FF6B2C` / `#2A0B00` | `#FF6B2C` / `#2A0B00` |
| surface / onSurface | `#FBF8FF` / `#1B1A24` | `#13121B` / `#E5E1EE` |
| surfaceContainerLow / Container / High / Highest | `#F5F2FC` / `#F0ECFA` / `#EAE6F4` / `#E4E1EE` | `#1B1A24` / `#1F1E28` / `#2A2933` / `#35343E` |
| onSurfaceVariant | `#474554` | `#C9C4D6` |
| outline / outlineVariant | `#787586` / `#C9C4D6` | `#928F9F` / `#474554` |
| error | `#BA1A1A` | `#FFB4AB` |

Fonts are bundled in `app/src/main/res/font/` (variable fonts, SIL Open Font
License; see `docs/licenses/`). Picture shapes: `ui/theme/PictureShapes.kt`.
Icons: `ui/theme/TukIcons.kt` (line icons from SVG paths, no icon library).

## Next

- Other screens (cart with the order type choice, checkout, orders, account)
  are designed when their phases start.
