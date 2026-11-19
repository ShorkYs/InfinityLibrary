> **3.0 update:** active planning now uses neighborhood constraint search rather than the older sibling beam search described below. See [BUILDER.md](BUILDER.md#generator). Shared planes, frontier checks, queued placement and dead-end behavior remain applicable.

# Room planning, dead ends and variant doors — 2.5

## Shared wall planes (2.5.1)

The next room's entrance is aligned exactly with the existing exit: both anchors and every
opening cell occupy the same coordinates. Placement, reservations, lookahead, loops and
commit-time checks use this spacing. There is no extra outward block offset.

Bounding boxes can overlap by a single boundary layer where opposing compatible doorways
coincide. Unrelated shared faces and interior overlap are rejected. Corner pillars/edges
between tiled rooms can be shared too. Terrain checks exclude only accepted shared shell
cells of actual placed rooms; solid blocks elsewhere still block the candidate.

The existing room owns shared wall/floor/ceiling blocks. The incoming template, Diversity
patches and props cannot overwrite those cells. Joined door openings are explicitly cleared
after placement so solid authoring markers do not leave an extra wall. Up/down openings
share the same floor/ceiling plane. Existing room positions and saved connections stay
unchanged on update. Plans written before 2.5.1 are invalidated and rebuilt using the new
spacing; a pending placed room retains its saved origin during recovery. No reset required.

## Plan before building

The planner considers every enabled template in the configured expansion pool (`/il baseroom all`
uses all eligible normal rooms; a selected base restricts that pool). It compares multiple
placements/rotations/reflections for each template and active Diversity door layout, plans
siblings together, and checks every unused connection on each candidate for a continuation.
It also checks open exits on other placed rooms. An existing exit that still has a possible continuation must remain connectable; a
placement is deferred if the planner cannot prove that it preserves those exits. Compatible doors at the same coordinates connect as
loops instead of requiring another room. Reservations, terrain and world boundaries are
checked before placement; saved reservations are revalidated at commitment.

This is bounded lookahead, not a proof that an unlimited future library will fit. Beam search
retains the best layouts rather than every permutation, and terrain/connection budgets limit
server work. A search that runs out of budget reports an unknown fit internally; it cannot
prove a dead end and must not cause a wall. Uncertain preservation checks defer placement.
`/il diagnose` reports when planning is deferred. Increase planning budgets for very large templates.
Distant provisional reservations do not count as built rooms. Existing rooms are not moved.

```yaml
generation:
  planning:
    beam-width: 12
    # Alternatives per room/topology choice per door; every eligible template is visited.
    candidates-per-door: 12
    max-doors: 16
    topology-choices-per-room: 64
    connection-check-budget: 200000
    world-block-budget: 300000
```

If Diversity combinations exceed `topology-choices-per-room`, those omitted combinations
remain unknown for dead-end classification. Room chance and variant weights influence
candidate order; fit and preserving existing exits take priority over chance.

## Dead-end walls

When construction is idle, the engine gradually checks unreserved open exits. If no enabled
compatible room fits any checked orientation, the opening is replaced with a wall **on the
room's own boundary**, not outside in its neighbor's space. Cardinal openings use exactly
width × height blocks, including even widths; UP/DOWN openings are width × width squares.
Connected doors stay open. A temporary reservation by itself cannot prove a permanent dead
end. Paused generation and an empty eligible room pool do not start closing doors.

```yaml
generation:
  path-blocking:
    enabled: true
    material: STONE_BRICKS
    # Optional full block state overrides material:
    # block-data: "minecraft:oak_planks"
```

Use a solid block. Invalid/non-solid values fall back to smooth stone. Save the configuration
and use `/il reload`. Closed-door metadata and the original opening blocks survive restart.
Changing the base/catalog replans and restores dead-end openings before checking them again.
`/il reopendeadends` also restores them; a door that remains impossible can close again.
Disabling `path-blocking.enabled` and reloading restores these walls. `/il status` and
`/il diagnose` report closed/open counts. No world reset is needed to update.

## Put a connection in a Diversity alternative

1. Select the entire authoring room. Capture a Diversity target and its equally sized source
   with `/il diversitywand`, as described in [DIVERSITY.md](DIVERSITY.md). Include the entire
   opening in the target area, on the outer room wall/floor/ceiling.
2. After capturing the source, run:

   ```text
   /il diversityconnection <areaId> <variantId> <NORTH|EAST|SOUTH|WEST|UP|DOWN> [floor] [id]
   ```

   For example: `/il diversityconnection eastwall open EAST 0 side_door`.
3. Hold the supplied **Diversity Connection Wand** and click two opposite corners of the
   opening on the captured **source variation**. Direction means outward in the original
   room's coordinates. North/south openings must lie in one Z plane; east/west in one X
   plane. UP/DOWN openings must be square in one Y plane.
4. Save the room normally or `/il applydiversity <roomId>`. Use another ID to add a second
   door, or reuse an ID to replace that door. The floor index defaults to 0 and must match
   the neighboring connection, along with width and height.

The wand removes captured blocks inside the opening so its connection cannot be hidden
behind the captured wall. The entire opening must fit inside the Diversity target and on
the outside boundary of the complete room. A decoration cannot obstruct its clearance.

To edit a saved variant, select the authoring room and `/il diversityload <roomId>`, then use
the same connection command. A newly loaded definition has no remembered source coordinates:
click the desired opening corners **on the target area** instead. The command tells you
whether it expects SOURCE or TARGET clicks. Directions autocomplete. To remove a staged door:

```text
/il removediversityconnection <areaId> <variantId> <id>
/il applydiversity <roomId>
```

Default designs retain normal room connections. An alternative replaces any original
connections fully inside its target with its own connections. An alternative with no
connections can therefore turn an original opening into a wall (capture that wall in its
source). Partial doorway targets are rejected. Original doors outside the target remain.

The chosen block patch and its connections are fixed together **before** the room is planned.
A variation door can be the room's entrance. Only selected doors participate in planning,
loop connections, expansion and dead-end sealing. Rotations/reflections apply to both blocks
and doors. Built instances and queued recovery retain their saved door snapshots even if
someone later edits the template. Definition changes affect future instances only.

## Builder preview and player navigation (2.6)

`/il preview <roomId>` renders a temporary client-only view with transform and Diversity
controls. `/il navigate` follows actually connected completed rooms and safe walking cells.
See [COMMUNITY.md](COMMUNITY.md) for usage and configuration.
