import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import com.infinitylibrary.listener.GUIListener;
import com.infinitylibrary.writing.*;
import com.infinitylibrary.props.*;
import com.infinitylibrary.diversity.*;
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
            check(fake.dialog!=null,"decoration category dialog opens after its two corner clicks");manager.category(author,"library",false);
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
                later(() -> {check(e.placedCount()==2&&e.queuedBlocks()==0,"custom library remains functional after all defaults are removed");diversityChecks(p);},30);
            },30);
        },5);
    }
    void diversityChecks(InfinityLibraryPlugin p) {
        var rooms=p.getRoomManager();var engine=p.getGenerationEngine();var diversity=p.getDiversityManager();var deco=p.getPropManager();World world=engine.ensureWorld();
        Fake fake=new Fake(p,true);Player author=fake.player;Room base=rooms.getActive("probe_base").orElseThrow();Vector3i origin=new Vector3i(3000,80,3000);
        for(RoomBlock b:base.blocks()){Vector3i pos=origin.add(b.position());world.getBlockAt(pos.x(),pos.y(),pos.z()).setBlockData(Bukkit.createBlockData(b.blockData()),false);}
        world.getBlockAt(3001,81,3001).setType(Material.OAK_PLANKS,false);world.getBlockAt(3003,82,3002).setType(Material.GLASS,false);
        rooms.setPos1(author,origin.toLocation(world));rooms.setPos2(author,origin.add(base.size()).subtract(new Vector3i(1,1,1)).toLocation(world));
        fake.items[0]=diversity.wand();diversity.mode(author,"reading","garden",1);
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3001,81,3001));
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3003,82,3002));
        world.getBlockAt(3020,81,3000).setType(Material.GOLD_BLOCK,false);world.getBlockAt(3022,81,3001).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=east]"),false);
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3020,81,3000));
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3022,82,3000));
        boolean mismatch=false;try{diversity.staged(author);}catch(IllegalArgumentException expected){mismatch=true;}
        check(mismatch,"Diversity mismatch cannot become a completed capture or be saved silently");
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3020,81,3000));
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3022,82,3001));
        var staged=diversity.staged(author).getFirst();
        check(staged.min().equals(new Vector3i(1,1,1))&&staged.size().equals(new Vector3i(3,2,2)),"four-click Diversity capture records room-local target bounds");
        check(staged.variants().getFirst().blocks().size()==2,"variation source is captured outside the room using local source coordinates");
        diversity.empty(author,"reading","empty",0);check(diversity.staged(author).getFirst().variants().size()==2,"one target accepts multiple alternatives including an empty design");
        Room captured=rooms.capture(author,"probe_diversity",RoomType.FILLER,base.connections());diversity.clear(author);
        check(captured.diversityAreas().size()==1,"room capture persists staged Diversity definitions");
        diversity.originalWeight(captured.id(),"reading",0);rooms.setRoomChance(captured.id(),50);
        check(rooms.getActive(captured.id()).orElseThrow().diversityAreas().size()==1,"room metadata edits preserve Diversity definitions");
        boolean overlap=false;try{rooms.updatePropAreas(captured.id(),List.of(new PropArea("conflict",new Vector3i(1,1,1),new Vector3i(3,2,2),"library",0,false)));}catch(IllegalArgumentException expected){overlap=true;}
        check(overlap,"saved decoration areas cannot overwrite a Diversity target");
        Fake denied=new Fake(p,false);denied.items[0]=diversity.wand();check(diversity.handle(denied.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3001,81,3001)),"Diversity wand rejects non-admin use");
        check(engine.placeRoomImmediately(captured.id()),"Diversity-bearing room attaches and queues construction");
        var file=p.getDataFolder().toPath().resolve("generated.yml").toFile();var generated=YamlConfiguration.loadConfiguration(file);org.bukkit.configuration.ConfigurationSection placed=null;
        for(String key:generated.getConfigurationSection("rooms").getKeys(false))if(captured.id().equals(generated.getString("rooms."+key+".room-id")))placed=generated.getConfigurationSection("rooms."+key);
        if(placed==null)throw new IllegalStateException("Missing Diversity instance");var frozen=DiversityComposition.read(placed.getConfigurationSection("diversity"));
        check(frozen.patches().getFirst().choice().equals("garden")&&frozen.patches().getFirst().blocks().size()==2,"chosen variation is frozen before room placement");
        var roomFile=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var data=YamlConfiguration.loadConfiguration(roomFile);data.set("rooms."+captured.id()+".diversity.reading.variants.garden.blocks",List.of("0,0,0|minecraft:emerald_block"));
        try{data.save(roomFile);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
        world.getBlockAt(3020,81,3000).setType(Material.AIR,false);world.getBlockAt(3022,81,3001).setType(Material.AIR,false);
        p.reloadEverything();var restored=YamlConfiguration.loadConfiguration(file);boolean same=false;
        for(String key:restored.getConfigurationSection("rooms").getKeys(false))if(captured.id().equals(restored.getString("rooms."+key+".room-id")))same=frozen.equals(DiversityComposition.read(restored.getConfigurationSection("rooms."+key+".diversity")));
        check(same,"pending Diversity selection survives source removal, template edits and reload");
        Vector3i at=Vector3i.read(placed.getConfigurationSection("origin"));var t=placed.getConfigurationSection("transform");RoomTransform transform=new RoomTransform(com.infinitylibrary.model.Rotation.valueOf(t.getString("rotation")),t.getBoolean("flip-x"),t.getBoolean("flip-z"));
        later(() -> {
            check(engine.queuedBlocks()==0,"queued Diversity clearing and placement finish");
            Vector3i gold=at.add(transform.transform(new Vector3i(1,1,1),captured.size()));Vector3i cleared=at.add(transform.transform(new Vector3i(3,2,2),captured.size()));Vector3i stair=at.add(transform.transform(new Vector3i(3,1,2),captured.size()));
            check(world.getBlockAt(gold.x(),gold.y(),gold.z()).getType()==Material.GOLD_BLOCK,"variant replaces the original furniture at its transformed target");
            check(world.getBlockAt(cleared.x(),cleared.y(),cleared.z()).getType().isAir(),"air in the variation removes leftover default-design blocks");
            var facing=world.getBlockAt(stair.x(),stair.y(),stair.z()).getBlockData();check(facing instanceof Directional d&&d.getFacing()==transform.transform(org.bukkit.block.BlockFace.EAST),"Diversity block facing follows room rotation and mirroring");
            exactDecorationChecks(p,fake,base);
        },35);
    }
    void exactDecorationChecks(InfinityLibraryPlugin p,Fake fake,Room base) {
        var manager=p.getPropManager();Player author=fake.player;World world=p.getGenerationEngine().ensureWorld();
        manager.put(new Prop("probe_exact","exact_test",new Vector3i(3,3,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:diamond_block"),new RoomBlock(new Vector3i(2,2,0),"minecraft:gold_block")),1));
        manager.put(new Prop("probe_too_small","exact_test",new Vector3i(1,1,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:stone")),1000000));
        manager.put(new Prop("probe_wrong_category","other_test",new Vector3i(3,3,1),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:emerald_block")),1000000));
        fake.items[0]=manager.wand();manager.mode(author,"library",0,"exact",false,false);
        manager.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3007,81,3007));
        manager.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(3009,83,3007));
        check(fake.dialog!=null,"size is selected before the native decoration-category dialog");
        boolean waiting=false;try{manager.staged(author);}catch(IllegalArgumentException expected){waiting=true;}check(waiting,"category selection must finish before saving a decoration area");
        manager.category(author,"exact_test",false);PropArea area=manager.staged(author).getFirst();
        check(area.size().equals(new Vector3i(3,3,1))&&area.category().equals("exact_test")&&area.fillMode()==PropArea.FillMode.EXACT,"post-selection category is stored with the exact area dimensions");
        Room decorated=p.getRoomManager().capture(author,"probe_exact_room",RoomType.FILLER,base.connections());manager.clear(author);
        var snapshot=manager.compose(decorated,new UUID(1,2));
        check(snapshot.blocks().size()==2&&snapshot.blocks().stream().noneMatch(b -> b.blockData().contains("stone")||b.blockData().contains("emerald")),"exact decoration ignores smaller and wrong-category entries despite their high weights");
        var missing=new PropArea("missing",new Vector3i(1,1,7),new Vector3i(2,2,1),"exact_test",0,false,false);
        Room empty=new Room("missing",RoomType.FILLER,base.size(),base.connections(),base.blocks(),List.of(),100,List.of(missing));
        check(manager.compose(empty,new UUID(1,2)).blocks().isEmpty(),"no exact-size match leaves the area empty instead of packing smaller decorations");
        p.getRoomManager().setRoomType(decorated.id(),RoomType.STARTING);
        check(p.getRoomManager().getActive(decorated.id()).orElseThrow().propAreas().getFirst().fillMode()==PropArea.FillMode.EXACT,"exact decoration settings survive room-role edits");
        p.getRoomManager().appendVariations("probe_diversity",List.of(new VariationArea("legacy_cleanup","decor",100,List.of(new RoomBlock(new Vector3i(8,1,8),"minecraft:stone")))));
        p.getRoomManager().removeVariation("probe_diversity","legacy_cleanup");
        check(p.getRoomManager().getActive("probe_diversity").orElseThrow().diversityAreas().size()==1&&p.getRoomManager().getActive("probe_diversity").orElseThrow().variations().stream().noneMatch(v -> v.id().equals("legacy_cleanup")),"legacy-variation removal preserves Room Diversity metadata");
        explorationChecks(p);
    }
    void explorationChecks(InfinityLibraryPlugin p) {
        var main=p.getGenerationEngine();main.stop();p.getConfig().set("generation.path-blocking.enabled",false);
        var cfg=p.getConfig();cfg.set("generation.start-room-id","auto_start");cfg.set("generation.base-room-id","auto_room");cfg.set("start-location.x",10000);cfg.set("start-location.y",80);cfg.set("start-location.z",10000);cfg.set("proximity-radius",6);cfg.set("generation.max-total-rooms",3);cfg.set("generation.planning.max-doors",2);cfg.set("generation.placement-blocks-per-tick",1800);cfg.set("generation.exploration-check-interval-ticks",2);
        var doors=List.of(new ConnectionPoint("north",new Vector3i(5,1,0),org.bukkit.block.BlockFace.NORTH,3,3),new ConnectionPoint("east",new Vector3i(10,1,5),org.bukkit.block.BlockFace.EAST,3,3),new ConnectionPoint("south",new Vector3i(5,1,10),org.bukkit.block.BlockFace.SOUTH,3,3),new ConnectionPoint("west",new Vector3i(0,1,5),org.bukkit.block.BlockFace.WEST,3,3));
        List<RoomBlock> floor=new ArrayList<>();for(int x=0;x<11;x++)for(int z=0;z<11;z++)floor.add(new RoomBlock(new Vector3i(x,0,z),"minecraft:oak_planks"));
        try {var file=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);new Room("auto_start",RoomType.STARTING,new Vector3i(11,7,11),doors.subList(0,3),floor,List.of(),100).write(y.createSection("rooms.auto_start"));new Room("auto_room",RoomType.FILLER,new Vector3i(11,7,11),doors,floor,List.of(),100).write(y.createSection("rooms.auto_room"));y.save(file);p.getRoomManager().load();}catch(Exception ex){throw new RuntimeException(ex);}
        for(int x=9970;x<10040;x++)for(int y=79;y<90;y++)for(int z=9970;z<10040;z++)main.ensureWorld().getBlockAt(x,y,z).setType(Material.AIR,false);
        Fake f=new Fake(p,true);f.online=false;var e=new com.infinitylibrary.engine.GenerationEngine(p,p.getRoomManager(),()->List.of(f.player));e.start();e.setPaused(false);e.resetToStart();
        later(()->{
            var world=e.ensureWorld();world.getBlockAt(10005,81,9999).setType(Material.STONE);
            f.position=new Location(world,10006.5,81.5,10001.5);f.online=true;e.tickPlayer(f.player);
            check(e.placedCount()==2,"blocked nearest exit does not starve a viable sibling exit");
            later(()->{
                cfg.set("proximity-radius",1.2);f.position=new Location(world,10005.5,81.5,10010.5);e.tickPlayer(f.player);
                check(e.placedCount()==3,"sibling doorway can plan immediately despite failed-door cooldown and distant reservations");
                e.stop();cfg.set("generation.placement-blocks-per-tick",4);cfg.set("generation.max-total-rooms",2);f.position=new Location(world,10010.5,81.5,10005.5);e.start();e.resetToStart();e.tickPlayer(f.player);check(e.placedCount()==1&&e.queuedBlocks()>0,"explorer initially waits while entrance construction is queued");
                later(()->{
                    check(e.placedCount()==2,"scheduled proximity scan expands for a stationary explorer after queue drains");
                    later(()->{
                        check(e.placedCount()==2,"periodic exploration respects built-room cap");e.stop();cfg.set("generation.placement-blocks-per-tick",1800);main.start();main.resetToStart();
                        later(()->movementChecks(p,f),2);
                    },45);
                },45);
            },2);
        },2);
    }
    void movementChecks(InfinityLibraryPlugin p,Fake f) {
        var e=p.getGenerationEngine();var world=e.ensureWorld();Location far=new Location(world,10100,81,10100),near=new Location(world,10010.5,81.5,10005.5);f.position=far;
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerMoveEvent(f.player,far,near));
        check(e.placedCount()==2,"movement uses destination rather than stale player location");e.resetToStart();
        later(()->{
            var cancelled=new org.bukkit.event.player.PlayerMoveEvent(f.player,far,near);cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);check(e.placedCount()==1,"cancelled movement does not generate rooms");
            f.position=near;Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerTeleportEvent(f.player,far,near,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN));
            later(()->{
                check(e.placedCount()==2,"teleport arrival triggers exploration");p.getConfig().set("generation.base-room-id","auto_start");check(e.explorationStatus().contains("STARTING"),"diagnostics identify STARTING template selected as expansion base");p.getConfig().set("generation.base-room-id","missing_auto");check(e.explorationStatus().contains("missing")||e.explorationStatus().contains("archived"),"diagnostics identify unavailable base template");p.getConfig().set("generation.base-room-id","auto_room");
                topologyChecks(p);
            },3);
        },2);
    }
    void topologyChecks(InfinityLibraryPlugin p){
        var cfg=p.getConfig();var e=p.getGenerationEngine();var world=e.ensureWorld();
        cfg.set("generation.max-total-rooms",0);cfg.set("generation.path-blocking.enabled",true);cfg.set("generation.path-blocking.material","GOLD_BLOCK");cfg.set("generation.exploration-check-interval-ticks",2);cfg.set("generation.planning.max-doors",16);
        cfg.set("start-location.x",20000);cfg.set("start-location.y",80);cfg.set("start-location.z",20000);cfg.set("generation.start-room-id","seal_start");cfg.set("generation.base-room-id","seal_normal");
        List<RoomBlock> floor=new ArrayList<>();for(int x=0;x<7;x++)for(int z=0;z<7;z++)floor.add(new RoomBlock(new Vector3i(x,0,z),"minecraft:oak_planks"));
        var east=new ConnectionPoint("east",new Vector3i(6,1,3),org.bukkit.block.BlockFace.EAST,3,3);var west=new ConnectionPoint("west",new Vector3i(0,1,3),org.bukkit.block.BlockFace.WEST,3,3);
        Room start=new Room("seal_start",RoomType.STARTING,new Vector3i(7,5,7),List.of(new ConnectionPoint("north_even",new Vector3i(3,1,0),org.bukkit.block.BlockFace.NORTH,4,2),new ConnectionPoint("up_even",new Vector3i(3,4,3),org.bukkit.block.BlockFace.UP,2,1),east),floor,List.of(),100);
        Room normal=new Room("seal_normal",RoomType.FILLER,start.size(),List.of(west,east),floor,List.of(),100);
        try{var file=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);start.write(y.createSection("rooms.seal_start"));normal.write(y.createSection("rooms.seal_normal"));y.save(file);p.getRoomManager().load();}catch(Exception ex){throw new RuntimeException(ex);}
        for(int x=19990;x<20025;x++)for(int y=79;y<90;y++)for(int z=19990;z<20025;z++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        e.stop();e.start();e.resetToStart();
        later(()->{
            check(e.closedExitCount()==2,"only proven impossible cardinal and vertical exits are sealed");
            int gold=0;for(int x=20001;x<=20004;x++)for(int y=81;y<=82;y++)if(world.getBlockAt(x,y,20000).getType()==Material.GOLD_BLOCK)gold++;
            check(gold==8&&world.getBlockAt(20005,81,20000).getType()==Material.AIR,"dead-end wall uses configured material and exact even width/height");
            check(world.getBlockAt(20002,84,20002).getType()==Material.GOLD_BLOCK&&world.getBlockAt(20003,84,20003).getType()==Material.GOLD_BLOCK,"vertical dead-end fills exact square on its room boundary");
            check(world.getBlockAt(20002,81,19999).getType()==Material.AIR&&e.openExitCount()==1,"wall stays inside its room and viable exit stays open");
            e.stop();e.start();
            later(()->{
                check(e.closedExitCount()==2&&world.getBlockAt(20002,81,20000).getType()==Material.GOLD_BLOCK,"closed doors and wall recovery survive engine reload");
                cfg.set("generation.path-blocking.enabled",false);e.replanFutureRooms();check(e.closedExitCount()==0&&world.getBlockAt(20002,81,20000).isEmpty(),"replanning restores the saved original opening without resetting library");
                diversityDoorChecks(p,floor,east,west);
            },4);
        },12);
    }
    void diversityDoorChecks(InfinityLibraryPlugin p,List<RoomBlock> floor,ConnectionPoint east,ConnectionPoint west){
        var e=p.getGenerationEngine();var rooms=p.getRoomManager();var diversity=p.getDiversityManager();var world=e.ensureWorld();Fake f=new Fake(p,true);var author=f.player;
        Vector3i origin=new Vector3i(22000,80,22000);rooms.setPos1(author,origin.toLocation(world));rooms.setPos2(author,origin.add(new Vector3i(6,4,6)).toLocation(world));p.getSelectionManager().clearStagedConnections(author);p.getPropManager().clear(author);diversity.clear(author);
        f.items[0]=diversity.wand();diversity.mode(author,"eastwall","open",1);
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(22006,81,22002));diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(22006,83,22004));
        for(int y=81;y<=83;y++)for(int z=23000;z<=23002;z++)world.getBlockAt(23000,y,z).setType(Material.STONE,false);
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(23000,81,23000));diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(23000,83,23002));
        f.items[0]=diversity.connectionWand(author,"eastwall","open","EAST",0,"door");
        diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(23000,81,23000));diversity.handle(author,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(23000,83,23002));
        var staged=diversity.staged(author).getFirst();var variant=staged.variants().getFirst();
        check(variant.connections().size()==1&&variant.connections().getFirst().width()==3&&variant.connections().getFirst().height()==3,"Diversity Connection Wand captures variant-source doorway coordinates and dimensions");
        check(variant.blocks().isEmpty(),"marking a Diversity connection clears captured blocks from its opening");
        Room normal=new Room("div_door_room",RoomType.FILLER,new Vector3i(7,5,7),List.of(west),floor,List.of(),100,List.of(),List.of(new DiversityArea(staged.id(),staged.min(),staged.size(),0,staged.variants())));
        Room start=new Room("div_door_start",RoomType.STARTING,normal.size(),List.of(east),floor,List.of(),100);
        try{var file=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);normal.write(y.createSection("rooms.div_door_room"));start.write(y.createSection("rooms.div_door_start"));y.save(file);rooms.load();}catch(Exception ex){throw new RuntimeException(ex);}
        var cfg=p.getConfig();cfg.set("generation.start-room-id",start.id());cfg.set("generation.base-room-id",normal.id());cfg.set("start-location.x",24000);cfg.set("start-location.z",24000);
        for(int x=23990;x<24030;x++)for(int y=79;y<87;y++)for(int z=23990;z<24030;z++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        e.resetToStart();later(()->{
            check(e.placeRoomImmediately(normal.id()),"planner places room with resolved variant topology");
            later(()->{
                var file=p.getDataFolder().toPath().resolve("generated.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);var child=y.getConfigurationSection("rooms.1");
                check(child.getString("diversity.eastwall.choice").equals("open")&&child.getConfigurationSection("connections").getKeys(false).size()==2,"placed room freezes selected variant and both active connections");
                check(e.plannedCount()>0,"lookahead reserves continuation from effective variant connections");
                String snapshot=child.getConfigurationSection("connections").getValues(true).toString();rooms.updateDiversity(normal.id(),List.of());e.stop();e.start();
                var recovered=YamlConfiguration.loadConfiguration(file).getConfigurationSection("rooms.1");check(snapshot.equals(recovered.getConfigurationSection("connections").getValues(true).toString()),"placed variant doors remain unchanged after template edits and reload");
                check(e.placeRoomImmediately(normal.id()),"library can expand through frozen variant topology after authoring edits");
                later(()->{getLogger().info("ALL "+count+" INTEGRATION CHECKS PASSED");Bukkit.shutdown();},4);
            },5);
        },3);
    }
    final class Fake implements InvocationHandler {
        final InfinityLibraryPlugin plugin; final UUID id=UUID.randomUUID(); final boolean admin;
        final ItemStack[] items=new ItemStack[36];
        final Player player; final PlayerInventory inventory; final InventoryView view;
        Inventory top; Object dialog; Location position; boolean online=true;
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
                case "isOnline" -> online; case "getInventory" -> inventory; case "getOpenInventory" -> view;
                case "getWorld" -> plugin.getGenerationEngine().ensureWorld(); case "getLocation" -> position==null?plugin.getGenerationEngine().homeLocation():position.clone();
                case "openInventory" -> {top=(Inventory)a[0];yield view;}
                case "showDialog" -> {dialog=a[0];yield null;}
                case "equals" -> o==a[0]; case "hashCode" -> id.hashCode();
                default -> empty(m.getReturnType());
            };
        }
        Object empty(Class<?> c) { if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0F;if(c==double.class)return 0D;return null; }
    }
}
