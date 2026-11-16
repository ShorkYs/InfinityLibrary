package com.infinitylibrary.diversity;

import com.infinitylibrary.model.*;
import com.infinitylibrary.props.PropArea;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

public record DiversityArea(String id,Vector3i min,Vector3i size,double originalWeight,List<DiversityVariant> variants) {
    public DiversityArea {
        if(id==null||!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Diversity area ID needs 1–48 lowercase letters/digits/_/-.");
        new PropArea(id,min,size,"*",0,false); // Shared positive-size and volume limits.
        if(!Double.isFinite(originalWeight)||originalWeight<0||originalWeight>1000000)throw new IllegalArgumentException("Original weight must be 0–1,000,000.");
        variants=List.copyOf(variants);if(variants.size()>16)throw new IllegalArgumentException("At most 16 alternatives per Diversity area.");
        Set<String> ids=new HashSet<>();for(DiversityVariant v:variants){if(!ids.add(v.id()))throw new IllegalArgumentException("Duplicate Diversity variant: "+v.id());
            Set<Vector3i> positions=new HashSet<>();for(RoomBlock b:v.blocks())if(!positions.add(b.position())||b.position().x()<0||b.position().y()<0||b.position().z()<0||b.position().x()>=size.x()||b.position().y()>=size.y()||b.position().z()>=size.z())throw new IllegalArgumentException("Variation block outside source bounds or duplicated");}
    }
    public void requireSourceSize(Vector3i actual) {if(!size.equals(actual))throw new IllegalArgumentException("Variation size must match X,Y,Z exactly: expected "+size+", selected "+actual+". Select two source corners again.");}
    public PropArea bounds() {return new PropArea(id,min,size,"*",0,false);}
    public DiversityArea withVariant(DiversityVariant variant) {
        List<DiversityVariant> updated=new ArrayList<>(variants);updated.removeIf(v -> v.id().equals(variant.id()));updated.add(variant);return new DiversityArea(id,min,size,originalWeight,updated);
    }
    public void write(ConfigurationSection s) {
        min.write(s.createSection("min"));size.write(s.createSection("size"));s.set("original-weight",originalWeight);
        var options=s.createSection("variants");for(DiversityVariant v:variants)v.write(options.createSection(v.id()));
    }
    public static DiversityArea read(String id,ConfigurationSection s) {
        List<DiversityVariant> variants=new ArrayList<>();var options=s.getConfigurationSection("variants");if(options!=null)for(String key:options.getKeys(false))variants.add(DiversityVariant.read(key,options.getConfigurationSection(key)));
        return new DiversityArea(id,Vector3i.read(s.getConfigurationSection("min")),Vector3i.read(s.getConfigurationSection("size")),s.getDouble("original-weight",1),variants);
    }
}
