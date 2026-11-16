package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Resolved local blocks are frozen per room; editing a prop never rerolls an existing room. */
public record PropComposition(List<PropArea> areas,List<RoomBlock> blocks) {
    public static final PropComposition EMPTY=new PropComposition(List.of(),List.of());
    public PropComposition { areas=List.copyOf(areas);blocks=List.copyOf(blocks); }
    public void write(ConfigurationSection s) {
        var a=s.createSection("areas");for(PropArea area:areas)area.write(a.createSection(area.id()));
        s.set("blocks",blocks.stream().map(b -> b.position()+"|"+b.blockData()).toList());
    }
    public static PropComposition read(ConfigurationSection s) {
        List<PropArea> areas=new ArrayList<>();var a=s.getConfigurationSection("areas");
        if(a!=null)for(String id:a.getKeys(false))areas.add(PropArea.read(id,a.getConfigurationSection(id)));
        List<RoomBlock> blocks=new ArrayList<>();
        for(String line:s.getStringList("blocks")){String[] p=line.split("\\|",2);blocks.add(new RoomBlock(Vector3i.parse(p[0]),p[1]));}
        for(RoomBlock b:blocks)if(areas.stream().noneMatch(area -> area.contains(b.position())))throw new IllegalArgumentException("Resolved prop block outside areas");
        return new PropComposition(areas,blocks);
    }
}
