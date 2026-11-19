package com.infinitylibrary.model;

import org.bukkit.Material;
import com.infinitylibrary.props.PropArea;
import com.infinitylibrary.diversity.DiversityArea;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class Room {
    private final List<RoomRegion> regions;
    public List<RoomRegion> regions(){return regions;}
    private final String id;
    private final RoomType type;
    private final Vector3i size;
    private final List<ConnectionPoint> connections;
    private final List<RoomBlock> blocks;
    private final List<VariationArea> variations;
    private final double spawnChance;
    private final List<PropArea> propAreas;
    private final List<DiversityArea> diversityAreas;

    public Room(String id, RoomType type, Vector3i size, List<ConnectionPoint> connections, List<RoomBlock> blocks, List<VariationArea> variations, double spawnChance) {
        this(id,type,size,connections,blocks,variations,spawnChance,List.of());
    }
    public Room(String id, RoomType type, Vector3i size, List<ConnectionPoint> connections, List<RoomBlock> blocks, List<VariationArea> variations, double spawnChance,List<PropArea> propAreas) {
        this(id,type,size,connections,blocks,variations,spawnChance,propAreas,List.of());
    }
    public Room(String id,RoomType type,Vector3i size,List<ConnectionPoint> connections,List<RoomBlock> blocks,List<VariationArea> variations,double spawnChance,List<PropArea> propAreas,List<DiversityArea> diversityAreas) {
        this(id,type,size,connections,blocks,variations,spawnChance,propAreas,diversityAreas,List.of());
    }
    public Room(String id,RoomType type,Vector3i size,List<ConnectionPoint> connections,List<RoomBlock> blocks,List<VariationArea> variations,double spawnChance,List<PropArea> propAreas,List<DiversityArea> diversityAreas,List<RoomRegion> regions){
        this.regions=List.copyOf(regions);
        this.diversityAreas=List.copyOf(diversityAreas);
        this.propAreas=List.copyOf(propAreas);
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.size = Objects.requireNonNull(size);
        this.connections = List.copyOf(connections);
        this.blocks = List.copyOf(blocks);
        this.variations = List.copyOf(variations);
        this.spawnChance = Math.max(0.0, Math.min(100.0, spawnChance));
        validate();
    }
    public String id() { return id; }
    public RoomType type() { return type; }
    public Vector3i size() { return size; }
    public List<ConnectionPoint> connections() { return connections; }
    public List<RoomBlock> blocks() { return blocks; }
    public List<VariationArea> variations() { return variations; }
    public List<DiversityArea> diversityAreas() {return diversityAreas;}
    public List<PropArea> propAreas() { return propAreas; }
    public double spawnChance() { return spawnChance; }

    public void validate() {
        if(regions.size()>64)throw new IllegalArgumentException("At most 64 reading/category regions per room.");for(var region:regions){var p=region.min();var z=region.size();if(p.x()<0||p.y()<0||p.z()<0||(long)p.x()+z.x()>size.x()||(long)p.y()+z.y()>size.y()||(long)p.z()+z.z()>size.z())throw new IllegalArgumentException("Room region outside room bounds.");}
        if (connections.isEmpty()&&diversityAreas.stream().flatMap(a -> a.variants().stream()).allMatch(v -> v.connections().isEmpty())) throw new IllegalArgumentException("Room " + id + " must contain at least one connection point");
        if (size.x() < 1 || size.y() < 1 || size.z() < 1) throw new IllegalArgumentException("Room " + id + " has invalid size");
        boolean hasBookshelf = false, hasSeat = false;
        for (RoomBlock block : blocks) {
            Vector3i p = block.position();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z()) {
                throw new IllegalArgumentException("Block outside room bounds in " + id + ": " + p);
            }
            Material m = materialFromBlockData(block.blockData());
            if (m == Material.CHISELED_BOOKSHELF) hasBookshelf = true;
            if (m != null && (m.name().endsWith("_STAIRS") || m.name().endsWith("_SLAB"))) hasSeat = true;
        }
        for (ConnectionPoint cp : connections) {
            Vector3i p = cp.position();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z()) {
                throw new IllegalArgumentException("Connection outside room bounds in " + id + ": " + cp.id());
            }
        }
        validatePropAreas(size,connections,propAreas);
        validateDiversityAreas(size,connections,propAreas,variations,diversityAreas);
        if (type == RoomType.BOOK && !hasBookshelf && propAreas.isEmpty() && diversityAreas.isEmpty()) throw new IllegalArgumentException("BOOK room " + id + " must contain a chiseled bookshelf");
        if (type == RoomType.READ && !hasSeat && propAreas.isEmpty() && diversityAreas.isEmpty()) throw new IllegalArgumentException("READ room " + id + " must contain a stair or slab");
    }

    public static void validatePropAreas(Vector3i roomSize,List<ConnectionPoint> connections,List<PropArea> areas) {
        if(areas.size()>32)throw new IllegalArgumentException("A room supports at most 32 prop areas.");
        Set<String> ids=new HashSet<>();long volume=0;
        for(int i=0;i<areas.size();i++) {
            PropArea a=areas.get(i);Vector3i m=a.min(),s=a.size();volume+=PropArea.volume(s);
            if(!ids.add(a.id()))throw new IllegalArgumentException("Duplicate prop area ID: "+a.id());
            if(m.x()<1||m.y()<1||m.z()<1||(long)m.x()+s.x()>=roomSize.x()||(long)m.y()+s.y()>=roomSize.y()||(long)m.z()+s.z()>=roomSize.z())
                throw new IllegalArgumentException("Prop area "+a.id()+" must stay inside the room's floor, walls and ceiling.");
            for(int j=0;j<i;j++)if(a.overlaps(areas.get(j)))throw new IllegalArgumentException("Prop areas overlap: "+a.id()+" and "+areas.get(j).id());
            for(ConnectionPoint cp:connections)if(intersectsDoor(a,cp))throw new IllegalArgumentException("Prop area "+a.id()+" obstructs doorway "+cp.id()+" or its entrance clearance.");
        }
        if(volume>32768)throw new IllegalArgumentException("Combined prop areas exceed 32,768 blocks.");
    }

    private static boolean intersectsDoor(PropArea area,ConnectionPoint cp) {
        Vector3i m=area.min(),s=area.size();
                Vector3i v=cp.position();int half=cp.width()/2;
                Vector3i min,max;
                switch(cp.direction()) {
                    case NORTH,SOUTH -> {int end=v.z()-cp.direction().getModZ()*2;min=new Vector3i(v.x()-half,v.y(),Math.min(v.z(),end));max=new Vector3i(v.x()-half+cp.width(),v.y()+cp.height(),Math.max(v.z(),end)+1);}
                    case EAST,WEST -> {int end=v.x()-cp.direction().getModX()*2;min=new Vector3i(Math.min(v.x(),end),v.y(),v.z()-half);max=new Vector3i(Math.max(v.x(),end)+1,v.y()+cp.height(),v.z()-half+cp.width());}
                    default -> {int end=v.y()-cp.direction().getModY()*2;min=new Vector3i(v.x()-half,Math.min(v.y(),end),v.z()-half);max=new Vector3i(v.x()-half+cp.width(),Math.max(v.y(),end)+1,v.z()-half+cp.width());}
                }
        return m.x()<max.x()&&(long)m.x()+s.x()>min.x()&&m.y()<max.y()&&(long)m.y()+s.y()>min.y()&&m.z()<max.z()&&(long)m.z()+s.z()>min.z();
    }
    public static void validateDiversityAreas(Vector3i roomSize,List<ConnectionPoint> connections,List<PropArea> props,List<VariationArea> legacy,List<DiversityArea> areas) {
        if(areas.size()>16)throw new IllegalArgumentException("At most 16 Diversity areas per room.");
        Set<String> ids=new HashSet<>();Map<String,String> doorOwners=new HashMap<>();long volume=0,alternatives=0;
        for(int i=0;i<areas.size();i++) {
            DiversityArea area=areas.get(i);PropArea bounds=area.bounds();Vector3i m=area.min(),s=area.size();
            if(!ids.add(area.id()))throw new IllegalArgumentException("Duplicate Diversity area: "+area.id());
            if(m.x()<0||m.y()<0||m.z()<0||(long)m.x()+s.x()>roomSize.x()||(long)m.y()+s.y()>roomSize.y()||(long)m.z()+s.z()>roomSize.z())throw new IllegalArgumentException("Diversity target must be within the selected room.");
            for(ConnectionPoint cp:connections)if(intersectsDoor(bounds,cp)&&!cp.cells().stream().allMatch(bounds::contains))throw new IllegalArgumentException("Diversity target must contain the entire doorway or stay clear of it: "+cp.id());
            for(var variant:area.variants())for(ConnectionPoint cp:variant.connections()) {
                ConnectionPoint local=cp.offset("div_"+area.id()+"_"+cp.id(),area.min());
                String owner=doorOwners.putIfAbsent(local.id(),area.id());
                if(owner!=null&&!owner.equals(area.id()))throw new IllegalArgumentException("Variant connection IDs collide across areas: "+local.id());
                if(connections.stream().anyMatch(old -> old.id().equals(local.id())&&!bounds.contains(old.position())))throw new IllegalArgumentException("Variant connection ID collides with a room connection: "+local.id());
                if(!local.onBoundary(roomSize))throw new IllegalArgumentException("Diversity connection must open on the outer room boundary: "+cp.id());
                for(PropArea prop:props)if(intersectsDoor(prop,local))throw new IllegalArgumentException("Diversity connection obstructed by decoration: "+cp.id());
            }
            for(PropArea prop:props)if(bounds.overlaps(prop))throw new IllegalArgumentException("Diversity and decoration areas cannot overlap: "+area.id()+" / "+prop.id());
            for(int j=0;j<i;j++)if(bounds.overlaps(areas.get(j).bounds()))throw new IllegalArgumentException("Diversity areas overlap.");
            for(VariationArea v:legacy)if(v.blocks().stream().anyMatch(b -> bounds.contains(b.position())))throw new IllegalArgumentException("Diversity overlaps a legacy variation; remove that variation or use a different target.");
            volume+=PropArea.volume(s);alternatives+=PropArea.volume(s)*area.variants().size();
        }
        if(volume>16384||alternatives>131072)throw new IllegalArgumentException("Diversity limits: 16,384 target cells and 131,072 captured source cells per room.");
    }

    private Material materialFromBlockData(String data) {
        String key = data.split("\\[")[0];
        if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);
        return Material.matchMaterial(key.toUpperCase(Locale.ROOT));
    }

    public BlockData data(RoomBlock block) { return org.bukkit.Bukkit.createBlockData(block.blockData()); }

    public void write(ConfigurationSection s) {
        var regionRoot=s.createSection("regions");int index=0;for(var region:regions)region.write(regionRoot.createSection("region_"+(index++)));
        s.set("type", type.name());
        ConfigurationSection sizeSec = s.createSection("size"); size.write(sizeSec);
        ConfigurationSection cSec = s.createSection("connections");
        for (ConnectionPoint cp : connections) cp.write(cSec.createSection(cp.id()));
        List<String> serialized = new ArrayList<>();
        for (RoomBlock b : blocks) serialized.add(b.position() + "|" + b.blockData());
        s.set("blocks", serialized);
        s.set("spawn-chance", spawnChance);
        ConfigurationSection props=s.createSection("prop-areas");
        for(PropArea a:propAreas)a.write(props.createSection(a.id()));
        ConfigurationSection diversity=s.createSection("diversity");for(DiversityArea a:diversityAreas)a.write(diversity.createSection(a.id()));
        ConfigurationSection vSec = s.createSection("variations");
        for (VariationArea variation : variations) variation.write(vSec.createSection(variation.id()));
    }

    public static Room read(String id, ConfigurationSection s) {
        RoomType type = RoomType.parse(s.getString("type", "FILLER"));
        Vector3i size = Vector3i.read(s.getConfigurationSection("size"));
        List<ConnectionPoint> cps = new ArrayList<>();
        ConfigurationSection cSec = s.getConfigurationSection("connections");
        if (cSec != null) for (String key : cSec.getKeys(false)) cps.add(ConnectionPoint.read(key, cSec.getConfigurationSection(key)));
        List<RoomBlock> blocks = new ArrayList<>();
        for (String line : s.getStringList("blocks")) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2) blocks.add(new RoomBlock(Vector3i.parse(p[0]), p[1]));
        }
        List<VariationArea> variations = new ArrayList<>();
        ConfigurationSection vSec = s.getConfigurationSection("variations");
        if (vSec != null) for (String key : vSec.getKeys(false)) variations.add(VariationArea.read(key, vSec.getConfigurationSection(key)));
        List<PropArea> areas=new ArrayList<>();ConfigurationSection a=s.getConfigurationSection("prop-areas");
        if(a!=null)for(String key:a.getKeys(false))areas.add(PropArea.read(key,a.getConfigurationSection(key)));
        List<DiversityArea> diversity=new ArrayList<>();var d=s.getConfigurationSection("diversity");if(d!=null)for(String key:d.getKeys(false))diversity.add(DiversityArea.read(key,d.getConfigurationSection(key)));
        List<RoomRegion> regions=new ArrayList<>();var regionRoot=s.getConfigurationSection("regions");if(regionRoot!=null)for(String key:regionRoot.getKeys(false))regions.add(RoomRegion.read(regionRoot.getConfigurationSection(key)));
        return new Room(id, type, size, cps, blocks, variations, s.getDouble("spawn-chance", 100.0),areas,diversity,regions);
    }
}
