# Infinity Library 3.1

A procedural library plugin for **Paper Minecraft Java Edition 26.3**, requiring
**Java 25**. An optional Fabric builder mod adds editing controls and world-space selection outlines; normal players need no client mod.

**[Download plugin JAR](downloads/InfinityLibraryPlugin-3.1.0.jar?raw=true)** ·
**[Download Fabric companion](downloads/InfinityLibraryBuilder-3.1.0.jar?raw=true)** ·
**[Download source ZIP](downloads/InfinityLibrary-3.1.0-source.zip?raw=true)**

If GitHub opens a file preview, use **Download raw file**. Put the plugin JAR in your
server's `plugins/` folder. Stop your server before replacing an older plugin JAR,
remove that old JAR, and keep the plugin data folder. Back up your library world first.

## Visual Studio (new in 3.1)

Press **B** with the optional companion to open a docked room editor: room catalog and area outliner, orbitable cutaway voxel viewport, editable Top/Front/Side slices, a properties inspector, and draft undo/redo. Move/resize areas visually, edit exact bounds and categories, paint/erase room blocks, then save a protected custom copy. **Join** previews an adjacent template on a shared doorway plane and refreshes its alignment after edits.

See **[Studio guide](STUDIO.md)** for controls, installation, validation rules and preview limits. The existing wand and actual-block preview interface remains under **Wands / live**. Ordinary readers still need no client mod.

## New in 3.0

- One neighborhood constraint planner considers nearby rooms and their open exits together, with bounded backtracking, forward checking and seeded weighted template choices.
- Shared doorway planes, terrain checks, frontier protection, queued placement, recovery, props/categories, exact/PACKED decoration, Room Diversity and configurable dead-end walls remain supported.
- All authoring wands use left-click first corner, right-click second corner. `/il builder` opens tools, with undo/redo/cancel and saved reading/shelf regions.
- **[Fabric builder mod JAR](downloads/InfinityLibraryBuilder-3.1.0.jar?raw=true)** adds **B** for the room editor and **V** for outlines. Includes a room browser, selection visualizations, settings and live preview controls. See [BUILDER.md](BUILDER.md).
- Writing uses Minecraft's Book & Quill editor. Published books automatically stock empty chiseled-bookshelf slots in loaded library chunks, preserving existing contents.

Existing `generated.yml` placements and room IDs are retained. Cached layout reservations are rebuilt for the new planner. No world reset is required. The proposed second generation mode is retired; any experimental world/files are left untouched.

## Community tools and particle navigation (new in 2.6)

Five additions are available through the main chest menu:

- **Room preview:** client-only room blocks with rotation, mirroring, prop/Diversity rerolls,
  explicit variant selection and colored area/door markers. Admins use `/il preview <roomId>`.
- **Community noticeboard:** expiring announcements/events, administrator pins, author/admin
  removal and recent public books. `/il noticeboard bind` attaches it to a physical block.
- **Reading progress:** a native library reader remembers pages and completion per player.
  Use **Continue reading**, `/il reading` or `/il readheld`. Vanilla book previews are separate.
- **Collaborative books:** durable shared manuscripts, invited UUID-based editors, owner-only
  publishing and revision checks that reject simultaneous stale edits. `/il collaborate`.
- **Particle pathfinding:** room-door waypoints plus incremental walkable A* trails to the
  entrance, generated rooms, bookshelves and personal bookmarks. `/il navigate`.

**[Community tools guide](COMMUNITY.md)** explains menus, permissions, commands, persistence,
configuration and practical limits. Existing worlds and data remain usable; no reset required.

## Shared doorway planes (2.5.1)

New rooms align their connection points at the **same block coordinates**, sharing one
wall layer instead of placing two doorway planes next to each other. The joined opening
is cleared, while the existing room's surrounding wall, floor and ceiling are preserved.
Normal interior/terrain collision checks still apply. This also works with rotated,
mirrored and Diversity doors, sibling reservations, loops and vertical connections.

Existing rooms stay where they are. Older unbuilt reservations are discarded automatically
and future rooms use the new spacing. Keep your plugin folder; no reset is needed.

## Smarter room generation and Diversity doors (new in 2.5)

Planning now considers every eligible room template, compares sibling layouts, checks
candidate exits for continuation, and rejects placements proven to strand another placed
room's open exit. Compatible neighboring doors connect directly as loops.

