# Free rental workspaces

A builder saves a template with role `RENTABLE`. Players enter a completed instance, open `/il rent` (or **Rental workspaces** in player home), and choose **Claim current room**. No economy plugin or payment is used. Each player can have one active rental in the current library.

The default claim lasts seven days (`rentals.duration-hours: 168`, bounded 1–2160 hours). Renewal sets expiry from the current time. The workspace menu shows expiry and offers books, notes, invitations, renewal and a confirmed release.

## Books and notes

**Archive held book** copies the text of your eligible signed book or Book & Quill into the workspace without consuming the original item. **Archive manuscript** copies your personal saved draft. These are text records, not an inventory for storing arbitrary items. Temporary editing items and managed shelf displays cannot be archived.

Each workspace holds up to 64 book records and 60 notes of up to 4,000 characters. Owners and active invited editors can read the archive and add notes. An authorized editor can delete their own contributed books; the owner can delete any workspace book after confirmation. Restoring a manuscript requires an empty personal draft, so existing writing is never overwritten. New-draft and delete controls ask for confirmation.

Loaded chiseled bookshelves in the rented room show managed book placeholders. Right-click a shelf or lectern to open the authorized workspace. Secret pages are not written into those display items. Existing nonmanaged shelf contents are not overwritten. Extra archived books remain available through the workspace menu even when the room has fewer shelf slots. Shelf trails require a loaded shelf; navigate to the room first when it is distant.

The guided reader saves pages and can resume from **Continue reading**. **Book view** opens Minecraft's native book screen; Minecraft does not report those page turns to this plugin, so only the guided reader updates progress.

## Invitations and expiry

The owner can invite an exact online player name or UUID from the menu, and revoke by UUID. Up to 16 editors are supported. A roommate invitation does not automatically grant access to separate collaborative manuscripts: invite those editors explicitly through **Collaborative books**.

Uninvited players cannot walk or teleport into an active rental; navigation avoids routing through it. Claims/revocations also remove unauthorized online occupants. Library structure protection blocks ordinary building/breaking, hoppers and common environmental changes. Builders can still administer the physical world; builder permission does not automatically reveal private archive text.

After expiry or release, room access becomes public and invited archive access ends. The original owner keeps the full workspace in `/il rent`, including books and notes, as a read-only archive. The next renter gets a new empty lease. Library reset or a missing room does not delete owner archives. Owners can read or restore their text without the old room; a trail requires a valid current destination.

Data files: `rentals.yml` and `rental-books.yml`. Back up both together with the rest of the plugin folder. These are access controls within the plugin, not encryption against a server owner with filesystem access.
