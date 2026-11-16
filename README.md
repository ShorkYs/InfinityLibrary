# Infinity Library 2.1

A procedural library plugin for **Paper Minecraft Java Edition 26.3**, requiring
**Java 25**. No client mod or resource pack is needed.

**[Download plugin JAR](downloads/InfinityLibraryPlugin-2.1.0.jar?raw=true)** ·
**[Download source ZIP](downloads/InfinityLibrary-2.1.0-source.zip?raw=true)**

If GitHub opens a file preview, use **Download raw file**. Put the plugin JAR in your
server's `plugins/` folder. Stop your server before replacing an older plugin JAR,
remove that old JAR, and keep the plugin data folder. Back up your library world first.

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
- Exact adjacent matching exits can join an already placed room, closing a loop.
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
placed **or reserved** rooms cannot be deleted/replaced; save revisions under a new ID.
Use `/il reset confirm` only if you intend to remove generated rooms and reservations.

Variation, bookshelf-category and reading-region wands from 2.0 remain available.
Run `/il help` for the complete command list. Six built-in templates provide a starting
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
with Paper 26.3/Java 25. Existing 2.0 YAML data is read directly; existing placed rooms are
not rebuilt by the upgrade. New plans form as doors are approached. `drafts.yml` is new.

New worlds use an empty generator; existing terrain worlds are not converted. Use a
world dedicated to this plugin. Changing the configured world while loading another
world's generation metadata is rejected.

Plugin ZIP backups contain **YAML only**, including drafts and reservations; they do not
include world blocks. Restore matching world and plugin-data backups while stopped.
Reset still clears room volumes synchronously and can cause a pause in a large library.
It removes player changes in those volumes. There is no general world undo.

Template capture stores block data rather than full block-entity NBT, inventories or
entities. Vertical connectors are geometric openings, not automatic elevators. Reading
seat/table wand marks remain session-only. Unfinished room recovery can reroll variations;
wait for construction before modifying those rooms. Private-book rules apply within the
plugin; they are not DRM against server administrators or other plugins.

## Build and verification

Install JDK 25, then run `./gradlew clean build` (`gradlew.bat clean build` on Windows).
Output: `build/libs/InfinityLibraryPlugin-2.1.0.jar`. The pinned Paper API is
`26.3.build.253-stable` and is supplied by the server at runtime.

`./gradlew test` covers transforms, multi-door planning and manuscript editing/validation.
See [VERIFICATION.md](VERIFICATION.md) for actual live-server checks and their limits.
The verification-only companion plugin must never be installed on a valuable server:
it resets the library and shuts down its disposable test server.
