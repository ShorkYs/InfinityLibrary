package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

public record Prop(String id,String category,Vector3i size,List<RoomBlock> blocks,double weight) {
    public Prop {
        if(id==null||!id.matches("[a-z0-9_-]{1,64}")||category==null||!category.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid prop ID or category.");
        if(size==null||size.x()<1||size.y()<1||size.z()<1||PropArea.volume(size)>8192) throw new IllegalArgumentException("Prop must contain 1–8,192 blocks.");
        if(!Double.isFinite(weight)||weight<0||weight>1000000) throw new IllegalArgumentException("Prop weight must be 0–1,000,000 (zero disables it).");
        blocks=List.copyOf(blocks);
        if(blocks.isEmpty()) throw new IllegalArgumentException("A prop needs at least one non-air block. Use area gaps for empty space.");
        for(RoomBlock b:blocks) if(b.position().x()<0||b.position().y()<0||b.position().z()<0||b.position().x()>=size.x()||b.position().y()>=size.y()||b.position().z()>=size.z()) throw new IllegalArgumentException("Prop block outside its bounds.");
    }
    public void write(ConfigurationSection s) {
        s.set("category",category);size.write(s.createSection("size"));s.set("weight",weight);
        s.set("blocks",blocks.stream().map(b -> b.position()+"|"+b.blockData()).toList());
    }
    public static Prop read(String id,ConfigurationSection s) {
        List<RoomBlock> blocks=new ArrayList<>();
        for(String line:s.getStringList("blocks")) { String[] parts=line.split("\\|",2);if(parts.length!=2)throw new IllegalArgumentException("Invalid prop block");blocks.add(new RoomBlock(Vector3i.parse(parts[0]),parts[1])); }
        return new Prop(id,s.getString("category","library"),Vector3i.read(s.getConfigurationSection("size")),blocks,s.getDouble("weight",1));
    }
}
