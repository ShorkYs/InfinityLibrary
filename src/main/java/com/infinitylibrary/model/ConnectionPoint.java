package com.infinitylibrary.model;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

public record ConnectionPoint(String id, Vector3i position, BlockFace direction, int width, int height, int floor) {
    public ConnectionPoint(String id, Vector3i position, BlockFace direction, int width, int height) {
        this(id, position, direction, width, height, 0);
    }

    public ConnectionPoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Connection id is required");
        if (!isSupportedDirection(direction)) throw new IllegalArgumentException("Connection direction must be NORTH/EAST/SOUTH/WEST/UP/DOWN");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Connection width and height must be positive");
    }
    public boolean compatibleWith(ConnectionPoint other) {
        return width == other.width && height == other.height && floor == other.floor && direction.getOppositeFace() == other.direction;
    }
    public ConnectionPoint rotate(Rotation rotation, Vector3i roomSize) {
        return transform(new RoomTransform(rotation, false, false), roomSize);
    }
    public ConnectionPoint transform(RoomTransform transform, Vector3i roomSize) {
        BlockFace facing=transform.transform(direction);Vector3i anchor=transform.transform(position,roomSize);
        if(width%2==0){
            // Mirroring an even opening swaps the two center cells. Correct the anchor.
            if(direction==BlockFace.UP||direction==BlockFace.DOWN){
                Vector3i a=transform.transform(position.add(new Vector3i(-width/2,0,-width/2)),roomSize);
                Vector3i b=transform.transform(position.add(new Vector3i(width-width/2-1,0,width-width/2-1)),roomSize);
                anchor=new Vector3i(Math.min(a.x(),b.x())+width/2,anchor.y(),Math.min(a.z(),b.z())+width/2);
            }else{
                Vector3i a=transform.transform(position.add(direction==BlockFace.NORTH||direction==BlockFace.SOUTH?new Vector3i(-width/2,0,0):new Vector3i(0,0,-width/2)),roomSize);
                Vector3i b=transform.transform(position.add(direction==BlockFace.NORTH||direction==BlockFace.SOUTH?new Vector3i(width-width/2-1,0,0):new Vector3i(0,0,width-width/2-1)),roomSize);
                anchor=facing==BlockFace.NORTH||facing==BlockFace.SOUTH?new Vector3i(Math.min(a.x(),b.x())+width/2,anchor.y(),anchor.z()):new Vector3i(anchor.x(),anchor.y(),Math.min(a.z(),b.z())+width/2);
            }
        }
        return new ConnectionPoint(id,anchor,facing,width,height,floor);
    }
    private static boolean isSupportedDirection(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST || face == BlockFace.UP || face == BlockFace.DOWN;
    }
    /** Exact opening cells; even widths use the same negative-side anchor as room capture. */
    public java.util.List<Vector3i> cells() {
        java.util.List<Vector3i> result=new java.util.ArrayList<>();
        for(int a=0;a<width;a++)for(int b=0;b<(direction==BlockFace.UP||direction==BlockFace.DOWN?width:height);b++) {
            int u=a-width/2,v=b-width/2;
            result.add(position.add(switch(direction){case NORTH,SOUTH -> new Vector3i(u,b,0);case EAST,WEST -> new Vector3i(0,b,u);default -> new Vector3i(u,0,v);}));
        }
        return result;
    }
    public ConnectionPoint offset(String newId,Vector3i offset) {return new ConnectionPoint(newId,position.add(offset),direction,width,height,floor);}
    public boolean onBoundary(Vector3i size) {
        boolean plane=switch(direction){case NORTH -> position.z()==0;case SOUTH -> position.z()==size.z()-1;case WEST -> position.x()==0;case EAST -> position.x()==size.x()-1;case DOWN -> position.y()==0;case UP -> position.y()==size.y()-1;default -> false;};
        return plane&&cells().stream().allMatch(p -> p.x()>=0&&p.y()>=0&&p.z()>=0&&p.x()<size.x()&&p.y()<size.y()&&p.z()<size.z());
    }
    public static ConnectionPoint read(String id, ConfigurationSection s) {
        Vector3i position = Vector3i.read(s.getConfigurationSection("position"));
        return new ConnectionPoint(id, position, BlockFace.valueOf(s.getString("direction", "NORTH")), s.getInt("width", 1), s.getInt("height", 2), s.getInt("floor", 0));
    }
    public void write(ConfigurationSection s) {
        ConfigurationSection p = s.createSection("position");
        position.write(p);
        s.set("direction", direction.name());
        s.set("width", width);
        s.set("height", height);
        s.set("floor", floor);
    }
}
