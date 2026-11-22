# Updating to 4.0

1. Stop Paper and back up the complete library world and `plugins/InfinityLibraryPlugin/` folder.
2. Replace the old plugin JAR with `InfinityLibraryPlugin-4.0.1.jar`. Keep the data folder. Remove any duplicate old plugin JAR.
3. Run Paper 26.3 with Java 25. The first schema-4 startup copies existing YAML into the plugin's backup directory before changing generation settings.
4. Use `/il status`, `/il diagnose` and `/il builder` to review the catalog and expansion state. No world reset is needed.

Existing placed rooms retain their origins, UUIDs and frozen props/Diversity. Compatible shared-plane reservations are recovered in parent order; obsolete or unreachable unbuilt reservations are discarded and replanned. Pending rooms resume their block queue. Generation state is atomically replaced before queued construction runs.

A previous `builtin_modular` base restriction becomes the mixed catalog on the first v4 migration. Explicit custom bases are retained. All room roles other than STARTING remain eligible when enabled; BOOK and READ remain valid saved names. New commands accept READING as an alias for READ. Old rooms are not rebuilt to add new seats, shelves or roles; changes affect future placements.

The active mode is `district`. Legacy mode names use this same engine rather than creating another library. Any former experimental world/files are left untouched. The old Fabric companion is shelved and its transport is disabled by default. Remove the client mod for ordinary server-only use; administrators maintaining legacy compatibility can explicitly set `builder.legacy-companion-enabled: true`.

## Backups and recovery

`/il backup` flushes current data and creates a YAML backup. It is not a world backup. Back up the world separately with the server stopped or an appropriate server backup tool. Multi-file updates are not a filesystem-wide transaction; restore a consistent complete backup rather than mixing individual files from different dates.

Malformed YAML in manuscripts, books, props or room catalogs now rejects loading. A failed startup does not flush partially initialized managers over those files. Read the named file in the server log, stop the server, and restore it from a consistent backup before restarting. Do not delete `drafts.yml` or the collection to bypass the error.

To roll back, stop the server and restore the pre-update plugin, data folder and world together. Do not run an older plugin against newly modified data without restoring its matching backup. A reset is destructive: `/il reset confirm` intentionally removes generated structures and starts a new library identity. Private rental archives remain, but old room destinations are unavailable.
