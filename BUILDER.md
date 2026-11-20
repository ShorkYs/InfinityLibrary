> **3.1:** B now opens the visual Studio workspace. See [STUDIO.md](STUDIO.md). The wand/settings/live-preview screen described here is available through **Wands / live**. Install matching 3.1 plugin and companion JARs.

# Infinity Library Builder 3.0

## Installation

The server runs Paper 26.3 and Java 25 with `InfinityLibraryPlugin-3.0.0.jar` in `plugins/`. Keep the existing plugin data folder when updating; back up worlds/data before upgrading.

The optional **client-only** companion runs Minecraft 26.3, Java 25, Fabric Loader 0.20.1 or newer compatible version, and Fabric API 0.166.2+26.3. Put Fabric API and `InfinityLibraryBuilder-3.0.0.jar` in the client's `mods/` folder. Do not put the companion JAR in the server plugins folder. Ordinary readers can use unmodified Minecraft.

An operator or player with `infinitylibrary.admin` presses **B** to open the editor. **V** toggles outlines; both keys are configurable in Controls. Without the mod, `/il builder` supplies the tools, actionbar prompts and corner particles.

## Building a room

1. Open Tools and choose Room. Left-click the first outside corner of your build; right-click the opposite outside corner. Coordinates are inclusive.
2. Choose Connection. Select each doorway rectangle with the same two-click sequence. Keep compatible dimensions, facing and floor indices. Existing connection commands remain available.
3. Choose Decoration, configure category and EXACT or PACKED in Settings, then select a target box. EXACT uses decorations with matching dimensions/category; PACKED permits combinations and gaps.
4. Choose Diversity. Configure area/variant/weight. Select the target box, then an equally sized source box containing the alternative build. Add further variants in Settings. Variant door wand selects connections for that source variant, preserving variant-specific topology.
5. Shelf, Table and Seat tools select category/reading regions inside the room. These regions are saved with the template and transform with its generated instances. Apply commits marks to the source build without saving a template.
6. In Rooms, enter an ID, select a type (including STARTING), and Save. Replacing an existing unused template requires the current version. Built templates retain existing replacement protection. Configure the starting-room selection through the existing `/il startroom` workflow; merely saving a STARTING template does not change an existing library.

Left-click restarts the current corner pair. Right-click requires a first corner. Undo/Redo covers staged room bounds and areas; Cancel drops only unfinished corners. Save/Apply commit staging and clear undo history. World changes and disconnects clear staging. Tools use an empty hotbar slot or replace an existing library wand, preserving ordinary held items.

Room browser **Load areas into selection** requires an already selected build with the same dimensions. This edits metadata around a physical build; it does not paste or relocate the saved structure. Preview sends temporary blocks only to your client, supports rotate/mirror/reroll and explicit Diversity variants, and expires under the configured preview limits. Stop preview to restore the real blocks.

The companion displays authoritative server boxes for room bounds, doors, decoration targets, Diversity targets/source, reading/shelf regions and previews. Optional X-ray makes boxes visible through blocks. It never supplies arbitrary world edits: normal block interactions, permissions, world/reach checks, session/revision checks and template version checks are enforced on the server.

## Generator

The single generator retains saved-template placement and all previous room features. It builds candidate domains for open connections across a nearby neighborhood, prioritizes constrained sockets, forward-checks conflicting candidates, and compares continuation quality. A fixed node limit bounds the search; it is a local planner, not a guarantee of a globally optimal infinite map. Seeded weights determine alternative ordering. Actual placement still checks terrain and nearby viable exits before committing queued blocks.

`generation.planning.neighborhood-radius` defaults to 48, `search-nodes` to 512 and `seed` to 20261120. Existing candidate/topology/world budgets remain effective. Increase budgets cautiously on a test server. `generation.mode: unified` is the sole mode; legacy `templates` commands are accepted as a compatibility alias, and experimental `procedural` switching is rejected. Old generated placements remain; stale cached plans are rebuilt once.

## Build

Server: `./gradlew build` in the repository root. Client: `cd fabric-client && ./gradlew build`. Both require Java 25. The client project uses Gradle 9.8.2 and Fabric Loom 1.19.4; the server wrapper remains independent.
