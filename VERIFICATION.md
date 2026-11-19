# Version 3.0 verification

- Server Java 25 / Paper 26.3 build 253: build succeeded; **117 JUnit tests** passed.
- Disposable Paper server: **219 integration assertions** passed, including exploration expansion, queued recovery, shared doorway planes, frontier protection, exact/category and packed props, Diversity topology/persistence, native personal/shared book edits, temporary-item payment protection, automatic bookshelf stocking, admin companion handshake/denial, unified engine continuity, two-click room/reading region staging, undo/redo, apply and region YAML round-trip.
- Fabric 26.3 / Loader 0.20.1 / API 0.166.2+26.3: client build passed. Actual modded client launched under a virtual display with a software Vulkan renderer, joined a disposable Paper server, received the catalog and opened its custom builder screen.

The server harness uses proxy players for player events/inventory and a real Paper world. This is not a full multiplayer or GPU compatibility test. The search is deliberately bounded and cannot prove a globally optimal layout. Back up your library and try this major update on a copy first.

# Version 2.6.0 verification — 2026-11-17

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Clean build and automated tests

`clean build` and the final incremental `build` passed with **99 JUnit tests**, zero failures:

- 38 retained geometry, room-planning/catalog, manuscript and prop-packing/persistence tests.
- 7 exact-decoration tests: reject smaller entries, require category and all dimensions,
  reject equal volume with different height, allow only explicitly enabled matching rotations,
  disabled entries/whole-area emptiness, exact-mode migration, wildcard and seeded choices.
- 9 Room Diversity tests: exact source dimensions, local-coordinate replacement, original
  choices, disabled-choice fallback, varied seeds, empty alternatives, room/snapshot round
  trips, overlap/door protection, malformed data and legacy reads.

Fixed Gradle resource-input tracking for the plugin version. The clean release JAR's
`plugin.yml` was inspected and reports **2.6.0**, matching the actual release filename.

## Community, reading, preview and navigation (2.6)

25 additional JUnit tests cover:

- Incremental path search around walls, enclosed goals, diagonal corner rejection, work/node
  caps, step-up headroom, ladder columns and rejecting drops through solid blocks.
- Shared manuscript stale-edit rejection, revocation, owner-only access/publishing controls,
  visibility ownership, retained credits, immutable state, page/member/content limits.
- Room graph routing for shared and legacy doors, excluding unavailable exits, matching
  dimensions/floors, same-room destinations and unreachable room types.
- Atomic Unicode YAML persistence, refusing malformed input without replacing it, failed-write
  preservation, notice expiry/content validation and reading page bounds.

The final disposable Paper run adds 53 assertions to the previous 141-check suite. It tests
shared-draft privacy/edits/revocation/reload, simultaneous stale edits, coauthor metadata,
durable shared publication and payment, transactional bad-data reloads and save-failure
rollback, native reader construction/resume/completion, private catalog/feed exclusion,
notice permissions/pins/expiry data/cooldowns, physical board interaction, menu exposure,
private bookmarks, unavailable shelf handling, owner-only particles, real-wall path detours,
magma/headroom/fence rejection, route cancellation, preview permissions/client packets,
unchanged real blocks, rotation/mirroring/live restoration, explicit Diversity preview,
world-switch isolation and complete plugin reload cleanup/persistence.

The proxy actors do not hold chunks like real players. The harness gives only its fixture
chunks test-plugin tickets for realistic loaded terrain; production navigation and previews
never add tickets or load chunks. Player actors are simulated; native dialogs are constructed and their backing operations
are exercised. Real response routing, multiplayer rendering, clicks and large-server
performance need in-game checks.
No real Minecraft client was connected, and no production world was reset or altered.

## Shared-plane regressions (2.5.1)

Seven new JUnit tests cover all six faces and sixteen transforms, exact even-width opening
cells, actual candidate origins with no outward offset, solid-interior collisions,
misaligned/mismatched width/floor/direction rejection, passive corner-pillar sharing,
rejection of one-block-apart loop anchors, and shared-face sibling planning.

