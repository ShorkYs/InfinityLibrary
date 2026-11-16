# Infinity Library 2.0

**Downloads:** [Installable plugin JAR](downloads/InfinityLibraryPlugin-2.0.0.jar?raw=true) · [Source ZIP](downloads/InfinityLibrary-2.0.0-source.zip?raw=true)

Requires Paper 26.3 and Java 25. Click a download above, or open a file and select **Download raw file**.

Procedural library plugin for **Paper Minecraft Java Edition 26.3**, compiled against
`paper-api:26.3.build.253-stable`. Requires **Java 25**. This is a server plugin:
put its JAR in the server's `plugins/` directory; players need no client mod.
Vanilla servers, Bedrock, Fabric, Forge and Folia are not supported targets.

## Install and explore

1. Install Paper 26.3 and run it with Java 25.
2. Stop the server, copy `InfinityLibraryPlugin-2.0.0.jar` into `plugins/`, and start it.
3. Wait for construction to finish, then use `/il home`.
4. Walk toward a doorway to expand the library. `/il status` reports queued work.
5. Use `/il gui` for book statistics. Right-click a library lectern for the book menu;
   left-click it for the book editor. `/il search <query>` searches title, author,
   category and tags. Results respect public/private ownership.

New library worlds use an empty generator so terrain cannot obstruct expansion.
The plugin does not convert existing terrain worlds into empty worlds.
The default world name is `InfinityLibrary`. Use a world dedicated to this plugin.

## What's new

- Paper 26.3 / Java 25 compatibility, pinned API dependency, Gradle wrapper.
- Six built-in templates: start junction, book room, reading room, vertical connector,
  larger gallery and lounge. Improved lighting and lecterns in reading rooms.
- `/il pause`, `/il resume`, `/il status`, `/il roominfo <id>` and template-ID completion.
- Optional total-room limit and bounded capture volume.
- Queued placement for saved rooms; expansion waits for current construction.
- Persisted pause state, sealed blockers and unfinished placement recovery on reload/restart.
- Safe standing-space selection for `/il home`.
- `/il backup` creates a ZIP of plugin YAML data. Reset creates this backup automatically
  and requires `/il reset confirm`.
- Native block rotation/mirroring for directional blocks, rails, stairs and doors.
- Search across title, author, category and tags, with up to ten sorted results and
  a particle guide to the first located match in your current world.
- Public/private edits enforce ownership; private books are excluded from random reading.
- Book interactions and shelf action bars are limited to the library world.
- Main-thread chat handling, coalesced book saves, full-inventory draft protection,
  pending book-edit recovery on logout, temporary seat cleanup, GUI drag protection.
- Cross-world selection protection, safe IDs, capture limits, finite chance checks,
  and protection against deleting or replacing templates used by placed rooms.

## Commands

`/infinitylibrary`, `/ilibrary` and `/il` are aliases.

| Command | Purpose |
| --- | --- |
| `help` | List commands |
| `home` / `start` | Teleport to safe standing space in the original start room |
| `gui` / `stats` | Book statistics |
| `status` | Template count, placed count, queued operations, pause state |
| `search <query...>` | Search readable books |
| `roominfo <id>` / `listrooms` | Inspect templates |
| `book public\|private\|edit` | Change your held book's visibility or make it editable |
| `bookmeta <category> <rating> <tags_csv> <comments...>` | Held book metadata |
| `roomgui` | Room browser; editing/deletion requires administrator permission |
| `pause` / `resume` | Admin: suspend/resume new expansion; queued work finishes |
| `backup` | Admin: ZIP plugin YAML into `plugins/InfinityLibraryPlugin/backups/` |
| `reset confirm` | Admin: back up plugin YAML, remove generated rooms, rebuild start |
| `reload` | Admin: reload config/templates/books and recover unfinished construction |
| `setstart` | Admin: set coordinates used by the **next reset**, not move existing rooms |
| `roomchance <id> <0-100>` | Admin: per-selection inclusion chance; zero disables spawning |
| `setrange <1-5>` / `genrange` | Admin: attempt nearby expansion; construction remains queued |
| `toggleblocking` / `setblocker <material>` | Admin: configure failed-doorway blockers |

General permission: `infinitylibrary.command` (everyone). Administrative permission:
`infinitylibrary.admin` (operators). `infinitylibrary.book.bypassdaily` bypasses the
one-draft-per-calendar-day limit (operators by default). The server's timezone defines
that calendar day. These commands require the general permission as well.

## Make your own rooms

1. Build a room away from the active library.
2. Use `/il wand`, then `/il wandmode pos1` and click one corner; switch to `pos2`
   and click the opposite corner. `/il pos1` and `/il pos2` also use your position.
