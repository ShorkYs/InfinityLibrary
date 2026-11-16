# Verification — 2026-11-16

Target: Paper Minecraft 26.3 build 253 (`63e41db`), API `26.3.build.253-stable`.
Runtime: Eclipse Temurin Java 25.0.5; build: Gradle 9.2.1.

## Completed

- Compilation against the actual 26.3 API and JAR packaging: passed.
- Three JUnit geometry tests: passed, zero failures.
  - Every transform maps a non-square 7×3×9 room bijectively into its transformed bounds.
  - Connections retain matching dimensions and opposite directions under each transform.
  - Unsupported connection directions and zero width are rejected.
- Live Paper 26.3 startup/shutdown: plugin 2.0.0 enabled and disabled successfully.
- Console checks: status, pause, resume, room details, reload, reset confirmation guard.
- Eight live integration assertions on a disposable server: passed.
  - Starting room finishes queued construction.
  - Home location provides empty standing space.
  - A compatible book room attaches to the starting room.
  - Manual room placement stays queued.
  - Reload recovers unfinished room placement with the same placed-room count.
  - Pause persists through reload.
  - Recovered construction completes.
  - Plugin YAML backup ZIP is created.

The verification-only companion plugin source is in `verification/`. It was compiled
against the release JAR and Paper dependencies, installed only on the disposable test
server, and removed from deliverables intended for production installation. **Do not
install the probe on a real server:** it resets the library and shuts down its test server.

External Minecraft authentication/discovery requests failed in this environment;
local offline server tests still completed. No multiplayer client was connected.
No claims are made about full multiplayer UI/book interaction coverage, load capacity,
complete world-data migration, or third-party plugin compatibility.

## Recommended in-game acceptance check

On a copy of your server, join as both an operator and an ordinary player. Walk through
new doorways, save a custom room with connections, test home, submit and return books,
search public/private titles with two accounts, check daily draft behavior with a full
inventory, and disconnect during a book edit. Restart during a slow room placement and
verify recovery. Measure tick times with your expected room sizes and player count.