Fourteen new assertions on Paper test solid authored room shells: interior terrain blocks
are rejected and preserved, door anchors and all nine aperture cells coincide, future
reservations use the new stride, pending placement recovers without duplicate rooms,
solid marker blocks are cleared, existing gold wall/floor/ceiling blocks survive,
there is no extra doorway plane, old-spacing plans are invalidated without moving placed
rooms, and a three-room shared-wall chain completes. Player protocol remains simulated;
no real Minecraft client was connected.

## Planner, dead-end and Diversity-door tests (2.5)

13 additional JUnit tests cover all-catalog coverage beyond old caps, unknown versus blocked
terrain, connection budgets, loops, preserving another parent's last continuation through
the actual search helper, variant topology enumeration, using a variant door as an entrance,
closed/original door alternatives, snapshot/template-edit stability, incomplete enumeration,
invalid doorway bounds, even-width/vertical geometry and transforms, and deferring unknown
preservation checks. Terrain functions are injected for these pure tests.

The disposable Paper run additionally tested exact GOLD_BLOCK walls, cardinal even widths,
vertical square openings, remaining viable exits, no blocks outside the room, close/restore
persistence across engine reload, replanning restoration, the actual variant-source wand,
clearing a captured wall opening, pre-placement variant snapshots, future reservations,
template edits and expansion through frozen variant doors. Player events are simulated,
with real Paper world/scheduler/configuration behavior. No Minecraft client was connected.

## Exploration regression checks

Real Paper scheduler/event dispatch with proxy players verified: a blocked nearest exit
allows a viable sibling, cooldowns do not block sibling planning, reservations do not
consume the built-room cap, stationary exploration resumes after queued construction,
the cap remains enforced, movement uses its destination, cancelled moves do not generate,
teleport arrivals trigger expansion, and diagnostics identify unavailable/STARTING bases.
No real client connected and the user's particular server configuration is unverified.

## Actual Paper server checks

The final correctly labeled 2.6.0 JAR passed **194 integration assertions**, including:

- Four-click Diversity capture with an external source, rejected dimension mismatch,
  prevention of silently saving an incomplete capture, multiple/empty alternatives,
  captured room-local bounds, saved definitions and metadata preservation.
- Diversity/decor-area overlap and administrator guards.
- A generated alternative frozen before placement, surviving removed source builds,
  edited template data and reload; clearing leftover default blocks; correct transformed
  world positions and facing.
- A native decoration-category dialog after selection, required completion before save,
  and the selected category stored with exact area dimensions.
- Smaller/wrong-category entries excluded despite much higher weights; missing exact
  matches leave the area empty; room-role edits retain exact-selection settings.
- Legacy variation removal preserves Diversity data.
- Prior book-writing/menu safety, durable publication/payment rollback, prop snapshots,
  room planning/recovery, STARTING controls, persistent default removal/archive/restore,
  home/reset and post-removal expansion checks.

Process-state-dependent startup assertions can change the count on a fresh test folder.
The recorded final run used saved data from the previous disposable test run.

## Harness and practical limits

`verification/LibraryProbe.java` is destructive test-only code: it resets the library,
writes fixtures/configuration and shuts down the disposable server. Never install it
on a valuable server. Its player/inventory protocol is simulated with Java proxies; the
Paper world, block data, metadata, inventories, dialogs, scheduler and persistence are real.

No Minecraft client was connected. Native dialogs were constructed and their backing
operations exercised through server methods/commands; real client rendering, input callback
routing, multiplayer synchronization and visual quality still require in-game checks.
Third-party compatibility and large-server load capacity are not certified.

Room/packed-prop planning remains bounded. Exact decoration chooses only full transformed
size/category matches. Capture excludes entities/full block-entity NBT, and custom Diversity
floor/wall edits do not constitute a global pathfinding guarantee. Earlier plugin versions
cannot safely preserve every new Diversity/snapshot field; use matching backups to downgrade.
Authentication/discovery services were unavailable, so the local server used offline mode
on loopback. No production server configuration or runtime data is included.
