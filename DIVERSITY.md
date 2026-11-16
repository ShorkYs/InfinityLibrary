# Room Diversity

Version 2.4 lets one room template have alternative designs for selected parts. For example,
a room can use its original reading table, a captured garden corner, or an empty reading
area. All operations require administrator permission.

## Capture a target and its alternative

1. Build the room's **original/default design** and select the whole room as usual with
   `/il pos1` and `/il pos2` or the normal selection wand. Stage its exits if saving a new room.
2. Build an alternative elsewhere in the **same world**. Its selected bounding box must
   have exactly the same X, Y and Z dimensions as the target. Do not change the whole-room
   selection when moving to the alternative.
3. Get the prismarine-shard wand and name this target/alternative:

```text
/il diversitywand
/il diversitymode reading garden 1
```

4. With the Diversity wand, click **target corner 1, target corner 2** inside the room.
   The plugin displays the required X,Y,Z size.
5. Walk to your alternative and click **source corner 1, source corner 2**. The source
   can be outside the selected room. Both selections include their corner blocks.
6. Save a new template or apply the captured Diversity to an existing one:

```text
/il saveroom standard FILLER
```

or, for an existing saved authoring room with matching whole-room dimensions:

```text
/il applydiversity standard
```

Use `/il baseroom standard` if this should be your normal expanding-room template.
Room Diversity also works on STARTING templates; a newly generated entrance uses it
when you deliberately rebuild with `/il reset confirm`.

The four clicks must be in the order **target → source**. A source with the same volume
but different dimensions is rejected: `3×2×2` does not match `2×3×2`. On mismatch, the
correct target remains selected; retry the **two source corners**, without retargeting.
An incomplete capture cannot silently be saved/applied. `/il cleardiversity` discards
staging and any unfinished selection.

The main-menu **Room Diversity** button gives the wand. Room details also has a Diversity
control with the relevant load/apply commands. The older stick variation wand remains a
legacy feature; use the prismarine-shard Diversity wand for this paired-selection workflow.

## More alternatives for the same target

After the first completed capture:

```text
/il diversityvariant reading lounge 2
```

The target is already known, so select only the new source's **two corners**. Its full
size must still match. Repeat with new alternative IDs and then save/apply.

To edit an already saved room, select the physical authoring room with the same dimensions,
then load its Diversity definitions into staging:

```text
/il diversityload standard
/il diversityvariant reading lounge 2
```

Capture the source, then `/il applydiversity standard`. This merges alternatives by target
ID and variant ID; recapturing the same variant ID replaces its saved content. Applying
cannot silently retarget an existing ID to different bounds. Explicitly remove that target
first if you intend to move or resize it.

For an entirely empty alternative, without trying to click empty air:

```text
/il diversityempty reading empty 1
```

The target must already be captured or loaded into staging. Apply/save afterwards.

## Original design and weights

Each target has an original/default choice, initially weight **1**, and each alternative
has its captured relative weight, also **1** by default. With one weight-1 alternative,
original and alternative are equally eligible. Several alternatives are independent of
other targets in the same room.

To always use an enabled alternative rather than the original:

```text
/il diversitydefault standard reading 0
```

Set another nonnegative original weight to bring back the default design. A variant weight
of zero disables that alternative; recapture/re-add its ID with the desired weight and apply.
If the original and every alternative are disabled, the original is retained as a fallback.
Weights are relative values, not percentages or guaranteed distributions over a small sample.

Inspect or remove saved Diversity:

```text
/il listdiversity standard
/il removediversity standard reading lounge
/il removediversity standard reading
```

The first removal deletes one alternative; the second deletes the whole target for future
instances. The original room blocks are retained in the template, so removing a target
restores normal base-template behavior for that region in newly generated rooms.

## Placement and persistence

Selected alternatives replace the **whole target box**. The target is cleared first, so
air in the source removes leftover table/seat blocks instead of merging two designs.
Block facing follows the generated room's rotation/mirroring. Source builds can be removed
after capture; their locations are not live dependencies.

Target bounds, captured alternatives and weights are stored in `rooms.yml`. Each generated
room saves its chosen design and resolved local blocks in `generated.yml` before placement,
including when the original design was chosen. Reload/recovery reuses that snapshot rather
than rerolling or depending on a later-edited source definition. Existing completed rooms
retain their saved designs. Changes apply to **future room instances**.

Targets must stay within the whole-room bounds. They may include wall/floor panels, but
must either contain a complete doorway or stay clear of its entrance clearance. They cannot
overlap another Diversity target or overlap
a decoration area. Keep walking paths clear yourself; this is not a pathfinding guarantee.
Legacy variation blocks cannot overlap a new Diversity target. To migrate such a target:

```text
/il listvariations standard
/il removevariation standard old_variation_id
```

Then capture/apply Room Diversity. Removing legacy variations and editing saved Diversity
requires the construction queue to finish first. Capture includes **block states**, not full
block-entity NBT or entities: sign text, chest contents and armor stands are not copied.

## Bounds and staging

- Up to 16 Diversity targets per room, 8,192 cells per target.
- Up to 16 alternatives per target.
- Up to 16,384 target cells and 131,072 source bounding-box cells across all alternatives.
- Staging is temporary. Changing the whole-room selection invalidates it; logout discards it.
  Save/apply before leaving. Saved templates and generated snapshots persist normally.

Keep matching world/plugin backups before upgrades, resets or manual YAML edits. Earlier
plugin versions do not understand all 2.4 Diversity/snapshot fields; restore a matching
backup if downgrading. The real 26.3 client presentation and your custom builds should be
checked before using the feature on a valuable world.

## Variant-only connection points (2.5)

After capturing a variant, `/il diversityconnection <area> <variant> <direction> [floor] [id]`
gives a separate connection wand. Click two opening corners on the captured source, then
save/apply Diversity. When editing a loaded definition, click on the target instead.
The variant's openings are included in planning before blocks are placed, and the chosen
connections persist with the instance. Alternatives replace normal doors fully inside their
target; default choices retain them. See [GENERATION.md](GENERATION.md) for the complete workflow.
