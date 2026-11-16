# Version 2.2.0 verification — 2026-11-16

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Build and automated tests

Compilation, JAR packaging and **30 JUnit tests** passed with zero failures:

- 3 geometry tests and 5 layout-planner tests retained from 2.1.
- 5 manuscript editing/validation tests retained from 2.1.
- 12 prop-packing tests: five `2×1×1` props fill `2×5×1`; `2×3×1 + 2×2×1`
  fill that same area; the equivalent horizontal strip; configurable empty space;
  category/weight filtering; rotation-dependent fitting; bounded work; deterministic
  catalog ordering/seeds; different layouts across seeds; nonoverlap/bounds/support
  checks over 40 seeds; and floor-only placement/height clearance.
- 5 prop/area persistence tests: template and frozen-composition round trips, legacy room
  reads, room-area round trips, shell/door/overlap rejection, invalid settings and
  rejection of snapshot blocks outside their areas.

## Actual Paper integration server

**52 assertions passed** on a disposable Paper 26.3 server, covering:

- Standard modular room creation with four empty placeholders and floor-only defaults.
- Different decorations for different room instances and starter prop availability.
- Two-click area staging, room-local coordinates, permission checks and stale-selection
  rejection after selecting a different region.
- Directional block-state capture, prop-catalog persistence and a custom prop-bearing room.
- Decoration resolved before block placement, catalog edits affecting future compositions,
  and a pending room retaining its frozen blocks after source-prop edits/deletion/reload.
- Correct world positions and transformed facing for the frozen prop blocks.
- Prop-browser construction and ordinary players not receiving prop-authoring controls.
- Previous construction/reload/reservation/pause/home/backup checks.
- Main menu/writer construction, transfer/drag/hotbar protections, ownership and permission
  checks, deferred actions, native dialog construction, draft persistence, signing,
  publication, private ownership, collection reload, and payment preservation/rollback
  after an injected storage-write failure.

The companion source is in `verification/`. It **resets the library, writes test props,
changes disposable-server config, and shuts down that server**. Never install it on a
valuable or production server. Its player/inventory protocol is simulated using Java
proxies, while Paper world blocks, metadata, dialogs and YAML persistence are real.

## Remaining manual verification

No Minecraft client was connected. These checks do not prove client rendering, dialog
response routing, multiplayer inventory synchronization, visual prop composition, or
third-party plugin compatibility. Test with a 26.3 client and your actual prop library
before using this on a valuable world. Load/performance capacity is not certified.

Prop filling and room layout use bounded heuristics rather than exhaustive/global
optimization. Packing support uses occupied bounding boxes, not physical block solidity
or gravity. Some valid areas can remain partly empty when templates or budgets constrain
fitting. Template capture still excludes entities and full block-entity NBT.

Authentication/discovery services were unavailable in this environment; the local test
server ran in offline mode on loopback. Production server settings are not included.
