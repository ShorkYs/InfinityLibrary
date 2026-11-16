package com.infinitylibrary.engine;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import com.infinitylibrary.room.RoomManager;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GenerationEngine {
    private final InfinityLibraryPlugin plugin;
    private final RoomManager roomManager;
    private final File file;
    private final List<PlacedRoom> placed = new ArrayList<>();
    private final Queue<Runnable> placementQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vector3i> sealedBlocks = new HashSet<>();
    private final Map<String,Reservation> reservations = new LinkedHashMap<>();
    private final Map<UUID,Long> lastPlanAttempt = new HashMap<>();
    private final LayoutPlanner planner = new LayoutPlanner();
    private record Reservation(UUID parentId,String doorId,LayoutPlanner.Candidate candidate) {}
    private String key(UUID parent,String door) { return parent+"/"+door; }
    private boolean running;
    private boolean paused;
    private final Set<String> pending = new HashSet<>();
    private int taskId = -1;

    public GenerationEngine(InfinityLibraryPlugin plugin, RoomManager roomManager) {
        this.plugin = plugin; this.roomManager = roomManager; this.file = new File(plugin.getDataFolder(), "generated.yml");
    }

    public void start() {
        running = true;
        placementQueue.clear();
        load();
        ensureWorld();
        for (PlacedRoom pr : placed) if (pending.contains(pr.origin().toString()))
            roomManager.get(pr.roomId()).ifPresent(r -> queuePlacement(r, pr.origin(), pr.transform()));
        if (placed.isEmpty()) resetToStart();
        int perTick = Math.max(1, plugin.getConfig().getInt("generation.placement-blocks-per-tick", 1800));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> { for (int i=0;i<perTick;i++) { Runnable r = placementQueue.poll(); if (r == null) break; r.run(); } }, 1L, 1L);
    }
    public void stop() { running = false; if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); save(); }
    public World ensureWorld() {
        String name = plugin.getConfig().getString("world", "InfinityLibrary");
        World w = Bukkit.getWorld(name);
        if (w == null) w = Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NORMAL).generator(new VoidLibraryGenerator()));
        return Objects.requireNonNull(w);
    }

    public void tickPlayer(Player player) {
        if (!running || paused || !placementQueue.isEmpty() || roomLimitReached() || !player.getWorld().getName().equals(plugin.getConfig().getString("world", "InfinityLibrary"))) return;
        double radius = plugin.getConfig().getDouble("proximity-radius", 9.0);
        Location loc = player.getLocation();
        List<Expansion> expansions = new ArrayList<>();
        synchronized (placed) {
            for (PlacedRoom pr : placed) roomManager.get(pr.roomId()).ifPresent(room -> {
                for (ConnectionPoint cp : room.connections()) if (!pr.generatedConnections().contains(cp.id())) {
                    ConnectionPoint worldPoint = cp.transform(pr.transform(), room.size());
                    Location cLoc = pr.origin().add(worldPoint.position()).toLocation(player.getWorld()).add(0.5, 0.5, 0.5);
                    if (cLoc.distanceSquared(loc) <= radius * radius) expansions.add(new Expansion(pr, cp, worldPoint));
                }
            });
        }
        expansions.sort(Comparator.comparingDouble(e -> e.parent().origin().add(e.worldPoint().position()).toLocation(player.getWorld()).distanceSquared(loc)));
        // One plan/commit per movement: the placement queue bounds all subsequent block work.
        if (!expansions.isEmpty()) expand(expansions.getFirst());
    }

    private void expand(Expansion expansion) {
        PlacedRoom parent=expansion.parent(); String door=expansion.localPoint().id();
        if(parent.generatedConnections().contains(door)) return;
        Reservation reservation=reservations.get(key(parent.instanceId(),door));
        if(reservation==null) {
            long now=System.currentTimeMillis();
            if(now-lastPlanAttempt.getOrDefault(parent.instanceId(),0L)<3000) return;
            lastPlanAttempt.put(parent.instanceId(),now);
            planParent(parent,door,null);
            reservation=reservations.get(key(parent.instanceId(),door));
        }
        if(reservation!=null) commit(reservation);
        // Bounded searches do not permanently seal a doorway when no layout was found.
    }

    private void planParent(PlacedRoom parent,String requested,String forcedRoom) {
        Room source=roomManager.get(parent.roomId()).orElse(null); if(source==null) return;
        List<LayoutPlanner.Box> occupied=new ArrayList<>();
        for(PlacedRoom pr:placed) occupied.add(new LayoutPlanner.Box(pr.origin(),pr.size()));
        for(Reservation r:reservations.values()) if(!r.parentId().equals(parent.instanceId())) occupied.add(r.candidate().box());
        int limit=plugin.getConfig().getInt("generation.max-total-rooms",0);
        long otherReservations=reservations.values().stream().filter(r -> !r.parentId().equals(parent.instanceId())).count();
        int capacity=limit<=0?32:(int)Math.max(0,limit-placed.size()-otherReservations);
        int perDoor=Math.max(1,Math.min(32,plugin.getConfig().getInt("generation.planning.candidates-per-door",12)));
        int maxDoors=Math.max(1,Math.min(32,plugin.getConfig().getInt("generation.planning.max-doors",16)));
        Map<String,List<LayoutPlanner.Candidate>> options=new LinkedHashMap<>();
        List<ConnectionPoint> exits=new ArrayList<>(source.connections());
        exits.sort(Comparator.comparingInt(cp -> cp.id().equals(requested)?0:1));
        for(ConnectionPoint cp:exits) {
            if(parent.generatedConnections().contains(cp.id())) continue;
            if(options.size()>=maxDoors) break;
            ConnectionPoint target=cp.transform(parent.transform(),source.size());
            List<RoomManager.RoomSelection> selections;
            if(forcedRoom!=null && cp.id().equals(requested)) {
                Room forced=roomManager.get(forcedRoom).orElseThrow(); selections=new ArrayList<>();
                for(ConnectionPoint entry:forced.connections()) for(RoomTransform t:RoomTransform.all())
                    if(target.compatibleWith(entry.transform(t,forced.size()))) selections.add(new RoomManager.RoomSelection(forced,entry,t));
            } else selections=roomManager.compatibleSelections(target,false);
            List<LayoutPlanner.Candidate> candidates=new ArrayList<>(); Set<String> unique=new HashSet<>();
            for(RoomManager.RoomSelection selection:selections) {
                Room room=selection.room(); ConnectionPoint entrance=selection.localConnection().transform(selection.transform(),room.size());
                Vector3i origin=parent.origin().add(target.position()).add(faceVector(target.direction())).subtract(entrance.position());
                LayoutPlanner.Candidate c=new LayoutPlanner.Candidate(room,selection.localConnection().id(),selection.transform(),origin);
                String signature=room.id()+"/"+origin+"/"+room.connections().stream().map(c::worldDoor).toList();
                if(!unique.add(signature) || occupied.stream().anyMatch(box -> box.overlaps(c.box()))) continue;
                candidates.add(c); if(candidates.size()>=perDoor) break;
            }
            options.put(cp.id(),candidates);
        }
        int[] budget={Math.max(1000,Math.min(1000000,plugin.getConfig().getInt("generation.planning.world-block-budget",150000)))};
        List<Room> catalog=roomManager.allRooms().stream().filter(r -> !r.id().equals(plugin.getConfig().getString("generation.start-room-id","builtin_start"))).toList();
        LayoutPlanner.Result result=planner.plan(options,occupied,
            Math.max(1,Math.min(32,plugin.getConfig().getInt("generation.planning.beam-width",12))),capacity,
            c -> isWorldClear(c.origin(),c.box().size(),budget),
            (c,obstacles) -> planner.continuationScore(c,catalog,obstacles),requested);
        if(forcedRoom!=null && !result.neighbors().containsKey(requested)) return;
        reservations.values().removeIf(r -> r.parentId().equals(parent.instanceId()));
        result.neighbors().forEach((door,c) -> reservations.put(key(parent.instanceId(),door),new Reservation(parent.instanceId(),door,c)));
        save();
    }

    private boolean commit(Reservation reservation) {
        if(roomLimitReached()) return false;
        PlacedRoom parent=placed.stream().filter(p -> p.instanceId().equals(reservation.parentId())).findFirst().orElse(null);
        if(parent==null || parent.generatedConnections().contains(reservation.doorId())) { reservations.remove(key(reservation.parentId(),reservation.doorId())); return false; }
        LayoutPlanner.Candidate c=reservation.candidate();
        // Revalidate terrain and reservations immediately before mutating any blocks.
        if(!isWorldClear(c.origin(),c.box().size(),new int[]{Integer.MAX_VALUE})
            || placed.stream().anyMatch(p -> new LayoutPlanner.Box(p.origin(),p.size()).overlaps(c.box()))
            || reservations.values().stream().filter(r -> r!=reservation).anyMatch(r -> r.candidate().box().overlaps(c.box()))) {
            reservations.values().removeIf(r -> r.parentId().equals(parent.instanceId())); save(); return false;
        }
        reservations.remove(key(parent.instanceId(),reservation.doorId()));
        parent.generatedConnections().add(reservation.doorId());
        PlacedRoom pr=new PlacedRoom(UUID.randomUUID(),c.room().id(),c.origin(),c.box().size(),c.transform());
        pr.generatedConnections().add(c.entranceId());
        connectExistingExits(pr,c.room());
        placed.add(pr);
        pending.add(pr.origin().toString());
        // Reserve the child's open exits before its first block is placed.
        planParent(pr,null,null);
        queuePlacement(c.room(),c.origin(),c.transform()); save(); return true;
    }

    private void connectExistingExits(PlacedRoom next,Room room) {
        for(ConnectionPoint cp:room.connections()) {
            if(next.generatedConnections().contains(cp.id())) continue;
            ConnectionPoint door=cp.transform(next.transform(),room.size());
            Vector3i adjacent=next.origin().add(door.position()).add(faceVector(door.direction()));
            for(PlacedRoom existing:placed) {
                Room template=roomManager.get(existing.roomId()).orElse(null); if(template==null) continue;
                for(ConnectionPoint old:template.connections()) {
                    if(existing.generatedConnections().contains(old.id())) continue;
                    ConnectionPoint other=old.transform(existing.transform(),template.size());
                    if(door.compatibleWith(other) && adjacent.equals(existing.origin().add(other.position()))) {
                        next.generatedConnections().add(cp.id()); existing.generatedConnections().add(old.id());
                        reservations.remove(key(existing.instanceId(),old.id()));
                    }
                }
            }
        }
    }

    private void sealConnection(PlacedRoom parent, ConnectionPoint target) {
        if (!plugin.getConfig().getBoolean("generation.path-blocking.enabled", true)) return;
        World w = ensureWorld();
        Material blockMaterial = Material.matchMaterial(plugin.getConfig().getString("generation.path-blocking.material", "SMOOTH_STONE"));
        if (blockMaterial == null) blockMaterial = Material.SMOOTH_STONE;
        Vector3i base = parent.origin().add(target.position());
        Vector3i outward = faceVector(target.direction());
        for (int dy=0;dy<target.height();dy++) for (int dw=-(target.width()/2);dw<=target.width()/2;dw++) {
            int x = base.x() + outward.x(), y = base.y() + dy + outward.y(), z = base.z() + outward.z();
            if (target.direction()==BlockFace.NORTH || target.direction()==BlockFace.SOUTH) x += dw;
            else if (target.direction()==BlockFace.EAST || target.direction()==BlockFace.WEST) z += dw;
            w.getBlockAt(x,y,z).setType(blockMaterial, false);
            sealedBlocks.add(new Vector3i(x,y,z));
        }
    }

    public void resetToStart() {
        World w = ensureWorld();
        synchronized (placed) {
            for (PlacedRoom pr : placed) clearBox(w, pr.origin(), pr.size());
            for (Vector3i pos : sealedBlocks) w.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
            placed.clear(); placementQueue.clear(); pending.clear(); reservations.clear(); lastPlanAttempt.clear();
            sealedBlocks.clear();
            Room start = roomManager.get(plugin.getConfig().getString("generation.start-room-id", "builtin_start")).orElseThrow();
            Vector3i origin = new Vector3i(plugin.getConfig().getInt("start-location.x"), plugin.getConfig().getInt("start-location.y"), plugin.getConfig().getInt("start-location.z"));
            PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), start.id(), origin, start.size());
            placed.add(pr); queuePlacement(start, origin, RoomTransform.IDENTITY);
        }
        save();
    }

    private void queuePlacement(Room room, Vector3i origin, RoomTransform transform) {
        World w = ensureWorld();
        pending.add(origin.toString());
        for (RoomBlock rb : room.blocks()) {
            Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
            BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
            placementQueue.add(() -> {
                Block block = w.getBlockAt(pos.x(), pos.y(), pos.z());
                block.setBlockData(data, false);
                if (block.getType() == Material.CHISELED_BOOKSHELF) plugin.getBookStorageManager().populateBookshelf(block);
                if (block.getState() instanceof Sign sign) updateLibrarySign(sign, pos);
            });
        }
        Map<String, List<VariationArea>> byCategory = new HashMap<>();
        for (VariationArea v : room.variations()) byCategory.computeIfAbsent(v.category(), k -> new ArrayList<>()).add(v);
        for (List<VariationArea> categoryVars : byCategory.values()) {
            Collections.shuffle(categoryVars);
            for (VariationArea v : categoryVars) {
                if (v.chancePercent() <= 0 || Math.random() * 100.0 >= v.chancePercent()) continue;
                for (RoomBlock rb : v.blocks()) {
                    Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
                    BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
                    placementQueue.add(() -> w.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(data, false));
                }
                break;
            }
        }
        placementQueue.add(() -> { pending.remove(origin.toString()); save(); });
    }

    private void rotateData(BlockData data, RoomTransform transform) {
        if (transform.flipX()) data.mirror(org.bukkit.block.structure.Mirror.FRONT_BACK);
        if (transform.flipZ()) data.mirror(org.bukkit.block.structure.Mirror.LEFT_RIGHT);
        data.rotate(switch (transform.rotation()) {
            case NONE -> org.bukkit.block.structure.StructureRotation.NONE;
            case CLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
            case CLOCKWISE_270 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
        });
    }

    private void updateLibrarySign(Sign sign, Vector3i pos) {
        if (placed.size() < plugin.getConfig().getInt("generation.sign-min-rooms", 6)) return;
        Vector3i start = new Vector3i(plugin.getConfig().getInt("start-location.x"), plugin.getConfig().getInt("start-location.y"), plugin.getConfig().getInt("start-location.z"));
        String bookDir = nearestRoomDirection(pos, RoomType.BOOK);
        String readDir = nearestRoomDirection(pos, RoomType.READ);
        sign.setLine(0, "Infinity Library");
        sign.setLine(1, "Start: " + directionToward(pos, start));
        sign.setLine(2, "Book:" + (bookDir == null ? "?" : bookDir) + " Read:" + (readDir == null ? "?" : readDir));
        sign.setLine(3, "Rooms: " + placed.size());
        sign.update(true, false);
    }

    private String nearestRoomDirection(Vector3i from, RoomType type) {
        double best = Double.MAX_VALUE;
        Vector3i bestTarget = null;
        synchronized (placed) {
            for (PlacedRoom pr : placed) {
                Room room = roomManager.get(pr.roomId()).orElse(null);
                if (room == null || room.type() != type) continue;
                Vector3i target = pr.origin().add(new Vector3i(room.size().x()/2, 1, room.size().z()/2));
                double dist = Math.pow(target.x()-from.x(),2) + Math.pow(target.y()-from.y(),2) + Math.pow(target.z()-from.z(),2);
                if (dist < best) { best = dist; bestTarget = target; }
            }
        }
        return bestTarget == null ? null : directionToward(from, bestTarget);
    }

    private String directionToward(Vector3i from, Vector3i to) {
        int dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        if (Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) return dy > 0 ? "UP" : "DOWN";
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "EAST" : "WEST";
        return dz > 0 ? "SOUTH" : "NORTH";
    }

    private boolean isWorldClear(Vector3i origin, Vector3i size,int[] budget) {
        World w = ensureWorld();
        if (origin.y() < w.getMinHeight() || (long) origin.y() + size.y() > w.getMaxHeight()) return false;
        if (!w.getWorldBorder().isInside(origin.toLocation(w)) || !w.getWorldBorder().isInside(origin.add(size).subtract(new Vector3i(1,1,1)).toLocation(w))) return false;
        for (int x=origin.x(); x<origin.x()+size.x(); x++) for (int y=origin.y(); y<origin.y()+size.y(); y++) for (int z=origin.z(); z<origin.z()+size.z(); z++) {
            if (budget[0]-- <= 0) return false;
            Material m = w.getBlockAt(x,y,z).getType();
            if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) return false;
        }
        return true;
    }


    private void clearBox(World w, Vector3i o, Vector3i s) {
        for (int x=o.x(); x<o.x()+s.x(); x++) for (int y=o.y(); y<o.y()+s.y(); y++) for (int z=o.z(); z<o.z()+s.z(); z++) w.getBlockAt(x,y,z).setType(Material.AIR, false);
    }
    private Vector3i faceVector(BlockFace f) { return new Vector3i(f.getModX(), f.getModY(), f.getModZ()); }

    public int generateAroundPlayer(Player player, int maxRooms) {
        int generated = 0;
        for (int i = 0; i < maxRooms; i++) {
            int before;
            synchronized (placed) { before = placed.size(); }
            tickPlayer(player);
            int after;
            synchronized (placed) { after = placed.size(); }
            if (after <= before) break;
            generated += (after - before);
        }
        return generated;
    }

    public boolean placeRoomImmediately(String roomId) {
        Room room=roomManager.get(roomId).orElse(null);
        if(paused||roomLimitReached()||!placementQueue.isEmpty()||room==null) return false;
        for(PlacedRoom parent:List.copyOf(placed)) {
            Room source=roomManager.get(parent.roomId()).orElse(null); if(source==null) continue;
            for(ConnectionPoint cp:source.connections()) {
                if(parent.generatedConnections().contains(cp.id())) continue;
                planParent(parent,cp.id(),roomId);
                Reservation r=reservations.get(key(parent.instanceId(),cp.id()));
                if(r!=null && r.candidate().room().id().equals(roomId)) return commit(r);
            }
        }
        return false;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration(); y.set("pending", new ArrayList<>(pending)); y.set("paused", paused); y.set("world", plugin.getConfig().getString("world")); y.set("sealed-blocks", sealedBlocks.stream().map(Vector3i::toString).toList()); ConfigurationSection root = y.createSection("rooms"); int i=0;
        synchronized (placed) { for (PlacedRoom pr : placed) { ConfigurationSection s = root.createSection(String.valueOf(i++)); s.set("uuid", pr.instanceId().toString()); s.set("room-id", pr.roomId()); pr.origin().write(s.createSection("origin")); pr.size().write(s.createSection("size")); writeTransform(s.createSection("transform"), pr.transform()); s.set("generated-connections", new ArrayList<>(pr.generatedConnections())); } }
        ConfigurationSection plans=y.createSection("plans"); int j=0;
        for(Reservation r:reservations.values()) {
            ConfigurationSection s=plans.createSection(String.valueOf(j++));
            s.set("parent",r.parentId().toString()); s.set("door",r.doorId()); s.set("room",r.candidate().room().id());
            s.set("entrance",r.candidate().entranceId()); r.candidate().origin().write(s.createSection("origin"));
            writeTransform(s.createSection("transform"),r.candidate().transform());
        }
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Unable to save generated.yml: " + e.getMessage()); }
    }
    private void load() {
        placed.clear(); pending.clear(); sealedBlocks.clear(); reservations.clear(); if (!file.exists()) return; YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.getString("world", plugin.getConfig().getString("world")).equals(plugin.getConfig().getString("world"))) throw new IllegalStateException("Restore the configured library world before loading generated.yml");
        pending.addAll(y.getStringList("pending")); paused = y.getBoolean("paused");
        for (String v : y.getStringList("sealed-blocks")) sealedBlocks.add(Vector3i.parse(v));
        ConfigurationSection root = y.getConfigurationSection("rooms"); if (root == null) return;
        for (String key : root.getKeys(false)) { ConfigurationSection s = root.getConfigurationSection(key); PlacedRoom pr = new PlacedRoom(UUID.fromString(s.getString("uuid")), s.getString("room-id"), Vector3i.read(s.getConfigurationSection("origin")), Vector3i.read(s.getConfigurationSection("size")), readTransform(s.getConfigurationSection("transform"))); pr.generatedConnections().addAll(s.getStringList("generated-connections")); placed.add(pr); }
        ConfigurationSection plans=y.getConfigurationSection("plans");
        if(plans!=null) for(String key:plans.getKeys(false)) try {
            var s=plans.getConfigurationSection(key); UUID parent=UUID.fromString(s.getString("parent"));
            Room room=roomManager.get(s.getString("room")).orElseThrow(); String door=s.getString("door");
            PlacedRoom owner=placed.stream().filter(p -> p.instanceId().equals(parent)).findFirst().orElseThrow();
            if(owner.generatedConnections().contains(door)) continue;
            reservations.put(key(parent,door),new Reservation(parent,door,new LayoutPlanner.Candidate(room,s.getString("entrance"),readTransform(s.getConfigurationSection("transform")),Vector3i.read(s.getConfigurationSection("origin")))));
        } catch(RuntimeException e) { plugin.getLogger().warning("Skipping invalid room reservation: "+e.getMessage()); }
    }
    private void writeTransform(ConfigurationSection s, RoomTransform transform) {
        s.set("rotation", transform.rotation().name());
        s.set("flip-x", transform.flipX());
        s.set("flip-z", transform.flipZ());
    }
    private RoomTransform readTransform(ConfigurationSection s) {
        if (s == null) return RoomTransform.IDENTITY;
        return new RoomTransform(Rotation.valueOf(s.getString("rotation", Rotation.NONE.name())), s.getBoolean("flip-x", false), s.getBoolean("flip-z", false));
    }
    public boolean roomLimitReached() { int limit = plugin.getConfig().getInt("generation.max-total-rooms", 0); return limit > 0 && placed.size() >= limit; }
    public int plannedCount() { return reservations.size(); }
    public int placedCount() { return placed.size(); }
    public int queuedBlocks() { return placementQueue.size(); }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean value) { paused = value; save(); }
    public boolean usesRoom(String id) { return placed.stream().anyMatch(r -> r.roomId().equals(id)) || reservations.values().stream().anyMatch(r -> r.candidate().room().id().equals(id)); }
    public Location homeLocation() {
        World w = ensureWorld();
        if (!placementQueue.isEmpty()) throw new IllegalArgumentException("Library construction is still in progress. Try again shortly.");
        if (placed.isEmpty()) throw new IllegalArgumentException("No starting room has been generated.");
        PlacedRoom start = placed.getFirst();
        for (int y = start.origin().y() + 1; y < start.origin().y() + start.size().y() - 1; y++)
            for (int x = start.origin().x() + 1; x < start.origin().x() + start.size().x() - 1; x++)
                for (int z = start.origin().z() + 1; z < start.origin().z() + start.size().z() - 1; z++) {
                    if (w.getBlockAt(x,y-1,z).getType().isSolid() && w.getBlockAt(x,y,z).isEmpty() && w.getBlockAt(x,y+1,z).isEmpty())
                        return new Location(w,x+0.5,y,z+0.5);
                }
        throw new IllegalArgumentException("No safe standing space in the starting room.");
    }
    private record Expansion(PlacedRoom parent, ConnectionPoint localPoint, ConnectionPoint worldPoint) {}
}
