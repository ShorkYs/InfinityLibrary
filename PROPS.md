# Room prop areas

Version 2.2 separates the **room shell** from its **decoration**. You can use a single
standard room and fill selected interior areas differently each time it is generated.
Props are reusable rectangular block templates; areas are placeholders that accept any
matching prop, or a combination of smaller matching props.

## Try it immediately

1. Stop the server, replace the old plugin JAR with version 2.2, and restart.
2. Use `/il props` or **Room props** in the administrator main menu.
3. Use `/il baseroom builtin_modular` to select the supplied standard room and discard
   previous future-room reservations. Existing built rooms stay unchanged.
4. Walk to unexpanded exits. New standard rooms use four prop areas, with random shelves,
   benches, tables, plants and lecterns. Their central paths remain clear.

On a fresh install the modular room is also the starting room. An existing world keeps
its old start until you deliberately reset it. `/il baseroom <id>` also selects that start
for the next reset. Reset removes generated rooms, so back up your world first.

## Dimensions: X × Y × Z

Dimensions are Minecraft's block axes, including both selected corners:
**X = east/west, Y = height, Z = north/south**.

- A `2×5×1` area is two blocks wide, five blocks tall, one block deep. In **stack** mode
  it can fit five `2×1×1` props, or one `2×3×1` plus one `2×2×1` prop.
- A horizontal five-unit strip with a one-block height is `2×1×5`. In **floor** mode it
  can fit five `2×1×1` props, or `2×1×3` plus `2×1×2` props.
- Props can combine across all three axes when stacking is enabled. Horizontal rotation
  swaps X/Z as needed; props are never tilted sideways or upside down.

Whole prop bounding boxes reserve space, including air inside them. This lets you include
intentional clearance in a captured prop and prevents another prop from intersecting it.

## Capture your props first

Build each prop outside the active generated library. Use the normal selection wand or
`/il pos1` and `/il pos2` to select opposite corners of the **prop itself**, then run:

```text
/il saveprop shelf_small library 3
```

This stores the prop under ID `shelf_small`, category `library`, relative weight `3`.
Capture differently sized versions with different IDs in the same category. The filler
can reuse one prop many times, so you do not need five copies of a one-unit template.
Capture props before generating rooms that depend on them: a room generated while its
category has no matching props will stay empty, because its layout is saved.

`/il listprops [category]` lists the catalog; `/il propinfo <id>` shows details.
`/il propweight <id> 0` disables future selection. Positive weights influence relative
selection, rather than being percentages. Catalog edits affect future decoration only.
You can recapture a custom ID to replace it or use `/il deleteprop <id>`. Built-in IDs
cannot be deleted/overwritten by capture; disable them with weight zero instead.

Props save **block states**, including facing, stairs and slabs. They do not capture
entities or full block-entity contents/NBT: chest items, custom sign text and armor stands
are not included. Generated chiseled bookshelves use the plugin's existing book population
logic; generated lecterns open the library menu. Bench props are decorative blocks, not
an automatic seat registration system.

## Mark areas in your standard room

1. Build one room with empty interiors and usable doorways.
2. Clear previous staged connections/areas with `/il clearconnections` and
   `/il clearpropareas`, then select the **whole room**, including floor, walls and ceiling.
3. Stage each exit with the connection wand as usual.
4. Get `/il propwand` and choose the area's settings:

```text
/il propmode library 15 furniture rotate floor
```

5. Holding the amethyst-shard prop wand, click two opposite **interior** corners per area.
   Use temporary blocks to mark corners if you need to select air. Each two-click selection
   stages one area, named `furniture_1`, `furniture_2`, and so on.
6. Repeat with different sizes or settings, then save the room:

```text
/il saveroom standard FILLER
/il baseroom standard
```

The selection is room-local, so the same areas rotate/mirror with each room instance.
Placeholder markers and other blocks within these areas are omitted from capture and
cleared during generation, then replaced by the chosen props. The physical authoring
room is not automatically cleared.

