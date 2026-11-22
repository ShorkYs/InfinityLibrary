package com.infinitylibrary.nextgen;

import java.util.*;

/** Immutable plan of a complete district, independent of Bukkit and block placement. */
public record DistrictPlan(List<Room> rooms, List<Link> links, Set<Socket> expansionFrontier) {
    public record Point(int x,int y,int z) {
        public Point add(Point p){return new Point(Math.addExact(x,p.x),Math.addExact(y,p.y),Math.addExact(z,p.z));}
    }
    public record Volume(Point min,Point size) {
        public Volume{Objects.requireNonNull(min);if(size.x<1||size.y<1||size.z<1)throw new IllegalArgumentException("Positive dimensions required");}
        public boolean contains(Point p){return p.x>=min.x&&p.y>=min.y&&p.z>=min.z&&p.x<(long)min.x+size.x&&p.y<(long)min.y+size.y&&p.z<(long)min.z+size.z;}
        public boolean interiorOverlaps(Volume b){return Math.max(min.x,b.min.x)<Math.min((long)min.x+size.x-1,(long)b.min.x+b.size.x-1)&&Math.max(min.y,b.min.y)<Math.min((long)min.y+size.y-1,(long)b.min.y+b.size.y-1)&&Math.max(min.z,b.min.z)<Math.min((long)min.z+size.z-1,(long)b.min.z+b.size.z-1);}
    }
    public enum Role { ENTRANCE,FILLER,READING,RENTABLE,PUBLIC_WRITING }
    public enum Facing { NORTH,SOUTH,WEST,EAST,UP,DOWN;
        public Facing opposite(){return values()[ordinal()^1];}
    }
    /** Opening cells are explicit: matching dimensions alone must never connect misaligned doors. */
    public record Door(String id,Facing facing,int floor,Set<Point> opening) {
        public Door{if(id==null||id.isBlank()||opening==null||opening.isEmpty())throw new IllegalArgumentException("Door ID and opening required");opening=Set.copyOf(opening);Objects.requireNonNull(facing);}
        public boolean matches(Door b){return facing.opposite()==b.facing&&floor==b.floor&&opening.equals(b.opening);}
    }
    public record Room(UUID id,String template,Role role,Volume bounds,List<Door> doors) {
        public Room{Objects.requireNonNull(id);Objects.requireNonNull(template);Objects.requireNonNull(role);doors=List.copyOf(doors);Set<String> names=new HashSet<>();for(Door d:doors){if(!names.add(d.id))throw new IllegalArgumentException("Duplicate door");for(Point p:d.opening)if(!bounds.contains(p)||!onFace(bounds,p,d.facing))throw new IllegalArgumentException("Opening must lie on its outward boundary");}}
    }
    public record Socket(UUID room,String door){}
    public record Link(Socket a,Socket b){}
    public DistrictPlan {
        rooms=List.copyOf(rooms);links=List.copyOf(links);expansionFrontier=Set.copyOf(expansionFrontier);
        Map<UUID,Room> byId=new HashMap<>();for(Room r:rooms)if(byId.put(r.id,r)!=null)throw new IllegalArgumentException("Duplicate room ID");
        Set<Socket> used=new HashSet<>();Map<UUID,Set<UUID>> graph=new HashMap<>();
        for(Link link:links){if(link.a.room.equals(link.b.room)||!used.add(link.a)||!used.add(link.b))throw new IllegalArgumentException("Socket connected more than once");Door a=door(byId,link.a),b=door(byId,link.b);if(!a.matches(b))throw new IllegalArgumentException("Incompatible openings");graph.computeIfAbsent(link.a.room,k->new HashSet<>()).add(link.b.room);graph.computeIfAbsent(link.b.room,k->new HashSet<>()).add(link.a.room);}
        for(Socket socket:expansionFrontier){door(byId,socket);if(used.contains(socket))throw new IllegalArgumentException("Connected socket cannot be a frontier");}
        for(Room r:rooms)for(Door d:r.doors)if(!used.contains(new Socket(r.id,d.id))&&!expansionFrontier.contains(new Socket(r.id,d.id)))throw new IllegalArgumentException("Unresolved internal opening");
        if(!rooms.isEmpty()){Set<UUID> reached=new HashSet<>();Deque<UUID> queue=new ArrayDeque<>();queue.add(rooms.getFirst().id);while(!queue.isEmpty()){UUID id=queue.remove();if(reached.add(id))queue.addAll(graph.getOrDefault(id,Set.of()));}if(reached.size()!=rooms.size())throw new IllegalArgumentException("Disconnected district");}
        for(int i=0;i<rooms.size();i++)for(int j=0;j<i;j++)if(rooms.get(i).bounds.interiorOverlaps(rooms.get(j).bounds))throw new IllegalArgumentException("Room interiors overlap");
    }
    private static Door door(Map<UUID,Room> rooms,Socket s){Room r=rooms.get(s.room);if(r==null)throw new IllegalArgumentException("Unknown room");return r.doors.stream().filter(d->d.id.equals(s.door)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown door"));}
    private static boolean onFace(Volume b,Point p,Facing f){return switch(f){case WEST->p.x==b.min.x;case EAST->p.x==(long)b.min.x+b.size.x-1;case DOWN->p.y==b.min.y;case UP->p.y==(long)b.min.y+b.size.y-1;case NORTH->p.z==b.min.z;case SOUTH->p.z==(long)b.min.z+b.size.z-1;};}
}
