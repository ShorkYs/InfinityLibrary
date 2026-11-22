> **4.0:** Player features open from `/il`; builder previews open from `/il builder` → Room catalog. Favorites and private rental books now join navigation and reading history; see [RENTALS.md](RENTALS.md). The retained controls below still apply.

# Library community, reading and navigation — 2.6

All five features have buttons in `/il` (the main chest menu). This update keeps existing
rooms and books. Stop the server, replace the old plugin JAR, keep the plugin data folder,
and restart. No library reset or client mod is required. Paper 26.3 and Java 25 are required.

## Room preview for builders

Open **Room templates → a room → Preview Room**, or:

```text
/il preview <roomId>
/il preview menu
/il previewvariant <areaId> <variantId|default>
/il preview stop
```

Requires `infinitylibrary.admin`. Face an open area: a client-only copy appears ahead of you,
with controls to rotate 90°, mirror X, reroll decorations/Diversity, pick a specific Diversity
alternative, and stop. It renders captured room blocks, legacy variations, exact/packed props
and the selected Diversity layout. Active door anchors have green particles; decoration
bounds are yellow and Diversity targets purple. The overview reports size, type and counts.

These are **visual ghost blocks**, not a physical room you can stand on. The server world,
inventories and room templates are never changed. Other players do not receive the preview.
Stopping restores the live block data rather than a stale captured copy. Previews also stop
on logout, world change, expiry, loss of admin permission, range/chunk loss, reload or shutdown.

Only already loaded chunks are used. Default volume is 32,768 cells, duration 90 seconds,
maximum distance 80 blocks; four builders can preview at once. Client updates are bounded
and spread across ticks. Very large rooms or cramped/unloaded locations are rejected.
Archived templates can also be previewed by command. A preview selection does not change
its generation weights or save edits to the template.

## Community noticeboard

Use **Community noticeboard** in the main menu, or `/il notices` / `/il noticeboard`.
Players can post announcements, events and recommendations through a native text form.
Each notice has a title, body, author and expiry. Administrators can pin notices; authors
and administrators can remove them. The board also shows the ten newest **public** books,
which can be opened directly in the library reader. Private books are excluded.

```text
/il notice post
/il notice remove <noticeUUID>
/il notice pin <noticeUUID>       # administrator
/il notice unpin <noticeUUID>     # administrator
```

For a physical board, look at a block within six blocks and run:

```text
/il noticeboard bind
/il noticeboard unbind
```

Binding requires admin permission and saves that block position. Right-clicking it opens
the board; the block itself is not replaced. Boards can be placed in other worlds, such as
server spawn. Up to 64 bindings are supported.

`infinitylibrary.notice.post` is enabled for everyone by default and can be controlled by
your permissions plugin. Regular players have a one-minute post cooldown and at most five
active notices; removing a notice does not bypass the cooldown. Titles are at most 48
characters, bodies 2,000. Notices expire after 1–30 days by default, even if pinned. The
active board limit defaults to 200. Administrators bypass personal posting limits, but not
the total board/content limits. There is no external publishing or messaging integration.

## Reading progress

Choose **Book collection** or **Continue reading** in the main menu. Click a book to open
its native library reader with Previous/Next controls. Page changes save immediately;
**Save & close** returns to reading history. On the last page, **Finished reading** records
completion; it can be marked unread again. Progress belongs to the player's UUID and the
stored book ID and survives logout, restart and plugin reload.

```text
/il read                         # browse readable collection
/il read <bookUUID>
/il reading                      # resume list
/il readheld                     # use the reader for the book in your hand
```

Newly catalogued library copies carry a stable ID and use this reader on ordinary held-book
interaction inside the library. Reading tables also use the managed reader. Older/untagged
items can be read with `/il readheld` or the held-book button. Non-catalogued held books use
a stable content/owner key; holding the same content again resumes its saved page. They are
not listed as collection history entries. Private books remain owner-only in the reader,
search results, catalog, history and noticeboard. Contributor credits are shown for shared books.

Minecraft's ordinary handheld book screen does not reliably send page-turn events to the
server. **Automatic progress applies to the library reader**, not arbitrary vanilla book
screens or previews. A draft preview does not count as reading a published book. Completion
is explicit; arriving on the last page alone does not prove someone finished reading it.

## Collaborative books

Choose **Collaborative books → Create shared manuscript**, or:

```text
/il collaborate
/il collaborate create <title...>
/il collaborate open <manuscriptUUID>
/il collaborate invite <manuscriptUUID> <playerName>
/il collaborate revoke <manuscriptUUID> <editorUUID>
```

