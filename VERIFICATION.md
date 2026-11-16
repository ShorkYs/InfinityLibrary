# Version 2.1.0 verification — 2026-11-16

Target: Paper Minecraft 26.3 build 253 (`63e41db`), Java 25.0.5, Gradle 9.2.1.

## Build and unit tests

Compilation, JAR packaging and **13 JUnit tests** passed with zero failures:

- 3 geometry tests: transformed bounds/bijection, matching connectors, invalid connectors.
- 5 layout-planner tests: retain alternatives to fit both sibling doors, reservations,
  terrain/cap rejection, score with sibling obstacles, open-versus-blocked lookahead,
  and avoid repeated terrain checks on each beam branch.
- 5 manuscript tests: write/insert/navigate/delete pages, retain one editable page,
  enforce page limits, preserve text after invalid input, require title/content for
  publication, and reject invalid metadata without partially changing details.

## Disposable Paper integration server

**33 assertions passed** against the actual 26.3 server/API, covering:

- Starting-room construction, safe home and planner-driven room attachment.
- Multiple sibling reservations before placement; reservations restored after reload
  and a later process start; unfinished queued construction recovery; pause persistence.
- Plugin data backup creation.
- Main menu and writing desk construction, expected controls, administrator visibility,
  holder/owner protections, shift-click/hotbar/drag cancellation and next-tick page actions.
- Manuscript details and multiline pages saved to disk and reloaded.
- Native multiline, book-details and search dialog construction through Paper's API.
- Preview page preservation and private-book ownership.
- Signing with player authorship, blank-book consumption, publishing and collection reload.
- Injected collection-write failure: publication rolls back and does not consume payment.
- Refusal to consume a book & quill containing existing text.

The companion plugin source is in `verification/`. It runs only on a **disposable test
server**: it resets the library and shuts down the server. Do not install it in production.
Its player/inventory protocol is simulated with Java proxies while the Paper world,
inventories, item metadata, dialogs and persistence use the actual server/API.

## Scope and remaining manual verification

No Minecraft client was connected. These checks do not prove visual appearance,
client dialog input/response routing, multiplayer inventory synchronization, accessibility,
or compatibility with other plugins. Test the real UI with a 26.3 client and two accounts
before using it on a valuable world. Also test with your custom templates and player load.
The bounded planner does not guarantee a globally optimal or entirely connected layout.

Minecraft authentication/discovery requests were unavailable in this environment; local
server tests ran in offline mode on loopback. Production server settings are not included.
