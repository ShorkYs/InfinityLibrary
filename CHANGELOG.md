# Changelog

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
