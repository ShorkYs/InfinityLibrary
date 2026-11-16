# Version 2.3.0 verification — 2026-11-16

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Compilation and automated tests

Build and JAR packaging passed. **38 JUnit tests** passed with zero failures:

- The 30 existing tests for transforms, sibling layout planning, book drafts, prop packing,
  area safety and frozen prop persistence.
- 8 room-catalog/type tests: persisted default removal, archived geometry retained across
  packaged-default changes, restoration, removed custom templates, batch-removal preflight,
  legacy room-file compatibility, active/archive conflict handling, and STARTING parsing
  and serialization.

## Disposable Paper server

The final run passed **81 integration assertions** on the actual Paper server/API. Coverage
includes all prior construction/planning, book-writing, prop-capture/packing/snapshot,
GUI-protection and storage-failure checks, plus the following room-management workflow:

- Default removals remain suppressed after a real process restart.
- Capturing via `/il saveroom ... STARTING` selects the entrance without expanding it or
  resetting the existing library.
- Normal base selection preserves the independent start choice.
- STARTING cannot be a normal base or manual expansion candidate; mixed selection excludes it.
- Active start/base templates are protected, and bulk removal checks all targets before changes.
- Room GUI confirmation/cancel, hidden removed defaults, and deferred STARTING button action.
- Default/custom template removal retains references required by built rooms and `/il home`.
- Removal, restoration, role and selected-start persistence through reload.
- Invalid reset selection is rejected before generated room volumes are cleared.
- Reset uses the selected custom entrance, places its distinctive floor block at the
  configured location, and normal expansion subsequently attaches successfully with all
  default templates removed.

The actual final run has 81 assertions; counts vary by one on a fresh test data folder
because the process-restart assertion requires a previous saved removal state.

## Test harness and limits

`verification/LibraryProbe.java` is a **destructive disposable-server test harness**. It
resets the library, writes fixtures, changes configuration and shuts down the server.
Never install it on a valuable server. Player/inventory protocol uses Java proxies while
world blocks, item metadata, inventories, dialogs, file persistence and scheduler are real.

No Minecraft client was connected. Client rendering/dialog response routing, full
multiplayer inventory synchronization, third-party plugin compatibility and large-server
load capacity remain manual checks. Test with your own rooms/props and a 26.3 client.

The room/prop planners are bounded heuristics; exact/global optimal layouts are not
promised. Prop support uses bounding boxes and capture excludes full NBT/entities.
Room-removal archives need the 2.3 data format; restore matching backups before downgrading.
Authentication/discovery services were unavailable, so the local test server used offline
mode on loopback. Production server settings are not included in the release.
