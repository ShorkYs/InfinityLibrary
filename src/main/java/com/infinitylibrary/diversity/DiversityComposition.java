package com.infinitylibrary.diversity;

import com.infinitylibrary.model.*;
import com.infinitylibrary.props.PropArea;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Frozen patches include original choices as well as alternatives, so recovery never rerolls. */
public record DiversityComposition(List<Patch> patches) {
    public static final DiversityComposition EMPTY=new DiversityComposition(List.of());
    public record Patch(String areaId,Vector3i min,Vector3i size,String choice,List<RoomBlock> blocks,List<ConnectionPoint> connections) {
        public Patch(String areaId,Vector3i min,Vector3i size,String choice,List<RoomBlock> blocks){this(areaId,min,size,choice,blocks,List.of());}
        public Patch {
            connections=List.copyOf(connections);
            PropArea bounds=new PropArea(areaId,min,size,"*",0,false);blocks=List.copyOf(blocks);
            for(ConnectionPoint cp:connections)if(!cp.cells().stream().allMatch(bounds::contains))throw new IllegalArgumentException("Diversity snapshot connection outside target");
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
            result.add(new Patch(area.id(),area.min(),area.size(),choice,blocks,selected==null?List.of():selected.connections().stream().map(cp -> cp.offset("div_"+area.id()+"_"+cp.id(),area.min())).toList()));
        }
        return new DiversityComposition(result);
    }
    /** Default choices retain original doors; alternatives replace doors inside their target. */
    public List<ConnectionPoint> connections(Room room) {
        List<ConnectionPoint> result=new ArrayList<>(room.connections());
        for(Patch p:patches)if(!p.choice().equals("default")) {
            result.removeIf(cp -> p.contains(cp.position()));result.addAll(p.connections());
        }
        return List.copyOf(result);
    }
    public record Choices(List<DiversityComposition> values,boolean complete) {}
    /** Enumerate topology choices before placement. Interior-only alternatives still roll normally. */
    public static Choices choices(Room room,int limit) {
        List<DiversityComposition> states=new ArrayList<>();states.add(resolve(room,UUID.randomUUID()));boolean complete=true;
        for(DiversityArea a:room.diversityAreas()) {
            boolean topology=a.variants().stream().anyMatch(v -> !v.connections().isEmpty())||room.connections().stream().anyMatch(cp -> a.bounds().contains(cp.position()));
            if(!topology)continue;
            List<String> options=new ArrayList<>();if(a.originalWeight()>0)options.add("default");for(DiversityVariant v:a.variants())if(v.weight()>0)options.add(v.id());if(options.isEmpty())options.add("default");
            List<DiversityComposition> next=new ArrayList<>();
            for(DiversityComposition state:states)for(String choice:options) {
                if(next.size()>=limit){complete=false;break;}
                DiversityVariant v=a.variants().stream().filter(x -> x.id().equals(choice)).findFirst().orElse(null);
                List<RoomBlock> blocks=v==null?room.blocks().stream().filter(b -> a.bounds().contains(b.position())).toList():v.blocks().stream().map(b -> new RoomBlock(a.min().add(b.position()),b.blockData())).toList();
                Patch patch=new Patch(a.id(),a.min(),a.size(),choice,blocks,v==null?List.of():v.connections().stream().map(cp -> cp.offset("div_"+a.id()+"_"+cp.id(),a.min())).toList());
                List<Patch> patches=new ArrayList<>(state.patches());patches.removeIf(p -> p.areaId().equals(a.id()));patches.add(patch);next.add(new DiversityComposition(patches));
            }
            states=next;
        }
        // Weighted ordering preserves variation preference when topology permits several choices.
        Map<DiversityComposition,Double> order=new HashMap<>();Random random=new Random();
        for(var state:states){double weight=1;for(var patch:state.patches()){DiversityArea a=room.diversityAreas().stream().filter(x -> x.id().equals(patch.areaId())).findFirst().orElseThrow();weight*=patch.choice().equals("default")?Math.max(0.000001,a.originalWeight()):a.variants().stream().filter(v -> v.id().equals(patch.choice())).findFirst().orElseThrow().weight();}order.put(state,-Math.log(Math.max(1e-12,random.nextDouble()))/Math.max(1e-12,weight));}
        states.sort(Comparator.comparingDouble(order::get));return new Choices(List.copyOf(states),complete);
    }
    public void write(ConfigurationSection root) {
        for(Patch p:patches){var s=root.createSection(p.areaId());p.min().write(s.createSection("min"));p.size().write(s.createSection("size"));s.set("choice",p.choice());var cps=s.createSection("connections");for(ConnectionPoint cp:p.connections())cp.write(cps.createSection(cp.id()));s.set("blocks",p.blocks().stream().map(b -> b.position()+"|"+b.blockData()).toList());}
    }
    public static DiversityComposition read(ConfigurationSection root) {
        List<Patch> result=new ArrayList<>();for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);List<RoomBlock> blocks=new ArrayList<>();for(String line:s.getStringList("blocks")){String[] p=line.split("\\|",2);if(p.length!=2)throw new IllegalArgumentException("Invalid Diversity snapshot");blocks.add(new RoomBlock(Vector3i.parse(p[0]),p[1]));}List<ConnectionPoint> cps=new ArrayList<>();var c=s.getConfigurationSection("connections");if(c!=null)for(String key:c.getKeys(false))cps.add(ConnectionPoint.read(key,c.getConfigurationSection(key)));result.add(new Patch(id,Vector3i.read(s.getConfigurationSection("min")),Vector3i.read(s.getConfigurationSection("size")),s.getString("choice","default"),blocks,cps));}
        return new DiversityComposition(result);
    }
}