The shared writing desk offers page editing, details, inserting/deleting pages, preview,
editor management and finishing a book. Invite a player through the desk's form; that player
must have joined this server before, so invitations resolve to a known UUID. Their desk
will list the shared manuscript. No personal draft is overwritten.

- The owner can invite/remove editors, change public/private visibility, sign/publish, and
  delete the manuscript with confirmation. Removing the owner is prohibited.
- Invited editors can write pages and edit title/category/tags. Each writer has their own
  page cursor. A revision check rejects stale edits rather than overwriting newer changes.
- Revoked editors immediately lose access, including saves from an already opened dialog.
  People who contributed retain their credit after removal.
- Signing/publishing uses the existing one-blank-book-and-quill payment and durable storage
  checks. Only the owner can finish it; a save failure does not consume the payment.
- Signed books include coauthor credits in metadata/lore. The linked owner remains responsible
  for the published book's privacy. Shared manuscripts stay saved after publication.

Defaults: 10 owned shared manuscripts per player, 16 members including the owner, 100 pages,
1,024 characters per page. Shared manuscripts are not public notices or readable library
books until the owner publishes them. Revision checks prevent conflicts; they do not provide
an archive of every historical revision.

## Particle pathfinding

Use **Particle navigation** in the main menu. It provides the entrance, nearest connected
reading/book room, a generated-room browser, book/shelf routes and personal bookmarks.
In the book catalog/history, **right-click a book** to follow particles to its shelf.
Search results still offer clickable reading and can start a shelf route when one exists.

```text
/il navigate
/il navigate entrance
/il navigate reading
/il navigate books
/il navigate rooms
/il navigate room <placedRoomUUID>
/il navigate book <bookUUID>
/il bookmark <lowercase_name>
/il navigate bookmark <name>
/il bookmark remove <name>
/il navigate stop
```

Start inside a completed library room. Each player can save 20 personal bookmarks. The
room browser supplies destinations without manually copying a placed-room UUID. A book
in the storage pool without a physical shelf can still be read, but cannot have a shelf trail.

Routing has two steps:

1. A graph of **actually connected, completed rooms** chooses doorway waypoints. It uses
   frozen Diversity doors, excludes sealed/unbuilt exits, and understands both old adjacent
   doors and new overlapping doorway planes.
2. Bounded incremental A* finds walkable block paths between waypoints. It checks feet/head
   clearance and floor support, avoids damaging blocks and liquids, permits normal steps and
   short drops, and can climb ladders/vines/scaffolding. It cannot invent stairs or ladders
   for a vertical opening. Cardinal movement avoids diagonal corner cutting.

Particles are shown **only to the navigating player**, on nearby verified walking cells.
The trail advances as the player moves. A changed obstruction triggers local replanning;
if no safe path is found, navigation explains the failure. It never substitutes a straight
line through walls, teleports the player or generates chunks/rooms to make a route.

Routes use already loaded chunks, last at most three minutes and stop on logout, world
change, library reset/reload or cancellation. Long/complex routes can reach configured search
limits; open blocked doors or approach loaded areas and try again. The room graph chooses
a structural route; this version does not exhaustively test every alternate room graph route
when one physical corridor is blocked. Bottom slabs/irregular floors are approximated by
block-height walking nodes; this is a guide, not an automated player movement controller.

## Data and limits

New files:

| File | Contents |
|---|---|
| `collaborative-books.yml` | Shared content, ownership, member UUIDs, credits and revisions |
| `reading-progress.yml` | Per-player book positions and completion |
| `noticeboard.yml` | Notices, pins, expiry, posting cooldowns and physical board bindings |
| `bookmarks.yml` | Personal bookmark positions |

Mutations use atomic YAML replacement and roll back memory when a durable save fails.
Reloads parse into temporary collections before replacing live data. `/il backup` includes
these YAML files; world blocks still need a separate server/world backup. Preview and trail
sessions are temporary and are not resumed after restart.

```yaml
community:
  max-shared-drafts-per-owner: 10
  notice-max-days: 30
  notice-limit: 200
navigation:
  max-active-routes: 32
  max-graph-rooms: 5000
  nodes-per-tick: 600
  max-search-nodes: 10000
  segment-range: 96
preview:
  max-volume: 32768
  duration-seconds: 90
  max-distance: 80
```

Navigation work and preview updates are bounded. Actual multiplayer client appearance,
large-server load and compatibility with other plugins still need server-specific testing.
