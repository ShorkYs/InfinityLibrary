# Infinity Library 4.0.1

A server-only procedural library for **Paper Minecraft 26.3**, requiring **Java 25**. Players and builders use ordinary Minecraft menus, dialogs, books and wands. Fabric development is shelved; no client mod is required.

**[Download plugin](downloads/InfinityLibraryPlugin-4.0.1.jar?raw=true)** · **[Download source](downloads/InfinityLibrary-4.0.1-source.zip?raw=true)**

On GitHub, choose **Download raw file** if a preview opens. Stop the server, replace the old plugin JAR in `plugins/`, and retain the plugin data folder. Start Paper, then use `/il` for the player menu or `/il builder` for the administrator menu.

## Bookshelf restocking fix (4.0.1)

Published and submitted books are queued for automatic delivery. Startup and `/il reload` rescan loaded shelves and restore missing catalog copies. Full shelves leave excess books in the saved collection; new generated rooms, placed shelves and loaded chunks make that space available automatically. Stale full/missing shelf destinations are reassigned, existing contents are preserved, and stocked catalog IDs prevent repeated copies. Uncategorized signed books match `general` shelves. Rental shelves remain separate from public stocking.

Shelf categories still apply: a category-specific shelf requires a matching book; blank or `*` shelves accept all categories. No manual `/il shelves` command is needed. Distant unloaded chunks are stocked when they load; the plugin does not force-load the whole library. Only submitted/published books join the library collection; unfinished personal drafts remain private.

## What changed

- **District generation:** searches complete groups of future rooms across several generations before committing. Large rooms, hallway turns, junctions and short adapters compete as connected layouts. Future rooms retain their reservations across reloads, including rooms whose parent hallway is not built yet. Shared doorway planes make transitions one wall thick.
- **Room roles:** filler, reading, rentable and public writing rooms, plus a separate starting room. Built-in examples include rental/writing spaces and several corridor sizes. Custom rooms, removable defaults, props and Diversity remain supported.
- **Room navigation:** generated rooms have stable instance IDs; browse and favorite a specific room, save named locations, and follow private walking particles back. Book collection entries offer trails to their actual loaded chiseled bookshelf.
- **Free rental workspaces:** timed claims, invitations, private notes, archived books and manuscripts, renewal/release, private shelves and room access. Owners keep their archive after expiration, release or library reset. New renters never inherit old contents.
- **Book writing and reading:** native Book & Quill editing, formatting tools, dividers, previews, collaborative drafts and saved reading progress. Native books show Minecraft's page counter; the guided reader shows page/total without adding text to your manuscript.
- **Separate menus:** player home contains reading, writing, rentals, navigation and community tools. Builder home contains template capture, tools, catalogs, Diversity, previews and generation administration. Common builder settings now have native dialogs.
- **Recovery:** atomic saves, migration backups, dependent reservation recovery and strict manuscript/catalog parsing. Existing generated rooms stay in place; no reset is required for this update.

## Getting started

1. Enter with `/il home`. Walk toward an open doorway; movement and periodic checks both trigger expansion.
2. Open `/il` to write, read, browse rooms, follow particles, claim a rental or view notices.
3. Administrators use `/il builder`. Capture room corners, configure and capture door openings, then **Save selected room** and choose its role.
4. Use `/il baseroom all` for the full active template catalog. `/il baseroom my_room` uses that template plus enabled built-in filler corridors. Use `roomchance 0` or remove templates to exclude them.
5. Use `/il diagnose` near a doorway when expansion pauses. `/il status` reports placed rooms, reservations, future-parent plans and construction work.

The planner searches a bounded region. Arbitrary template sizes, missing corridor turns, incompatible doors or obstacles can make a layout impossible. Exhausting a budget defers planning; it does not prove a dead end. This does **not** guarantee an infinite, globally perfect layout for every room catalog.

## Guides

- [Generation, configuration and diagnostics](GENERATION.md)
- [Room roles, entrances and removable defaults](ROOMS.md)
- [Vanilla builder tools](BUILDER.md)
- [Rental workspaces and privacy](RENTALS.md)
- [Updating, backups and recovery](MIGRATION-4.0.md)
- [Decoration areas and reusable props](PROPS.md)
- [Room Diversity and variant doors](DIVERSITY.md)
- [Community, reading, collaboration and navigation](COMMUNITY.md)
- [Implementation and verification](REBUILD-4.0.md) · [Test evidence](VERIFICATION.md)

`infinitylibrary.command` permits player features (default true); `infinitylibrary.admin` permits builder/administration features (operators by default). `infinitylibrary.notice.post` controls notice creation. Menus recheck ownership and permissions; rental and shared-manuscript permissions are independent.

## Build

Run `./gradlew build` with Java 25. Output: `build/libs/InfinityLibraryPlugin-4.0.1.jar`. The build runs JUnit tests. The archived `fabric-client/` project and old downloads describe the previous optional companion; they are not part of the 4.0 server release.