Proven dead-end openings become configurable walls. Saved openings can be restored with
`/il reopendeadends`, and base/catalog changes reopen them automatically before replanning.
Diversity alternatives can have their own doors: capture a variation, run
`/il diversityconnection <area> <variant> <direction> [floor] [id]`, then use the supplied
wand on its opening corners. The chosen variant's doors and blocks are planned and saved together.

**[Generation and variant-door guide](GENERATION.md)** includes wall configuration,
authoring steps, editing, compatibility and bounded-search limits. Existing buildings
retain their saved connections; no reset is required.

## Automatic exploration fixes (2.4.1)

Expansion now checks movement destinations and periodically checks nearby online players,
so waiting at a doorway while construction finishes can still generate the next room.
A blocked nearest exit no longer prevents a viable planned sibling from being used.
Retry delays apply per doorway, and provisional reservations do not consume the built-room cap.

If expansion stops, run **`/il diagnose` while standing near the doorway**. It reports the
configured base, eligible normal templates, open exits, distances and compatible connectors.
`/il status` also reports the automatic-expansion state. Check the configured base in `/il status`: a selected
base must be active, enabled and not STARTING. Use `/il baseroom <normalRoomId>` or
`/il baseroom all` to use the eligible normal-room pool. Door width, height and floor
must match, and adjoining space must be empty and inside world limits. A STARTING room
is only an entrance. Updating does not require `/il reset`.

## Room Diversity and exact decorations (new in 2.4)

**Room Diversity** lets you capture alternatives for part of a room. Select the whole
room, get `/il diversitywand`, then click two target corners and two equally sized source
corners elsewhere in the same world. Save the room or use `/il applydiversity <roomId>`.
A room can choose its original table layout or a completely different captured design;
several weighted alternatives are supported, and choices persist per generated instance.

**[Room Diversity guide](DIVERSITY.md)** covers the capture phases, original/alternative
weights, empty alternatives, editing and frozen snapshots.

Decoration areas now select **one exact-size, category-matching decoration**. After two
corner clicks with `/il decowand`, a dialog asks for the category. A `3×3×1` area does not
accept smaller decorations or another category. `/il savedeco` captures the catalog entries.
The older smaller-piece packer remains available only when explicitly selected.

**[Updated decoration guide](PROPS.md)** explains category selection, dimension matching,
rotation, empty-area probability, migration and optional PACKED mode. These changes affect
newly generated rooms; existing instances retain their saved layouts.

## Custom entrances and removable defaults (new in 2.3)

Save your entrance as **`/il saveroom <id> STARTING`**. This automatically selects it for
the next reset and excludes it from ordinary generation. Convert an existing template
with `/il roomtype <id> STARTING`, or use **Use As STARTING Room** in room details.
`/il startroom <id>` switches between saved entrances. Changing the expansion base no
longer changes the entrance.

After selecting your custom entrance and normal base, **`/il removedefaults confirm`**
removes the packaged room templates from generation and menus, persistently. You can
also remove one using `/il deleteroom <id>`. Built rooms retain archived template data,
so their exits, home lookup and reset cleanup keep working. `/il restoreroom <id>` undoes
a template removal.

**[Starting-room and default-removal guide](ROOMS.md)** covers commands, menu controls,
archives, and applying a new entrance with an intentional world reset.

## Modular rooms and reusable props (new in 2.2)

Future expansion now defaults to **one standard empty room**, `builtin_modular`, with
four prop areas and clear paths between its doors. Shelves, benches, tables, plants
and lecterns are selected from reusable prop templates for each instance. Existing
built rooms stay as they are.

Use `/il props` (or the administrator **Room props** menu button) to inspect the catalog
and get the prop-area wand. Build your own props, capture them with
`/il saveprop <id> <category> [weight]`, and mark differently sized areas in your room.
The default selector chooses one full-size matching decoration per area. Explicit PACKED
mode retains the optional smaller-piece combinations from 2.2.

**[Prop-area setup guide and commands](PROPS.md)** explains dimensions, floor versus
stacked filling, categories, relative weights, placeholder clearing and the authoring
workflow. New settings and saved per-room decoration snapshots are compatible with
existing 2.1 data; snapshots stop reloads or later prop edits from rerolling built rooms.

`/il baseroom <id>` chooses a single standard template and discards old future reservations.
It leaves the entrance selection unchanged. `/il baseroom all` restores the
older mixed-template selection mode. Neither command rebuilds existing rooms.

