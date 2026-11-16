# Decoration areas — exact size and category

Starting in **2.4**, a decoration area chooses **one full-size decoration** from the
matching category. Smaller templates are not combined or used as fallback. A `3×3×1`
area with category `tables` only accepts a `3×3×1` table decoration, or a horizontally
rotated template whose full transformed dimensions match when rotation is enabled.

Dimensions use **Minecraft X × Y × Z**: east/west × height × north/south. Matching is based
on the selected capture box, including intentional air, rather than the number of solid
blocks in it. Nothing automatically pads a smaller template to make it eligible.

## Capture decorations

Build a complete decoration layout outside the active library. Select its full bounding
box with the normal room-selection tools (`/il pos1` and `/il pos2` or the selection wand),
then capture it:

```text
/il savedeco table_large tables 2
```

`/il saveprop` remains an alias for the same operation. The ID is `table_large`, the category
is `tables`, and relative selection weight is `2`. Capture other designs with **the same
box dimensions and category** to create that size/category's eligible list.

Use `/il listdecos [category]` (also `/il listprops`) and `/il propinfo <id>` to inspect the
catalog. `/il propweight <id> 0` disables an entry for future rooms. `/il deleteprop <id>`
removes a custom template; disable built-ins with weight zero instead. Source builds can
be removed after capture, and edits/deletion do not reroll existing room snapshots.

## Select the area, then choose its category

1. Select the **whole authoring room**, including its shell, and stage its exits as usual.
2. Get `/il decowand` (`/il propwand` also works).
3. Click two opposite interior corners of the decoration placeholder.
4. A native text dialog shows the exact X,Y,Z size and categories that currently have a
   matching full-size decoration. Enter the category and choose whether horizontal
   rotations are allowed, then click **Use category**.
5. Save the room or apply the staged area:

```text
/il saveroom standard FILLER
```

or, for an existing template with the same selected room dimensions:

```text
/il applypropareas standard
```

If you close the category dialog, you can still finish that pending area in chat:

```text
/il decocategory tables
```

Saving/applying is blocked until category selection is complete, so it cannot silently
omit the placeholder you just selected. A missing category/size match is allowed for
future authoring, with a warning; that area stays empty until a matching entry exists.
It never falls back to another category or smaller template. `*` explicitly permits any
category, while still requiring the full size to match.

Categories use lowercase letters, digits, underscores and hyphens. Temporary corner
markers inside the placeholder are omitted from room capture and cleared in generated
rooms. The physical authoring build is not automatically cleared.

## Settings and existing templates

`/il propmode` supplies defaults for the next selection/dialog:

```text
/il propmode tables 0 furniture fixed floor exact
```

The parameters are `<category> [emptyPercent] [prefix] [rotate|fixed] [floor|stack]
[exact|packed]`. In EXACT mode, emptyPercent is the chance to leave the **entire area**
empty: `0` chooses a matching decoration whenever one is available; `100` leaves it blank.
The floor/stack setting has no effect on a single exact-size decoration.

Inspect or edit an existing saved area without recapturing it:

```text
/il listpropareas standard
/il setdecocategory standard furniture_1 tables
/il decofill standard furniture_1 exact
/il removeproparea standard furniture_1
```

These edits affect future instances. They require queued construction to finish first.
`/il clearpropareas` discards temporary staging and the pending category selection.

Rotation never changes height. With rotation off, all three captured dimensions must
match directly. With rotation on, a `1×3×3` decoration may turn into `3×3×1`, but a `3×1×3`
decoration is rejected because its height is different.

## Standard room and compatibility

The standard `builtin_modular` room has four `3×3×3` placeholders. New full-size shelf,
reading and garden corner arrangements match those slots. The older small props remain
in the catalog for explicitly sized slots or packed mode. Use `/il props` or the main
menu's **Room props** control for the browser/wand; `/il baseroom <id>` chooses expansion.
See [ROOMS.md](ROOMS.md) for STARTING templates and default-room removal.

Areas without a stored fill mode now default to EXACT for **new instances**. Existing
completed or pending per-room decoration snapshots retain their saved block layouts.
After upgrading, custom old areas may stay empty until you capture their exact-size
variants and category, or explicitly switch that area to packed mode.

## Optional legacy packing

If you deliberately want the previous behavior of combining several smaller pieces:

```text
/il decofill standard furniture_1 packed
```

PACKED mode uses the bounded, category-filtered randomized packer. It can combine `3+2`
pieces across a five-unit strip, reuse templates, leave gaps, and optionally stack. Floor
mode anchors all pieces at the area's lowest Y; stack mode uses occupied bounding boxes
as support, not actual block solidity/gravity. `props.packing-attempts` and
`props.cell-check-budget` apply only to PACKED mode. Exact selection ignores these budgets.

## Safety, limits and data

Decoration areas must remain inside floor/walls/ceiling and cannot overlap each other,
Diversity targets, doorways or entrance clearance. Preserve intended paths outside the
areas. Props capture block states, not full NBT/entities: chest contents, sign text and
armor stands are not copied. Generated chiseled bookshelves use the book collection;
lecterns open the library menu. Bench blocks do not automatically register seats.

A decoration or area is limited to 8,192 bounding cells; rooms support 32 decoration areas
and 32,768 combined cells. The catalog limit is 256 entries including built-ins. Packed
attempts are capped at 32 and collision checks at 1,000,000 per area. Clearing and placement
use the normal per-tick block budget.

`props.yml` stores templates/categories/weights, `rooms.yml` stores areas and fill mode,
and `generated.yml` stores resolved per-instance decoration. Plugin ZIP backups contain
YAML only; restore matching world/plugin backups while stopped. New layout choices are
saved before placement and survive reloads without rerolling.
