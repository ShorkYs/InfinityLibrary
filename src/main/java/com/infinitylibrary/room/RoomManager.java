package com.infinitylibrary.room;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class RoomManager {
    private final InfinityLibraryPlugin plugin;
    private final File roomFile;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, UUID> selectionWorld = new HashMap<>();
    private final Map<UUID, Vector3i> pos1 = new HashMap<>();
    private final Map<UUID, Vector3i> pos2 = new HashMap<>();

    public RoomManager(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        this.roomFile = new File(plugin.getDataFolder(), "rooms.yml");
    }

    public void load() {
        rooms.clear();
        addBuiltinRooms();
        if (!roomFile.exists()) save();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(roomFile);
        var root = yml.getConfigurationSection("rooms");
        if (root != null) for (String id : root.getKeys(false)) {
            try { rooms.put(id, Room.read(id, root.getConfigurationSection(id))); }
            catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Skipping invalid room " + id, ex); }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        var root = yml.createSection("rooms");
        for (Room room : rooms.values()) room.write(root.createSection(room.id()));
        try { yml.save(roomFile); } catch (IOException e) { throw new IllegalStateException("Unable to save rooms.yml", e); }
    }

    public Collection<Room> allRooms() { return Collections.unmodifiableCollection(rooms.values()); }
    public Optional<Room> get(String id) { return Optional.ofNullable(rooms.get(id)); }
    public void delete(String id) {
        if (!rooms.containsKey(id)) throw new IllegalArgumentException("Room not found: " + id);
        if (id.startsWith("builtin_") || plugin.getGenerationEngine().usesRoom(id)) throw new IllegalArgumentException("Cannot delete built-in or placed templates. Use /il roomchance <id> 0 to disable future spawning.");
        rooms.remove(id);
        String startId = plugin.getConfig().getString("generation.start-room-id", "builtin_start");
        if (id.equals(startId)) {
            String replacement = rooms.keySet().stream().findFirst().orElse("builtin_start");
            plugin.getConfig().set("generation.start-room-id", replacement);
            plugin.saveConfig();
        }
        save();
    }
    public void setPos1(Player p) { setPos1(p, p.getLocation()); }
    public void setPos2(Player p) { setPos2(p, p.getLocation()); }
    public void setPos1(Player p, Location location) { checkSelectionWorld(p, location); pos1.put(p.getUniqueId(), Vector3i.from(location)); }
    public void setPos2(Player p, Location location) { checkSelectionWorld(p, location); pos2.put(p.getUniqueId(), Vector3i.from(location)); }
    private void checkSelectionWorld(Player p, Location l) {
        UUID world = l.getWorld().getUID();
        if (!world.equals(selectionWorld.put(p.getUniqueId(), world))) { pos1.remove(p.getUniqueId()); pos2.remove(p.getUniqueId()); }
    }
    public Vector3i relativeToSelection(Player p, Location location) {
        SelectionBounds bounds = selectionBounds(p);
        int minX = bounds.min().x(), minY = bounds.min().y(), minZ = bounds.min().z();
        return new Vector3i(location.getBlockX() - minX, location.getBlockY() - minY, location.getBlockZ() - minZ);
    }

    public SelectionBounds selectionBounds(Player p) {
        Vector3i a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null) throw new IllegalArgumentException("Set both selection positions before using the connection wand");
        return new SelectionBounds(
                new Vector3i(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Vector3i(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()))
        );
    }

    public record SelectionBounds(Vector3i min, Vector3i max) {}

    public Room capture(Player player, String id, RoomType type, List<ConnectionPoint> connections) {
        if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Room ID must use 1-64 lowercase letters, digits, underscores or hyphens.");
        if (plugin.getGenerationEngine().usesRoom(id)) throw new IllegalArgumentException("This template is already placed. Save under a new ID to preserve existing connections.");
        if (!player.getWorld().getUID().equals(selectionWorld.get(player.getUniqueId()))) throw new IllegalArgumentException("Select both corners in your current world.");
        Vector3i a = pos1.get(player.getUniqueId()), b = pos2.get(player.getUniqueId());
        if (a == null || b == null) throw new IllegalArgumentException("Set both selection positions first");
        World w = player.getWorld();
        int minX = Math.min(a.x(), b.x()), minY = Math.min(a.y(), b.y()), minZ = Math.min(a.z(), b.z());
        int maxX = Math.max(a.x(), b.x()), maxY = Math.max(a.y(), b.y()), maxZ = Math.max(a.z(), b.z());
        long volume = ((long)maxX-minX+1)*((long)maxY-minY+1)*((long)maxZ-minZ+1);
        if (volume > plugin.getConfig().getLong("rooms.max-capture-volume", 100000)) throw new IllegalArgumentException("Selection is too large: " + volume + " blocks.");
        Set<String> ids = new HashSet<>();
        for (ConnectionPoint cp : connections) {
            Vector3i v = cp.position();
            if (!ids.add(cp.id()) || v.x()<0 || v.y()<0 || v.z()<0 || v.x()>maxX-minX || v.y()>maxY-minY || v.z()>maxZ-minZ) throw new IllegalArgumentException("Duplicate or out-of-bounds connection: " + cp.id());
        }
        List<RoomBlock> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            Block block = w.getBlockAt(x, y, z);
            if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
                blocks.add(new RoomBlock(new Vector3i(x - minX, y - minY, z - minZ), block.getBlockData().getAsString()));
            }
        }
        Room room = new Room(id, type, new Vector3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), connections, blocks, plugin.getSelectionManager().stagedVariations(player), 100.0);
        rooms.put(id, room);
        save();
        return room;
    }

    public Optional<RoomSelection> selectCompatible(ConnectionPoint target, boolean preferBook) {
        List<RoomSelection> candidates = new ArrayList<>();
        String startRoomId = plugin.getConfig().getString("generation.start-room-id", "builtin_start");
        for (Room room : rooms.values()) {
            if (room.id().equals(startRoomId)) continue;
            if (preferBook && room.type() != RoomType.BOOK) continue;
            if (room.spawnChance() <= 0 || Math.random() * 100.0 >= room.spawnChance()) continue;
            for (ConnectionPoint cp : room.connections()) for (RoomTransform transform : RoomTransform.all()) {
                ConnectionPoint transformed = cp.transform(transform, room.size());
                if (target.compatibleWith(transformed)) candidates.add(new RoomSelection(room, cp, transform));
            }
        }
        if (candidates.isEmpty() && preferBook) return selectCompatible(target, false);
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    public List<RoomSelection> compatibleSelections(ConnectionPoint target, boolean preferBook) {
        List<RoomSelection> candidates = new ArrayList<>();
        String startRoomId = plugin.getConfig().getString("generation.start-room-id", "builtin_start");
        for (Room room : rooms.values()) {
            if (room.id().equals(startRoomId)) continue;
            if (preferBook && room.type() != RoomType.BOOK) continue;
            if (room.spawnChance() <= 0 || Math.random() * 100.0 >= room.spawnChance()) continue;
            for (ConnectionPoint cp : room.connections()) for (RoomTransform transform : RoomTransform.all()) {
                ConnectionPoint transformed = cp.transform(transform, room.size());
                if (target.compatibleWith(transformed)) candidates.add(new RoomSelection(room, cp, transform));
            }
        }
        if (candidates.isEmpty() && preferBook) return compatibleSelections(target, false);
        Collections.shuffle(candidates);
        return candidates;
    }

    public record RoomSelection(Room room, ConnectionPoint localConnection, RoomTransform transform) {}

    public void appendVariations(String roomId, List<VariationArea> additions) {
        Room existing = rooms.get(roomId);
        if (existing == null) throw new IllegalArgumentException("Room not found: " + roomId);
        List<VariationArea> merged = new ArrayList<>(existing.variations());
        merged.addAll(additions);
        Room updated = new Room(existing.id(), existing.type(), existing.size(), existing.connections(), existing.blocks(), merged, existing.spawnChance());
        rooms.put(roomId, updated);
        save();
    }

    public void setRoomChance(String roomId, double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100) throw new IllegalArgumentException("Chance must be finite and between 0 and 100.");
        Room existing = rooms.get(roomId);
        if (existing == null) throw new IllegalArgumentException("Room not found: " + roomId);
        Room updated = new Room(existing.id(), existing.type(), existing.size(), existing.connections(), existing.blocks(), existing.variations(), chance);
        rooms.put(roomId, updated);
        save();
    }

    private void addBuiltinRooms() {
        rooms.put("builtin_start", rectangular("builtin_start", RoomType.FILLER, 9, 6, 9,
                List.of(new ConnectionPoint("north", new Vector3i(4,1,0), BlockFace.NORTH,3,3), new ConnectionPoint("south", new Vector3i(4,1,8), BlockFace.SOUTH,3,3), new ConnectionPoint("east", new Vector3i(8,1,4), BlockFace.EAST,3,3), new ConnectionPoint("west", new Vector3i(0,1,4), BlockFace.WEST,3,3)), false, false));
        rooms.put("builtin_book", rectangular("builtin_book", RoomType.BOOK, 9, 6, 7,
                List.of(new ConnectionPoint("west", new Vector3i(0,1,3), BlockFace.WEST,3,3), new ConnectionPoint("east", new Vector3i(8,1,3), BlockFace.EAST,3,3)), true, false));
        rooms.put("builtin_read", rectangular("builtin_read", RoomType.READ, 7, 6, 9,
                List.of(new ConnectionPoint("north", new Vector3i(3,1,0), BlockFace.NORTH,3,3), new ConnectionPoint("south", new Vector3i(3,1,8), BlockFace.SOUTH,3,3)), false, true));
        rooms.put("builtin_gallery", rectangular("builtin_gallery", RoomType.BOOK, 13, 7, 9,
                List.of(new ConnectionPoint("north", new Vector3i(6,1,0), BlockFace.NORTH,3,3), new ConnectionPoint("south", new Vector3i(6,1,8), BlockFace.SOUTH,3,3), new ConnectionPoint("east", new Vector3i(12,1,4), BlockFace.EAST,3,3)), true, false));
        rooms.put("builtin_lounge", rectangular("builtin_lounge", RoomType.READ, 11, 6, 11,
                List.of(new ConnectionPoint("west", new Vector3i(0,1,5), BlockFace.WEST,3,3), new ConnectionPoint("east", new Vector3i(10,1,5), BlockFace.EAST,3,3)), false, true));
        rooms.put("builtin_elevator", rectangular("builtin_elevator", RoomType.FILLER, 7, 9, 7,
                List.of(new ConnectionPoint("west", new Vector3i(0,1,3), BlockFace.WEST,3,3), new ConnectionPoint("up", new Vector3i(3,8,3), BlockFace.UP,3,2), new ConnectionPoint("down", new Vector3i(3,0,3), BlockFace.DOWN,3,2)), false, true));
    }

    private Room rectangular(String id, RoomType type, int sx, int sy, int sz, List<ConnectionPoint> cps, boolean bookshelf, boolean seats) {
        List<RoomBlock> blocks = new ArrayList<>();
        for (int x=0;x<sx;x++) for (int z=0;z<sz;z++) { blocks.add(new RoomBlock(new Vector3i(x,0,z), Material.SMOOTH_STONE.getKey().toString())); blocks.add(new RoomBlock(new Vector3i(x,sy-1,z), Material.DARK_OAK_PLANKS.getKey().toString())); }
        for (int x=0;x<sx;x++) for (int y=1;y<sy-1;y++) { blocks.add(new RoomBlock(new Vector3i(x,y,0), Material.BOOKSHELF.getKey().toString())); blocks.add(new RoomBlock(new Vector3i(x,y,sz-1), Material.BOOKSHELF.getKey().toString())); }
        for (int z=0;z<sz;z++) for (int y=1;y<sy-1;y++) { blocks.add(new RoomBlock(new Vector3i(0,y,z), Material.BOOKSHELF.getKey().toString())); blocks.add(new RoomBlock(new Vector3i(sx-1,y,z), Material.BOOKSHELF.getKey().toString())); }
        for (ConnectionPoint cp : cps) for (int dy=0;dy<cp.height();dy++) {
            if (cp.direction() == BlockFace.UP || cp.direction() == BlockFace.DOWN) {
                for (int wx=-(cp.width()/2);wx<=cp.width()/2;wx++) for (int wz=-(cp.width()/2);wz<=cp.width()/2;wz++) removeDoorBlock(blocks, new Vector3i(cp.position().x()+wx, cp.position().y(), cp.position().z()+wz));
            } else {
                for (int w=-(cp.width()/2);w<=cp.width()/2;w++) {
                    int x=cp.position().x(), y=cp.position().y()+dy, z=cp.position().z();
                    if (cp.direction()==BlockFace.NORTH || cp.direction()==BlockFace.SOUTH) x += w; else z += w;
                    removeDoorBlock(blocks, new Vector3i(x, y, z));
                }
            }
        }
        blocks.add(new RoomBlock(new Vector3i(sx/2,sy-2,sz/2), Material.GLOWSTONE.getKey().toString()));
        if (bookshelf) blocks.add(new RoomBlock(new Vector3i(sx/2,1,1), Material.CHISELED_BOOKSHELF.getKey().toString()));
        if (seats) blocks.add(new RoomBlock(new Vector3i(sx/2,1,sz/2), Material.OAK_STAIRS.getKey().toString()));
        if (seats) blocks.add(new RoomBlock(new Vector3i(sx/2+1,1,sz/2), Material.LECTERN.getKey().toString()));
        return new Room(id, type, new Vector3i(sx, sy, sz), cps, blocks, List.of(), 100.0);
    }

    private void removeDoorBlock(List<RoomBlock> blocks, Vector3i doorwayBlock) {
        blocks.removeIf(b -> b.position().equals(doorwayBlock));
    }
}
