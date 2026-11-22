# Version 4.0.1 bookshelf recovery fix

Java 25 / Paper Minecraft 26.3 build 253: build succeeded, **149 JUnit tests passed**, and the final release JAR passed **283 disposable Paper integration assertions**.

Twelve additional runtime checks cover full shelves retaining pending books across reload, immediate delivery to new shelf space, reassignment from a full stale destination, preserving unrelated items, automatic reload restocking, unique catalog IDs, remaining overflow using the next shelf, no extra copies in surplus space, uncategorized books on general shelves, tracked direct lectern submissions, and recovering old untagged copies without duplication. The retained rental/private-shelf, generation, navigation, writing and community checks also passed. The shelf fixture cleans up its own added shelves before the independent rental fixture.

Stocking inspects loaded chunks; it never force-loads the whole library. Startup, chunk entry, publication/submission, room construction and reload activate the stocker. Existing valid copies remain assigned; unplaced books remain in `books.yml` until matching space is available. Validation uses proxy player actors with real Paper inventories, block state, scheduler, saves and reloads; this is not a real-client rendering or large-server performance benchmark.

---

# Version 4.0 verification

Target: **Paper Minecraft 26.3 build 253**, Java 25, Gradle 9.2.1.

- **149 JUnit tests passed**, no failures/errors/skips. Covers retained geometry, shared planes, even doors, transforms, props, Diversity, pathfinding, collaboration and persistence, plus district dependencies, stable seeded IDs, preservation of other approaches, unknown-budget deferral, automatic frontier retention, rental archives/expiry, typography and rejecting malformed catalogs without discarding active/archive state.
- **271 disposable Paper integration assertions passed** against the final `4.0.0` JAR. Covers automatic exploration, blocked-nearest-exit fallback, multi-generation future-parent reservations and reload recovery; props/Diversity and variant-door freezing; publication rollback; real loaded chiseled-bookshelf stocking; previews and private particles; separate menu permissions and favorites; free rental claims, invitations, notes and private books; native builder dialogs; reader progress/revocation; movement, teleport and hopper protection; owner archive retention; malformed manuscript load preservation. Retained companion protocol checks explicitly enable that legacy transport only in this fixture.
- **12 release smoke checks passed** in a separate fresh Paper world with the normal mixed catalog. Verified exact release version, default disabled companion transport/no incoming channel, old modular-base/schema migration, automatic expansion, future-generation reservations, completed block queue, an open growth frontier and reload persistence.

- **2 external startup-recovery checks passed:** intentionally malformed `drafts.yml` prevented plugin startup, and SHA-256 comparisons confirmed all five tracked manuscript/collection/layout/catalog/prop files were byte-for-byte unchanged after shutdown.

The harness uses proxy player actors with real Paper worlds, inventories, events, scheduler and persistence. It constructs native dialogs and tests their backing services; it does not establish a complete real-client click/rendering or simultaneous multiplayer test. Vanilla native books supply their page counter; only guided-reader turns update saved progress. Large-world performance and every third-party protection-plugin combination have not been benchmarked.

The live generator is the bounded `DistrictLayoutPlanner` integrated with `GenerationEngine`; the separate `nextgen` model/journal tests are isolated foundations, not evidence for a second live generator. Search runs on the server thread, construction is queued, and exhausted budgets defer safe expansion. No claim of universally perfect/infinite routing is made.

See `verification/README.md` for the destructive disposable-server harness instructions. Build it with `./gradlew paperProbeJar`; never install it on a real library server. See MIGRATION-4.0.md for consistent backups and recovery.
