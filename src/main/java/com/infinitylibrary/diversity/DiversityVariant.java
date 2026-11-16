package com.infinitylibrary.diversity;

import com.infinitylibrary.model.*;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Non-air source blocks relative to an exactly sized captured source box. */
public record DiversityVariant(String id,double weight,List<RoomBlock> blocks) {
    public DiversityVariant {
        if(id==null||!id.matches("[a-z0-9_-]{1,64}")||id.equals("default"))throw new IllegalArgumentException("Variant ID needs 1–64 lowercase letters/digits/_/- and cannot be 'default'.");
        if(!Double.isFinite(weight)||weight<0||weight>1000000)throw new IllegalArgumentException("Weight must be 0–1,000,000.");
        blocks=List.copyOf(blocks);if(blocks.size()>8192)throw new IllegalArgumentException("Variation exceeds 8,192 blocks.");
    }
    public void write(ConfigurationSection s) {s.set("weight",weight);s.set("blocks",blocks.stream().map(b -> b.position()+"|"+b.blockData()).toList());}
    public static DiversityVariant read(String id,ConfigurationSection s) {
        List<RoomBlock> blocks=new ArrayList<>();for(String line:s.getStringList("blocks")){String[] p=line.split("\\|",2);if(p.length!=2)throw new IllegalArgumentException("Invalid variation block");blocks.add(new RoomBlock(Vector3i.parse(p[0]),p[1]));}
        return new DiversityVariant(id,s.getDouble("weight",1),blocks);
    }
}
