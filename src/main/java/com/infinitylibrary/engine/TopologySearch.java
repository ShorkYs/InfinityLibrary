package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import com.infinitylibrary.diversity.DiversityComposition;
import java.util.*;
import java.util.function.Function;

/** One planning transaction: shared terrain cache/budget and all eligible catalog entries. */
public final class TopologySearch {
    public enum Fit { YES, NO, UNKNOWN }
    public record Template(Room room,DiversityComposition diversity) {
        public List<ConnectionPoint> connections(){return diversity.connections(room);}
    }
    private final List<Template> templates=new ArrayList<>();
    private final Function<LayoutPlanner.Box,Fit> terrain;
    private final Map<LayoutPlanner.Box,Fit> cache=new HashMap<>();
    private final Map<ConnectionPoint,LayoutPlanner.Box> witnesses=new HashMap<>();
    private boolean complete=true,limited;
    public boolean limited(){return limited||!complete;}
    private int checks;
    public TopologySearch(Collection<Room> rooms,int topologyLimit,int checks,Function<LayoutPlanner.Box,Fit> terrain) {
        this(rooms,topologyLimit,checks,terrain,0L);
    }
    public TopologySearch(Collection<Room> rooms,int topologyLimit,int checks,Function<LayoutPlanner.Box,Fit> terrain,long seed) {
        this.terrain=terrain;this.checks=checks;
        Map<Room,Double> order=new HashMap<>();Random random=new Random(seed);for(Room room:rooms.stream().sorted(Comparator.comparing(Room::id)).toList())order.put(room,-Math.log(Math.max(1e-12,random.nextDouble()))/Math.max(1e-12,room.spawnChance()));
        List<Room> ordered=new ArrayList<>(rooms);ordered.sort(Comparator.comparingDouble(order::get));
        for(Room room:ordered){var choices=DiversityComposition.choices(room,topologyLimit);complete&=choices.complete();for(var c:choices.values())templates.add(new Template(room,c));}
    }
    public Fit clear(LayoutPlanner.Box box){Fit fit=cache.computeIfAbsent(box,terrain);if(fit==Fit.UNKNOWN)limited=true;return fit;}
    public List<LayoutPlanner.Candidate> candidates(ConnectionPoint target,List<LayoutPlanner.Box> obstacles,int perTemplate,String forced) {
        Map<String,List<LayoutPlanner.Candidate>> byRoom=new LinkedHashMap<>();Set<String> seen=new HashSet<>();
        for(Template template:templates) {
            Room room=template.room();if(forced!=null&&!forced.equals(room.id()))continue;
            List<LayoutPlanner.Candidate> found=byRoom.computeIfAbsent(room.id()+"/"+template.diversity().patches().stream().map(p -> p.areaId()+":"+p.choice()).toList(),id -> new ArrayList<>());
            for(ConnectionPoint cp:template.connections())for(RoomTransform transform:RoomTransform.all()) {
                ConnectionPoint entrance=cp.transform(transform,room.size());if(!target.compatibleWith(entrance))continue;
                Vector3i origin=target.position().subtract(entrance.position());
                var c=new LayoutPlanner.Candidate(room,cp.id(),transform,origin,UUID.randomUUID(),template.diversity());
                String signature=room.id()+"/"+origin+"/"+c.connections().stream().map(c::worldDoor).toList()+"/"+template.diversity().patches().stream().map(p -> p.areaId()+":"+p.choice()).toList();
                if(!seen.add(signature)||obstacles.stream().anyMatch(b -> b.conflicts(c.box())))continue;
                if(found.size()>=perTemplate)continue;
                if(clear(c.box())==Fit.YES)found.add(c);
            }
        }
        List<LayoutPlanner.Candidate> result=new ArrayList<>();
        // Interleave templates: a large catalog never disappears behind the first template's transforms.
        for(int i=0;i<perTemplate;i++)for(var list:byRoom.values())if(i<list.size())result.add(list.get(i));
        return result;
    }
    public Fit continuation(ConnectionPoint target,List<LayoutPlanner.Box> obstacles,Collection<ConnectionPoint> openDoors) {
        if(openDoors.stream().anyMatch(cp -> linked(target,cp)))return Fit.YES;
        LayoutPlanner.Box prior=witnesses.get(target);if(prior!=null&&obstacles.stream().noneMatch(b->b.conflicts(prior))&&clear(prior)==Fit.YES)return Fit.YES;
        if(checks<=0){limited=true;return Fit.UNKNOWN;}
        boolean unknown=!complete;
        for(Template template:templates)for(ConnectionPoint cp:template.connections())for(RoomTransform transform:RoomTransform.all()) {
            ConnectionPoint entrance=cp.transform(transform,template.room().size());if(!target.compatibleWith(entrance))continue;
            Vector3i origin=target.position().subtract(entrance.position());
            var box=new LayoutPlanner.Box(origin,transform.transformedSize(template.room().size()),template.connections().stream().map(c -> c.transform(transform,template.room().size()).offset(c.id(),origin)).toList());
            if(obstacles.stream().anyMatch(b -> b.conflicts(box)))continue;
            if(--checks<0){limited=true;unknown=true;continue;}
            Fit fit=clear(box);if(fit==Fit.YES){witnesses.put(target,box);return Fit.YES;}if(fit==Fit.UNKNOWN)unknown=true;
        }
        return unknown?Fit.UNKNOWN:Fit.NO;
    }
    public boolean preserves(List<ConnectionPoint> viable,List<ConnectionPoint> frontier,List<LayoutPlanner.Box> occupied,Collection<LayoutPlanner.Candidate> chosen){
        List<LayoutPlanner.Box> boxes=new ArrayList<>(occupied);List<ConnectionPoint> doors=new ArrayList<>(frontier);
        for(var c:chosen){boxes.add(c.box());for(ConnectionPoint cp:c.connections())doors.add(c.worldDoor(cp));}
        for(ConnectionPoint cp:viable){
            LayoutPlanner.Box witness=witnesses.get(cp);
            if(witness!=null&&chosen.stream().noneMatch(c -> c.box().conflicts(witness)))continue;
            if(continuation(cp,boxes,doors)!=Fit.YES)return false;
        }
        return true;
    }
    public static Vector3i step(ConnectionPoint cp){return new Vector3i(cp.direction().getModX(),cp.direction().getModY(),cp.direction().getModZ());}
    public static boolean linked(ConnectionPoint a,ConnectionPoint b){return a.compatibleWith(b)&&a.position().equals(b.position());}
}
