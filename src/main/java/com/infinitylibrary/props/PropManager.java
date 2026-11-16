package com.infinitylibrary.props;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import com.infinitylibrary.model.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PropManager {
    private final InfinityLibraryPlugin plugin;
    private final Map<String,Prop> props=new LinkedHashMap<>();
    private final Map<UUID,List<PropArea>> staged=new HashMap<>();
    private final Map<UUID,Context> contexts=new HashMap<>();
    private final Map<UUID,Vector3i> first=new HashMap<>();
    private final Map<UUID,Settings> settings=new HashMap<>();
    private final Map<UUID,Integer> counters=new HashMap<>();
    private final NamespacedKey wandKey;
    private record Context(UUID world,Vector3i min,Vector3i max) {}
    private record Settings(String category,double gap,String prefix,boolean rotate,boolean stack) {}
    public PropManager(InfinityLibraryPlugin plugin) {this.plugin=plugin;wandKey=new NamespacedKey(plugin,"prop_area_wand");}
    public Collection<Prop> all() { return List.copyOf(props.values()); }
    public Prop get(String id) {Prop p=props.get(id);if(p==null)throw new IllegalArgumentException("Prop not found: "+id);return p;}
    public void load() {
        props.clear(); builtins();
        var root=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("props.yml").toFile()).getConfigurationSection("props");
        if(root==null&&!Files.exists(plugin.getDataFolder().toPath().resolve("props.yml")))save();
        if(root!=null)for(String id:root.getKeys(false))try {if(props.size()>=256&&!props.containsKey(id))break;props.put(id,Prop.read(id,root.getConfigurationSection(id)));}catch(RuntimeException e){plugin.getLogger().warning("Invalid prop "+id+": "+e.getMessage());}
    }
    private void save() {
        YamlConfiguration y=new YamlConfiguration();var root=y.createSection("props");for(Prop p:props.values())p.write(root.createSection(p.id()));
        Path target=plugin.getDataFolder().toPath().resolve("props.yml"),temp=target.resolveSibling("props.yml.tmp");
        try{Files.writeString(temp,y.saveToString());try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException e){throw new IllegalStateException("Unable to save prop templates",e);}
    }
    public void put(Prop prop) {
        if(props.size()>=256&&!props.containsKey(prop.id()))throw new IllegalArgumentException("The prop catalog supports at most 256 entries.");
        Prop old=props.put(prop.id(),prop);try{save();}catch(RuntimeException e){if(old==null)props.remove(prop.id());else props.put(old.id(),old);throw e;}
    }
    public void weight(String id,double weight) {Prop p=get(id);put(new Prop(p.id(),p.category(),p.size(),p.blocks(),weight));}
    public void delete(String id) {
        Prop old=get(id);if(id.startsWith("builtin_"))throw new IllegalArgumentException("Disable a built-in prop with /il propweight <id> 0.");
        props.remove(id);try{save();}catch(RuntimeException e){props.put(id,old);throw e;}
    }
    private Context context(Player p) {var b=plugin.getRoomManager().selectionBounds(p);return new Context(p.getWorld().getUID(),b.min(),b.max());}
    private void checkContext(Player p) {
        Context now=context(p),old=contexts.get(p.getUniqueId());
        if(!now.equals(old)){clear(p);contexts.put(p.getUniqueId(),now);}
    }
    public List<PropArea> staged(Player p) {checkContext(p);return List.copyOf(staged.getOrDefault(p.getUniqueId(),List.of()));}
    public void clear(Player p) {staged.remove(p.getUniqueId());first.remove(p.getUniqueId());contexts.remove(p.getUniqueId());}
    public void endSession(Player p) {clear(p);settings.remove(p.getUniqueId());counters.remove(p.getUniqueId());}
    public void mode(Player p,String category,double gap,String prefix,boolean rotate) {mode(p,category,gap,prefix,rotate,true);}
    public void mode(Player p,String category,double gap,String prefix,boolean rotate,boolean stack) {
        new PropArea("check",new Vector3i(1,1,1),new Vector3i(1,1,1),category,gap,rotate);
        if(!prefix.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Prefix needs 1–48 lowercase letters, digits, underscores or hyphens.");
        settings.put(p.getUniqueId(),new Settings(category,gap,prefix,rotate,stack));first.remove(p.getUniqueId());
    }
    public ItemStack wand() {
        ItemStack item=new ItemStack(Material.AMETHYST_SHARD);var meta=item.getItemMeta();meta.setDisplayName("§dRoom Prop Area Wand");meta.setLore(List.of("§7Select the whole room first.","§7Click two interior corners per prop area.","§7/il propmode <category> [gap%] [prefix] [rotate|fixed] [floor|stack]"));meta.getPersistentDataContainer().set(wandKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;
    }
    public boolean handle(Player p,Action action,Block block) {
        ItemStack held=p.getInventory().getItemInMainHand();
        if(block==null||held==null||!held.hasItemMeta()||!held.getItemMeta().getPersistentDataContainer().has(wandKey,PersistentDataType.BYTE))return false;
        if(action!=Action.LEFT_CLICK_BLOCK&&action!=Action.RIGHT_CLICK_BLOCK)return false;
        if(!p.hasPermission("infinitylibrary.admin")){p.sendMessage("§cMissing permission infinitylibrary.admin");return true;}
        try {
            checkContext(p);Context room=contexts.get(p.getUniqueId());Vector3i point=Vector3i.from(block.getLocation()).subtract(room.min());
            Vector3i roomSize=room.max().subtract(room.min()).add(new Vector3i(1,1,1));
            if(!block.getWorld().getUID().equals(room.world())||point.x()<1||point.y()<1||point.z()<1||point.x()>=roomSize.x()-1||point.y()>=roomSize.y()-1||point.z()>=roomSize.z()-1)throw new IllegalArgumentException("Select prop corners inside the room, clear of its shell.");
            Vector3i a=first.get(p.getUniqueId());
            if(a==null){first.put(p.getUniqueId(),point);p.sendMessage("§dProp area corner 1: "+point);return true;}
            first.remove(p.getUniqueId());Vector3i min=new Vector3i(Math.min(a.x(),point.x()),Math.min(a.y(),point.y()),Math.min(a.z(),point.z()));
            Vector3i size=new Vector3i(Math.abs(a.x()-point.x())+1,Math.abs(a.y()-point.y())+1,Math.abs(a.z()-point.z())+1);
            Settings s=settings.getOrDefault(p.getUniqueId(),new Settings("library",15,"prop",true,false));
            PropArea area=new PropArea(s.prefix()+"_"+counters.merge(p.getUniqueId(),1,Integer::sum),min,size,s.category(),s.gap(),s.rotate(),s.stack());
            List<PropArea> all=new ArrayList<>(staged.getOrDefault(p.getUniqueId(),List.of()));all.add(area);
            Room.validatePropAreas(roomSize,plugin.getSelectionManager().stagedConnections(p),all);
            staged.put(p.getUniqueId(),all);
            p.sendMessage("§dStaged "+area.id()+" | X×Y×Z "+size+" | "+s.category()+" | gaps "+s.gap()+"%. Save the room or /il applypropareas <roomId>.");
            boolean available=props.values().stream().anyMatch(prop -> prop.weight()>0&&(area.category().equals("*")||area.category().equals(prop.category()))&&prop.size().y()<=size.y()&&((prop.size().x()<=size.x()&&prop.size().z()<=size.z())||(area.rotate()&&prop.size().z()<=size.x()&&prop.size().x()<=size.z())));
            if(!available)p.sendMessage("§eNo enabled prop currently fits this area/category. It will stay empty until you save a matching prop.");
        }catch(IllegalArgumentException e){p.sendMessage("§c"+e.getMessage());}
        return true;
    }
    public Prop capture(Player p,String id,String category,double weight) {
        if(id.startsWith("builtin_"))throw new IllegalArgumentException("Use a new ID for a custom prop.");
        Context c=context(p);Vector3i size=c.max().subtract(c.min()).add(new Vector3i(1,1,1));
        if(PropArea.volume(size)>8192)throw new IllegalArgumentException("Prop captures are limited to 8,192 blocks.");
        List<RoomBlock> blocks=new ArrayList<>();
        for(int y=0;y<size.y();y++)for(int z=0;z<size.z();z++)for(int x=0;x<size.x();x++){
            Block b=p.getWorld().getBlockAt(c.min().x()+x,c.min().y()+y,c.min().z()+z);
            if(!b.getType().isAir())blocks.add(new RoomBlock(new Vector3i(x,y,z),b.getBlockData().getAsString()));
        }
        Prop prop=new Prop(id,category,size,blocks,weight);put(prop);return prop;
    }
    public PropComposition compose(Room room,UUID instance) {
        if(room.propAreas().isEmpty())return PropComposition.EMPTY;
        List<RoomBlock> blocks=new ArrayList<>();long seed=instance.getMostSignificantBits()^instance.getLeastSignificantBits();
        PropPacker packer=new PropPacker();
        for(PropArea area:room.propAreas()) {
            PropPacker.Result result=packer.pack(area,all(),seed^area.id().hashCode(),plugin.getConfig().getInt("props.packing-attempts",8),plugin.getConfig().getInt("props.cell-check-budget",200000));
            for(PropPacker.Placement placement:result.placements())for(RoomBlock rb:placement.prop().blocks()){
                Vector3i local=area.min().add(placement.offset()).add(placement.rotation().rotate(rb.position(),placement.prop().size().x(),placement.prop().size().z()));
                BlockData data=Bukkit.createBlockData(rb.blockData());data.rotate(switch(placement.rotation()){
                    case NONE -> org.bukkit.block.structure.StructureRotation.NONE;
                    case CLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
                    case CLOCKWISE_180 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
                    case CLOCKWISE_270 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
                });
                blocks.add(new RoomBlock(local,data.getAsString()));
            }
        }
        return new PropComposition(room.propAreas(),blocks);
    }
    public void browser(Player p) {
        LibraryMenu menu=new LibraryMenu(p,LibraryMenu.Screen.PROPS,54,"Library • Room Props");Inventory inv=menu.getInventory();var ui=plugin.getBookWritingManager();
        for(int i=0;i<54;i++)ui.icon(inv,i,Material.BLACK_STAINED_GLASS_PANE," ");
        ui.icon(inv,4,Material.ARMOR_STAND,"§dReusable room props","§7Select prop bounds with /il pos1 and /il pos2","§7/il saveprop <id> <category> [weight]","§7Dimensions are X × Y × Z");
        int slot=9;for(Prop prop:all()){if(slot>=45)break;ui.icon(inv,slot++,Material.BRICKS,"§b"+prop.id(),"§7Category: "+prop.category(),"§7Size: "+prop.size(),"§7Relative weight: "+prop.weight());}
        ui.icon(inv,45,Material.AMETHYST_SHARD,"§aGet prop area wand","§7Mark areas inside the selected room");
        ui.icon(inv,49,Material.CHEST,"§eMain menu");
        ui.icon(inv,53,Material.PAPER,"§eAll props","§7/il listprops lists the complete catalog","§7/il baseroom <id> selects a standard room");p.openInventory(inv);
    }
    public void menuAction(Player p,int slot) {
        if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Missing permission infinitylibrary.admin");
        if(slot==45){plugin.getBookStorageManager().returnItem(p,wand());p.sendMessage("§dSelect the room, then click two interior corners with this wand.");}
        if(slot==49)plugin.getGuiManager().openMainMenu(p);
    }
    private void builtins() {
        List<RoomBlock> shelf=new ArrayList<>();for(int x=0;x<2;x++)for(int y=0;y<3;y++)shelf.add(new RoomBlock(new Vector3i(x,y,0),y==1?"minecraft:chiseled_bookshelf[facing=south]":"minecraft:bookshelf"));
        props.put("builtin_shelf",new Prop("builtin_shelf","library",new Vector3i(2,3,1),shelf,3));
        props.put("builtin_bench",new Prop("builtin_bench","library",new Vector3i(2,1,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:oak_stairs[facing=south]"),new RoomBlock(new Vector3i(1,0,0),"minecraft:oak_stairs[facing=south]")),1));
        props.put("builtin_table",new Prop("builtin_table","library",new Vector3i(2,2,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:oak_fence"),new RoomBlock(new Vector3i(1,0,0),"minecraft:oak_fence"),new RoomBlock(new Vector3i(0,1,0),"minecraft:oak_pressure_plate"),new RoomBlock(new Vector3i(1,1,0),"minecraft:oak_pressure_plate")),1));
        props.put("builtin_plant",new Prop("builtin_plant","library",new Vector3i(1,1,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:potted_fern")),1));
        props.put("builtin_lectern",new Prop("builtin_lectern","library",new Vector3i(1,1,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:lectern[facing=south]")),1));
    }
}
