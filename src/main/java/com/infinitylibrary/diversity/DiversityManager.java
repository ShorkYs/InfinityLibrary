package com.infinitylibrary.diversity;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Target two corners, then source two corners. Source selection may be outside the room. */
public final class DiversityManager {
    private final InfinityLibraryPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private record Context(UUID world,Vector3i min,Vector3i max) {Vector3i size(){return max.subtract(min).add(new Vector3i(1,1,1));}}
    private static final class Session {
        Context context;String areaId="area_1",variantId="variant_1";double weight=1;
        Vector3i firstTarget,firstSource;DiversityArea target;
        final Map<String,DiversityArea> staged=new LinkedHashMap<>();
    }
    public DiversityManager(InfinityLibraryPlugin plugin){this.plugin=plugin;wandKey=new NamespacedKey(plugin,"room_diversity_wand");}
    private Context context(Player p){var b=plugin.getRoomManager().selectionBounds(p);return new Context(p.getWorld().getUID(),b.min(),b.max());}
    private Session session(Player p){Context c=context(p);Session s=sessions.computeIfAbsent(p.getUniqueId(),id -> new Session());if(!c.equals(s.context)){s.firstTarget=null;s.firstSource=null;s.target=null;s.staged.clear();s.context=c;}return s;}
    public void clear(Player p){sessions.remove(p.getUniqueId());}
    public void mode(Player p,String areaId,String variantId,double weight){
        new DiversityArea(areaId,new Vector3i(0,0,0),new Vector3i(1,1,1),1,List.of(new DiversityVariant(variantId,weight,List.of())));
        Session s=session(p);s.areaId=areaId;s.variantId=variantId;s.weight=weight;s.firstTarget=null;s.firstSource=null;s.target=null;
    }
    public void variant(Player p,String areaId,String variantId,double weight){
        Session s=session(p);DiversityArea area=s.staged.get(areaId);if(area==null)throw new IllegalArgumentException("No staged area with that ID. Capture it first or /il diversityload <roomId>.");
        new DiversityVariant(variantId,weight,List.of());s.areaId=areaId;s.variantId=variantId;s.weight=weight;s.target=area;s.firstTarget=null;s.firstSource=null;
        p.sendMessage("§dSelect two source corners for "+variantId+". Required X,Y,Z size: "+area.size());
    }
    public void empty(Player p,String areaId,String variantId,double weight){
        Session s=session(p);if(s.target!=null||s.firstTarget!=null)throw new IllegalArgumentException("Finish or cancel the current capture first.");
        DiversityArea area=s.staged.get(areaId);if(area==null)throw new IllegalArgumentException("Load or capture this Diversity target first.");
        DiversityArea next=area.withVariant(new DiversityVariant(variantId,weight,List.of()));List<DiversityArea> all=new ArrayList<>(s.staged.values());all.removeIf(a -> a.id().equals(areaId));all.add(next);
        Room.validateDiversityAreas(s.context.size(),plugin.getSelectionManager().stagedConnections(p),plugin.getPropManager().staged(p),plugin.getSelectionManager().stagedVariations(p),all);s.staged.put(areaId,next);
    }
    public List<DiversityArea> staged(Player p){Session s=session(p);if(s.target!=null||s.firstTarget!=null)throw new IllegalArgumentException("Finish the four-click Diversity capture first, or /il cleardiversity to discard staging.");return List.copyOf(s.staged.values());}
    public void load(Player p,String roomId){
        Room room=plugin.getRoomManager().getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));Session s=session(p);
        if(!room.size().equals(s.context.size()))throw new IllegalArgumentException("Select the authoring room with the saved template's exact dimensions.");
        s.staged.clear();for(DiversityArea a:room.diversityAreas())s.staged.put(a.id(),a);s.target=null;s.firstTarget=null;s.firstSource=null;
    }
    public ItemStack wand(){ItemStack item=new ItemStack(Material.PRISMARINE_SHARD);var meta=item.getItemMeta();meta.setDisplayName("§dRoom Diversity Wand");meta.setLore(List.of("§71 + 2: target corners inside your room","§73 + 4: equally sized variation source","§7Source can be elsewhere in the same world","§7/il diversitymode <area> <variant> [weight]"));meta.getPersistentDataContainer().set(wandKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;}
    public boolean handle(Player p,Action action,Block clicked){
        ItemStack held=p.getInventory().getItemInMainHand();if(clicked==null||held==null||!held.hasItemMeta()||!held.getItemMeta().getPersistentDataContainer().has(wandKey,PersistentDataType.BYTE))return false;
        if(action!=Action.LEFT_CLICK_BLOCK&&action!=Action.RIGHT_CLICK_BLOCK)return false;
        if(!p.hasPermission("infinitylibrary.admin")){p.sendMessage("§cMissing permission infinitylibrary.admin");return true;}
        try{
            Session s=session(p);if(!clicked.getWorld().getUID().equals(s.context.world()))throw new IllegalArgumentException("Select source and target in the same world.");
            Vector3i absolute=Vector3i.from(clicked.getLocation());
            if(s.target==null){
                Vector3i point=absolute.subtract(s.context.min());if(point.x()<0||point.y()<0||point.z()<0||point.x()>=s.context.size().x()||point.y()>=s.context.size().y()||point.z()>=s.context.size().z())throw new IllegalArgumentException("Target corners must be inside the selected whole room.");
                if(s.firstTarget==null){s.firstTarget=point;p.sendMessage("§dDiversity target corner 1 set. Select target corner 2.");return true;}
                Vector3i min=min(s.firstTarget,point),size=size(s.firstTarget,point);s.firstTarget=null;
                DiversityArea old=s.staged.get(s.areaId);if(old!=null&&(!old.min().equals(min)||!old.size().equals(size)))throw new IllegalArgumentException("That area ID already targets different bounds. Choose another /il diversitymode area ID.");
                DiversityArea target=old==null?new DiversityArea(s.areaId,min,size,1,List.of()):old;
                List<DiversityArea> candidates=new ArrayList<>(s.staged.values());candidates.removeIf(a -> a.id().equals(target.id()));candidates.add(target);
                Room.validateDiversityAreas(s.context.size(),plugin.getSelectionManager().stagedConnections(p),plugin.getPropManager().staged(p),plugin.getSelectionManager().stagedVariations(p),candidates);
                s.target=target;p.sendMessage("§dTarget "+target.id()+" set: X,Y,Z "+size+". Now click two corners around its variation, anywhere in this world.");return true;
            }
            if(s.firstSource==null){s.firstSource=absolute;p.sendMessage("§dVariation source corner 1 set. Select source corner 2 (required size "+s.target.size()+").");return true;}
            Vector3i sourceMin=min(s.firstSource,absolute),sourceSize=size(s.firstSource,absolute);s.firstSource=null;
            s.target.requireSourceSize(sourceSize);
            List<RoomBlock> blocks=new ArrayList<>();
            for(int y=0;y<sourceSize.y();y++)for(int z=0;z<sourceSize.z();z++)for(int x=0;x<sourceSize.x();x++){
                Block b=p.getWorld().getBlockAt(sourceMin.x()+x,sourceMin.y()+y,sourceMin.z()+z);if(!b.getType().isAir())blocks.add(new RoomBlock(new Vector3i(x,y,z),b.getBlockData().getAsString()));
            }
            DiversityArea updated=s.target.withVariant(new DiversityVariant(s.variantId,s.weight,blocks));
            List<DiversityArea> all=new ArrayList<>(s.staged.values());all.removeIf(a -> a.id().equals(updated.id()));all.add(updated);
            Room.validateDiversityAreas(s.context.size(),plugin.getSelectionManager().stagedConnections(p),plugin.getPropManager().staged(p),plugin.getSelectionManager().stagedVariations(p),all);
            s.staged.put(updated.id(),updated);s.target=null;
            p.sendMessage("§aCaptured Diversity "+updated.id()+" / "+s.variantId+" ("+sourceSize+"). Save the room, or /il applydiversity <roomId>. Add another with /il diversityvariant <area> <variant>.");
        }catch(IllegalArgumentException e){p.sendMessage("§c"+e.getMessage());}return true;
    }
    public void apply(Player p,String roomId){
        Room room=plugin.getRoomManager().getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));Session s=session(p);
        if(!room.size().equals(s.context.size()))throw new IllegalArgumentException("Whole-room selection must match saved room dimensions.");
        List<DiversityArea> captured=staged(p);if(captured.isEmpty())throw new IllegalArgumentException("No completed Diversity captures.");
        Map<String,DiversityArea> merged=new LinkedHashMap<>();for(DiversityArea a:room.diversityAreas())merged.put(a.id(),a);
        for(DiversityArea a:captured){DiversityArea old=merged.get(a.id());if(old!=null){if(!old.min().equals(a.min())||!old.size().equals(a.size()))throw new IllegalArgumentException("Saved area has different bounds; remove it explicitly before retargeting.");for(DiversityVariant v:a.variants())old=old.withVariant(v);merged.put(a.id(),old);}else merged.put(a.id(),a);}
        plugin.getRoomManager().updateDiversity(roomId,List.copyOf(merged.values()));clear(p);
    }
    public void originalWeight(String roomId,String areaId,double weight){
        Room room=plugin.getRoomManager().getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));List<DiversityArea> updated=new ArrayList<>();boolean found=false;
        for(DiversityArea a:room.diversityAreas())if(a.id().equals(areaId)){updated.add(new DiversityArea(a.id(),a.min(),a.size(),weight,a.variants()));found=true;}else updated.add(a);
        if(!found)throw new IllegalArgumentException("Diversity area not found");plugin.getRoomManager().updateDiversity(roomId,updated);
    }
    public void remove(String roomId,String areaId,String variantId){
        Room room=plugin.getRoomManager().getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));List<DiversityArea> updated=new ArrayList<>();boolean found=false;
        for(DiversityArea a:room.diversityAreas())if(a.id().equals(areaId)){found=true;if(variantId!=null){if(a.variants().stream().noneMatch(v -> v.id().equals(variantId)))throw new IllegalArgumentException("Variant not found");updated.add(new DiversityArea(a.id(),a.min(),a.size(),a.originalWeight(),a.variants().stream().filter(v -> !v.id().equals(variantId)).toList()));}}else updated.add(a);
        if(!found)throw new IllegalArgumentException("Area not found");plugin.getRoomManager().updateDiversity(roomId,updated);
    }
    private Vector3i min(Vector3i a,Vector3i b){return new Vector3i(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()));}
    private Vector3i size(Vector3i a,Vector3i b){return new Vector3i(Math.abs(a.x()-b.x())+1,Math.abs(a.y()-b.y())+1,Math.abs(a.z()-b.z())+1);}
}