**Settings:**

```text
/il propmode <category|*> [gapPercent] [prefix] [rotate|fixed] [floor|stack]
```

- **category:** only props in that category; `*` allows all enabled categories.
- **gapPercent:** `0` requests dense filling where possible; `100` leaves the area empty.
  Intermediate values introduce random gaps. It is an approximate packing preference,
  not an exact percentage of the volume.
- **prefix:** the name prefix for staged areas.
- **rotate:** allow 90-degree horizontal rotations that fit the bounds.
- **fixed:** preserve captured facing relative to the room; useful for wall shelves.
- **floor:** all props anchor at the area's lowest Y. Tall props must still fit its height.
  This is the command default and the standard room's mode, preventing furniture piles.
- **stack:** permits multiple vertical layers, using occupied prop bounding boxes as support.
  Packing support is a box rule, not a simulation of block solidity or gravity.

For your vertical `2×5×1` example, use `fixed stack`. For a horizontal `2×1×5` strip,
use `fixed floor`. Capture the `2×1`, `2×2` and `2×3` pieces along the same intended axis.

## Add areas to an existing saved template

Select the physical authoring room again with the **same dimensions** as its saved
version. Mark the areas with the prop wand and run:

```text
/il applypropareas standard
/il listpropareas standard
```

The areas are added to the saved template for **future instances**; existing room
snapshots stay unchanged. Wait until construction finishes before editing saved areas.
Overlapping IDs/areas are rejected. Choose a different prefix if you need more IDs.

Use `/il removeproparea <roomId> <areaId>` to remove one area from future instances.
Removing an area leaves that part of the template empty; it does not restore omitted
placeholder blocks. `/il clearpropareas` discards staging and the unfinished two-click
selection, without deleting saved room areas. Changing the whole-room selection or
logging out discards stale staging so it cannot be applied to a different room by accident.

## Protection, randomness and persistence

Areas must stay inside the floor/walls/ceiling. They cannot overlap each other, a doorway,
or its first two inward blocks of entrance clearance. This protects exits; it does not
prove a full walkable route through an arbitrary custom room. Keep your intended
hallways outside prop areas. The supplied standard room has a clear central cross.

The packer uses seeded, randomized fitting with several bounded attempts. It can combine
props, reuse templates, rotate them, and leave intentional gaps. It favors useful coverage
without overlaps; it is not an exhaustive exact-cover solver. Awkward dimensions, missing
props or an exhausted work budget can leave space empty even with gap setting zero.

Each room's resolved prop blocks and area bounds are saved in `generated.yml` **before
placement**. Changing/deleting templates, changing weights or reloading will not reroll
those blocks. Pending construction replays its saved decoration, including block facing.
The base/variation recovery behavior outside prop areas otherwise remains as in 2.1.

Data files:

- `props.yml`: reusable templates, categories and weights.
- `rooms.yml`: prop areas on room templates.
- `generated.yml`: resolved per-instance decoration and future-room reservations.

`/il backup` includes these YAML files, but not world region files. Restore a matching
plugin-data/world backup together while the server is stopped.

## Limits and performance

A prop or area supports up to **8,192 bounding-box cells**. A room supports at most
**32 areas** and **32,768 combined area cells**. The catalog supports up to **256 props**,
including built-ins. These limits bound capture, snapshots and placement queues.

```yaml
props:
  packing-attempts: 8
  cell-check-budget: 200000
```

Attempts are capped at 32; collision/support cell checks per area are capped at 1,000,000
and shared across attempts. The usual `generation.placement-blocks-per-tick` budget also
applies to clearing placeholders and placing props. More areas and larger snapshots use
more disk space and server work; test with your intended room sizes and player load.

## Keep mixed room designs if desired

`/il baseroom all` restores selection across the eligible room catalog. Areas work in
those templates too. The new modular default applies to new expansion; upgrading does
not redecorate or remove completed rooms. Old saved reservations for a different base
are discarded when single-base mode is loaded.
