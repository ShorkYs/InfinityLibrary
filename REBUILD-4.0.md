# Server-only rebuild: 4.0 implementation

The live `GenerationEngine` uses `DistrictLayoutPlanner`, replacing the earlier one-generation neighborhood planner. `TopologySearch` supplies transformed template/Diversity candidates and continuation witnesses. `generated.yml` is the live atomic construction/reservation record. The separate `nextgen` prototype models and journal remain isolated test utilities; they are not a second running generator or a second source of world state.

## Planning and construction

Search states contain proposed room volumes, links, open sockets and dependencies. Candidate sets interleave alternatives from enabled templates rather than consuming the entire attempt budget on one template's rotations. Existing viable exits must remain connectable, every remaining planned exit needs a continuation witness, and automatic unlimited growth must retain an expansion frontier. Scores favor useful room roles, template diversity, larger spaces and loops; compact filler corridors can connect them. All retained proposals use exact shared-plane door geometry and are checked against terrain, bounds and other reservations.

A district is persisted before construction. Only rooms reachable from completed parents can be placed. Pending placements, frozen props/Diversity and unbuilt descendants survive reload. A changed obstacle discards the affected unbuilt branch; already built rooms remain fixed. Candidate identity is derived from the seed, template, transform, position and frozen variant choices.

The default search considers at most 12 reserved rooms, depth 6, beam width 4 and 768 candidate states, with separate topology/terrain budgets. It runs synchronously against server-owned world data, with queued block construction; it is not an asynchronous global solver. Large catalogs can cause tick spikes; measure a copy of your world before raising budgets. Safe deferral can occur before the configured room count is reached. Lookahead improves fit but cannot promise that every custom catalog can expand forever.

## Player services

Room favorites reference library and room UUIDs. Reset changes the library identity, so favorites do not silently point at unrelated rooms. Named coordinate bookmarks retain their explicit coordinates. Room graph routing and incremental walking search show particles only to the requesting player, reject private rental shortcuts and never force-load distant navigation chunks.

Rentals are timed free leases stored independently of room geometry. Their immutable records and plain-text book archives are atomically saved. Physical shelf displays contain placeholders, with private content delivered only after authorization. Owner archives survive expiry/reset; invited access ends on expiry/revocation. Public writing rooms open the writing desk and reading rooms open the collection through their lecterns. Tagged seats and tables support the existing reading interaction.

Personal manuscripts use native Book & Quill. Formatting supports bold, italic, headings and dividers without parsing arbitrary commands or click events. Guided reading tracks progress for public, personal and authorized rental books. Shared manuscripts retain explicit editor lists, owner-only publication and stale-revision rejection.

## Authoring and compatibility

Vanilla wands retain two-corner selections, action-bar prompts, particles, undo/redo and cancel. Native dialogs configure capture roles, decoration fill/category, connections and Diversity sources/doors. Room previews remain client-only block previews, with transforms and variant choices. The previous companion transport is disabled by default and can be explicitly enabled for compatibility; no new mod is shipped.

Existing templates, archives, BOOK/READ role names, room UUIDs, positions, books, drafts, community records, props and Diversity snapshots remain supported. Schema migration first backs up existing YAML and retires the old default single-modular restriction. See MIGRATION-4.0.md for recovery and limits. This is a service-by-service rebuild around preserved data formats, not a deletion of all legacy compatibility code.
