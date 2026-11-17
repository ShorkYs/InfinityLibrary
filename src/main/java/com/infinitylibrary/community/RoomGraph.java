package com.infinitylibrary.community;

import com.infinitylibrary.engine.LayoutPlanner;
import com.infinitylibrary.model.*;
import java.util.*;

/** Connected-room graph from frozen, generated doorways. Old adjacent and new shared planes coexist. */
public final class RoomGraph {
    public record RoomNode(UUID id,String template,RoomType type,LayoutPlanner.Box box,List<ConnectionPoint> connected){public RoomNode{connected=List.copyOf(connected);}}
    private record End(UUID room,ConnectionPoint door){}
    private record Edge(UUID next,Vector3i portal){}
    private final Map<UUID,RoomNode> rooms=new LinkedHashMap<>();private final Map<UUID,List<Edge>> edges=new HashMap<>();
    public RoomGraph(Collection<RoomNode> nodes){Map<Vector3i,List<End>> sockets=new HashMap<>();for(var room:nodes){rooms.put(room.id(),room);edges.put(room.id(),new ArrayList<>());for(var cp:room.connected())sockets.computeIfAbsent(cp.position(),p->new ArrayList<>()).add(new End(room.id(),cp));}
        for(var room:nodes)for(var cp:room.connected())for(Vector3i at:List.of(cp.position(),cp.position().add(new Vector3i(cp.direction().getModX(),cp.direction().getModY(),cp.direction().getModZ()))))for(var other:sockets.getOrDefault(at,List.of()))if(!other.room().equals(room.id())&&cp.compatibleWith(other.door()))edges.get(room.id()).add(new Edge(other.room(),cp.position()));
    }
    public Optional<RoomNode> containing(Vector3i p){return rooms.values().stream().filter(r->r.box().contains(p)).min(Comparator.comparingDouble(r->distance(center(r),p)));}
    public static Vector3i center(RoomNode n){var b=n.box();return b.origin().add(new Vector3i(b.size().x()/2,1,b.size().z()/2));}
    private static double distance(Vector3i a,Vector3i b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}
    public Optional<List<Vector3i>> route(UUID from,UUID to){if(!rooms.containsKey(from)||!rooms.containsKey(to))return Optional.empty();Map<UUID,UUID> previous=new HashMap<>();Map<UUID,Vector3i> portals=new HashMap<>();Deque<UUID> q=new ArrayDeque<>();q.add(from);previous.put(from,from);while(!q.isEmpty()){UUID at=q.removeFirst();if(at.equals(to)){List<Vector3i> result=new ArrayList<>();for(UUID p=to;!p.equals(from);p=previous.get(p))result.add(portals.get(p));Collections.reverse(result);return Optional.of(result);}for(var edge:edges.getOrDefault(at,List.of()))if(!previous.containsKey(edge.next())){previous.put(edge.next(),at);portals.put(edge.next(),edge.portal());q.addLast(edge.next());}}return Optional.empty();}
    public Optional<RoomNode> closestReachable(UUID from,RoomType type){Deque<UUID> q=new ArrayDeque<>();Set<UUID> seen=new HashSet<>();q.add(from);seen.add(from);while(!q.isEmpty()){UUID id=q.removeFirst();RoomNode n=rooms.get(id);if(n!=null&&n.type()==type)return Optional.of(n);for(var e:edges.getOrDefault(id,List.of()))if(seen.add(e.next()))q.addLast(e.next());}return Optional.empty();}
}
