package com.infinitylibrary.rentals;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.community.*;
import com.infinitylibrary.model.*;
import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.time.*;
import java.util.*;

/** Timed, free room workspaces. Expiry retains owner notes under the original lease ID. */
public final class RentalManager {
 public record Lease(UUID id,UUID library,UUID room,UUID owner,long expires,Set<UUID> editors,List<String> notes){
  public Lease{Objects.requireNonNull(id);Objects.requireNonNull(library);Objects.requireNonNull(room);Objects.requireNonNull(owner);editors=Set.copyOf(editors);notes=List.copyOf(notes);if(editors.size()>16||notes.size()>60||notes.stream().anyMatch(n->n==null||n.length()>4000))throw new IllegalArgumentException("Rental workspace limits exceeded");}
  public boolean active(long now){return expires>now;}
  public boolean access(UUID actor,long now){return owner.equals(actor)||active(now)&&editors.contains(actor);}
 }
 private final InfinityLibraryPlugin plugin;private final Clock clock;private Map<UUID,Lease> leases=Map.of();private static final Gson JSON=new Gson();
 public RentalManager(InfinityLibraryPlugin p){this(p,Clock.systemUTC());}
 public RentalManager(InfinityLibraryPlugin p,Clock clock){plugin=p;this.clock=clock;load();}
 public void load(){var y=AtomicYaml.read(plugin.getDataFolder().toPath().resolve("rentals.yml"));Map<UUID,Lease> next=new LinkedHashMap<>();for(String row:y.getStringList("leases")){Lease l=JSON.fromJson(row,Lease.class);if(next.put(l.id(),l)!=null)throw new IllegalArgumentException("Duplicate rental lease");}leases=Map.copyOf(next);}
 private void persist(Lease lease){Map<UUID,Lease> next=new LinkedHashMap<>(leases);next.put(lease.id(),lease);var y=new YamlConfiguration();y.set("schema",1);y.set("leases",next.values().stream().map(JSON::toJson).toList());AtomicYaml.write(plugin.getDataFolder().toPath().resolve("rentals.yml"),y);leases=Map.copyOf(next);}
 private UUID currentRoom(Player p){if(!plugin.isLibraryWorld(p.getWorld().getName()))throw new IllegalArgumentException("Enter the library first");var room=new RoomGraph(plugin.getGenerationEngine().navigationRooms()).containing(Vector3i.from(p.getLocation())).orElseThrow(()->new IllegalArgumentException("Stand inside a completed room"));if(room.type()!=RoomType.RENTABLE)throw new IllegalArgumentException("This room is not RENTABLE");return room.id();}
 private Optional<Lease> active(UUID room){UUID library=plugin.getGenerationEngine().libraryId();return leases.values().stream().filter(l->l.library().equals(library)&&l.room().equals(room)&&l.active(clock.millis())).findFirst();}
 private long expiry(){long hours=Math.max(1,Math.min(2160,plugin.getConfig().getLong("rentals.duration-hours",168)));return Math.addExact(clock.millis(),hours*3600000L);}
 public Lease claim(Player p){if(mine(p).size()>=100)throw new IllegalArgumentException("Rental archive limit reached; contact an administrator before creating more workspaces");UUID room=currentRoom(p);if(active(room).isPresent())throw new IllegalArgumentException("This room is already claimed");if(leases.values().stream().anyMatch(l->l.library().equals(plugin.getGenerationEngine().libraryId())&&l.owner().equals(p.getUniqueId())&&l.active(clock.millis())))throw new IllegalArgumentException("You already have an active rental");Lease lease=new Lease(UUID.randomUUID(),plugin.getGenerationEngine().libraryId(),room,p.getUniqueId(),expiry(),Set.of(),List.of());persist(lease);return lease;}
 private Lease ownedHere(Player p){Lease l=active(currentRoom(p)).orElseThrow(()->new IllegalArgumentException("This room has no active claim"));if(!l.owner().equals(p.getUniqueId()))throw new IllegalArgumentException("Only the renter can manage this claim");return l;}
 public void renew(Player p){Lease l=ownedHere(p);persist(new Lease(l.id(),l.library(),l.room(),l.owner(),expiry(),l.editors(),l.notes()));}
 public void release(Player p){Lease l=ownedHere(p);persist(new Lease(l.id(),l.library(),l.room(),l.owner(),clock.millis(),l.editors(),l.notes()));}
 public void invite(Player p,UUID editor){Lease l=ownedHere(p);Set<UUID> editors=new HashSet<>(l.editors());editors.add(editor);persist(new Lease(l.id(),l.library(),l.room(),l.owner(),l.expires(),editors,l.notes()));}
 public void note(Player p,String text){if(text==null||text.isBlank()||text.length()>4000)throw new IllegalArgumentException("Notes need 1–4,000 characters");Lease l=active(currentRoom(p)).orElseThrow(()->new IllegalArgumentException("Claim this room first"));if(!l.access(p.getUniqueId(),clock.millis()))throw new IllegalArgumentException("Private rental workspace");List<String> notes=new ArrayList<>(l.notes());notes.add(text);persist(new Lease(l.id(),l.library(),l.room(),l.owner(),l.expires(),l.editors(),notes));}
 public List<Lease> mine(Player p){return leases.values().stream().filter(l->l.owner().equals(p.getUniqueId())).sorted(Comparator.comparingLong(Lease::expires).reversed()).toList();}
 public List<Lease> visibleNotes(Player p){List<Lease> result=new ArrayList<>(mine(p));try{active(currentRoom(p)).filter(l->l.access(p.getUniqueId(),clock.millis())).filter(l->!l.owner().equals(p.getUniqueId())).ifPresent(result::add);}catch(IllegalArgumentException ignored){}return List.copyOf(result);}
 public void command(Player p,String[] args){String op=args.length<2?"help":args[1].toLowerCase(Locale.ROOT);switch(op){case "claim"->{Lease l=claim(p);p.sendMessage("Room claimed without payment until "+Instant.ofEpochMilli(l.expires()));}case "renew"->{renew(p);p.sendMessage("Rental renewed");}case "release"->{release(p);p.sendMessage("Claim released. Your notes remain in /il rent notes");}case "invite"->{if(args.length!=3)throw new IllegalArgumentException("/il rent invite <player UUID>");invite(p,UUID.fromString(args[2]));p.sendMessage("Workspace editor invited");}case "note"->{if(args.length<3)throw new IllegalArgumentException("/il rent note <text>");note(p,String.join(" ",Arrays.copyOfRange(args,2,args.length)));p.sendMessage("Private note saved");}case "notes"->{for(Lease l:visibleNotes(p).stream().limit(5).toList()){p.sendMessage("Room "+l.room()+" • "+(l.active(clock.millis())?"active":"archived"));for(String n:l.notes().stream().limit(20).toList())p.sendMessage(n.length()>500?n.substring(0,500)+"… [full note retained]":n);}}default->p.sendMessage("/il rent claim|renew|release|invite <UUID>|note <text>|notes • Expired notes remain yours.");}}
}
