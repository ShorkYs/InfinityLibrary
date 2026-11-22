package com.infinitylibrary.nextgen;

import com.google.gson.Gson;
import com.infinitylibrary.community.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.*;

/** Durable compare-and-set journal; a room is ready only after its access route exists. */
public final class DistrictJournal {
    public record Entry(UUID id,long revision,DistrictPlan plan,Set<UUID> built) {
        public Entry{Objects.requireNonNull(id);Objects.requireNonNull(plan);built=Set.copyOf(built);if(revision<0||plan.rooms().isEmpty()||plan.rooms().size()>64)throw new IllegalArgumentException("Invalid district entry");Set<UUID> rooms=new HashSet<>();plan.rooms().forEach(r->rooms.add(r.id()));if(!rooms.containsAll(built))throw new IllegalArgumentException("Unknown completed room");}
        public boolean complete(){return built.size()==plan.rooms().size();}
        public boolean ready(UUID room){if(built.contains(room)||plan.rooms().stream().noneMatch(r->r.id().equals(room)))return false;if(built.isEmpty())return plan.rooms().getFirst().id().equals(room);return plan.links().stream().anyMatch(l->l.a().room().equals(room)&&built.contains(l.b().room())||l.b().room().equals(room)&&built.contains(l.a().room()));}
    }
    private static final Gson JSON=new Gson();private final Path file;private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    public DistrictJournal(Path file){this.file=file;var yaml=AtomicYaml.read(file);if(yaml.contains("schema")&&yaml.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported district journal schema");for(String row:yaml.getStringList("districts")){Entry e=JSON.fromJson(row,Entry.class);if(entries.put(e.id(),e)!=null)throw new IllegalArgumentException("Duplicate district entry");}}
    public synchronized List<Entry> entries(){return List.copyOf(entries.values());}
    public synchronized Entry get(UUID id){Entry e=entries.get(id);if(e==null)throw new IllegalArgumentException("Unknown district");return e;}
    public synchronized Entry reserve(UUID id,DistrictPlan plan){if(entries.containsKey(id))throw new IllegalArgumentException("District already reserved");Entry e=new Entry(id,0,plan,Set.of());write(e,null);return e;}
    public synchronized Entry completedRoom(UUID district,long expected,UUID room){Entry old=get(district);if(old.revision()!=expected)throw new IllegalArgumentException("District revision changed");if(!old.ready(room))throw new IllegalArgumentException("Access route is not built or room is already complete");Set<UUID> built=new HashSet<>(old.built());built.add(room);Entry next=new Entry(district,old.revision()+1,old.plan(),built);write(next,old);return next;}
    private void write(Entry next,Entry old){entries.put(next.id(),next);try{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("districts",entries.values().stream().map(JSON::toJson).toList());AtomicYaml.write(file,yaml);}catch(RuntimeException ex){if(old==null)entries.remove(next.id());else entries.put(old.id(),old);throw ex;}}
}
