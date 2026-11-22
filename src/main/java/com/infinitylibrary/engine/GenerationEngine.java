package com.infinitylibrary.engine;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import com.infinitylibrary.room.RoomManager;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GenerationEngine {
    private final InfinityLibraryPlugin plugin;
    private final RoomManager roomManager;
    private final File file;
    private final boolean procedural=false;
    private final List<PlacedRoom> placed = new ArrayList<>();
    private final Queue<Runnable> placementQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vector3i> sealedBlocks = new HashSet<>();
    private final Map<String,Reservation> reservations = new LinkedHashMap<>();
    private final Map<String,Long> lastPlanAttempt = new HashMap<>();
    private final java.util.function.Supplier<? extends Collection<? extends Player>> explorers;
    private int explorationTicks, explorerCursor, deadEndCursor;
    private final LayoutPlanner planner = new LayoutPlanner();
    private record Reservation(UUID parentId,String doorId,LayoutPlanner.Candidate candidate) {}
    private String key(UUID parent,String door) { return parent+"/"+door; }
    private String lastPlanningNote="";
    private boolean running;
    private boolean paused;
    private final Set<String> pending = new HashSet<>();
    private int taskId = -1;

    public GenerationEngine(InfinityLibraryPlugin plugin,RoomManager roomManager) {this(plugin,roomManager,Bukkit::getOnlinePlayers);}
    /** Player source is injectable for deterministic scheduler verification without a client connection. */
    public GenerationEngine(InfinityLibraryPlugin plugin,RoomManager roomManager,java.util.function.Supplier<? extends Collection<? extends Player>> explorers) {
        this(plugin,roomManager,explorers,false);
    }
    public GenerationEngine(InfinityLibraryPlugin plugin,RoomManager roomManager,java.util.function.Supplier<? extends Collection<? extends Player>> explorers,boolean procedural){
        this.plugin=plugin;this.roomManager=roomManager;this.explorers=Objects.requireNonNull(explorers);this.file=new File(plugin.getDataFolder(),"generated.yml");
    }

    public String worldName(){return plugin.getConfig().getString("world","InfinityLibrary");}
    public String startConfigKey(){return "start-location.";}
    public boolean isProcedural(){return procedural;}
    public boolean isRunning(){return running;}
    public void loadForInspection(){load();}
    public void start() {
        if(taskId!=-1)stop();
        running = false;
        explorationTicks=0;explorerCursor=0;lastPlanAttempt.clear();
        placementQueue.clear();
        load();
        ensureWorld();
        for (PlacedRoom pr : placed) if (pending.contains(pr.origin().toString()))
            roomManager.get(pr.roomId()).ifPresent(r -> queuePlacement(r, pr));
        if (placed.isEmpty()) resetToStart();
        if(plugin.getAuthoringManager()!=null){plugin.getAuthoringManager().clearGenerated();for(var pr:placed)roomManager.get(pr.roomId()).ifPresent(room->plugin.getAuthoringManager().generated(room,pr,worldName()));}
        if(!plugin.getConfig().getBoolean("generation.path-blocking.enabled",true))reopenDeadEnds();else for(PlacedRoom pr:placed)reapplyWalls(pr);
        save(); // Persist newly resolved recovery snapshots before the first queued block runs.
        int perTick = Math.max(1, plugin.getConfig().getInt("generation.placement-blocks-per-tick", 1800));
        int interval=Math.max(2,Math.min(200,plugin.getConfig().getInt("generation.exploration-check-interval-ticks",5)));
        running=true;
        taskId=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,() -> {
            for(int i=0;i<perTick;i++){Runnable r=placementQueue.poll();if(r==null)break;r.run();}
            if(++explorationTicks>=interval){explorationTicks=0;checkExplorers(explorers.get());if(placementQueue.isEmpty()&&!paused)checkDeadEnds();}
        },1L,1L);
        if(districtCatalog().isEmpty())plugin.getLogger().warning("Automatic expansion has no eligible normal templates. "+eligibilityProblem());
    }
    public void stop() { running = false; if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);taskId=-1; save(); }
    public World ensureWorld() {
        String name = worldName();
        World w = Bukkit.getWorld(name);
        if (w == null) w = Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NORMAL).generator(new VoidLibraryGenerator()));
        return Objects.requireNonNull(w);
    }

    public void tickPlayer(Player player) {tickPlayer(player,player.getLocation());}
    public void tickPlayer(Player player,Location location) {checkPlayer(player,location,new int[]{1});}

    /** Called by the repeating task, including while a player waits in the same block. */
    public void checkExplorers(Collection<? extends Player> players) {
        if(!canExpand())return;
        String world=worldName();
        List<? extends Player> present=players.stream().filter(Player::isOnline).filter(p -> p.getWorld().getName().equals(world)).toList();
        if(present.isEmpty())return;int[] planBudget={1};
        int count=Math.min(4,present.size());int start=Math.floorMod(explorerCursor,present.size());
        for(int i=0;i<count;i++) {
            Player player=present.get((start+i)%present.size());
            if(checkPlayer(player,player.getLocation(),planBudget)){explorerCursor=(start+i+1)%present.size();return;}
        }
        explorerCursor=(start+count)%present.size();
    }
    private boolean canExpand() {return running&&!paused&&placementQueue.isEmpty()&&!roomLimitReached()&&!districtCatalog().isEmpty();}
    private double proximityRadius() {double r=plugin.getConfig().getDouble("proximity-radius",9);return Double.isFinite(r)&&r>0?r:9;}
    private List<Expansion> openExits(Location location,double radius) {
        List<Expansion> exits=new ArrayList<>();
        for(PlacedRoom pr:placed)roomManager.get(pr.roomId()).ifPresent(room -> {
            for(ConnectionPoint cp:pr.connections(room))if(isOpen(pr,cp)){
                ConnectionPoint transformed=cp.transform(pr.transform(),room.size());
                double distance=pr.origin().add(transformed.position()).toLocation(location.getWorld()).add(0.5,0.5,0.5).distanceSquared(location);
                if(distance<=radius*radius)exits.add(new Expansion(pr,cp,transformed,distance));
            }
        });
        exits.sort(Comparator.comparingDouble(Expansion::distanceSquared));return exits;
    }
    private boolean checkPlayer(Player player,Location location,int[] planBudget) {
        if(!player.isOnline()||!canExpand()||location==null||location.getWorld()==null||!location.getWorld().getName().equals(worldName()))return false;
        for(Expansion expansion:openExits(location,proximityRadius()))if(expand(expansion,planBudget))return true;
        return false;
    }
    private boolean expand(Expansion expansion,int[] planBudget) {
        PlacedRoom parent=expansion.parent();String door=expansion.localPoint().id();String retryKey=key(parent.instanceId(),door);
        if(parent.generatedConnections().contains(door)||parent.closedConnections().containsKey(door))return false;
        Reservation reservation=reservations.get(retryKey);
        if(reservation==null) {
            long now=System.nanoTime();Long previous=lastPlanAttempt.get(retryKey);
            if(previous!=null&&now-previous<java.util.concurrent.TimeUnit.SECONDS.toNanos(3))return false;
            if(planBudget[0]<=0)return false;
            planBudget[0]--;lastPlanAttempt.put(retryKey,now);planParent(parent,door,null);reservation=reservations.get(retryKey);
        }
        if(reservation==null)return false;
        boolean committed=commit(reservation);
        if(!committed)lastPlanAttempt.put(retryKey,System.nanoTime());
        return committed;
    }

    private record Socket(PlacedRoom parent,Room room,ConnectionPoint local){}
    private List<Room> districtCatalog(){String base=plugin.getConfig().getString("generation.base-room-id","");if(base!=null&&!base.isBlank()&&roomManager.getActive(base).filter(r->r.type()!=RoomType.STARTING&&r.spawnChance()>0).isEmpty())return List.of();return roomManager.allRooms().stream().filter(r->r.type()!=RoomType.STARTING&&r.spawnChance()>0).filter(r->base==null||base.isBlank()||r.id().equals(base)||r.type()==RoomType.FILLER&&r.id().startsWith("builtin_corridor_")).sorted(Comparator.comparing(Room::id)).toList();}
    private List<ConnectionPoint> planningDoors(){List<ConnectionPoint> result=new ArrayList<>(frontier());for(var r:reservations.values())for(var cp:r.candidate().connections())result.add(r.candidate().worldDoor(cp));return result;}
    private void planParent(PlacedRoom parent,String requested,String forcedRoom){
        Room room=roomManager.get(parent.roomId()).orElse(null);if(room==null)return;
        if(forcedRoom!=null){var old=reservations.get(key(parent.instanceId(),requested));if(old!=null&&!old.candidate().room().id().equals(forcedRoom))discardBranch(old.candidate().instanceId());}
        List<DistrictLayoutPlanner.Socket> roots=new ArrayList<>();var exits=new ArrayList<>(parent.connections(room));exits.sort(Comparator.comparingInt(cp->cp.id().equals(requested)?-1:0));for(var cp:exits)if(isOpen(parent,cp)&&!reservations.containsKey(key(parent.instanceId(),cp.id())))roots.add(new DistrictLayoutPlanner.Socket(parent.instanceId(),cp.id(),worldDoor(parent,room,cp),0));if(roots.isEmpty())return;
        var catalog=districtCatalog();var normalSearch=search(catalog);if(forcedRoom!=null&&catalog.stream().noneMatch(r->r.id().equals(forcedRoom))){var forced=roomManager.getActive(forcedRoom).filter(r->r.type()!=RoomType.STARTING).orElseThrow(()->new IllegalArgumentException("Active normal room required"));catalog=new ArrayList<>(catalog);catalog.add(forced);}
        var search=forcedRoom==null?normalSearch:search(catalog);List<LayoutPlanner.Box> occupied=obstacles(null);List<ConnectionPoint> external=planningDoors();List<ConnectionPoint> viable=frontier().stream().filter(cp->normalSearch.continuation(cp,occupied,external)==TopologySearch.Fit.YES).toList();
        roots.removeIf(socket->(forcedRoom!=null&&socket.door().equals(requested)?search:normalSearch).continuation(socket.world(),occupied,external)!=TopologySearch.Fit.YES);if(roots.isEmpty()||forcedRoom!=null&&roots.stream().noneMatch(socket->socket.door().equals(requested))){lastPlanningNote=search.limited()||normalSearch.limited()?"District planning deferred: continuation budget reached":"Requested exit has no compatible continuation";return;}
        int limit=plugin.getConfig().getInt("generation.max-total-rooms",0);int capacity=limit<=0?32:Math.max(0,limit-placed.size()-reservations.size());int count=Math.min(capacity,Math.max(2,Math.min(24,plugin.getConfig().getInt("generation.district.rooms",12))));
        long seed=plugin.getConfig().getLong("generation.planning.seed",20261120L)^parent.origin().hashCode();
        var result=new DistrictLayoutPlanner().plan(roots,occupied,external,(socket,boxes)->{boolean forced=forcedRoom!=null&&socket.owner().equals(parent.instanceId())&&socket.door().equals(requested);return (forced?search:normalSearch).candidates(socket.world(),boxes,Math.max(1,Math.min(8,plugin.getConfig().getInt("generation.district.alternatives-per-template",4))),forced?forcedRoom:null);},(door,boxes)->normalSearch.continuation(door,boxes,external),chosen->search.preserves(viable,external,occupied,chosen),count,plugin.getConfig().getInt("generation.district.depth",6),plugin.getConfig().getInt("generation.district.beam-width",4),plugin.getConfig().getInt("generation.district.search-states",768),seed,forcedRoom!=null||limit>0);
        lastPlanningNote=result.edges().isEmpty()?(result.limited()||search.limited()||normalSearch.limited()?"District planning deferred: search/terrain budget reached. No opening was sealed.":"No compatible connected district fits the available templates and terrain."):"District reserved: "+result.edges().size()+" rooms across multiple generations";
        Map<String,Reservation> before=new LinkedHashMap<>(reservations);for(var edge:result.edges())reservations.put(key(edge.parent(),edge.door()),new Reservation(edge.parent(),edge.door(),edge.candidate()));try{save();}catch(RuntimeException ex){reservations.clear();reservations.putAll(before);throw ex;}
    }
    private void discardBranch(UUID instance){Set<UUID> invalid=new HashSet<>();invalid.add(instance);boolean changed;do{changed=false;for(var r:new ArrayList<>(reservations.values()))if(invalid.contains(r.parentId())||invalid.contains(r.candidate().instanceId())){changed|=invalid.add(r.candidate().instanceId());reservations.remove(key(r.parentId(),r.doorId()));}}while(changed);}

    private boolean commit(Reservation reservation) {
        if(roomLimitReached()) return false;
        PlacedRoom parent=placed.stream().filter(p -> p.instanceId().equals(reservation.parentId())).findFirst().orElse(null);
        if(parent==null || parent.generatedConnections().contains(reservation.doorId())||parent.closedConnections().containsKey(reservation.doorId())) { discardBranch(reservation.candidate().instanceId());save();return false; }
        LayoutPlanner.Candidate c=reservation.candidate();
        if(roomManager.getActive(c.room().id()).filter(r -> r.type()!=RoomType.STARTING).isEmpty()){replanFutureRooms();return false;}
        // Revalidate terrain and reservations immediately before mutating any blocks.
        if(!isWorldClear(c.box(),new int[]{Integer.MAX_VALUE})
            || placed.stream().anyMatch(p -> footprint(p).conflicts(c.box()))
            || reservations.values().stream().filter(r -> r!=reservation).anyMatch(r -> r.candidate().box().conflicts(c.box()))) {
            discardBranch(c.instanceId()); save(); return false;
        }
        var guard=search(districtCatalog());var before=obstacles(parent.instanceId());var open=planningDoors();var viable=open.stream().filter(cp -> guard.continuation(cp,before,open)!=TopologySearch.Fit.NO).toList();
        if(!guard.preserves(viable,open,before,List.of(c))){lastPlanningNote="Reserved placement deferred: cannot prove surrounding exits remain connectable. Check terrain/planning budgets.";discardBranch(c.instanceId());save();return false;}
        reservations.remove(key(parent.instanceId(),reservation.doorId()));
        parent.generatedConnections().add(reservation.doorId());
        PlacedRoom pr=new PlacedRoom(c.instanceId(),c.room().id(),c.origin(),c.box().size(),c.transform());
        pr.diversity(c.diversity());pr.connections(c.connections());
        pr.generatedConnections().add(c.entranceId());
        connectExistingExits(pr,c.room());
        placed.add(pr);
        pending.add(pr.origin().toString());
        // Reserve the child's open exits before its first block is placed.
        if(reservations.values().stream().noneMatch(r->r.parentId().equals(pr.instanceId())))planParent(pr,null,null);
        queuePlacement(c.room(),pr); save(); return true;
    }

    private void connectExistingExits(PlacedRoom next,Room room) {
        for(ConnectionPoint cp:next.connections(room)) {
            if(!isOpen(next,cp)) continue;
            ConnectionPoint door=cp.transform(next.transform(),room.size());
            Vector3i at=next.origin().add(door.position());
            for(PlacedRoom existing:placed) {
                Room template=roomManager.get(existing.roomId()).orElse(null); if(template==null) continue;
                for(ConnectionPoint old:existing.connections(template)) {
                    if(!isOpen(existing,old)) continue;
                    ConnectionPoint other=old.transform(existing.transform(),template.size());
                    if(door.compatibleWith(other) && at.equals(existing.origin().add(other.position()))) {
                        next.generatedConnections().add(cp.id()); existing.generatedConnections().add(old.id());
                        reservations.remove(key(existing.instanceId(),old.id()));
                    }
                }
            }
        }
    }

    private boolean isOpen(PlacedRoom pr,ConnectionPoint cp){return !pr.generatedConnections().contains(cp.id())&&!pr.closedConnections().containsKey(cp.id());}
    private ConnectionPoint worldDoor(PlacedRoom pr,Room room,ConnectionPoint cp){return cp.transform(pr.transform(),room.size()).offset(cp.id(),pr.origin());}
    private List<ConnectionPoint> frontier(){List<ConnectionPoint> result=new ArrayList<>();for(PlacedRoom pr:placed)roomManager.get(pr.roomId()).ifPresent(room -> {for(ConnectionPoint cp:pr.connections(room))if(isOpen(pr,cp))result.add(worldDoor(pr,room,cp));});return result;}
    private LayoutPlanner.Box footprint(PlacedRoom pr){Room room=roomManager.get(pr.roomId()).orElse(null);return new LayoutPlanner.Box(pr.origin(),pr.size(),room==null?List.of():pr.connections(room).stream().map(cp -> worldDoor(pr,room,cp)).toList());}
    private boolean sharedCell(Vector3i pos,List<LayoutPlanner.Box> shared){return shared.stream().anyMatch(b -> b.contains(pos));}
    private List<LayoutPlanner.Box> sharedFaces(LayoutPlanner.Box box,UUID except){return placed.stream().filter(pr -> !pr.instanceId().equals(except)).map(this::footprint).filter(box::sharesBoundary).toList();}
    private List<LayoutPlanner.Box> obstacles(UUID ignoreParent){List<LayoutPlanner.Box> result=new ArrayList<>();for(PlacedRoom pr:placed)result.add(footprint(pr));for(Reservation r:reservations.values())if(ignoreParent==null||!r.parentId().equals(ignoreParent))result.add(r.candidate().box());return result;}
    private TopologySearch search(Collection<Room> catalog){
        int[] budget={Math.max(1000,Math.min(2000000,plugin.getConfig().getInt("generation.planning.world-block-budget",300000)))};
        return new TopologySearch(catalog,Math.max(1,Math.min(256,plugin.getConfig().getInt("generation.planning.topology-choices-per-room",64))),Math.max(1000,Math.min(2000000,plugin.getConfig().getInt("generation.planning.connection-check-budget",200000))),box -> {
            World w=ensureWorld();Vector3i o=box.origin(),z=box.size();List<LayoutPlanner.Box> shared=sharedFaces(box,null);
            if(o.y()<w.getMinHeight()||(long)o.y()+z.y()>w.getMaxHeight()||!w.getWorldBorder().isInside(o.toLocation(w))||!w.getWorldBorder().isInside(o.add(z).subtract(new Vector3i(1,1,1)).toLocation(w)))return TopologySearch.Fit.NO;
            for(int x=o.x();x<o.x()+z.x();x++)for(int y=o.y();y<o.y()+z.y();y++)for(int k=o.z();k<o.z()+z.z();k++){if(budget[0]--<=0)return TopologySearch.Fit.UNKNOWN;if(!sharedCell(new Vector3i(x,y,k),shared)&&!w.getBlockAt(x,y,k).getType().isAir())return TopologySearch.Fit.NO;}
            return TopologySearch.Fit.YES;
        },plugin.getConfig().getLong("generation.planning.seed",20261120L));
    }
    private void checkDeadEnds(){
        if(!running||!plugin.getConfig().getBoolean("generation.path-blocking.enabled",true)||districtCatalog().isEmpty())return;
        List<Expansion> exits=openExits(new Location(ensureWorld(),0,0,0),Double.POSITIVE_INFINITY);if(exits.isEmpty())return;
        var search=search(districtCatalog());var doors=planningDoors();var boxes=placed.stream().map(pr -> footprint(pr)).toList();
        int checks=Math.min(2,exits.size());int start=Math.floorMod(deadEndCursor,exits.size());
        for(int i=0;i<checks;i++){Expansion e=exits.get((start+i)%exits.size());if(reservations.containsKey(key(e.parent().instanceId(),e.localPoint().id())))continue;Room room=roomManager.get(e.parent().roomId()).orElse(null);if(room!=null&&search.continuation(worldDoor(e.parent(),room,e.localPoint()),boxes,doors)==TopologySearch.Fit.NO)sealConnection(e.parent(),e.localPoint());}
        deadEndCursor=(start+checks)%exits.size();
    }
    private BlockData wallData(){
        String value=plugin.getConfig().getString("generation.path-blocking.block-data",plugin.getConfig().getString("generation.path-blocking.material","SMOOTH_STONE"));
        try{BlockData data=Bukkit.createBlockData(value.toLowerCase(Locale.ROOT));if(!data.getMaterial().isSolid())throw new IllegalArgumentException();return data;}catch(IllegalArgumentException ex){return Material.SMOOTH_STONE.createBlockData();}
    }
    private void sealConnection(PlacedRoom parent,ConnectionPoint local){
        if(!isOpen(parent,local))return;Room room=roomManager.get(parent.roomId()).orElse(null);if(room==null)return;
        List<RoomBlock> restore=new ArrayList<>();World w=ensureWorld();for(Vector3i cell:local.cells()){Vector3i pos=parent.origin().add(parent.transform().transform(cell,room.size()));restore.add(new RoomBlock(pos,w.getBlockAt(pos.x(),pos.y(),pos.z()).getBlockData().getAsString()));}
        parent.closedConnections().put(local.id(),List.copyOf(restore));reservations.remove(key(parent.instanceId(),local.id()));save();reapplyWalls(parent);
    }
    private void reapplyWalls(PlacedRoom pr){World w=ensureWorld();for(var entry:pr.closedConnections().entrySet())for(RoomBlock rb:entry.getValue()){BlockData data=wallData();String door=entry.getKey();placementQueue.add(() -> {if(pr.closedConnections().containsKey(door))w.getBlockAt(rb.position().x(),rb.position().y(),rb.position().z()).setBlockData(data,false);});}}
    public int closedExitCount(){return placed.stream().mapToInt(pr -> pr.closedConnections().size()).sum();}
    public void reopenDeadEnds(){World w=ensureWorld();for(PlacedRoom pr:placed){for(var blocks:pr.closedConnections().values())for(RoomBlock rb:blocks)w.getBlockAt(rb.position().x(),rb.position().y(),rb.position().z()).setBlockData(Bukkit.createBlockData(rb.blockData()),false);pr.closedConnections().clear();}save();}

    public void resetToStart() {
        Room start=roomManager.getActive(roomManager.activeStartId()).orElseThrow(() -> new IllegalArgumentException("Active start template is missing. Restore it or select /il startroom <id> before resetting."));
        World w = ensureWorld();
        int startY=plugin.getConfig().getInt(startConfigKey()+"y",80);
        if(startY<w.getMinHeight()||(long)startY+start.size().y()>w.getMaxHeight())throw new IllegalArgumentException("Starting room would exceed world height; fix /il setstart before resetting.");
        if(plugin.getAuthoringManager()!=null)plugin.getAuthoringManager().clearGenerated();
        synchronized (placed) {
            for (PlacedRoom pr : placed) clearBox(w, pr.origin(), pr.size());
            for (Vector3i pos : sealedBlocks) w.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
            placed.clear(); placementQueue.clear(); pending.clear(); reservations.clear(); lastPlanAttempt.clear();
            sealedBlocks.clear();
            Vector3i origin = new Vector3i(plugin.getConfig().getInt(startConfigKey()+"x",0), plugin.getConfig().getInt(startConfigKey()+"y",80), plugin.getConfig().getInt(startConfigKey()+"z",0));
            PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), start.id(), origin, start.size());
            placed.add(pr); queuePlacement(start, pr);
        }
        save();
    }

    private void queuePlacement(Room room, PlacedRoom instance) {
        Vector3i origin=instance.origin();RoomTransform transform=instance.transform();
        if(instance.diversity()==null)instance.diversity(com.infinitylibrary.diversity.DiversityComposition.resolve(room,instance.instanceId()));
        if(instance.frozenConnections()==null)instance.connections(instance.diversity().connections(room));
        if(instance.props()==null)instance.props(plugin.getPropManager().compose(room,instance.instanceId()));
        World w = ensureWorld();
        List<LayoutPlanner.Box> shared=sharedFaces(footprint(instance),instance.instanceId());
        pending.add(origin.toString());
        for (RoomBlock rb : room.blocks()) {
            if(instance.diversity().patches().stream().anyMatch(patch -> patch.contains(rb.position())))continue;
            Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
            BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
            placementQueue.add(() -> {
                if(sharedCell(pos,shared))return;
                Block block = w.getBlockAt(pos.x(), pos.y(), pos.z());
                block.setBlockData(data, false);
                if (block.getType() == Material.CHISELED_BOOKSHELF){applyShelfCategory(room,rb.position(),block);plugin.getBookStorageManager().populateBookshelf(block);}
                if (block.getState() instanceof Sign sign) updateLibrarySign(sign, pos);
            });
        }
        Map<String, List<VariationArea>> byCategory = new HashMap<>();
        for (VariationArea v : room.variations()) byCategory.computeIfAbsent(v.category(), k -> new ArrayList<>()).add(v);
        for (List<VariationArea> categoryVars : byCategory.values()) {
            Collections.shuffle(categoryVars);
            for (VariationArea v : categoryVars) {
                if (v.chancePercent() <= 0 || Math.random() * 100.0 >= v.chancePercent()) continue;
                for (RoomBlock rb : v.blocks()) {
                    Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
                    BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
                    placementQueue.add(() -> {if(!sharedCell(pos,shared))w.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(data, false);});
                }
                break;
            }
        }
        for(var patch:instance.diversity().patches()) {
            for(int y=0;y<patch.size().y();y++)for(int z=0;z<patch.size().z();z++)for(int x=0;x<patch.size().x();x++) {
                Vector3i pos=origin.add(transform.transform(patch.min().add(new Vector3i(x,y,z)),room.size()));
                placementQueue.add(() -> {if(!sharedCell(pos,shared))w.getBlockAt(pos.x(),pos.y(),pos.z()).setType(Material.AIR,false);});
            }
            for(RoomBlock rb:patch.blocks()) {
                Vector3i pos=origin.add(transform.transform(rb.position(),room.size()));BlockData data=Bukkit.createBlockData(rb.blockData());rotateData(data,transform);
                placementQueue.add(() -> {if(sharedCell(pos,shared))return;Block block=w.getBlockAt(pos.x(),pos.y(),pos.z());block.setBlockData(data,false);if(block.getType()==Material.CHISELED_BOOKSHELF){applyShelfCategory(room,rb.position(),block);plugin.getBookStorageManager().populateBookshelf(block);}if(block.getState() instanceof Sign sign)updateLibrarySign(sign,pos);});
            }
        }
        for(com.infinitylibrary.props.PropArea area:instance.props().areas()) {
            for(int y=0;y<area.size().y();y++)for(int z=0;z<area.size().z();z++)for(int x=0;x<area.size().x();x++) {
                Vector3i local=area.min().add(new Vector3i(x,y,z));
                Vector3i pos=origin.add(transform.transform(local,room.size()));
                placementQueue.add(() -> {if(!sharedCell(pos,shared))w.getBlockAt(pos.x(),pos.y(),pos.z()).setType(Material.AIR,false);});
            }
        }
        for(RoomBlock rb:instance.props().blocks()) {
            Vector3i pos=origin.add(transform.transform(rb.position(),room.size()));
            BlockData data=Bukkit.createBlockData(rb.blockData());rotateData(data,transform);
            placementQueue.add(() -> {
                if(sharedCell(pos,shared))return;
                Block block=w.getBlockAt(pos.x(),pos.y(),pos.z());block.setBlockData(data,false);
                if(block.getType()==Material.CHISELED_BOOKSHELF){applyShelfCategory(room,rb.position(),block);plugin.getBookStorageManager().populateBookshelf(block);}
                if(block.getState() instanceof Sign sign)updateLibrarySign(sign,pos);
            });
        }
        // Both templates may contain doorway markers/blocks. Open the joined aperture last.
        for(ConnectionPoint cp:instance.connections(room))if(instance.generatedConnections().contains(cp.id()))for(Vector3i cell:cp.cells()){
            Vector3i pos=origin.add(transform.transform(cell,room.size()));
            placementQueue.add(() -> w.getBlockAt(pos.x(),pos.y(),pos.z()).setType(Material.AIR,false));
        }
        placementQueue.add(() -> {if(plugin.getAuthoringManager()!=null)plugin.getAuthoringManager().generated(room,instance,worldName());pending.remove(origin.toString()); save(); });
    }

    private void applyShelfCategory(Room room,Vector3i local,Block block){for(var region:room.regions())if(region.kind().equals("shelf")&&region.contains(local))plugin.getBookStorageManager().setShelfCategory(block.getLocation(),region.category());}
    public void rotateData(BlockData data, RoomTransform transform) {
        if (transform.flipX()) data.mirror(org.bukkit.block.structure.Mirror.FRONT_BACK);
        if (transform.flipZ()) data.mirror(org.bukkit.block.structure.Mirror.LEFT_RIGHT);
        data.rotate(switch (transform.rotation()) {
            case NONE -> org.bukkit.block.structure.StructureRotation.NONE;
            case CLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
            case CLOCKWISE_270 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
        });
    }

    private void updateLibrarySign(Sign sign, Vector3i pos) {
        if (placed.size() < plugin.getConfig().getInt("generation.sign-min-rooms", 6)) return;
        Vector3i start = new Vector3i(plugin.getConfig().getInt(startConfigKey()+"x",0), plugin.getConfig().getInt(startConfigKey()+"y",80), plugin.getConfig().getInt(startConfigKey()+"z",0));
        String bookDir = nearestRoomDirection(pos, RoomType.BOOK);
        String readDir = nearestRoomDirection(pos, RoomType.READ);
        sign.setLine(0, "Infinity Library");
        sign.setLine(1, "Start: " + directionToward(pos, start));
        sign.setLine(2, "Book:" + (bookDir == null ? "?" : bookDir) + " Read:" + (readDir == null ? "?" : readDir));
        sign.setLine(3, "Rooms: " + placed.size());
        sign.update(true, false);
    }

    private String nearestRoomDirection(Vector3i from, RoomType type) {
        double best = Double.MAX_VALUE;
        Vector3i bestTarget = null;
        synchronized (placed) {
            for (PlacedRoom pr : placed) {
                Room room = roomManager.get(pr.roomId()).orElse(null);
                if (room == null || room.type() != type) continue;
                Vector3i target = pr.origin().add(new Vector3i(room.size().x()/2, 1, room.size().z()/2));
                double dist = Math.pow(target.x()-from.x(),2) + Math.pow(target.y()-from.y(),2) + Math.pow(target.z()-from.z(),2);
                if (dist < best) { best = dist; bestTarget = target; }
            }
        }
        return bestTarget == null ? null : directionToward(from, bestTarget);
    }

    private String directionToward(Vector3i from, Vector3i to) {
        int dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        if (Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) return dy > 0 ? "UP" : "DOWN";
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "EAST" : "WEST";
        return dz > 0 ? "SOUTH" : "NORTH";
    }

    private boolean isWorldClear(LayoutPlanner.Box box,int[] budget) {
        Vector3i origin=box.origin(),size=box.size();World w=ensureWorld();List<LayoutPlanner.Box> shared=sharedFaces(box,null);
        if(origin.y()<w.getMinHeight()||(long)origin.y()+size.y()>w.getMaxHeight())return false;
        if(!w.getWorldBorder().isInside(origin.toLocation(w))||!w.getWorldBorder().isInside(origin.add(size).subtract(new Vector3i(1,1,1)).toLocation(w)))return false;
        for(int x=origin.x();x<origin.x()+size.x();x++)for(int y=origin.y();y<origin.y()+size.y();y++)for(int z=origin.z();z<origin.z()+size.z();z++){
            if(budget[0]--<=0)return false;
            if(!sharedCell(new Vector3i(x,y,z),shared)&&!w.getBlockAt(x,y,z).getType().isAir())return false;
        }
        return true;
    }


    private void clearBox(World w, Vector3i o, Vector3i s) {
        for (int x=o.x(); x<o.x()+s.x(); x++) for (int y=o.y(); y<o.y()+s.y(); y++) for (int z=o.z(); z<o.z()+s.z(); z++) w.getBlockAt(x,y,z).setType(Material.AIR, false);
    }
    private Vector3i faceVector(BlockFace f) { return new Vector3i(f.getModX(), f.getModY(), f.getModZ()); }

    public int generateAroundPlayer(Player player, int maxRooms) {
        int generated = 0;
        for (int i = 0; i < maxRooms; i++) {
            int before;
            synchronized (placed) { before = placed.size(); }
            tickPlayer(player);
            int after;
            synchronized (placed) { after = placed.size(); }
            if (after <= before) break;
            generated += (after - before);
        }
        return generated;
    }

    public boolean placeRoomImmediately(String roomId) {
        Room room=roomManager.getActive(roomId).orElse(null);
        if(room!=null&&room.type()==RoomType.STARTING)return false;
        if(paused||roomLimitReached()||!placementQueue.isEmpty()||room==null) return false;
        for(PlacedRoom parent:List.copyOf(placed)) {
            Room source=roomManager.get(parent.roomId()).orElse(null); if(source==null) continue;
            for(ConnectionPoint cp:parent.connections(source)) {
                if(!isOpen(parent,cp)) continue;
                planParent(parent,cp.id(),roomId);
                Reservation r=reservations.get(key(parent.instanceId(),cp.id()));
                if(r!=null && r.candidate().room().id().equals(roomId)) return commit(r);
            }
        }
        return false;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration(); y.set("connection-layout","shared-plane-v1");y.set("layout-mode","district-v1"); y.set("pending", new ArrayList<>(pending)); y.set("paused", paused); y.set("world", worldName()); y.set("sealed-blocks", sealedBlocks.stream().map(Vector3i::toString).toList()); ConfigurationSection root = y.createSection("rooms"); int i=0;
        synchronized (placed) { for (PlacedRoom pr : placed) { ConfigurationSection s = root.createSection(String.valueOf(i++)); s.set("uuid", pr.instanceId().toString()); s.set("room-id", pr.roomId()); pr.origin().write(s.createSection("origin")); pr.size().write(s.createSection("size")); writeTransform(s.createSection("transform"), pr.transform()); s.set("generated-connections", new ArrayList<>(pr.generatedConnections())); if(pr.props()!=null)pr.props().write(s.createSection("props"));if(pr.diversity()!=null)pr.diversity().write(s.createSection("diversity"));if(pr.frozenConnections()!=null){var cps=s.createSection("connections");for(ConnectionPoint cp:pr.frozenConnections())cp.write(cps.createSection(cp.id()));}var closed=s.createSection("closed-connections");for(var entry:pr.closedConnections().entrySet())closed.set(entry.getKey(),entry.getValue().stream().map(b -> b.position()+"|"+b.blockData()).toList()); } }
        ConfigurationSection plans=y.createSection("plans"); int j=0;
        for(Reservation r:reservations.values()) {
            ConfigurationSection s=plans.createSection(String.valueOf(j++));
            s.set("parent",r.parentId().toString()); s.set("door",r.doorId()); s.set("room",r.candidate().room().id());
            s.set("instance",r.candidate().instanceId().toString());r.candidate().diversity().write(s.createSection("diversity"));s.set("entrance",r.candidate().entranceId()); r.candidate().origin().write(s.createSection("origin"));
            writeTransform(s.createSection("transform"),r.candidate().transform());
        }
        com.infinitylibrary.community.AtomicYaml.write(file.toPath(),y);
    }
    private void load() {
        YamlConfiguration y = com.infinitylibrary.community.AtomicYaml.read(file.toPath());
        placed.clear(); pending.clear(); sealedBlocks.clear(); reservations.clear(); if (!file.exists()) return;
        if (!y.getString("world", worldName()).equals(worldName())) throw new IllegalStateException("Restore the configured library world before loading generated.yml");
        pending.addAll(y.getStringList("pending")); paused = y.getBoolean("paused");
        for (String v : y.getStringList("sealed-blocks")) sealedBlocks.add(Vector3i.parse(v));
        ConfigurationSection root = y.getConfigurationSection("rooms"); if (root == null) return;
        for (String key : root.getKeys(false)) { ConfigurationSection s = root.getConfigurationSection(key); PlacedRoom pr = new PlacedRoom(UUID.fromString(s.getString("uuid")), s.getString("room-id"), Vector3i.read(s.getConfigurationSection("origin")), Vector3i.read(s.getConfigurationSection("size")), readTransform(s.getConfigurationSection("transform"))); pr.generatedConnections().addAll(s.getStringList("generated-connections"));
            if(s.isConfigurationSection("props"))pr.props(com.infinitylibrary.props.PropComposition.read(s.getConfigurationSection("props")));
            else if(!pending.contains(pr.origin().toString()))pr.props(com.infinitylibrary.props.PropComposition.EMPTY);
            if(s.isConfigurationSection("diversity"))pr.diversity(com.infinitylibrary.diversity.DiversityComposition.read(s.getConfigurationSection("diversity")));
            else if(!pending.contains(pr.origin().toString()))pr.diversity(com.infinitylibrary.diversity.DiversityComposition.EMPTY);
            if(s.isConfigurationSection("connections")){var c=s.getConfigurationSection("connections");List<ConnectionPoint> cps=new ArrayList<>();for(String id:c.getKeys(false))cps.add(ConnectionPoint.read(id,c.getConfigurationSection(id)));pr.connections(cps);}
            else if(!pending.contains(pr.origin().toString()))roomManager.get(pr.roomId()).ifPresent(r -> pr.connections(pr.diversity().connections(r)));
            var closed=s.getConfigurationSection("closed-connections");if(closed!=null)for(String id:closed.getKeys(false)){List<RoomBlock> blocks=new ArrayList<>();for(String line:closed.getStringList(id)){String[] parts=line.split("\\|",2);blocks.add(new RoomBlock(Vector3i.parse(parts[0]),parts[1]));}pr.closedConnections().put(id,List.copyOf(blocks));}
            placed.add(pr); }
        ConfigurationSection plans=y.getConfigurationSection("plans");
        List<Reservation> waiting=new ArrayList<>();
        if(plans!=null&&y.getString("connection-layout","").equals("shared-plane-v1")&&Set.of("district-v1","unified-v1").contains(y.getString("layout-mode","")))for(String id:plans.getKeys(false))try{var section=plans.getConfigurationSection(id);Room template=roomManager.getActive(section.getString("room")).filter(r->r.type()!=RoomType.STARTING).orElseThrow();UUID instance=UUID.fromString(section.getString("instance"));var diversity=section.isConfigurationSection("diversity")?com.infinitylibrary.diversity.DiversityComposition.read(section.getConfigurationSection("diversity")):com.infinitylibrary.diversity.DiversityComposition.resolve(template,instance);waiting.add(new Reservation(UUID.fromString(section.getString("parent")),section.getString("door"),new LayoutPlanner.Candidate(template,section.getString("entrance"),readTransform(section.getConfigurationSection("transform")),Vector3i.read(section.getConfigurationSection("origin")),instance,diversity)));}catch(RuntimeException ex){plugin.getLogger().warning("Invalid district reservation skipped: "+ex.getMessage());}
        Map<UUID,LayoutPlanner.Candidate> future=new HashMap<>();boolean progressed;
        do{progressed=false;for(var r:new ArrayList<>(waiting)){PlacedRoom owner=placed.stream().filter(p->p.instanceId().equals(r.parentId())).findFirst().orElse(null);var planned=future.get(r.parentId());if(owner==null&&planned==null)continue;waiting.remove(r);try{ConnectionPoint target;if(owner!=null){Room template=roomManager.get(owner.roomId()).orElseThrow();var cp=owner.connections(template).stream().filter(c->c.id().equals(r.doorId())&&isOpen(owner,c)).findFirst().orElseThrow();target=worldDoor(owner,template,cp);}else{var cp=planned.connections().stream().filter(c->c.id().equals(r.doorId())&&!c.id().equals(planned.entranceId())).findFirst().orElseThrow();target=planned.worldDoor(cp);}var entrance=r.candidate().connections().stream().filter(c->c.id().equals(r.candidate().entranceId())).findFirst().orElseThrow();if(!TopologySearch.linked(target,r.candidate().worldDoor(entrance)))throw new IllegalArgumentException("Door alignment changed");if(obstacles(null).stream().anyMatch(box->box.conflicts(r.candidate().box())))throw new IllegalArgumentException("Reservation overlaps a room");reservations.put(key(r.parentId(),r.doorId()),r);future.put(r.candidate().instanceId(),r.candidate());progressed=true;}catch(RuntimeException ex){plugin.getLogger().warning("Unreachable district branch discarded: "+ex.getMessage());}}}while(progressed&&!waiting.isEmpty());
        if(!waiting.isEmpty())plugin.getLogger().warning("Discarded "+waiting.size()+" orphan district reservations");
    }
    private void writeTransform(ConfigurationSection s, RoomTransform transform) {
        s.set("rotation", transform.rotation().name());
        s.set("flip-x", transform.flipX());
        s.set("flip-z", transform.flipZ());
    }
    private RoomTransform readTransform(ConfigurationSection s) {
        if (s == null) return RoomTransform.IDENTITY;
        return new RoomTransform(Rotation.valueOf(s.getString("rotation", Rotation.NONE.name())), s.getBoolean("flip-x", false), s.getBoolean("flip-z", false));
    }
    private String eligibilityProblem() {
        String base=plugin.getConfig().getString("generation.base-room-id","builtin_modular");
        if(base!=null&&!base.isBlank()) {
            Room selected=roomManager.getActive(base).orElse(null);
            if(selected==null)return "Base '"+base+"' is missing/archived; use /il baseroom <normal-room-id|all>.";
            if(selected.type()==RoomType.STARTING)return "Base '"+base+"' is STARTING; choose a normal room role.";
            if(selected.spawnChance()<=0)return "Base '"+base+"' has roomchance 0; enable it with /il roomchance.";
        }
        return "No enabled normal expansion template. Save/select a normal base or restore one.";
    }
    public int eligibleTemplates(){return districtCatalog().size();}
    public int openExitCount() {int count=0;for(PlacedRoom pr:placed){Room r=roomManager.get(pr.roomId()).orElse(null);if(r!=null)for(ConnectionPoint cp:pr.connections(r))if(isOpen(pr,cp))count++;}return count;}
    public String explorationStatus() {
        if(!running)return "Stopped";if(paused)return "Paused (/il resume)";
        if(districtCatalog().isEmpty())return eligibilityProblem();
        if(roomLimitReached())return "Built-room limit reached";if(!placementQueue.isEmpty())return "Building queued rooms; exploration resumes automatically";
        if(openExitCount()==0)return "No open exits in the placed rooms";
        return "Ready: movement and periodic proximity checks enabled";
    }
    public List<String> diagnose(Player player) {
        List<String> lines=new ArrayList<>();if(!lastPlanningNote.isEmpty())lines.add(lastPlanningNote);lines.add("Automatic generation: "+explorationStatus());
        lines.add("World: "+worldName()+" | Start: "+roomManager.activeStartId()+" | Base: "+plugin.getConfig().getString("generation.base-room-id","builtin_modular"));
        lines.add("Eligible normal templates: "+districtCatalog().size()+" | Closed exits: "+closedExitCount()+" | Open exits: "+openExitCount()+" | Trigger radius: "+proximityRadius());
        if(!player.getWorld().getName().equals(worldName())){lines.add("You are outside the configured library world.");return lines;}
        List<Expansion> nearest=openExits(player.getLocation(),Double.POSITIVE_INFINITY);
        if(nearest.isEmpty())return lines;
        for(Expansion e:nearest.stream().limit(3).toList()) {
            ConnectionPoint door=e.worldPoint();Set<String> matching=new LinkedHashSet<>();
            for(Room r:districtCatalog())for(var choice:com.infinitylibrary.diversity.DiversityComposition.choices(r,64).values())for(ConnectionPoint cp:choice.connections(r))for(RoomTransform t:RoomTransform.all())if(door.compatibleWith(cp.transform(t,r.size())))matching.add(r.id());
            lines.add("Exit "+e.parent().roomId()+"/"+e.localPoint().id()+" | "+String.format(Locale.ROOT,"%.1f",Math.sqrt(e.distanceSquared()))+" blocks | "+door.direction()+" "+door.width()+"x"+door.height()+" floor "+door.floor()+" | Matching templates: "+(matching.isEmpty()?"NONE (check exit sizes/floor index)":String.join(", ",matching)));
        }
        if(nearest.getFirst().distanceSquared()>proximityRadius()*proximityRadius())lines.add("Move closer to an open exit; all are outside the trigger radius.");
        else lines.add("If a compatible exit cannot expand, its reserved volume must be clear of terrain/rooms and inside world bounds. Other nearby exits are still tried.");
        return lines;
    }

    public boolean roomLimitReached() { int limit = plugin.getConfig().getInt("generation.max-total-rooms", 0); return limit > 0 && placed.size() >= limit; }
    public void replanFutureRooms() { lastPlanningNote="";reservations.clear();lastPlanAttempt.clear();reopenDeadEnds();save(); }
    /** Immutable navigation snapshots; only completed rooms and actually connected, open doorways. */
    public UUID libraryId(){return placed.isEmpty()?null:placed.getFirst().instanceId();}
    public List<com.infinitylibrary.community.RoomGraph.RoomNode> navigationRooms(){
        List<com.infinitylibrary.community.RoomGraph.RoomNode> result=new ArrayList<>();
        for(PlacedRoom pr:placed)if(!pending.contains(pr.origin().toString()))roomManager.get(pr.roomId()).ifPresent(room -> result.add(new com.infinitylibrary.community.RoomGraph.RoomNode(pr.instanceId(),pr.roomId(),room.type(),footprint(pr),pr.connections(room).stream().filter(cp -> pr.generatedConnections().contains(cp.id())&&!pr.closedConnections().containsKey(cp.id())).map(cp -> worldDoor(pr,room,cp)).toList())));
        return List.copyOf(result);
    }
    public int plannedCount() { return reservations.size(); }
    public Optional<PlacedRoom> roomAt(Location location){if(location==null||location.getWorld()==null||!worldName().equals(location.getWorld().getName()))return Optional.empty();var point=Vector3i.from(location);return placed.stream().filter(p->footprint(p).contains(point)).min(Comparator.comparingLong(p->(long)p.size().x()*p.size().y()*p.size().z()));}
    public int futurePlanCount(){Set<UUID> built=new HashSet<>();placed.forEach(p->built.add(p.instanceId()));return (int)reservations.values().stream().filter(r->!built.contains(r.parentId())).count();}
    public int placedCount() { return placed.size(); }
    public int queuedBlocks() { return placementQueue.size(); }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean value) { paused = value; save(); }
    public boolean usesRoom(String id) { return placed.stream().anyMatch(r -> r.roomId().equals(id)) || reservations.values().stream().anyMatch(r -> r.candidate().room().id().equals(id)); }
    public Location homeLocation() {
        World w = ensureWorld();
        if (!placementQueue.isEmpty()) throw new IllegalArgumentException("Library construction is still in progress. Try again shortly.");
        if (placed.isEmpty()) throw new IllegalArgumentException("No starting room has been generated.");
        PlacedRoom start = placed.getFirst();
        for (int y = start.origin().y() + 1; y < start.origin().y() + start.size().y() - 1; y++)
            for (int x = start.origin().x() + 1; x < start.origin().x() + start.size().x() - 1; x++)
                for (int z = start.origin().z() + 1; z < start.origin().z() + start.size().z() - 1; z++) {
                    if (w.getBlockAt(x,y-1,z).getType().isSolid() && w.getBlockAt(x,y,z).isEmpty() && w.getBlockAt(x,y+1,z).isEmpty())
                        return new Location(w,x+0.5,y,z+0.5);
                }
        throw new IllegalArgumentException("No safe standing space in the starting room.");
    }
    private record Expansion(PlacedRoom parent, ConnectionPoint localPoint, ConnectionPoint worldPoint,double distanceSquared) {}
}
