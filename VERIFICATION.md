# Version 2.4.0 verification — 2026-11-16

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Clean build and automated tests

`clean build` passed with **54 JUnit tests**, zero failures:

- 38 retained geometry, room-planning/catalog, manuscript and prop-packing/persistence tests.
- 7 exact-decoration tests: reject smaller entries, require category and all dimensions,
  reject equal volume with different height, allow only explicitly enabled matching rotations,
  disabled entries/whole-area emptiness, exact-mode migration, wildcard and seeded choices.
- 9 Room Diversity tests: exact source dimensions, local-coordinate replacement, original
  choices, disabled-choice fallback, varied seeds, empty alternatives, room/snapshot round
  trips, overlap/door protection, malformed data and legacy reads.

Fixed Gradle resource-input tracking for the plugin version. The clean release JAR's
`plugin.yml` was inspected and reports **2.4.0**, matching the actual release filename.

## Actual Paper server checks

The final correctly labeled 2.4.0 JAR passed **104 integration assertions**, including:

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
