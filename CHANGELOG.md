# Changelog

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
