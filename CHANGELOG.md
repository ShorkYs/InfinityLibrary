# Changelog

## 2.5.1

- Align new connection anchors/openings at identical coordinates, sharing a wall plane.
- Apply the same geometry to catalog search, lookahead, sibling reservations, loops,
  commitment validation and queued recovery; preserve solid-interior collision checks.
- Allow only matching boundary faces and passive tiled corner edges to share volume.
- Preserve existing shell blocks across template, Diversity and prop writes, then clear
  joined apertures explicitly. Supports even widths, transforms and UP/DOWN connections.
- Invalidate old unbuilt reservations while preserving placed room origins and recovery.
- Added seven shared-plane geometry tests and fourteen actual Paper regression assertions.

## 2.5.0

- Consider every eligible catalog template, with separate alternatives per template/topology;
  retain room/variant weighting while prioritizing fit and preserving viable connections.
- Check other placed rooms' open exits during sibling search and again before committing;
  connect compatible adjacent exits as loops and score candidate continuations.
- Close proven dead ends with configurable boundary walls; exact even-width and UP/DOWN
  openings, persisted restore data, reload recovery, automatic reopening on catalog changes,
  `/il reopendeadends`, and closed-exit diagnostics. Budget exhaustion never proves a dead end.
- Added variant-only Diversity connections and a two-corner connection wand, capture/load
  editing and removal. Resolve block/connection choices before planning and persist them
  for reservations, placed rooms and queued recovery. Alternatives can replace original doors.
- Added catalog/terrain/frontier/topology/geometry tests and actual Paper wand/wall/recovery checks.

## 2.4.1

- Fixed movement using the old player position instead of the destination.
- Added bounded periodic proximity checks, plus join/teleport arrival checks.
- Try nearby planned sibling exits when the closest door cannot expand; retry cooldowns
  are per door, and distant reservations no longer consume the built-room allowance.
- Added `/il diagnose`, expansion state in `/il status`, startup eligibility warnings,
  and connection dimensions/floors in `/il roominfo`.
- Added disposable Paper scheduler and movement-event regression checks.

## 2.4.0

- Added Room Diversity: paired target/source corner selections with exact X/Y/Z checks,
  multiple weighted alternatives, original-design weighting, empty alternatives, staging,
  saved-room editing and per-instance frozen replacement patches.
- Alternative air clears the original target, and generated block states follow room
  transforms. Captured source builds are not live dependencies.
- Added Diversity wand/menu controls and commands; legacy-variation removal helps migration.
- Made EXACT the decoration default: one full-size template matching dimensions/category,
  with optional matching rotation and whole-area emptiness. Smaller entries are not fallback.
- Added the native category dialog after area selection, chat fallback and saved-area
  category/fill-mode editing. PACKED remains an explicit legacy option.
- Added complete 3x3x3 starter arrangements for the standard room slots and retained old
  generated decoration snapshots.
- Tracked plugin-version changes as resource inputs to prevent a cached old descriptor.
- Preserved Diversity fields through role/area/chance/archive operations and persisted
  recovery snapshots before queued placement resumes.

## 2.3.0

- Added STARTING room type, automatic entrance selection on save/conversion, and explicit
  `/il startroom` / `/il roomtype` commands. STARTING is excluded from every expansion path.
- Decoupled `/il baseroom` from the entrance selection. Existing libraries are rebuilt
  only when reset is explicitly requested.
- Added persistent default/custom template removal, archived template retention for built
  rooms, bulk default removal with role checks, and restoration/list-archive commands.
- Added holder-backed room-management menus, STARTING controls, removal confirmation,
  authoritative slot-to-template mapping and administrator checks.
- Validated the start template and world height before reset clears existing room volumes.
- Preserved legacy saved geometry and added atomic room-catalog file replacement.

## 2.2.0

- Added one standard modular room with four independently filled prop areas, alongside
  five starter prop templates. Single-base-room mode is the new expansion default;
  mixed template selection remains available with `/il baseroom all`.
- Added prop capture, category filtering, relative weights, optional rotations, configurable
  gaps, floor-only furniture placement and optional 3D stacking.
- Added bounded randomized packing that combines differently sized props without overlaps.
- Added a prop-area wand, a prop browser and room-area editing/inspection commands.
- Saved per-instance decoration snapshots and queued area clearing/prop placement;
  prop edits/deletion affect future instances rather than rerolling existing decoration.
- Validated shell/doorway clearances, area overlaps, capture sizes and catalog limits.
- Kept legacy room data and earlier downloadable versions.

## 2.1.0

- Added a 54-slot main menu to `/il`, `/il menu`, `/il gui` and library lecterns.
- Added a custom chest writing desk with native multiline text dialogs, book details,
  page insertion/deletion/navigation, preview, signing and library publication.
- Added persistent manuscripts, blank-book payment protection and durable publication.
- Added holder/owner checks, canceled transfers/drags, and deferred menu navigation.
- Replaced greedy single-door expansion with sibling layout beam search, geometric
  continuation scoring, reserved neighbor volumes, exact adjacent-exit linking,
  commit-time terrain checks and persisted plans.
- Added planned-room counts and bounded planning controls.
- Kept 2.0 downloads and existing world/template/book data compatible.

## 2.0.0

Initial migration of the supplied Minecraft 1.21.11 project to Paper 26.3 / Java 25,
with generation controls, queued placement recovery, safer room operations and
book ownership fixes. Earlier downloads remain in the repository's downloads directory.
