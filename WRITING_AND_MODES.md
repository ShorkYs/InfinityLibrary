# Native writing and physical shelves

The writing desk opens a temporary Book & Quill in an empty hotbar slot. Right-click it to use Minecraft's native editor. Done saves its pages into the personal/shared manuscript; signing captures pages but final publication stays in the desk. Temporary books cannot be traded, dropped or used for payment. Shared manuscripts retain optimistic revision checks. A rejected stale save leaves recovery text in the temporary book for copying.

Published books queue a bounded scan of loaded library chunks. Empty chiseled-bookshelf slots receive category-matching catalogued books; occupied slots are preserved and a shelf does not receive the same ID twice. Generated and newly placed shelves are registered too. `/il shelves` queues another pass. Bookshelf slot block states and searchable physical locations update when stocking succeeds. Unloaded chunks are not force-loaded for stocking.

Generation is now one unified system. See [BUILDER.md](BUILDER.md) for migration, the new planner and the optional Fabric companion. No second generation world is created.
