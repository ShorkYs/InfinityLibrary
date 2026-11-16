package com.infinitylibrary.diversity;

import com.infinitylibrary.model.*;
import com.infinitylibrary.props.PropArea;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Frozen patches include original choices as well as alternatives, so recovery never rerolls. */
public record DiversityComposition(List<Patch> patches) {
    public static final DiversityComposition EMPTY=new DiversityComposition(List.of());
    public record Patch(String areaId,Vector3i min,Vector3i size,String choice,List<RoomBlock> blocks) {
        public Patch {
            PropArea bounds=new PropArea(areaId,min,size,"*",0,false);blocks=List.copyOf(blocks);
            for(RoomBlock b:blocks)if(!bounds.contains(b.position()))throw new IllegalArgumentException("Diversity snapshot block outside target");
        }
        public boolean contains(Vector3i p) {return p.x()>=min.x()&&p.x()<min.x()+size.x()&&p.y()>=min.y()&&p.y()<min.y()+size.y()&&p.z()>=min.z()&&p.z()<min.z()+size.z();}
    }
    public DiversityComposition {patches=List.copyOf(patches);if(patches.size()>16)throw new IllegalArgumentException("Too many frozen Diversity patches");}
    public static DiversityComposition resolve(Room room,UUID instance) {
        List<Patch> result=new ArrayList<>();
        for(DiversityArea area:room.diversityAreas()) {
            List<DiversityVariant> variants=area.variants().stream().filter(v -> v.weight()>0).sorted(Comparator.comparing(DiversityVariant::id)).toList();
            double total=area.originalWeight()+variants.stream().mapToDouble(DiversityVariant::weight).sum();
            Random random=new Random(instance.getMostSignificantBits()^instance.getLeastSignificantBits()^area.id().hashCode());
            double pick=random.nextDouble()*total;DiversityVariant selected=null;
            if(total>0&&pick>=area.originalWeight()) {
                pick-=area.originalWeight();for(DiversityVariant v:variants){selected=v;pick-=v.weight();if(pick<=0)break;}
            }
            PropArea bounds=area.bounds();
            String choice=selected==null?"default":selected.id();
            List<RoomBlock> blocks=selected==null?room.blocks().stream().filter(b -> bounds.contains(b.position())).toList():selected.blocks().stream().map(b -> new RoomBlock(area.min().add(b.position()),b.blockData())).toList();
            result.add(new Patch(area.id(),area.min(),area.size(),choice,blocks));
        }
        return new DiversityComposition(result);
    }
    public void write(ConfigurationSection root) {
        for(Patch p:patches){var s=root.createSection(p.areaId());p.min().write(s.createSection("min"));p.size().write(s.createSection("size"));s.set("choice",p.choice());s.set("blocks",p.blocks().stream().map(b -> b.position()+"|"+b.blockData()).toList());}
    }
    public static DiversityComposition read(ConfigurationSection root) {
        List<Patch> result=new ArrayList<>();for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);List<RoomBlock> blocks=new ArrayList<>();for(String line:s.getStringList("blocks")){String[] p=line.split("\\|",2);if(p.length!=2)throw new IllegalArgumentException("Invalid Diversity snapshot");blocks.add(new RoomBlock(Vector3i.parse(p[0]),p[1]));}result.add(new Patch(id,Vector3i.read(s.getConfigurationSection("min")),Vector3i.read(s.getConfigurationSection("size")),s.getString("choice","default"),blocks));}
        return new DiversityComposition(result);
    }
}
