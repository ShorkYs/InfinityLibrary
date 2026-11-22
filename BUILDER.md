# Vanilla builder tools — 4.0

Paper 26.3 / Java 25, `infinitylibrary.admin`. No client mod required. `/il builder` opens the separate administrator menu.

## Capture a room

1. Choose **Selection tools → Room**. Left-click the first corner and right-click the opposite corner of the complete room. Bounds are inclusive. Wands use an available hotbar slot without replacing an ordinary held item.
2. Open **Door settings**, choose the ID prefix and floor index, and capture each doorway rectangle with left/right clicks. Its boundary determines its facing; dimensions and floor indices must match the connecting template. Connections share the same wall plane during generation.
3. Open **Decoration settings**, choose category and EXACT/PACKED, and mark target boxes. EXACT chooses one decoration of matching dimensions/category. PACKED can combine smaller props with optional gaps. Capture catalog props using `/il saveprop <id> <category> [weight]` or `/il savedeco`.
4. **Room Diversity** configures an area ID, variant and weight. Select a target region, then an equally sized source build. Use **Add variant** for additional alternatives and **Variant door** to capture a connection in a variant. Different variants can therefore expose different doors, frozen with each generated instance.
5. Shelf, table and seat tools capture room-local regions. Settings choose the category. These marks transform with the room and support shelf categories and reading interactions.
6. Return to builder home → **Save selected room**, enter an ID and choose FILLER, READING, RENTABLE, PUBLIC_WRITING or STARTING. Replacing an unused ID requires confirmation and version checks; built templates remain protected. Saving STARTING selects the entrance for the next intentional reset, not for rebuilding the current world.

**Undo**, **Redo** and **Cancel** operate on staged selections. `/il builder undo|redo|cancel` also work. **Apply** commits regions to the source build; saving writes the template. Loading a template's areas requires selected bounds of exactly the same dimensions and does not paste the structure. Disconnect/world changes clear staging.

## Catalog, preview and administration

**Room catalog** opens template details, roles, weights, start/base choice, archive/removal and room preview. `/il preview <id>` shows temporary blocks only to you; rotate, mirror, reroll decorations/Diversity or choose a specific variant, then stop to restore your view. It requires loaded chunks, observes size/distance limits and never edits real blocks. Neighbor rendering in the old Studio is part of the shelved companion, not this vanilla preview.

Builder home also offers pause/resume, nearby generation diagnostics, YAML backup and loaded-bookshelf refresh. `/il diagnose`, `/il status`, `/il backup`, `/il shelves` remain available. A YAML backup does not include world chunks. `/il reset confirm` is an explicit destructive reset and first creates a data backup.

See [GENERATION.md](GENERATION.md), [ROOMS.md](ROOMS.md), [PROPS.md](PROPS.md) and [DIVERSITY.md](DIVERSITY.md) for detailed geometry and command options. [BUILDER-LEGACY.md](BUILDER-LEGACY.md) and [STUDIO.md](STUDIO.md) are archived client-mod references; they do not describe the current default workflow.