## Main menu

Use **`/il`**, **`/il menu`** or **`/il gui`**, or right-click a lectern in the library.
A 54-slot chest menu brings together:

- Writing desk and your saved manuscript.
- Search by title, author, category or tag, using a text dialog.
- Library statistics, entrance teleport, signed-book submission and book returns.
- Your daily book & quill claim.
- Room templates and pause/resume for administrators.

`/il stats` still opens statistics directly. `/il write` opens the writing desk.
Menu controls cannot be transferred into player inventories. The new menus use
inventory-holder identity and ownership, rather than matching titles.

## Custom book writing

The writing desk is a chest interface with **native Minecraft text dialogs** for
multiline page text and book details. It is separate from the vanilla book-edit screen.

1. Open the writing desk from `/il` or `/il write`.
2. Select **Book details** to set the title, category, tags and public/private visibility.
   The author is your Minecraft player name.
3. Select **Write this page**, enter multiline text, then **Save**.
4. Use previous/next, insert-page and delete-page controls. Deletion asks for confirmation.
5. Preview the manuscript as a readable book without spending an item.
6. Select **Finish book**, then choose **Sign a copy** or **Publish to library**.

Each player has one virtual manuscript. Pressing Save persists text and details to
`drafts.yml`; you can continue after logout or restart. Page navigation and insert/delete
changes save immediately. Closing a dialog with Escape discards text **not yet saved**;
reopen with `/il write`. Manuscripts support up to **100 pages**, **1,024 characters per
page**, and a **32-character title**.

Signing or publishing costs **one blank book & quill** in your storage inventory.
Books containing text and blank books tagged to another player are never consumed.
Use the daily claim button if you need a blank book. Signing gives you a written-book
copy; publishing stores the book directly in the searchable collection for future shelf
population. Your manuscript stays saved, so additional copies cost additional blank books.

**Import held book & quill** copies pages from a book you own into an empty manuscript.
It does not consume the original. An import cannot overwrite existing manuscript text.
The existing signed-book submission/return editors remain available from the main menu.

## Room generation that plans ahead

The generator no longer selects and places the first room that fits a single doorway.

- When expansion is triggered, it considers a group of open doors on the parent room.
- A bounded **beam search** compares combinations of neighboring templates and transforms.
  It can retain a different choice for the first door to make the other doors fit.
- It checks candidate volumes against placed rooms, terrain, world bounds, and other
  reserved rooms. Sibling candidates cannot overlap each other.
- It scores the unused exits of each candidate by whether another compatible room can
  geometrically attach. Final scoring includes the selected siblings as obstacles.
- It **reserves the chosen neighbor volumes before placing blocks**. Walking toward a
  doorway commits that reservation; the new room's outgoing-door plans are also reserved
  before its first block is placed.
- Matching exits on the same doorway plane can join an already placed room, closing a loop.
- Reservations, unfinished placement and pause state persist in `generated.yml`.
  Terrain is rechecked before committing a reservation. Invalidated plans can be retried.
- A failed bounded search leaves a doorway available for a later attempt rather than
  permanently closing it. Attempts at an unplanned doorway have a short cooldown.

This is local planning with one additional geometric lookahead step, not a global
optimal layout or unlimited-depth search. It does not promise a neighbor for every exit:
room templates, terrain, room-count limits and search budgets still constrain the result.
The one-step lookahead is geometric; actual terrain is checked for direct reservations
and again before placement. No speculative world blocks are written during planning.

### Planning settings

New settings use defaults when absent from an existing config:

```yaml
generation:
  max-total-rooms: 0             # 0 = unlimited
  placement-blocks-per-tick: 1800
  planning:
    beam-width: 12
    candidates-per-door: 12
    max-doors: 16
    world-block-budget: 150000
```

Search settings are capped internally (beam/candidates/door group at most 32; world-block
budget at most 1,000,000). Continuation checks are also bounded. Doors beyond the group
limit are planned on later triggers. Lower settings trade search quality for less work.
`/il status` reports both **placed** and **planned** counts. Planned rooms reserve space
but are not built until approached. Changing the total-room limit does not delete built
rooms; reservations are rechecked against the limit before placement.

In 2.1, movement commits one room at a time and waits for its queued construction. The old
`max-rooms-per-tick`/`retry-room-selection` settings no longer control planning. The old
path-blocking settings and blocker commands are retained for config compatibility, but
new planning failures no longer create permanent blockers. Existing 2.0 blockers remain
tracked until reset.

