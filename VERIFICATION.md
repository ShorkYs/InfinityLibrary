# Version 2.5.1 verification — 2026-11-17

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Clean build and automated tests

`build` passed with **74 JUnit tests**, zero failures:

- 38 retained geometry, room-planning/catalog, manuscript and prop-packing/persistence tests.
- 7 exact-decoration tests: reject smaller entries, require category and all dimensions,
  reject equal volume with different height, allow only explicitly enabled matching rotations,
  disabled entries/whole-area emptiness, exact-mode migration, wildcard and seeded choices.
- 9 Room Diversity tests: exact source dimensions, local-coordinate replacement, original
  choices, disabled-choice fallback, varied seeds, empty alternatives, room/snapshot round
  trips, overlap/door protection, malformed data and legacy reads.

Fixed Gradle resource-input tracking for the plugin version. The clean release JAR's
`plugin.yml` was inspected and reports **2.5.1**, matching the actual release filename.

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

The final correctly labeled 2.5.1 JAR passed **141 integration assertions**, including:

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
