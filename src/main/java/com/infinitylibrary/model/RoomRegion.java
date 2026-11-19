package com.infinitylibrary.model;
import org.bukkit.configuration.ConfigurationSection;
import java.util.Set;
/** Saved authoring annotations move with the room, including rotated/reflected instances. */
public record RoomRegion(String kind,Vector3i min,Vector3i size,String category){
    public RoomRegion{if(!Set.of("table","seat","shelf").contains(kind)||size.x()<1||size.y()<1||size.z()<1||(long)size.x()*size.y()*size.z()>100000)throw new IllegalArgumentException("Invalid room region.");if(category==null||category.length()>64)throw new IllegalArgumentException("Invalid region category.");}
    public boolean contains(Vector3i p){return p.x()>=min.x()&&p.y()>=min.y()&&p.z()>=min.z()&&p.x()<min.x()+size.x()&&p.y()<min.y()+size.y()&&p.z()<min.z()+size.z();}
    public void write(ConfigurationSection s){s.set("kind",kind);min.write(s.createSection("min"));size.write(s.createSection("size"));s.set("category",category);}
    public static RoomRegion read(ConfigurationSection s){return new RoomRegion(s.getString("kind"),Vector3i.read(s.getConfigurationSection("min")),Vector3i.read(s.getConfigurationSection("size")),s.getString("category","general"));}
}