## Custom room templates

1. Build outside the active library and select opposite bounding corners with `/il pos1`
   and `/il pos2`, or `/il wand` plus `/il wandmode pos1|pos2` and block clicks.
2. Use `/il connectionwand`; click two corners of each outside doorway to detect its
   direction and size. Repeat for every exit. `/il connectionmode <prefix>` names exits.
3. Save using `/il saveroom <id> <FILLER|BOOK|READ>`.
4. Use `/il roominfo <id>` and `/il roomchance <id> <0-100>` to inspect/control spawning.

Connections need matching width, height and floor index and opposite transformed facing.
Explicit exits: `/il addconnection <id> <x,y,z> <NORTH|EAST|SOUTH|WEST|UP|DOWN> <width> <height> [floor]`.
Room IDs use 1–64 lowercase letters, digits, underscores or hyphens. Captures are limited
to 100,000 bounding-box blocks by default (`rooms.max-capture-volume`). Templates used by
built rooms can be removed from the active catalog and retained in the archive. Their
geometry cannot be overwritten; save revisions under a new ID.
Use `/il reset confirm` only if you intend to rebuild generated rooms and reservations.

Variation, bookshelf-category and reading-region wands from 2.0 remain available.
Run `/il help` for the complete command list. Seven built-in templates provide a modular room, starting
junction, book room, reading room, vertical connector, gallery and lounge.

## Other commands and permissions

| Command | Purpose |
| --- | --- |
| `/il home` | Return to safe standing space in the start room |
| `/il search <query...>` | Search readable books; show up to 10 results and a shelf guide |
| `/il book public|private|edit` | Held-book ownership tools |
| `/il bookmeta <category> <rating> <tags_csv> <comments...>` | Held-book metadata |
| `/il status` | Templates, placed rooms, planned rooms, queue and pause state |
| `/il pause` / `/il resume` | Admin: stop/resume new room expansion |
| `/il backup` | Admin: ZIP the plugin's YAML files |
| `/il reload` | Admin: reload templates/config/books; recover placement and reservations |
| `/il reset confirm` | Admin: back up YAML, remove generated rooms/plans, rebuild start |
| `/il setstart` | Admin: set coordinates for the next reset |

General permission: `infinitylibrary.command` (everyone). Administration:
`infinitylibrary.admin` (operators). Daily-limit bypass:
`infinitylibrary.book.bypassdaily` (operators). `/ilibrary` and `/infinitylibrary`
are command aliases. The server's calendar/timezone defines daily draft claims.

## Upgrading and limitations

Stop the server, back up the world and plugin folder, replace the old JAR, then restart
with Paper 26.3/Java 25. Existing 2.0/2.1 YAML data is read directly; existing placed rooms are
not rebuilt by the upgrade. New plans form as doors are approached. `props.yml` stores reusable props; room areas are in `rooms.yml`, and resolved decoration is in `generated.yml`.

New worlds use an empty generator; existing terrain worlds are not converted. Use a
world dedicated to this plugin. Changing the configured world while loading another
world's generation metadata is rejected.

Plugin ZIP backups contain **YAML only**, including drafts and reservations; they do not
include world blocks. Restore matching world and plugin-data backups while stopped.
Reset still clears room volumes synchronously and can cause a pause in a large library.
It removes player changes in those volumes. There is no general world undo.

Template capture stores block data rather than full block-entity NBT, inventories or
entities. Vertical connectors are geometric openings, not automatic elevators. Reading
Reading/shelf regions captured in templates follow generated instances; use `/il builder apply` for source-build marks. Unfinished room recovery can reroll legacy variations outside frozen Diversity patches;
wait for construction before modifying those rooms. Private-book rules apply within the
plugin; they are not DRM against server administrators or other plugins.

## Build and verification

Install JDK 25, then run `./gradlew clean build` (`gradlew.bat clean build` on Windows).
Output: `build/libs/InfinityLibraryPlugin-3.1.0.jar`. The pinned Paper API is
`26.3.build.253-stable` and is supplied by the server at runtime.

`./gradlew test` covers transforms, planning, manuscripts, room catalogs, exact decoration
matching and Room Diversity selection/persistence.
See [VERIFICATION.md](VERIFICATION.md) for actual live-server checks and their limits.
The verification-only companion plugin must never be installed on a valuable server:
it resets the library and shuts down its disposable test server.
