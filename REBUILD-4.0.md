# Current implementation milestone

Implemented in the development snapshot: separate player/builder menus; room IDs and scoped favorites; new rentable/public-writing roles; timed, free claims; owner and invited-editor private notes; retention of owner notes on claim release/expiry; basic typography tools. District plans, bounded search, and durable dependency-aware journaling are implemented separately and tested. The new generator is not connected to the live engine yet.

Commands: `/il builder`, `/il rent claim`, `/il rent renew`, `/il rent release`, `/il rent invite <UUID>`, `/il rent note <text>`, `/il rent notes`. Rental duration defaults to 168 hours and is configurable through `rentals.duration-hours` (bounded 1–2160 hours). Notes remain attached to their original lease; a subsequent renter never inherits them. The chat listing abbreviates long notes; full text is retained in the private stored record. Physical protection, rental book storage and workspace GUI are outstanding.

Next engineering gate: template/corridor candidate expansion, safe terrain snapshots and runtime integration of persistent district reservations, including the supplied sketch as a regression fixture. Main release remains unchanged.

# Server-only rebuild: implementation contract

Status: design and isolated planning foundation; **not a released replacement plugin**. The 3.1 release and existing library worlds remain unchanged. Fabric development is shelved. The next release must not advertise completion until all acceptance checks below pass.

## Confirmed requirements

- Minecraft/Paper 26.3, Java 25; no client mod required.
- Procedural generation using saved room templates and shared-plane connection points.
- Reserve large rooms and the filler corridors that connect them as one district before construction. The user's sketch is the layout reference: avoid filling the connecting space with an unrelated room.
- Room roles: filler, reading, rentable and public writing; entrance remains a special role. Existing BOOK/READ data needs explicit migration rather than silently changing semantics.
- Stable generated room identity, room location display, saved locations, room favorites, particle routes to rooms and to the actual chiseled bookshelf holding a book.
- Timed rental claims **without payment**. Proposed default: seven days, configurable. Private books, drafts, notes, invited collaborators and a space to spend time.
- Native Book & Quill writing with formatting tools, dividers, automatic page numbering and immersive reading/writing.
- Two distinct entry menus: player menu and builder/admin menu.
- Preserve and rebuild the existing props/categories, exact/packed decoration, Diversity and variant doors, previews, notices, reading progress, collaboration, shelf stocking, backups, recovery and permissions around the new services.

## Generation architecture

1. Snapshot existing room volumes, exact door cells, terrain and world bounds for a bounded planning region. Keep already built rooms immutable.
2. Choose a district goal: one or more destination rooms, role balance and multiple routes where the catalog permits. Reserve the destination room footprint and doorway approaches before routing corridors.
3. Search corridor/template arrangements toward those destinations. Candidate state contains every proposed room volume, doorway link and external expansion socket. Rotations and reflections use exact opening cells and compatible floor indices.
4. Backtrack across the whole district when one route blocks another. Prefer circuits and multiple connections; penalize enclosed leftover spaces, narrow stranded sockets and unnecessary corridor length. Consider alternative large-room anchors, not just alternative first hallways.
5. Validate all internal links and collisions. Every unused doorway must be an intentional expansion-frontier socket with a proven continuation witness; missing witnesses caused by a budget limit defer planning, not seal the doorway.
6. Persist the district and its placement dependencies atomically. Build its reachable prefix as players explore; retain the unbuilt destination and corridor reservations across restarts. No detached room may appear before its access route finishes.
7. Revalidate loaded-world terrain before each batch. If externally changed terrain blocks a reservation, replan the affected unbuilt district without moving built rooms or deleting stored contents.

Arbitrary room catalogs cannot guarantee a fully connected infinite layout. A catalog validator must identify unsupported doorway sizes, floor offsets, missing turns/junctions and unrouteable template combinations. Report the reason and defer when connectivity cannot be established. Configurable walls remain a final, proven-impossible/intentional terminal treatment, not the response to exhausted search time.

`nextgen/DistrictPlan` is the first immutable plan validator; `DistrictSearch` searches whole proposals with explicit completion/budget outcomes. **Candidate expansion, terrain snapshots, continuation witnesses, persistent reservations and runtime placement integration are not implemented yet.** In particular, an explicit frontier entry in this foundation is not itself a proof that more rooms fit there.

## Domain and storage boundaries

- Room catalog: saved template geometry, role, weight, doorway rules, props, Diversity and reading/shelf annotations.
- Library layout: library UUID, district IDs, stable room instance IDs, transforms, graph edges, construction journal and reservations.
- Library collection: stable book IDs and physical shelf/slot locations; stock and movement events update destinations.
- Player profile: favorites referencing room instance IDs, named bookmarks, reading progress and navigation preferences.
- Workspace: owned drafts, shared manuscripts, notes and revision history.
- Rentals: claim referencing library/room IDs, owner, expiry and explicit collaborators. No economy dependency.

Mutations run through server-owned services and durable transactions, with version checks. GUI callbacks cannot write YAML or place blocks directly. Use a read-only world snapshot for off-thread search and validate its revision on the main thread before committing. Bound snapshots, queues, search memory and time per tick.

Rental expiry must never delete personal writing. End access, remove the claim and return stored records to an owner recovery inbox atomically. Use managed virtual storage for personal books/notes; protect interactions and physical shelf copies consistently against non-members. Collaborator access is scoped to that rental, and manuscript permissions remain explicit. Library reset, missing templates, world changes and missing room records must not strand private data.

## User flows

Player menu: explore library, current room/address, favorites, book catalog/search, particle navigation, reading history, writing desk, personal workspace, rental management, notices and collaborative work. Builder tools appear only in the separate authorized builder menu, not as extra player-home slots.

Builder menu: template capture and catalog, room roles, door configuration, props/categories, Diversity/variant sources, preview, district diagnostics, generation controls, backups and migration reports. All wands retain consistent first/second-corner behavior, visible prompts and cancel/undo.

Writing desk: edit pages in Book & Quill; insert supported formatting and dividers through explicit controls; preview before publishing. Unknown markup is escaped, not executed. Player-provided text must not gain run-command/click-event privileges. Page numbering belongs in the reading presentation/renderer; do not repeatedly append numbers to editable text or truncate prose to make a footer fit. Preserve rejected/stale edits for recovery.

## Release gates

- Reproduce the supplied large-room-and-surrounding-hallways arrangement with synthetic templates; demonstrate that a locally fitting blocker is rejected in favor of a complete district.
- Seeded layouts are reproducible; identical plans use stable derived IDs. Turns, even-width doors, multiple approaches, vertical floor offsets, mirrors and Diversity doors all have tests.
- Budget exhaustion never commits a disconnected district or seals a potentially viable socket. Detect invalid overlaps, unpaired internal openings and reused sockets.
- Save/restart during every construction phase resumes the same reservation graph without duplicated rooms/books.
- Favorite a specific generated room, restart and navigate back to that same instance; a reset invalidates the destination clearly rather than redirecting it silently.
- Track a book moved between shelves and route only authorized readers to its current reachable shelf.
- Claim/invite/leave/expire/recover rental data with simulated clock and crash points; prove that non-members cannot access private records.
- Separate menu permissions and all legacy feature equivalents are exercised on a disposable Paper server.
- Validate book formatting, Unicode, page limits, numbering, shared edit conflicts and publication rollback without loss of authored text.
- Compare old/new save data in a migration dry-run. Backups and an explicit recovery path precede any live data conversion.
