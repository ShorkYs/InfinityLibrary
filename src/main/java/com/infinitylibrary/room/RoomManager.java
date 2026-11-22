package com.infinitylibrary.room;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import com.infinitylibrary.props.PropArea;
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
    private final RoomCatalog catalog = new RoomCatalog();
    private final Map<String, Room> rooms = catalog.activeMap();
    private Map<String,Room> builtinRooms=Map.of();
    private final Map<UUID, UUID> selectionWorld = new HashMap<>();
    private final Map<UUID, Vector3i> pos1 = new HashMap<>();
    private final Map<UUID, Vector3i> pos2 = new HashMap<>();

    public RoomManager(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        this.roomFile = new File(plugin.getDataFolder(), "rooms.yml");
    }

    public void load() {
        var data=com.infinitylibrary.community.AtomicYaml.read(roomFile.toPath());
        var previous=new LinkedHashMap<>(rooms);
        rooms.clear();addBuiltinRooms();builtinRooms=new LinkedHashMap<>(rooms);rooms.clear();rooms.putAll(previous);
        boolean missing=!roomFile.exists();
        catalog.load(builtinRooms,data,plugin.getLogger()::warning);
        if(missing)save();
    }

    public void save() {
        java.nio.file.Path target=roomFile.toPath(),temp=target.resolveSibling("rooms.yml.tmp");
        try {
            java.nio.file.Files.writeString(temp,catalog.write().saveToString());
            try{java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){throw new IllegalStateException("Unable to save rooms.yml",e);}
    }
    public Collection<Room> allRooms() {return catalog.active();}
    /** Includes archives for generation/home/reset references; authoring commands use getActive. */
    public Optional<Room> get(String id) {return catalog.get(id);}
    public Optional<Room> getActive(String id) {return Optional.ofNullable(rooms.get(id));}
    public boolean isDefault(String id) {return catalog.isDefault(id);}
    public Set<String> archivedIds() {return catalog.archivedIds();}
    public String activeStartId() {return plugin.getConfig().getString("generation.start-room-id","builtin_start");}
    public List<Room> expansionRooms() {
        String base=plugin.getConfig().getString("generation.base-room-id","builtin_modular");
        return rooms.values().stream().filter(r -> r.type()!=RoomType.STARTING && r.spawnChance()>0)
            .filter(r -> base!=null&&!base.isBlank()?r.id().equals(base):!r.id().equals(activeStartId())).toList();
    }
    private void guardRemoval(String id) {
        if(!rooms.containsKey(id))throw new IllegalArgumentException("Active room not found: "+id);
        if(id.equals(activeStartId()))throw new IllegalArgumentException("Choose your custom STARTING room first: /il startroom <id>. This is the active start template.");
        if(id.equals(plugin.getConfig().getString("generation.base-room-id","builtin_modular")))throw new IllegalArgumentException("Choose another expansion template first: /il baseroom <id|all>. This is the active base template.");
    }
    private void remove(Collection<String> ids) {
        for(String id:ids)guardRemoval(id);
        YamlConfiguration before=catalog.write();catalog.remove(ids);
        try{save();}catch(RuntimeException e){catalog.load(builtinRooms,before,plugin.getLogger()::warning);throw e;}
        plugin.getGenerationEngine().replanFutureRooms();
    }
    public void delete(String id) {remove(List.of(id));}
    public int removeDefaults() {
        List<String> ids=rooms.keySet().stream().filter(catalog::isDefault).toList();
        if(!ids.isEmpty())remove(ids);return ids.size();
    }
    public void restore(String id) {
        YamlConfiguration before=catalog.write();catalog.restore(id);
        try{save();}catch(RuntimeException e){catalog.load(builtinRooms,before,plugin.getLogger()::warning);throw e;}
        plugin.getGenerationEngine().replanFutureRooms();
    }
    public void chooseStart(String id) {
        Room room=getActive(id).orElseThrow(() -> new IllegalArgumentException("Active room not found: "+id));
        if(room.type()!=RoomType.STARTING)throw new IllegalArgumentException("Select a STARTING template, or use /il roomtype "+id+" STARTING.");
        plugin.getConfig().set("generation.start-room-id",id);
        if(id.equals(plugin.getConfig().getString("generation.base-room-id","builtin_modular")))plugin.getConfig().set("generation.base-room-id","");
        plugin.saveConfig();plugin.getGenerationEngine().replanFutureRooms();
    }
    public void chooseBase(String id) {
        if(!id.equalsIgnoreCase("all")){
            Room room=getActive(id).orElseThrow(() -> new IllegalArgumentException("Active room not found: "+id));
            if(room.type()==RoomType.STARTING)throw new IllegalArgumentException("STARTING rooms are reserved for the entrance. Choose a FILLER, BOOK or READ base.");
        }
        plugin.getConfig().set("generation.base-room-id",id.equalsIgnoreCase("all")?"":id);
        plugin.saveConfig();plugin.getGenerationEngine().replanFutureRooms();
    }
    public void setRoomType(String id,RoomType type) {
        Room old=getActive(id).orElseThrow(() -> new IllegalArgumentException("Active room not found: "+id));
        if(old.type()==RoomType.STARTING&&type!=RoomType.STARTING&&id.equals(activeStartId()))throw new IllegalArgumentException("Select another active STARTING room before changing this room's type.");
        Room next=new Room(old.id(),type,old.size(),old.connections(),old.blocks(),old.variations(),old.spawnChance(),old.propAreas(),old.diversityAreas(),old.regions());
        rooms.put(id,next);try{save();}catch(RuntimeException e){rooms.put(id,old);throw e;}
        if(type==RoomType.STARTING)chooseStart(id);else plugin.getGenerationEngine().replanFutureRooms();
    }

    public Runnable checkpoint(Player p){UUID id=p.getUniqueId();Vector3i a=pos1.get(id),b=pos2.get(id);UUID w=selectionWorld.get(id);return ()->{restore(pos1,id,a);restore(pos2,id,b);restore(selectionWorld,id,w);};}
    private static <T> void restore(Map<UUID,T> map,UUID id,T value){if(value==null)map.remove(id);else map.put(id,value);}
    public Optional<Vector3i> point1(Player p){return Optional.ofNullable(pos1.get(p.getUniqueId()));}
    public Optional<Vector3i> point2(Player p){return Optional.ofNullable(pos2.get(p.getUniqueId()));}
    public void clearSelection(Player p){pos1.remove(p.getUniqueId());pos2.remove(p.getUniqueId());selectionWorld.remove(p.getUniqueId());}
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
        if(!p.getWorld().getUID().equals(selectionWorld.get(p.getUniqueId())))throw new IllegalArgumentException("Select both corners in your current world.");
        Vector3i a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null) throw new IllegalArgumentException("Set both selection positions before using the connection wand");
        return new SelectionBounds(
                new Vector3i(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Vector3i(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()))
        );
    }

    public record SelectionBounds(Vector3i min, Vector3i max) {}

    public Room saveVisualTemplate(Room room){
        String id=room.id();if(!id.matches("[a-z0-9_-]{1,64}")||id.startsWith("builtin_"))throw new IllegalArgumentException("Choose a custom room ID (built-ins are protected).");
        if(plugin.isRoomInUse(id))throw new IllegalArgumentException("This template is already placed. Save under a new ID.");
        if(archivedIds().contains(id))throw new IllegalArgumentException("Restore or choose another archived room ID first.");
        if(id.equals(activeStartId())&&room.type()!=RoomType.STARTING)throw new IllegalArgumentException("Choose another active STARTING room before changing this template type.");
        room.validate();Room old=rooms.put(id,room);try{save();}catch(RuntimeException ex){if(old==null)rooms.remove(id);else rooms.put(id,old);throw ex;}plugin.getGenerationEngine().replanFutureRooms();return room;
    }

    public Room capture(Player player, String id, RoomType type, List<ConnectionPoint> connections) {
        if(id.startsWith("builtin_"))throw new IllegalArgumentException("Choose a custom ID; built-in IDs are reserved. Use /il roomtype to change a saved room role.");
        if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Room ID must use 1-64 lowercase letters, digits, underscores or hyphens.");
        if (plugin.isRoomInUse(id)) throw new IllegalArgumentException("This template is already placed. Save under a new ID to preserve existing connections.");
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
        List<PropArea> areas=plugin.getPropManager().staged(player);
        Room.validatePropAreas(new Vector3i(maxX-minX+1,maxY-minY+1,maxZ-minZ+1),connections,areas);
        List<RoomBlock> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            Block block = w.getBlockAt(x, y, z);
            if (!block.getType().isAir() && areas.stream().noneMatch(area -> area.contains(Vector3i.from(block.getLocation()).subtract(new Vector3i(minX,minY,minZ))))) {
                blocks.add(new RoomBlock(new Vector3i(x - minX, y - minY, z - minZ), block.getBlockData().getAsString()));
            }
        }
        Room room = new Room(id, type, new Vector3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), connections, blocks, plugin.getSelectionManager().stagedVariations(player), 100.0,areas,plugin.getDiversityManager().staged(player),plugin.getAuthoringManager()==null?List.of():plugin.getAuthoringManager().regions(player));
        Room previous=rooms.put(id,room);
        try{save();}catch(RuntimeException e){if(previous==null)rooms.remove(id);else rooms.put(id,previous);throw e;}
        if(type==RoomType.STARTING)chooseStart(id);
        return room;
    }

    public Optional<RoomSelection> selectCompatible(ConnectionPoint target,boolean preferBook) {
        return compatibleSelections(target,preferBook).stream().findFirst();
    }
    public List<RoomSelection> compatibleSelections(ConnectionPoint target,boolean preferBook) {
        List<RoomSelection> candidates=new ArrayList<>();
        for(Room room:expansionRooms()) {
            if(preferBook&&room.type()!=RoomType.BOOK)continue;
            if(Math.random()*100>=room.spawnChance())continue;
            for(ConnectionPoint cp:room.connections())for(RoomTransform transform:RoomTransform.all())
                if(target.compatibleWith(cp.transform(transform,room.size())))candidates.add(new RoomSelection(room,cp,transform));
        }
        if(candidates.isEmpty()&&preferBook)return compatibleSelections(target,false);
        Collections.shuffle(candidates);return candidates;
    }

    public record RoomSelection(Room room, ConnectionPoint localConnection, RoomTransform transform) {}

    public void appendVariations(String roomId, List<VariationArea> additions) {
        Room existing = rooms.get(roomId);
        if (existing == null) throw new IllegalArgumentException("Room not found: " + roomId);
        List<VariationArea> merged = new ArrayList<>(existing.variations());
        merged.addAll(additions);
        Room updated = new Room(existing.id(), existing.type(), existing.size(), existing.connections(), existing.blocks(), merged, existing.spawnChance(),existing.propAreas(),existing.diversityAreas(),existing.regions());
        rooms.put(roomId, updated);
        save();
    }

    public void setRoomChance(String roomId, double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100) throw new IllegalArgumentException("Chance must be finite and between 0 and 100.");
        Room existing = rooms.get(roomId);
        if (existing == null) throw new IllegalArgumentException("Room not found: " + roomId);
        Room updated = new Room(existing.id(), existing.type(), existing.size(), existing.connections(), existing.blocks(), existing.variations(), chance,existing.propAreas(),existing.diversityAreas(),existing.regions());
        rooms.put(roomId, updated);
        save();
    }

    public void updatePropAreas(String roomId,List<PropArea> areas) {
        Room old=getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found: "+roomId));
        if(plugin.getGenerationEngine().queuedBlocks()>0)throw new IllegalArgumentException("Wait for construction to finish before editing prop areas.");
        Room next=new Room(old.id(),old.type(),old.size(),old.connections(),
            old.blocks().stream().filter(b -> areas.stream().noneMatch(a -> a.contains(b.position()))).toList(),old.variations(),old.spawnChance(),areas,old.diversityAreas(),old.regions());
        rooms.put(roomId,next);
        try{save();}catch(RuntimeException e){rooms.put(roomId,old);throw e;}
        plugin.getGenerationEngine().replanFutureRooms();
    }

    public void removeVariation(String roomId,String variationId) {
        Room old=getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));
        if(plugin.getGenerationEngine().queuedBlocks()>0)throw new IllegalArgumentException("Wait for construction to finish before editing variations.");
        List<VariationArea> remaining=old.variations().stream().filter(v -> !v.id().equals(variationId)).toList();
        if(remaining.size()==old.variations().size())throw new IllegalArgumentException("Legacy variation not found");
        Room next=new Room(old.id(),old.type(),old.size(),old.connections(),old.blocks(),remaining,old.spawnChance(),old.propAreas(),old.diversityAreas(),old.regions());
        rooms.put(roomId,next);try{save();}catch(RuntimeException e){rooms.put(roomId,old);throw e;}plugin.getGenerationEngine().replanFutureRooms();
    }

    public void updateDiversity(String roomId,List<com.infinitylibrary.diversity.DiversityArea> areas) {
        Room old=getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));
        if(plugin.getGenerationEngine().queuedBlocks()>0)throw new IllegalArgumentException("Wait for construction to finish before editing Diversity.");
        Room next=new Room(old.id(),old.type(),old.size(),old.connections(),old.blocks(),old.variations(),old.spawnChance(),old.propAreas(),areas,old.regions());
        rooms.put(roomId,next);try{save();}catch(RuntimeException e){rooms.put(roomId,old);throw e;}plugin.getGenerationEngine().replanFutureRooms();
    }

    private void addBuiltinRooms() {
        rooms.put("builtin_corridor_straight",rectangular("builtin_corridor_straight",RoomType.FILLER,5,5,11,List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(2,1,10),BlockFace.SOUTH,3,3)),false,false));
        rooms.put("builtin_corridor_short",rectangular("builtin_corridor_short",RoomType.FILLER,5,5,6,List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(2,1,5),BlockFace.SOUTH,3,3)),false,false));
        rooms.put("builtin_corridor_adapter",rectangular("builtin_corridor_adapter",RoomType.FILLER,5,5,2,List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(2,1,1),BlockFace.SOUTH,3,3)),false,false));
        rooms.put("builtin_corridor_corner",rectangular("builtin_corridor_corner",RoomType.FILLER,5,5,5,List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("east",new Vector3i(4,1,2),BlockFace.EAST,3,3)),false,false));
        rooms.put("builtin_corridor_junction",rectangular("builtin_corridor_junction",RoomType.FILLER,5,5,5,List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(2,1,4),BlockFace.SOUTH,3,3),new ConnectionPoint("west",new Vector3i(0,1,2),BlockFace.WEST,3,3),new ConnectionPoint("east",new Vector3i(4,1,2),BlockFace.EAST,3,3)),false,false));
        rooms.put("builtin_rental",rectangular("builtin_rental",RoomType.RENTABLE,9,6,9,List.of(new ConnectionPoint("north",new Vector3i(4,1,0),BlockFace.NORTH,3,3)),true,true));
        rooms.put("builtin_writing",rectangular("builtin_writing",RoomType.PUBLIC_WRITING,11,7,11,List.of(new ConnectionPoint("north",new Vector3i(5,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(5,1,10),BlockFace.SOUTH,3,3),new ConnectionPoint("west",new Vector3i(0,1,5),BlockFace.WEST,3,3),new ConnectionPoint("east",new Vector3i(10,1,5),BlockFace.EAST,3,3)),true,true));
        List<ConnectionPoint> exits=List.of(new ConnectionPoint("north",new Vector3i(5,1,0),BlockFace.NORTH,3,3),new ConnectionPoint("south",new Vector3i(5,1,10),BlockFace.SOUTH,3,3),new ConnectionPoint("west",new Vector3i(0,1,5),BlockFace.WEST,3,3),new ConnectionPoint("east",new Vector3i(10,1,5),BlockFace.EAST,3,3));
        Room shell=rectangular("builtin_modular",RoomType.FILLER,11,7,11,exits,false,false);
        List<RoomBlock> plain=shell.blocks().stream().map(b -> new RoomBlock(b.position(),b.blockData().equals("minecraft:bookshelf")?"minecraft:dark_oak_planks":b.blockData())).toList();
        List<PropArea> areas=List.of(
            new PropArea("northwest",new Vector3i(1,1,1),new Vector3i(3,3,3),"library",15,true,false),
            new PropArea("northeast",new Vector3i(7,1,1),new Vector3i(3,3,3),"library",15,true,false),
            new PropArea("southwest",new Vector3i(1,1,7),new Vector3i(3,3,3),"library",15,true,false),
            new PropArea("southeast",new Vector3i(7,1,7),new Vector3i(3,3,3),"library",15,true,false));
        rooms.put("builtin_modular",new Room(shell.id(),shell.type(),shell.size(),shell.connections(),plain,List.of(),100,areas));
        rooms.put("builtin_start",new Room("builtin_start",RoomType.STARTING,shell.size(),shell.connections(),plain,List.of(),100,areas));
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
        return new Room(id,type,new Vector3i(sx,sy,sz),cps,blocks,List.of(),100,List.of(),List.of(),seats?List.of(new RoomRegion("seat",new Vector3i(sx/2,1,sz/2),new Vector3i(1,1,1),"general"),new RoomRegion("table",new Vector3i(sx/2+1,1,sz/2),new Vector3i(1,1,1),"general")):List.of());
    }

    private void removeDoorBlock(List<RoomBlock> blocks, Vector3i doorwayBlock) {
        blocks.removeIf(b -> b.position().equals(doorwayBlock));
    }
}
