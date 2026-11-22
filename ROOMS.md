# Room roles — 4.0

Use the builder **Save selected room** dialog or `/il saveroom <id> <type>`. Change a saved unused template in its details or with `/il roomtype <id> <type>`.

| Role | Purpose |
| --- | --- |
| STARTING | Entrance only; excluded from normal expansion |
| FILLER | Corridors, turns, junctions and connecting spaces |
| READING | Reading areas; lecterns open the collection, marked seats support sitting |
| RENTABLE | Free timed private workspaces with shelf archive and notes |
| PUBLIC_WRITING | Communal writing areas; lecterns open the writing desk |
| BOOK | Retained legacy book-room role and navigation destination |

`READING` is stored as legacy-compatible `READ`. The new default rental and public writing examples include shelves, tables, seats and lecterns; existing instances are not rebuilt. Roles influence layout preferences but do not relax collision/door rules. Only positive-weight active templates can expand the library.

# Starting rooms and removing default templates

Version 2.3 adds a **STARTING** room type and persistent template removal. These are
administrator actions (`infinitylibrary.admin`).

## Make your entrance

Build your entrance, select its full bounds with the existing selection tools, and
stage at least one usable exit. Then save it:

```text
/il saveroom my_entrance STARTING
```

Saving STARTING automatically selects that template for the **next library reset**.
It is never inserted as an ordinary expanding room. Prop areas and variations work on
STARTING templates just as on other rooms.

To turn an existing active template into your entrance:

```text
/il roomtype my_entrance STARTING
```

Or open `/il roomgui`, click the template, and click **Use As STARTING Room**. This sets
the type and selects it. The room list marks STARTING templates with a nether star and
shows which template is selected for the next reset.

You can save several STARTING templates. Switch between them using:

```text
/il startroom my_entrance
```

`/il setstartroom` is an alias. The selected template must already have STARTING type.
Saving or converting another STARTING template makes that one the active selection.
To demote the active STARTING room to another type, select a different STARTING room first.

## Entrance versus expansion base

The entrance and expanding-room template are now independent:

```text
/il startroom my_entrance
/il baseroom my_standard_room
```

`my_standard_room` should have FILLER, BOOK or READ type. `/il baseroom all` enables mixed
selection among eligible normal templates. Every STARTING template is excluded from
both mixed selection and manual expansion, not just the currently selected one.

Changing the base no longer changes your entrance. If you convert the current base
itself to STARTING, expansion switches to mixed mode, because a STARTING room cannot
serve as the normal expansion base. Choose a different normal base afterwards if desired.

`/il setstart` still sets **coordinates**, using your current position. It does not select
a room template. Legacy 2.2 configurations where a FILLER template was both start and base
remain readable until you choose your own roles; the upgrade does not rebuild those rooms.
Fresh installs use a dedicated `builtin_start` STARTING template with the modular footprint.

## Apply the new entrance to the world

Choosing an entrance leaves your existing library available. `/il home` continues to
return to its actual current entrance. When you intend to rebuild the library, back up
the world and plugin data, then run:

```text
/il reset confirm
```

Reset removes generated room volumes and their player changes, then builds the selected
STARTING template at the configured coordinates. It validates that the selected template
exists and fits world height **before** clearing generated rooms. Reset is still synchronous
and can pause a large server. Plugin YAML backups do not include world region files.

## Remove the packaged/default rooms

Choose your custom STARTING room and normal base first. For example, once both have
been captured:

```text
/il startroom my_entrance
/il baseroom my_standard_room
/il removedefaults confirm
```

This removes all currently active packaged templates from normal generation and room
menus. Their removal is saved and persists across reloads and restarts; the plugin does
not recreate these templates on the next load. It does not remove default **props**.

The room browser also has **Remove Default Rooms**, with a confirmation screen. Choosing
Cancel leaves the catalog unchanged. Removal is refused if the selected entrance or base
is still one of the defaults. The error points to the relevant start/base command.
The entire bulk operation is checked first, so it cannot partly remove defaults and then
stop on a protected template.

To remove one default or custom template instead:

```text
/il deleteroom builtin_book
```

You can also shift-right-click it in the room list, or use **Remove Template** in details.
The active entrance/base must be changed before removing that particular template.

## What happens to built rooms?

Built rooms remain in the world. Their removed templates are retained in an **archive**
so door connections, home lookup, recovery and reset cleanup remain valid. Archived
templates do not appear in the active room list, cannot be chosen as a start/base, and
cannot be selected for new expansion. Future reservations are replanned after catalog
changes. Already queued placement can finish using its retained template/snapshot.

Use:

```text
/il listarchivedrooms
/il restoreroom builtin_book
```

Restoring reactivates the exact saved template, including its role and prop areas. It
does not automatically select it as an entrance/base. Restoration and subsequent removal
also persist across reloads. To edit a template that built rooms reference, keep using a
new room ID rather than replacing its geometry.

`rooms.yml` now stores active `rooms`, retained `archived-rooms`, and
`removed-default-rooms`. Keep this file with `generated.yml` when backing up/restoring.
Earlier plugin versions do not understand the removal/archive metadata; restore a matching
pre-upgrade backup if you intend to downgrade rather than using an older JAR on 2.3 data.

## Commands added in 2.3

| Command | Effect |
| --- | --- |
| `/il saveroom <id> STARTING` | Capture and select an entrance for the next reset |
| `/il roomtype <id> <STARTING/FILLER/BOOK/READ>` | Change a saved template's role; STARTING also selects it |
| `/il startroom <id>` | Select an existing STARTING template |
| `/il removedefaults confirm` | Remove all active default templates after role checks |
| `/il restoreroom <id>` | Reactivate an archived template |
| `/il listarchivedrooms` | List retained removed templates |

Existing `/il baseroom`, `/il deleteroom`, `/il roomgui`, `/il setstart`, `/il home` and
`/il reset confirm` remain available with the behavior explained above.
