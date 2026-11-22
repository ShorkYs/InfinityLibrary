# Disposable Paper integration fixture

**Do not install this on a real server.** The fixture resets the library, edits templates/configuration, creates books and private records, and shuts Paper down after testing. Its actors are proxy players; worlds, block states, inventories, YAML, scheduler and plugin services are real Paper.

With Java 25, run `./gradlew build paperProbeJar`. Copy the release plugin and `build/paper-probe/LibraryProbe.jar` to a disposable Paper 26.3 server. Accept the server EULA yourself. Set `builder.legacy-companion-enabled: true` in the test plugin config so retained companion protocol regressions can run; the release default remains false. Set `generation.max-total-rooms: 0` and normal placement budgets before the run. Start the server and look for `ALL ... INTEGRATION CHECKS PASSED`; an exception shuts it down early and means failure.

The fixture recovers its own data across repeated runs and includes reservation reload checks. Additional release-only smoke checks were run separately against a fresh mixed catalog, the default disabled transport and an intentionally malformed startup file. See the root VERIFICATION.md for recorded results and limits.

`ReleaseSmoke.java` records the additional fresh-world/default-transport fixture. Compile it together with LibraryProbe, use `main: ReleaseSmoke` and `softdepend: [InfinityLibraryPlugin]` in its disposable plugin descriptor, and begin with a fresh world and an old config containing `generation.base-room-id: builtin_modular` without a schema key. For malformed startup testing, compare file hashes externally and shut the disposable server down; this fixture depends on plugin classes and is intended for successful startup.
