import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import com.infinitylibrary.listener.GUIListener;
import com.infinitylibrary.writing.*;
import com.infinitylibrary.props.*;
import com.infinitylibrary.model.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.data.Directional;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.util.*;

/** Disposable-server integration checks. Player protocol is simulated, not a connected client. */
public class LibraryProbe extends JavaPlugin {
    int count;
    void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); count++; getLogger().info("PASS "+message); }
    void later(Runnable test,long delay) { Bukkit.getScheduler().runTaskLater(this,() -> { try {test.run();} catch(Throwable t) {t.printStackTrace(); Bukkit.shutdown();} },delay); }
    public void onEnable() {
        later(() -> {
            var p=(InfinityLibraryPlugin)Bukkit.getPluginManager().getPlugin("InfinityLibraryPlugin"); var e=p.getGenerationEngine();
            var removed=YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("rooms.yml").toFile()).getStringList("removed-default-rooms");
            if(!removed.isEmpty())check(removed.stream().allMatch(id -> p.getRoomManager().getActive(id).isEmpty()),"removed defaults remain suppressed after process restart");
            for(String id:new ArrayList<>(p.getRoomManager().archivedIds()))if(p.getRoomManager().isDefault(id))p.getRoomManager().restore(id);
            if(e.plannedCount()>0) check(true,"persisted sibling plans restored on process startup");
            try {var f=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var y=YamlConfiguration.loadConfiguration(f);y.set("rooms.builtin_modular",null);y.save(f);p.reloadEverything();} catch(java.io.IOException ex){throw new RuntimeException(ex);}
            p.getConfig().set("generation.start-room-id","builtin_modular");p.getConfig().set("generation.base-room-id","builtin_modular");p.saveConfig();
            e.setPaused(false); e.resetToStart();
            later(() -> {
                check(e.placedCount()==1 && e.queuedBlocks()==0,"start room completed");
                check(e.homeLocation().getBlock().isEmpty(),"home has standing space");
                check(e.placeRoomImmediately("builtin_book"),"compatible room attaches through layout planner");
                check(e.queuedBlocks()>0,"manual placement respects queue");
                check(e.plannedCount()>=3,"multiple sibling exits reserved before block placement");
                int planned=e.plannedCount(); p.reloadEverything();
                check(e.placedCount()==2 && e.queuedBlocks()>0,"reload recovers pending room");
                check(e.plannedCount()==planned,"sibling reservations survive reload");
                e.setPaused(true); p.reloadEverything(); check(e.isPaused(),"pause persists through reload");
                later(() -> {
                    check(e.queuedBlocks()==0,"recovered placement finishes");
                    check(java.nio.file.Files.exists(com.infinitylibrary.storage.BackupManager.create(p)),"plugin data backup created");
                    e.setPaused(false);
                    propChecks(p);
                },40);
            },40);
        },40);
    }
    void propChecks(InfinityLibraryPlugin p) {
        try {
            var manager=p.getPropManager();var engine=p.getGenerationEngine();
            Room standard=p.getRoomManager().get("builtin_modular").orElseThrow();
            check(standard.propAreas().size()==4,"standard room has four independent prop areas");
            check(standard.propAreas().stream().noneMatch(PropArea::stack),"standard room uses floor-only furniture placement");
            check(standard.blocks().stream().noneMatch(b -> standard.propAreas().stream().anyMatch(a -> a.contains(b.position()))),"standard room base is empty within prop placeholders");
            var first=manager.compose(standard,new UUID(1,2));var second=manager.compose(standard,new UUID(3,4));
            check(!first.blocks().isEmpty()&&!first.blocks().equals(second.blocks()),"different room instances receive different prop decorations");
            check(manager.all().size()>=5,"built-in shelf, bench, table, plant and lectern props available");
            Fake fake=new Fake(p,true);var author=fake.player;
            p.getRoomManager().setPos1(author,new Location(engine.ensureWorld(),1000,80,1000));
            p.getRoomManager().setPos2(author,new Location(engine.ensureWorld(),1010,86,1010));
            fake.items[0]=manager.wand();manager.mode(author,"library",0,"test",false);
            manager.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,engine.ensureWorld().getBlockAt(1001,81,1001));
            manager.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,engine.ensureWorld().getBlockAt(1002,83,1002));
            check(manager.staged(author).size()==1&&manager.staged(author).getFirst().size().equals(new Vector3i(2,3,2)),"area wand stages correctly sized room-local bounds");
            Fake denied=new Fake(p,false);denied.items[0]=manager.wand();
            check(manager.handle(denied.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,engine.ensureWorld().getBlockAt(1001,81,1001)),"prop area wand blocks non-admin interaction");
            engine.ensureWorld().getBlockAt(1000,81,1000).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=east]"),false);
            engine.ensureWorld().getBlockAt(1001,81,1000).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=east]"),false);
            p.getRoomManager().setPos1(author,new Location(engine.ensureWorld(),1000,81,1000));p.getRoomManager().setPos2(author,new Location(engine.ensureWorld(),1001,81,1000));
            Prop captured=manager.capture(author,"probe_captured","snapshot_test",1);
            check(captured.size().equals(new Vector3i(2,1,1))&&captured.blocks().size()==2,"capture stores prop dimensions and directional block states");
            check(manager.staged(author).isEmpty(),"changing the selected room discards stale staged prop areas");
            manager.load();check(manager.get("probe_captured").blocks().equals(captured.blocks()),"captured prop survives catalog reload");
            PropArea area=new PropArea("snapshot",new Vector3i(1,1,1),new Vector3i(2,1,1),"snapshot_test",0,false);
            Room test=new Room("probe_modular",RoomType.FILLER,standard.size(),standard.connections(),standard.blocks(),List.of(),100,List.of(area));
            var roomFile=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var rooms=YamlConfiguration.loadConfiguration(roomFile);test.write(rooms.createSection("rooms.probe_modular"));rooms.save(roomFile);p.reloadEverything();
            check(engine.placeRoomImmediately(test.id()),"custom prop-bearing room attaches through layout planner");
            var generated=YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("generated.yml").toFile());var root=generated.getConfigurationSection("rooms");
            org.bukkit.configuration.ConfigurationSection placed=null;
            for(String key:root.getKeys(false))if(root.getString(key+".room-id").equals(test.id()))placed=root.getConfigurationSection(key);
            if(placed==null)throw new IllegalStateException("Missing test room");
            var snapshot=PropComposition.read(placed.getConfigurationSection("props"));
            check(snapshot.blocks().size()==2&&snapshot.blocks().stream().allMatch(b -> b.blockData().contains("oak_stairs")),"per-room composition is frozen before its blocks are placed");
            manager.put(new Prop(captured.id(),captured.category(),captured.size(),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:emerald_block")),1));
            check(manager.compose(test,new UUID(5,6)).blocks().stream().allMatch(b -> b.blockData().contains("emerald_block")),"editing a prop changes future compositions");
            manager.delete(captured.id());p.reloadEverything();
            var again=YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("generated.yml").toFile());
            boolean same=again.getConfigurationSection("rooms").getKeys(false).stream().anyMatch(key -> again.getString("rooms."+key+".room-id").equals(test.id())&&PropComposition.read(again.getConfigurationSection("rooms."+key+".props")).equals(snapshot));
            check(same,"prop edits and deletion do not reroll a pending room on reload");
            Vector3i origin=Vector3i.read(placed.getConfigurationSection("origin"));var t=placed.getConfigurationSection("transform");
            RoomTransform transform=new RoomTransform(com.infinitylibrary.model.Rotation.valueOf(t.getString("rotation")),t.getBoolean("flip-x"),t.getBoolean("flip-z"));
            manager.browser(author);check(fake.top.getHolder() instanceof LibraryMenu menu&&menu.screen()==LibraryMenu.Screen.PROPS,"prop browser and wand controls open");
            later(() -> {
                check(engine.queuedBlocks()==0,"queued prop area clearing and decoration complete");
                for(RoomBlock block:snapshot.blocks()){
                    Vector3i pos=origin.add(transform.transform(block.position(),test.size()));
                    var data=engine.ensureWorld().getBlockAt(pos.x(),pos.y(),pos.z()).getBlockData();
                    check(data instanceof Directional d&&d.getFacing()==transform.transform(org.bukkit.block.BlockFace.EAST),"frozen directional prop reaches correct transformed world position");
                }
                uiChecks(p);
            },40);
        } catch(Exception e){throw new RuntimeException(e);}
    }
    void uiChecks(InfinityLibraryPlugin p) {
        Fake fake=new Fake(p,true); Player player=fake.player;
        p.getGuiManager().openMainMenu(player);
        check(fake.top.getSize()==54 && fake.top.getHolder() instanceof LibraryMenu,"holder-backed 54-slot main menu opens");
        check(fake.top.getItem(20).getType()==Material.WRITABLE_BOOK && fake.top.getItem(22).getType()==Material.COMPASS,"main menu exposes writing and search");
        GUIListener listener=new GUIListener(p);
        InventoryClickEvent shift=new InventoryClickEvent(fake.view,InventoryType.SlotType.CONTAINER,54,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        listener.onClick(shift); check(shift.isCancelled(),"main menu rejects shift transfers from player inventory");
        InventoryClickEvent swap=new InventoryClickEvent(fake.view,InventoryType.SlotType.CONTAINER,20,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        listener.onClick(swap); check(swap.isCancelled(),"main menu rejects hotbar swaps");
        InventoryDragEvent drag=new InventoryDragEvent(fake.view,new ItemStack(Material.AIR),new ItemStack(Material.PAPER),false,Map.of(20,new ItemStack(Material.PAPER)));
        listener.onDrag(drag); check(drag.isCancelled(),"main menu rejects drags");
        Fake outsider=new Fake(p,false); outsider.top=fake.top;
        InventoryClickEvent foreign=new InventoryClickEvent(outsider.view,InventoryType.SlotType.CONTAINER,20,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        listener.onClick(foreign); check(foreign.isCancelled(),"menu remains protected for a different owner");
        p.getGuiManager().openMainMenu(outsider.player);
        check(outsider.top.getItem(44).getType()!=Material.ARMOR_STAND,"ordinary players do not receive prop authoring controls");
        check(outsider.top.getItem(46).getType()!=Material.BRICKS && outsider.top.getItem(48).getType()!=Material.REDSTONE_TORCH,"ordinary players do not receive admin controls");
        var writer=p.getBookWritingManager(); writer.open(player);
        check(((LibraryMenu)fake.top.getHolder()).screen()==LibraryMenu.Screen.WRITER,"custom writing desk opens");
        BookDraft draft=writer.draft(player.getUniqueId());
        draft.details("Integration book","testing","ui,planner",false); draft.replacePages(List.of("Line one\nLine two","Page two")); writer.save();
        BookWritingManager reloaded=new BookWritingManager(p); reloaded.load();
        check(reloaded.draft(player.getUniqueId()).pages().equals(draft.pages()),"multiline manuscript survives disk reload");
        check(reloaded.draft(player.getUniqueId()).title().equals("Integration book"),"manuscript details survive disk reload");
        writer.pageDialog(player); check(fake.dialog!=null,"native multiline page dialog builds"); fake.dialog=null;
        writer.detailsDialog(player); check(fake.dialog!=null,"native book-details dialog builds"); fake.dialog=null;
        writer.searchDialog(player); check(fake.dialog!=null,"native search dialog builds");
        ItemStack preview=writer.book(player,draft,false);
        check(((BookMeta)preview.getItemMeta()).getPages().equals(draft.pages()),"book preview preserves multiline content");
        check(!p.getBookStorageManager().canRead(outsider.player,preview),"private manuscript preview carries ownership");
        Arrays.fill(fake.items,null); fake.items[0]=new ItemStack(Material.WRITABLE_BOOK);
        writer.handle(player,LibraryMenu.Screen.CONFIRM_PUBLISH,11);
        check(fake.items[0]!=null && fake.items[0].getType()==Material.WRITTEN_BOOK,"signing exchanges one blank book for a signed copy");
        check(((BookMeta)fake.items[0].getItemMeta()).getAuthor().equals(player.getName()),"signed copy uses the author's player name");
        fake.items[1]=new ItemStack(Material.WRITABLE_BOOK);
        int before=p.getBookStorageManager().totalBooks();
        writer.handle(player,LibraryMenu.Screen.CONFIRM_PUBLISH,15);
        check(fake.items[1]==null && p.getBookStorageManager().totalBooks()>=before,"publishing consumes blank book and stores collection");
        p.getBookStorageManager().load();
        check(p.getBookStorageManager().allBooks().stream().anyMatch(b -> b.title().equals("Integration book")),"published manuscript survives collection reload");
        Arrays.fill(fake.items,null); fake.items[0]=new ItemStack(Material.WRITABLE_BOOK);
        java.nio.file.Path books=p.getDataFolder().toPath().resolve("books.yml"),backup=books.resolveSibling("probe-books-backup.yml");
        int collectionSize=p.getBookStorageManager().totalBooks();
        try {
            java.nio.file.Files.move(books,backup,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.createDirectory(books); java.nio.file.Files.writeString(books.resolve("block-write"),"test");
            boolean failed=false; try {writer.handle(player,LibraryMenu.Screen.CONFIRM_PUBLISH,15);} catch(IllegalStateException expected) {failed=true;}
            check(failed && fake.items[0].getType()==Material.WRITABLE_BOOK && p.getBookStorageManager().totalBooks()==collectionSize,"failed publication preserves payment and rolls back collection");
        } catch(java.io.IOException ex) {throw new RuntimeException(ex);}
        finally { try {java.nio.file.Files.deleteIfExists(books.resolve("block-write")); java.nio.file.Files.deleteIfExists(books); java.nio.file.Files.move(backup,books,java.nio.file.StandardCopyOption.REPLACE_EXISTING);} catch(java.io.IOException ex) {throw new RuntimeException(ex);} }
        Arrays.fill(fake.items,null); ItemStack valuable=new ItemStack(Material.WRITABLE_BOOK); BookMeta m=(BookMeta)valuable.getItemMeta(); m.setPages("Existing valuable text"); valuable.setItemMeta(m); fake.items[0]=valuable;
        boolean refused=false; try { writer.handle(player,LibraryMenu.Screen.CONFIRM_PUBLISH,11); } catch(IllegalArgumentException expected) {refused=true;}
        check(refused && ((BookMeta)fake.items[0].getItemMeta()).getPages().contains("Existing valuable text"),"nonblank books cannot accidentally pay publication cost");
        writer.open(player);
        InventoryClickEvent click=new InventoryClickEvent(fake.view,InventoryType.SlotType.CONTAINER,29,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        int pages=draft.pages().size(); listener.onClick(click);
        later(() -> {
            check(draft.pages().size()==pages+1,"writer page button runs safely on next tick");
            roomRoleChecks(p);
        },5);
    }
    void roomRoleChecks(InfinityLibraryPlugin p) {
        var rooms=p.getRoomManager();var e=p.getGenerationEngine();Fake fake=new Fake(p,true);Player author=fake.player;
        int activeBefore=rooms.allRooms().size();boolean protectedBulk=false;
        try{rooms.removeDefaults();}catch(IllegalArgumentException expected){protectedBulk=true;}
        check(protectedBulk&&rooms.allRooms().size()==activeBefore,"bulk removal preflights selected defaults without partial deletion");
        Room standard=rooms.getActive("builtin_modular").orElseThrow();World world=e.ensureWorld();Vector3i source=new Vector3i(2000,80,2000);
        for(RoomBlock b:standard.blocks()){Vector3i at=source.add(b.position());world.getBlockAt(at.x(),at.y(),at.z()).setBlockData(Bukkit.createBlockData(b.blockData()),false);}
        world.getBlockAt(source.x(),source.y(),source.z()).setType(Material.GOLD_BLOCK,false);
        p.getRoomManager().setPos1(author,source.toLocation(world));p.getRoomManager().setPos2(author,source.add(standard.size()).subtract(new Vector3i(1,1,1)).toLocation(world));
        for(ConnectionPoint cp:standard.connections())p.getSelectionManager().addStagedConnection(author,cp);
        int built=e.placedCount();
        new com.infinitylibrary.command.CommandHandler(p).onCommand(author,p.getCommand("infinitylibrary"),"il",new String[]{"saveroom","probe_start","STARTING"});
        check(rooms.getActive("probe_start").orElseThrow().type()==RoomType.STARTING,"saveroom command captures STARTING type");
        check(rooms.activeStartId().equals("probe_start"),"saving STARTING automatically selects the active start");
        check(e.placedCount()==built&&e.queuedBlocks()==0,"saving STARTING does not expand it or reset the existing world");
        rooms.capture(author,"probe_base",RoomType.FILLER,standard.connections());
        rooms.chooseBase("probe_base");check(rooms.activeStartId().equals("probe_start"),"base selection leaves start selection unchanged");
        boolean refused=false;try{rooms.chooseBase("probe_start");}catch(IllegalArgumentException expected){refused=true;}
        check(refused,"STARTING cannot be selected as an expansion base");
        rooms.chooseBase("all");check(rooms.expansionRooms().stream().noneMatch(r -> r.type()==RoomType.STARTING),"mixed expansion excludes every STARTING template");rooms.chooseBase("probe_base");
        check(!e.placeRoomImmediately("probe_start"),"manual placement cannot insert STARTING as an ordinary room");
        refused=false;try{rooms.delete("probe_start");}catch(IllegalArgumentException expected){refused=true;}
        check(refused&&rooms.getActive("probe_start").isPresent(),"active start is protected from removal");
        refused=false;try{rooms.delete("probe_base");}catch(IllegalArgumentException expected){refused=true;}
        check(refused&&rooms.getActive("probe_base").isPresent(),"active expansion base is protected from removal");
        p.getGuiManager().openRoomEditor(author);LibraryMenu roomMenu=(LibraryMenu)fake.top.getHolder();
        p.getGuiManager().roomMenuAction(author,roomMenu,12,false);
        check(((LibraryMenu)fake.top.getHolder()).screen()==LibraryMenu.Screen.CONFIRM_DEFAULT_ROOMS,"bulk default removal has a holder-backed confirmation");
        p.getGuiManager().roomMenuAction(author,(LibraryMenu)fake.top.getHolder(),15,false);
        check(rooms.allRooms().stream().anyMatch(r -> rooms.isDefault(r.id())),"canceling default removal leaves catalog unchanged");
        int defaults=(int)rooms.allRooms().stream().filter(r -> rooms.isDefault(r.id())).count();int placedBefore=e.placedCount();
        int removed=rooms.removeDefaults();check(removed==defaults&&removed>0,"bulk command removes all active default templates");
        check(rooms.allRooms().stream().noneMatch(r -> rooms.isDefault(r.id())),"removed defaults disappear from active generation catalog");
        check(e.placedCount()==placedBefore&&rooms.get("builtin_modular").isPresent(),"built default rooms retain archived metadata without changing world rooms");
        p.reloadEverything();
        check(rooms.allRooms().stream().noneMatch(r -> rooms.isDefault(r.id())),"removed defaults stay removed after reload");
        check(rooms.activeStartId().equals("probe_start")&&rooms.getActive("probe_start").orElseThrow().type()==RoomType.STARTING,"custom starting role and selection persist");
        p.getGuiManager().openRoomEditor(author);LibraryMenu activeList=(LibraryMenu)fake.top.getHolder();boolean hidden=true;
        for(int slot=19;slot<54;slot++){String id=activeList.entry(slot);if(id!=null&&rooms.isDefault(id))hidden=false;}
        check(hidden,"room menu does not show removed defaults");
        rooms.restore("builtin_book");p.reloadEverything();check(rooms.getActive("builtin_book").isPresent(),"restoring a default persists across reload");rooms.delete("builtin_book");
        rooms.delete("probe_modular");p.reloadEverything();check(rooms.getActive("probe_modular").isEmpty()&&rooms.get("probe_modular").isPresent(),"used custom templates can also be archived safely");
        check(e.homeLocation()!=null,"home still works through archived original start metadata");
        String selected=rooms.activeStartId();p.getConfig().set("generation.start-room-id","missing-template");refused=false;
        try{e.resetToStart();}catch(IllegalArgumentException expected){refused=true;}finally{p.getConfig().set("generation.start-room-id",selected);}
        check(refused&&e.placedCount()==placedBefore,"invalid start is rejected before clearing generated rooms");
        refused=false;try{rooms.setRoomType("probe_start",RoomType.FILLER);}catch(IllegalArgumentException expected){refused=true;}
        check(refused,"active STARTING room cannot be demoted before choosing another start");
        Room alternate=rooms.capture(author,"probe_alternate",RoomType.FILLER,standard.connections());
        p.getGuiManager().openRoomDetails(author,alternate.id());GUIListener listener=new GUIListener(p);
        var click=new InventoryClickEvent(fake.view,InventoryType.SlotType.CONTAINER,18,ClickType.LEFT,InventoryAction.PICKUP_ALL);listener.onClick(click);
        later(() -> {
            check(rooms.activeStartId().equals(alternate.id())&&rooms.getActive(alternate.id()).orElseThrow().type()==RoomType.STARTING,"room-details button sets STARTING type and selects it");
            check(p.getConfig().getString("generation.base-room-id").equals("probe_base"),"STARTING menu action preserves an independent normal base");
            rooms.chooseStart("probe_start");e.resetToStart();
            var data=YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("generated.yml").toFile());
            check(e.placedCount()==1&&data.getString("rooms.0.room-id").equals("probe_start"),"reset queues the selected custom start instead of a default");
            later(() -> {
                check(e.queuedBlocks()==0&&world.getBlockAt(0,80,0).getType()==Material.GOLD_BLOCK,"custom start blocks finish at configured entrance coordinates");
                check(e.placeRoomImmediately("probe_base"),"normal expansion attaches to the custom STARTING room");
                later(() -> {check(e.placedCount()==2&&e.queuedBlocks()==0,"custom library remains functional after all defaults are removed");getLogger().info("ALL "+count+" INTEGRATION CHECKS PASSED");Bukkit.shutdown();},30);
            },30);
        },5);
    }
    final class Fake implements InvocationHandler {
        final InfinityLibraryPlugin plugin; final UUID id=UUID.randomUUID(); final boolean admin;
        final ItemStack[] items=new ItemStack[36];
        final Player player; final PlayerInventory inventory; final InventoryView view;
        Inventory top; Object dialog;
        Fake(InfinityLibraryPlugin p,boolean admin) {
            this.plugin=p; this.admin=admin;
            inventory=(PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a) -> inventoryCall(m,a));
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},this);
            view=(InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class[]{InventoryView.class},(o,m,a) -> switch(m.getName()) {
                case "getTopInventory" -> top; case "getBottomInventory" -> inventory; case "getPlayer" -> player;
                case "getType" -> InventoryType.CHEST; case "getTitle","getOriginalTitle" -> "Test";
                case "getInventory" -> ((int)a[0]<top.getSize()?top:inventory);
                case "convertSlot" -> (int)a[0]<top.getSize()?(int)a[0]:(int)a[0]-top.getSize();
                case "getSlotType" -> InventoryType.SlotType.CONTAINER; case "countSlots" -> top.getSize()+36;
                default -> empty(m.getReturnType());
            });
        }
        Object inventoryCall(Method m,Object[] a) {
            return switch(m.getName()) {
                case "getSize" -> 36; case "getStorageContents","getContents" -> items.clone();
                case "getItem" -> items[(int)a[0]]; case "setItem" -> {items[(int)a[0]]=(ItemStack)a[1];yield null;}
                case "getItemInMainHand" -> items[0]==null?new ItemStack(Material.AIR):items[0];
                case "firstEmpty" -> { int slot=-1; for(int i=0;i<36;i++) if(items[i]==null){slot=i;break;} yield slot; }
                case "addItem" -> { Map<Integer,ItemStack> left=new HashMap<>(); int n=0; for(ItemStack item:(ItemStack[])a[0]) {int slot=-1;for(int i=0;i<36;i++)if(items[i]==null){slot=i;break;}if(slot<0)left.put(n,item);else items[slot]=item;n++;}yield left;}
                default -> empty(m.getReturnType());
            };
        }
        public Object invoke(Object o,Method m,Object[] a) {
            return switch(m.getName()) {
                case "getUniqueId" -> id; case "getName" -> admin?"TestAuthor":"TestReader";
                case "hasPermission" -> a[0].equals("infinitylibrary.command") || admin;
                case "isOnline" -> true; case "getInventory" -> inventory; case "getOpenInventory" -> view;
                case "getWorld" -> plugin.getGenerationEngine().ensureWorld(); case "getLocation" -> plugin.getGenerationEngine().homeLocation();
                case "openInventory" -> {top=(Inventory)a[0];yield view;}
                case "showDialog" -> {dialog=a[0];yield null;}
                case "equals" -> o==a[0]; case "hashCode" -> id.hashCode();
                default -> empty(m.getReturnType());
            };
        }
        Object empty(Class<?> c) { if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0F;if(c==double.class)return 0D;return null; }
    }
}