3. Use `/il connectionwand`; click two corners of a doorway on an outside face.
   The wand detects direction and opening size. `/il connectionmode <prefix>` names
   the connections. Repeat for every exit. `/il clearconnections` discards staged exits.
4. Save with `/il saveroom <id> <FILLER|BOOK|READ>`. IDs use lowercase letters,
   digits, hyphens or underscores, maximum 64 characters. Rooms default to 100% chance.
5. A saved room is queued into an available compatible exit if possible; otherwise it
   becomes available for later expansion. Check `/il status` before walking into it.
6. Use `/il roomchance <id> 0` to stop future spawning. Templates already used by placed
   rooms cannot be deleted or replaced; save an edited template under a new ID.

Connections must match opening width, height and floor index and face opposite
ways after transformation. `/il addconnection <id> <x,y,z> <direction> <width> <height> [floor]`
adds an explicit local connection. Directions: NORTH, EAST, SOUTH, WEST, UP, DOWN.

`/il variationwand`, `/il variationmode <category> <chancePercent> [prefix]` and two
corner clicks stage alternate blocks. Save with the room or apply with
`/il applyvariations <roomId>`. `/il clearvariations` discards staged variations.

`/il bookshelfcategorywand` and `/il bookshelfcategorymode <category>` mark shelves
in a selected region; `/il shelfcategory <category>` marks a looked-at chiseled shelf.
Reading table and seat wands mark interactive blocks in the current world.

## Configuration

See `src/main/resources/config.yml` for all defaults. Notable options:

- `generation.placement-blocks-per-tick`: block budget, default 1800, minimum effective 1.
- `generation.max-rooms-per-tick`: expansion attempt limit per movement trigger.
- `generation.max-total-rooms`: 0 = unlimited; positive values cap future room count.
- `rooms.max-capture-volume`: bounding-box volume limit, default 100000 blocks.
- `proximity-radius`: distance from a doorway that triggers expansion.
- `lectern-gui.save-shelf-radius`: nearby shelf search radius; capped at 32 blocks.

Pause and room-count limits prevent new expansion; they do not cancel existing work.
Increasing block budgets and capture limits can cause server lag.

## Upgrade from the uploaded 1.x project

1. Stop the server and back up both the library **world** and the plugin data folder.
2. Upgrade the server to Paper 26.3 and Java 25, following Paper's world upgrade guidance.
3. Replace the old plugin JAR with the new one. Keep `rooms.yml`, `books.yml`,
   `daily-books.yml`, `generated.yml` and `config.yml`.
4. New configuration values use defaults when absent; add them to your existing
   config if you want to customize them. New built-in gallery/lounge templates become
   available automatically. Saved old templates keep their existing block layouts.
5. Check startup logs and `/il status`, then test `/il home` in a copy of your world.

Changing `world` while `generated.yml` belongs to a different world is rejected.
Use a separate data-folder/world backup to start a separate library.

## Backups, recovery and current limits

`/il backup` and automatic pre-reset backups contain **plugin YAML only**, including
books, templates and generated-room metadata. They do **not** include world blocks.
Restore matching plugin data and world backups together while the server is stopped.
Backups are retained until you remove them; monitor disk use.

Unfinished rooms replay their template blocks on reload/restart. Wait for the queue
before modifying a room. Room variations may be rerolled during recovery. Sudden process
or filesystem failure is not equivalent to a coordinated world/plugin backup.

Reset still clears all registered room volumes synchronously and can cause a pause for
large libraries. It removes player changes within those room volumes. Run large resets
in maintenance time. This version has no general undo of world changes.

Template capture stores **block data**, not full block entity NBT: custom chest contents,
sign text, entities and custom lectern contents are not captured. Stored books have their
own persistence. Reading-seat/table wand region marks remain session-only; they are not
copied into generated templates. Vertical connections are geometric openings, not an
automatic elevator; supply suitable ladders/stairs in custom builds. Search shows up to
ten results and a straight particle guide, rather than navigation pathfinding.

## Build and verification

Install JDK 25, then run:

```sh
./gradlew clean build
# Windows: gradlew.bat clean build
```

Output: `build/libs/InfinityLibraryPlugin-2.0.0.jar`.
`./gradlew test` checks transform bounds/bijection, transformed connector compatibility,
and invalid connection dimensions/directions. Paper API classes are compile-only in the
plugin JAR; the server supplies them at runtime.

See `VERIFICATION.md` for the actual build and live Paper verification performed for
this release. Full multiplayer UI, book workflows and large-library load tests remain
manual validation tasks before deploying to a valuable world.
