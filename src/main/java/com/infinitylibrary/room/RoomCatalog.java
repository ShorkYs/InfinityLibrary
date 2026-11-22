package com.infinitylibrary.room;

import com.infinitylibrary.model.Room;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
import java.util.function.Consumer;

/** Active authoring templates are separate from archived templates needed by existing rooms. */
public final class RoomCatalog {
    private final Map<String,Room> active=new LinkedHashMap<>();
    private final Map<String,Room> archived=new LinkedHashMap<>();
    private final Set<String> defaults=new LinkedHashSet<>();
    private final Set<String> removedDefaults=new LinkedHashSet<>();
    Map<String,Room> activeMap() {return active;}
    public Collection<Room> active() {return Collections.unmodifiableCollection(active.values());}
    public Set<String> archivedIds() {return Collections.unmodifiableSet(archived.keySet());}
    public boolean isDefault(String id) {return defaults.contains(id);}
    public boolean isActive(String id) {return active.containsKey(id);}
    public Optional<Room> get(String id) {return Optional.ofNullable(active.containsKey(id)?active.get(id):archived.get(id));}
    public void load(Map<String,Room> builtinRooms,YamlConfiguration data,Consumer<String> warning) {
        Map<String,Room> nextActive=new LinkedHashMap<>(builtinRooms),nextArchived=new LinkedHashMap<>();
        read(data.getConfigurationSection("rooms"),nextActive,warning);
        read(data.getConfigurationSection("archived-rooms"),nextArchived,warning);
        active.clear();archived.clear();defaults.clear();removedDefaults.clear();
        defaults.addAll(builtinRooms.keySet());active.putAll(builtinRooms);
        active.putAll(nextActive);archived.putAll(nextArchived);
        for(String id:data.getStringList("removed-default-rooms"))if(defaults.contains(id)) {
            removedDefaults.add(id);Room removed=active.remove(id);if(removed!=null)archived.putIfAbsent(id,removed);
        }
        // An active record is authoritative when an old file contains a duplicate archive entry.
        active.keySet().forEach(archived::remove);
    }
    private void read(ConfigurationSection section,Map<String,Room> target,Consumer<String> warning) {
        if(section==null)return;
        for(String id:section.getKeys(false))try{target.put(id,Room.read(id,section.getConfigurationSection(id)));}
        catch(RuntimeException e){throw new IllegalArgumentException("Invalid room "+id+"; restore rooms.yml from backup",e);}
    }
    public YamlConfiguration write() {
        YamlConfiguration data=new YamlConfiguration();
        var rooms=data.createSection("rooms");for(Room r:active.values())r.write(rooms.createSection(r.id()));
        var archive=data.createSection("archived-rooms");for(Room r:archived.values())r.write(archive.createSection(r.id()));
        data.set("removed-default-rooms",new ArrayList<>(removedDefaults));return data;
    }
    public void remove(Collection<String> ids) {
        for(String id:ids)if(!active.containsKey(id))throw new IllegalArgumentException("Active room not found: "+id);
        for(String id:ids){archived.put(id,active.remove(id));if(defaults.contains(id))removedDefaults.add(id);}
    }
    public Room restore(String id) {
        if(active.containsKey(id))throw new IllegalArgumentException("Room is already active: "+id);
        Room room=archived.remove(id);if(room==null)throw new IllegalArgumentException("Archived room not found: "+id);
        removedDefaults.remove(id);active.put(id,room);return room;
    }
}
