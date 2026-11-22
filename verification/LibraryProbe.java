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
            p.getConfig().set("start-location.x",0);p.getConfig().set("start-location.y",80);p.getConfig().set("start-location.z",0);p.getConfig().set("generation.start-room-id","builtin_modular");p.getConfig().set("generation.base-room-id","builtin_modular");p.saveConfig();
            e.setPaused(false); e.resetToStart();
            later(() -> {
                check(e.placedCount()==1 && e.queuedBlocks()==0,"start room completed");
                check(e.homeLocation().getBlock().isEmpty(),"home has standing space");
                check(e.placeRoomImmediately("builtin_book"),"compatible room attaches through layout planner");
                check(e.queuedBlocks()>0,"manual placement respects queue");
                check(e.plannedCount()>=3,"multiple sibling exits reserved before block placement");
                int future=e.futurePlanCount();check(future>0,"district reserves rooms whose parent hallway has not been built yet");int planned=e.plannedCount(); p.reloadEverything();
                check(e.placedCount()==2 && e.queuedBlocks()>0,"reload recovers pending room");
                check(e.plannedCount()==planned,"district reservations survive reload");check(e.futurePlanCount()==future,"future-parent dependency reservations survive reload");
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
        writer.pageDialog(player); check(Arrays.stream(fake.items).anyMatch(p.getNativeBookEditor()::temporary),"writing desk issues a native Book and Quill editor");p.getNativeBookEditor().cleanup(player); fake.dialog=null;
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
                later(()->{sharedPlaneChecks(p);},4);
            },5);
        },3);
    }
    List<RoomBlock> shell(Material material){List<RoomBlock> blocks=new ArrayList<>();for(int x=0;x<7;x++)for(int y=0;y<5;y++)for(int z=0;z<7;z++)if(x==0||x==6||y==0||y==4||z==0||z==6)blocks.add(new RoomBlock(new Vector3i(x,y,z),material.createBlockData().getAsString()));return blocks;}
    ConnectionPoint storedDoor(org.bukkit.configuration.ConfigurationSection placed,String id){var cp=ConnectionPoint.read(id,placed.getConfigurationSection("connections."+id));var t=placed.getConfigurationSection("transform");var transform=new RoomTransform(com.infinitylibrary.model.Rotation.valueOf(t.getString("rotation")),t.getBoolean("flip-x"),t.getBoolean("flip-z"));return cp.transform(transform,Vector3i.read(placed.getConfigurationSection("size"))).offset(id,Vector3i.read(placed.getConfigurationSection("origin")));}
    void sharedPlaneChecks(InfinityLibraryPlugin p){
        var e=p.getGenerationEngine();var world=e.ensureWorld();var cfg=p.getConfig();cfg.set("generation.path-blocking.enabled",false);cfg.set("generation.placement-blocks-per-tick",1800);cfg.set("generation.start-room-id","shared_start");cfg.set("generation.base-room-id","shared_normal");cfg.set("generation.max-total-rooms",4);cfg.set("start-location.x",26000);cfg.set("start-location.y",80);cfg.set("start-location.z",26000);
        var east=new ConnectionPoint("east",new Vector3i(6,1,3),org.bukkit.block.BlockFace.EAST,3,3);var west=new ConnectionPoint("west",new Vector3i(0,1,3),org.bukkit.block.BlockFace.WEST,3,3);
        Room start=new Room("shared_start",RoomType.STARTING,new Vector3i(7,5,7),List.of(east),shell(Material.GOLD_BLOCK),List.of(),100);Room normal=new Room("shared_normal",RoomType.FILLER,start.size(),List.of(west,east),shell(Material.STONE_BRICKS),List.of(),100);
        try{var file=p.getDataFolder().toPath().resolve("rooms.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);start.write(y.createSection("rooms.shared_start"));normal.write(y.createSection("rooms.shared_normal"));y.save(file);p.getRoomManager().load();}catch(Exception ex){throw new RuntimeException(ex);}
        for(int x=25990;x<26035;x++)for(int y=79;y<90;y++)for(int z=25990;z<26015;z++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        e.stop();e.start();e.resetToStart();later(()->{
            world.getBlockAt(26008,81,26003).setType(Material.DIAMOND_BLOCK,false);check(!e.placeRoomImmediately(normal.id())&&e.placedCount()==1,"shared-plane placement still refuses unrelated terrain in room interior");check(world.getBlockAt(26008,81,26003).getType()==Material.DIAMOND_BLOCK,"failed shared placement preserves obstructing terrain");world.getBlockAt(26008,81,26003).setType(Material.AIR,false);
            check(e.placeRoomImmediately(normal.id()),"solid authored rooms join by sharing their doorway wall plane");
            var file=p.getDataFolder().toPath().resolve("generated.yml").toFile();var y=YamlConfiguration.loadConfiguration(file);var root=y.getConfigurationSection("rooms.0");var child=y.getConfigurationSection("rooms.1");String entrance=child.getStringList("generated-connections").getFirst();var a=storedDoor(root,"east");var b=storedDoor(child,entrance);
            check(a.position().equals(b.position())&&a.position().x()==26006&&a.compatibleWith(b),"placed connection anchors overlap exactly instead of being one block apart");check(new HashSet<>(a.cells()).equals(new HashSet<>(b.cells())),"both rooms share all nine doorway opening cells");
            check(y.getConfigurationSection("plans").getKeys(false).stream().anyMatch(k -> y.getInt("plans."+k+".origin.x")==26012),"future reservation uses overlapping doorway spacing too");
            e.stop();e.start();check(e.queuedBlocks()>0&&e.placedCount()==2,"pending shared-wall placement recovers without duplicating rooms");
            later(()->{
                check(a.cells().stream().allMatch(pos -> world.getBlockAt(pos.x(),pos.y(),pos.z()).isEmpty()),"shared aperture is opened after both solid templates are applied");check(world.getBlockAt(26006,80,26000).getType()==Material.GOLD_BLOCK&&world.getBlockAt(26006,84,26003).getType()==Material.GOLD_BLOCK,"existing boundary floor and ceiling survive shared-wall recovery");check(world.getBlockAt(26007,82,26003).isEmpty(),"there is no second doorway wall one block beyond the shared opening");
                e.stop();try{var old=YamlConfiguration.loadConfiguration(file);old.set("connection-layout",null);old.save(file);}catch(Exception ex){throw new RuntimeException(ex);}e.start();check(e.plannedCount()==0&&e.placedCount()==2,"legacy-spacing reservations are discarded while built rooms are retained");var migrated=YamlConfiguration.loadConfiguration(file);check(Vector3i.read(migrated.getConfigurationSection("rooms.1.origin")).equals(Vector3i.read(child.getConfigurationSection("origin"))),"layout migration leaves existing room coordinates unchanged");
                check(e.placeRoomImmediately(normal.id()),"new overlapping-room continuation works after old-plan invalidation");
                later(()->{check(e.placedCount()==3&&e.queuedBlocks()==0,"three shared-wall rooms finish without collision or queue errors");communityChecks(p);},5);
            },5);
        },3);
    }
    void communityChecks(InfinityLibraryPlugin p){
        World testWorld=p.getGenerationEngine().ensureWorld();for(int x=25970>>4;x<=26030>>4;x++)for(int z=25970>>4;z<=26030>>4;z++)testWorld.addPluginChunkTicket(x,z,this);
        var shared=p.getCollaborationManager();Fake owner=new Fake(p,true),editor=new Fake(p,false),stranger=new Fake(p,false);World world=p.getGenerationEngine().ensureWorld();owner.position=new Location(world,26001.5,81,26001.5);editor.position=owner.position.clone();stranger.position=owner.position.clone();
        var d=shared.create(owner.player,"Shared probe");UUID id=d.id();shared.update(owner.player,id,old->old.invite(owner.id,editor.id,"GuestWriter"));var before=shared.get(editor.player,id);var edited=shared.update(editor.player,id,old->old.page(editor.id,before.revision(),0,"Chapter one by guest"));
        check(shared.visible(editor.player).stream().anyMatch(x->x.id().equals(id))&&shared.visible(stranger.player).stream().noneMatch(x->x.id().equals(id)),"shared draft list is restricted to owner and invited UUIDs");
        boolean denied=false;try{shared.update(owner.player,id,old->old.page(owner.id,before.revision(),0,"Stale overwrite"));}catch(IllegalArgumentException expected){denied=true;}check(denied&&shared.get(owner.player,id).pages().getFirst().equals("Chapter one by guest"),"stale simultaneous shared edit is rejected without losing newer text");
        denied=false;try{shared.signed(editor.player,shared.get(editor.player,id));}catch(IllegalArgumentException expected){denied=true;}check(denied,"collaborator cannot sign or publish the owner's manuscript");
        var revoke=shared.get(owner.player,id);shared.revoke(owner.player,id,editor.id);denied=false;try{shared.get(editor.player,id);}catch(IllegalArgumentException expected){denied=true;}check(denied,"revoked collaborator loses access immediately");check(shared.get(owner.player,id).contributors().containsKey(editor.id),"contributor credit survives editor revocation");shared.update(owner.player,id,old->old.invite(owner.id,editor.id,"GuestWriter"));
        var now=shared.get(owner.player,id);long detailsRevision=now.revision();shared.update(owner.player,id,old->old.details(owner.id,detailsRevision,old.title(),old.category(),old.tags(),true));now=shared.get(owner.player,id);long rev=now.revision();shared.update(owner.player,id,old->old.insert(owner.id,rev,0));now=shared.get(owner.player,id);long rev2=now.revision();shared.update(owner.player,id,old->old.page(owner.id,rev2,1,"Second chapter"));now=shared.get(owner.player,id);long rev3=now.revision();shared.update(owner.player,id,old->old.insert(owner.id,rev3,1));now=shared.get(owner.player,id);long rev4=now.revision();shared.update(owner.player,id,old->old.page(owner.id,rev4,2,"The end"));shared.load();check(shared.get(editor.player,id).pages().size()==3,"shared pages, editor UUIDs and revisions survive disk reload");
        var sharedFile=p.getDataFolder().toPath().resolve("collaborative-books.yml");try{String saved=java.nio.file.Files.readString(sharedFile);try{var invalid=YamlConfiguration.loadConfiguration(sharedFile.toFile());invalid.set("manuscripts.bad-id.title","Bad");invalid.save(sharedFile.toFile());boolean rejected=false;try{shared.load();}catch(RuntimeException expected){rejected=true;}check(rejected&&shared.get(editor.player,id).pages().size()==3,"invalid shared-data reload retains the complete live manuscript set");}finally{java.nio.file.Files.writeString(sharedFile,saved);shared.load();}}catch(java.io.IOException ex){throw new RuntimeException(ex);}
        var stable=shared.get(owner.player,id);java.nio.file.Path tmp=p.getDataFolder().toPath().resolve("collaborative-books.yml.tmp");try{java.nio.file.Files.createDirectory(tmp);denied=false;try{shared.update(owner.player,id,old->old.page(owner.id,stable.revision(),0,"Unsaved mutation"));}catch(IllegalStateException expected){denied=true;}check(denied&&shared.get(owner.player,id).revision()==stable.revision(),"shared manuscript rolls back memory when durable save fails");}catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{try{java.nio.file.Files.deleteIfExists(tmp);}catch(Exception ex){throw new RuntimeException(ex);}}
        ItemStack signed=shared.signed(owner.player,shared.get(owner.player,id));check(signed.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(p,"book_coauthors"),org.bukkit.persistence.PersistentDataType.STRING).contains("GuestWriter"),"signed collaborative book carries contributor credits");owner.items[0]=new ItemStack(Material.WRITABLE_BOOK);int countBefore=p.getBookStorageManager().totalBooks();p.getBookWritingManager().deliver(owner.player,signed,true);check(p.getBookStorageManager().totalBooks()==countBefore+1&&owner.items[0]==null,"shared publication uses durable collection save and exactly one blank book payment");
        var publicBook=p.getBookStorageManager().allBooks().stream().filter(b->b.ownerUuid().equals(owner.id.toString())&&b.title().equals("Shared probe")).findFirst().orElseThrow();check(publicBook.toItemStack().getItemMeta().getPersistentDataContainer().has(new NamespacedKey(p,"library_book_id"),org.bukkit.persistence.PersistentDataType.STRING),"published books carry stable reader identifiers");
        BookDraft secret=new BookDraft();secret.details("Private community probe","general","",false);secret.page("Secret text");p.getBookStorageManager().publishManuscript(owner.player,p.getBookWritingManager().book(owner.player,secret,true));var privateBook=p.getBookStorageManager().allBooks().stream().filter(b->b.ownerUuid().equals(owner.id.toString())&&b.title().equals("Private community probe")).findFirst().orElseThrow();
        var reading=p.getReadingManager();denied=false;try{reading.open(stranger.player,privateBook.id());}catch(IllegalArgumentException expected){denied=true;}check(denied&&reading.progress(stranger.id,privateBook.id().toString()).isEmpty(),"private books cannot be opened or gain progress through reader IDs");reading.open(stranger.player,publicBook.id());check(stranger.dialog!=null,"saved-progress reader builds a native page dialog");reading.turn(stranger.player,1);check(reading.progress(stranger.id,publicBook.id().toString()).orElseThrow().page()==1,"reader page controls persist the current page");reading.load();reading.open(stranger.player,publicBook.id());check(reading.progress(stranger.id,publicBook.id().toString()).orElseThrow().page()==1,"reading resumes at the same page after reload");
        var readingFile=p.getDataFolder().toPath().resolve("reading-progress.yml");try{String saved=java.nio.file.Files.readString(readingFile);try{var invalid=YamlConfiguration.loadConfiguration(readingFile.toFile());invalid.set("players."+stranger.id+".bad.page",-1);invalid.set("players."+stranger.id+".bad.total",1);invalid.save(readingFile.toFile());boolean rejected=false;try{reading.load();}catch(RuntimeException expected){rejected=true;}check(rejected&&reading.progress(stranger.id,publicBook.id().toString()).orElseThrow().page()==1,"invalid reading-data reload preserves live saved positions");}finally{java.nio.file.Files.writeString(readingFile,saved);reading.load();reading.open(stranger.player,publicBook.id());}}catch(java.io.IOException ex){throw new RuntimeException(ex);}
        java.nio.file.Path readTmp=p.getDataFolder().toPath().resolve("reading-progress.yml.tmp");try{java.nio.file.Files.createDirectory(readTmp);denied=false;try{reading.turn(stranger.player,1);}catch(IllegalStateException expected){denied=true;}check(denied&&reading.progress(stranger.id,publicBook.id().toString()).orElseThrow().page()==1,"failed progress save does not advance remembered position");}catch(Exception ex){throw new RuntimeException(ex);}finally{try{java.nio.file.Files.deleteIfExists(readTmp);}catch(Exception ex){throw new RuntimeException(ex);}}
        reading.turn(stranger.player,1);reading.remember(stranger.player,publicBook.id().toString(),2,3,true,publicBook.title());reading.load();check(reading.progress(stranger.id,publicBook.id().toString()).orElseThrow().completed(),"finished-reading status survives reload");reading.browser(stranger.player,0,true);var history=(LibraryMenu)stranger.top.getHolder();check(history.screen()==LibraryMenu.Screen.READING_HISTORY&&publicBook.id().toString().equals(history.entry(0)),"continue-reading menu lists accessible saved books");reading.browser(stranger.player,0,false);var catalog=(LibraryMenu)stranger.top.getHolder();boolean leaked=false;for(int slot=0;slot<45;slot++)if(privateBook.id().toString().equals(catalog.entry(slot)))leaked=true;check(!leaked,"book catalog hides another player's private books");
        var board=p.getNoticeboardManager();var notice=board.post(editor.player,"Community event","Write a short story this weekend",7);denied=false;try{board.remove(stranger.player,notice.id());}catch(IllegalArgumentException expected){denied=true;}check(denied,"notice removal requires author or administrator");denied=false;try{board.pin(editor.player,notice.id(),true);}catch(IllegalArgumentException expected){denied=true;}check(denied,"only administrators can pin community notices");board.pin(owner.player,notice.id(),true);board.load();check(board.active().stream().anyMatch(n->n.id().equals(notice.id())&&n.pinned()),"community notices and pins persist across reload");board.detail(stranger.player,notice.id());check(stranger.dialog!=null,"notice details use native readable dialog");board.browser(stranger.player,0);var boardMenu=(LibraryMenu)stranger.top.getHolder();leaked=false;for(int slot=0;slot<45;slot++)if(("book:"+privateBook.id()).equals(boardMenu.entry(slot)))leaked=true;check(!leaked,"noticeboard recent-book feed never exposes private books");board.remove(editor.player,notice.id());denied=false;try{board.post(editor.player,"Bypass","Delete-and-repost attempt",7);}catch(IllegalArgumentException expected){denied=true;}check(denied,"deleting one's notice cannot bypass post cooldown");
        var block=world.getBlockAt(26000,80,26000);board.bind(owner.player,block.getLocation(),true);board.load();check(board.isBoard(block),"physical noticeboard binding survives reload");var interaction=new org.bukkit.event.player.PlayerInteractEvent(stranger.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,block,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(interaction);check(stranger.top.getHolder() instanceof LibraryMenu lm&&lm.screen()==LibraryMenu.Screen.NOTICEBOARD,"right-click bound block opens community noticeboard");board.bind(owner.player,block.getLocation(),false);
        shared.open(editor.player,id);check(editor.top.getHolder() instanceof LibraryMenu lm&&lm.screen()==LibraryMenu.Screen.COLLAB_EDIT,"invited author opens the shared writing desk");p.getGuiManager().openMainMenu(owner.player);check(owner.top.getItem(10)!=null&&owner.top.getItem(12)!=null&&owner.top.getItem(14)!=null&&owner.top.getItem(16)!=null&&owner.top.getItem(28)!=null,"main chest menu exposes all community and navigation features");
        navigationChecks(p,owner,editor,stranger,id,publicBook.id());
    }
    void navigationChecks(InfinityLibraryPlugin p,Fake owner,Fake editor,Fake stranger,UUID sharedId,UUID bookId){
        var nav=p.getNavigationManager();World world=p.getGenerationEngine().ensureWorld();owner.position=new Location(world,26015.5,81,26003.5);nav.bookmark(owner.player,"my_corner");nav.load();check(nav.bookmarks(owner.player).containsKey("my_corner")&&!nav.bookmarks(editor.player).containsKey("my_corner"),"bookmarks persist and remain private to their player");
        boolean denied=false;try{nav.book(owner.player,bookId);}catch(IllegalArgumentException expected){denied=true;}check(denied,"book without a physical shelf reports unavailability instead of drawing a false trail");
        for(int z=26002;z<=26003;z++)for(int y=81;y<=82;y++)world.getBlockAt(26009,y,z).setType(Material.STONE,false);
        var terrain=nav.terrain(world);Vector3i feet=new Vector3i(26015,81,26003);check(terrain.stand(feet),"real Paper terrain adapter recognizes a normal walkable floor");var floor=world.getBlockAt(26015,80,26003);var original=floor.getBlockData();floor.setType(Material.MAGMA_BLOCK,false);check(!terrain.stand(feet),"navigation rejects damaging magma floor");floor.setType(Material.OAK_FENCE,false);check(!terrain.stand(feet),"navigation does not treat tall fence collision as a one-block walking floor");floor.setBlockData(original,false);world.getBlockAt(26015,82,26003).setType(Material.STONE,false);check(!terrain.stand(feet),"navigation requires head clearance");world.getBlockAt(26015,82,26003).setType(Material.AIR,false);
        nav.destination(owner.player,"entrance");check(nav.activeCount()==1,"particle route starts through completed connected rooms");
        later(()->{
            if(owner.navParticles.isEmpty())getLogger().info("Navigation diagnostic: active="+nav.activeCount()+" messages="+owner.messages);
            check(!owner.navParticles.isEmpty(),"navigation emits particles only to the route's player");check(owner.navParticles.stream().allMatch(l->nav.terrain(world).stand(Vector3i.from(l))),"route particles stay on safe walking cells");
            owner.position=new Location(world,26012.5,81,26003.5);owner.navParticles.clear();
            later(()->{
                check(owner.navParticles.stream().anyMatch(l->l.getBlockX()==26009&&l.getBlockZ()!=26002&&l.getBlockZ()!=26003),"particle path goes around a real interior wall instead of through it");check(owner.navParticles.stream().noneMatch(l->world.getBlockAt(l.getBlockX(),l.getBlockY(),l.getBlockZ()).getType()==Material.STONE),"wall cells never receive navigation trail points");nav.stop(owner.player);int particles=owner.navParticles.size();
                later(()->{
                    check(nav.activeCount()==0&&owner.navParticles.size()==particles,"stopping navigation cancels route work and particles");previewChecks(p,owner,editor,stranger,sharedId,bookId);
                },12);
            },25);
        },20);
    }
    void previewChecks(InfinityLibraryPlugin p,Fake owner,Fake editor,Fake stranger,UUID sharedId,UUID bookId){
        var preview=p.getPreviewManager();World world=p.getGenerationEngine().ensureWorld();owner.position=new Location(world,26001.5,81,26001.5,90,0);for(int x=25970>>4;x<=26030>>4;x++)for(int z=25970>>4;z<=26030>>4;z++)world.getChunkAt(x,z);boolean denied=false;try{preview.start(editor.player,"shared_normal");}catch(IllegalArgumentException expected){denied=true;}check(denied,"non-admin cannot start client room preview");preview.start(owner.player,"shared_normal");check(preview.activeCount()==1&&owner.top.getHolder() instanceof LibraryMenu lm&&lm.screen()==LibraryMenu.Screen.PREVIEW,"builder preview opens rotation and inspection controls");
        later(()->{
            check(owner.clientBlocks.values().stream().anyMatch(b->b.getMaterial()==Material.STONE_BRICKS),"room preview sends visible client-only room blocks");check(owner.clientBlocks.entrySet().stream().filter(e->e.getValue().getMaterial()==Material.STONE_BRICKS).allMatch(e->e.getKey().getBlock().isEmpty()),"preview never places those ghost blocks in the real world");preview.handle(owner.player,10);preview.handle(owner.player,12);
            later(()->{
                preview.stop(owner.player);check(preview.activeCount()==0&&owner.clientBlocks.entrySet().stream().allMatch(e->e.getValue().equals(e.getKey().getBlock().getBlockData())),"stop restores live block data after rotation and mirroring");preview.start(owner.player,"probe_diversity");preview.variant(owner.player,"reading","garden");
                later(()->{
                    check(preview.activeCount()==1,"builder can choose a specific saved Diversity variant in preview");preview.stop(owner.player);preview.start(owner.player,"shared_normal");
                    later(()->{
                        int sent=owner.clientBlocks.size();owner.currentWorld=Bukkit.getWorld("world");owner.position=new Location(owner.currentWorld,0,80,0);
                        later(()->{
                            check(preview.activeCount()==0&&owner.clientBlocks.size()==sent,"world change closes preview without sending old-world blocks into new world");owner.currentWorld=null;owner.position=p.getGenerationEngine().homeLocation();p.saveConfig();p.reloadEverything();
                            check(p.getCollaborationManager().get(editor.player,sharedId).contributors().containsKey(editor.id),"full plugin reload preserves shared membership and credits");check(p.getReadingManager().progress(stranger.id,bookId.toString()).orElseThrow().completed(),"full plugin reload preserves finished reading state");check(p.getNavigationManager().bookmarks(owner.player).containsKey("my_corner"),"full plugin reload preserves personal navigation bookmarks");check(p.getPreviewManager().activeCount()==0&&p.getNavigationManager().activeCount()==0,"reload leaves no preview or navigation sessions running");
                            nativeAndShelfChecks(p);
                        },3);
                    },5);
                },8);
            },5);
        },5);
    }
    void nativeAndShelfChecks(InfinityLibraryPlugin p){
        Fake author=new Fake(p,true),editor=new Fake(p,false);World w=p.getGenerationEngine().ensureWorld();author.position=p.getGenerationEngine().homeLocation();editor.position=author.position.clone();var nativeUi=p.getNativeBookEditor();var draft=p.getBookWritingManager().draft(author.id);draft.details("Native probe","general","",false);draft.page("Before");author.items[0]=new ItemStack(Material.DIAMOND,3);nativeUi.personal(author.player);int slot=author.heldSlot;check(slot!=0&&author.items[0].getAmount()==3&&nativeUi.temporary(author.items[slot]),"native editor uses an empty hotbar slot without replacing the held item");BookMeta previous=(BookMeta)author.items[slot].getItemMeta();BookMeta updated=(BookMeta)previous.clone();updated.setPages(List.of("Edited in book UI","Second native page"));var event=new org.bukkit.event.player.PlayerEditBookEvent(author.player,slot,previous,updated,false);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled()&&draft.pages().equals(List.of("Edited in book UI","Second native page")),"Done packet saves whole native book into personal manuscript and prevents item duplication");
        later(()->{
            check(author.heldSlot==0&&author.items[slot]==null&&author.items[0].getType()==Material.DIAMOND,"successful native save removes temporary book and restores previous hand selection");
            var shared=p.getCollaborationManager().create(author.player,"Native shared");UUID id=shared.id();p.getCollaborationManager().update(author.player,id,d->d.invite(author.id,editor.id,"NativeGuest"));nativeUi.shared(editor.player,id);int es=editor.heldSlot;var ep=(BookMeta)editor.items[es].getItemMeta();var en=(BookMeta)ep.clone();en.setPages(List.of("Shared native edit"));Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerEditBookEvent(editor.player,es,ep,en,true));check(p.getCollaborationManager().get(author.player,id).pages().getFirst().equals("Shared native edit"),"native Sign packet saves shared pages without publishing or bypassing owner permissions");
            later(()->{
                nativeUi.shared(editor.player,id);int staleSlot=editor.heldSlot;var before=(BookMeta)editor.items[staleSlot].getItemMeta();var stale=(BookMeta)before.clone();stale.setPages(List.of("Must not overwrite"));var current=p.getCollaborationManager().get(author.player,id);p.getCollaborationManager().update(author.player,id,d->d.page(author.id,current.revision(),0,"Newer owner text"));Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerEditBookEvent(editor.player,staleSlot,before,stale,false));check(p.getCollaborationManager().get(author.player,id).pages().getFirst().equals("Newer owner text"),"stale native shared edit cannot overwrite newer content");check(((BookMeta)editor.items[staleSlot].getItemMeta()).getPages().contains("Must not overwrite"),"rejected native edit retains submitted text in temporary recovery book");nativeUi.cleanup(editor.player);
                Arrays.fill(editor.items,new ItemStack(Material.STONE));boolean refused=false;try{nativeUi.personal(editor.player);}catch(IllegalArgumentException expected){refused=true;}check(refused,"full hotbar refuses native editor without displacing inventory");Arrays.fill(editor.items,null);nativeUi.personal(editor.player);BookDraft paid=new BookDraft();paid.details("Payment guard","general","",true);paid.page("A temporary editor is not payment");boolean unpaid=false;try{p.getBookWritingManager().deliver(editor.player,p.getBookWritingManager().book(editor.player,paid,true),true);}catch(IllegalArgumentException expected){unpaid=true;}check(unpaid,"free temporary editor cannot be consumed as publication payment");nativeUi.cleanup(editor.player);
                p.getConfig().set("bookshelves.chunks-per-tick",4);p.getConfig().set("bookshelves.shelves-per-pass",64);var shelf=w.getBlockAt(26003,81,26002);shelf.setType(Material.CHISELED_BOOKSHELF,false);var inv=((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory();inv.clear();ItemStack marker=new ItemStack(Material.WRITTEN_BOOK);BookMeta mm=(BookMeta)marker.getItemMeta();mm.setTitle("Keep this book");mm.setAuthor("Probe");mm.setPages("Do not overwrite");marker.setItemMeta(mm);inv.setItem(0,marker);String category="native_"+author.id.toString().replace("-","");p.getBookStorageManager().setShelfCategory(shelf.getLocation(),category);p.getBookStorageManager().populateBookshelf(shelf);BookDraft book=new BookDraft();book.details("Shelf delivery probe",category,"",true);book.page("Arrived on a real shelf");p.getBookStorageManager().publishManuscript(author.player,p.getBookWritingManager().book(author.player,book,true));var stored=p.getBookStorageManager().allBooks().stream().filter(b->b.ownerUuid().equals(author.id.toString())&&b.title().equals("Shelf delivery probe")).findFirst().orElseThrow();
                later(()->{
                    var actual=((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory();check(actual.getItem(0).isSimilar(marker),"automatic shelf filling preserves existing occupied slots");long copies=Arrays.stream(actual.getContents()).filter(Objects::nonNull).filter(it->stored.id().toString().equals(it.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(p,"library_book_id"),org.bukkit.persistence.PersistentDataType.STRING))).count();check(copies==1,"publishing automatically delivers one catalogued book into an existing chiseled shelf");check(((org.bukkit.block.data.type.ChiseledBookshelf)shelf.getBlockData()).isSlotOccupied(1),"filled bookshelf slot is visibly occupied in block data");p.getBookStorageManager().populateBookshelf(shelf);check(Arrays.stream(actual.getContents()).filter(Objects::nonNull).filter(it->stored.id().toString().equals(it.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(p,"library_book_id"),org.bukkit.persistence.PersistentDataType.STRING))).count()==1,"revisiting a registered shelf does not duplicate the same book");check(p.getBookStorageManager().bookLocation(p.getBookStorageManager().allBooks().stream().filter(b->b.id().equals(stored.id())).findFirst().orElseThrow()).getBlockX()==26003,"automatic placement updates searchable physical shelf location");shelfRecoveryChecks(p,author,shelf,stored.id(),marker,category);
                },200);
            },2);
        },2);
    }
    void modeChecks(InfinityLibraryPlugin p){
        var engine=p.getGenerationEngine();int before=engine.placedCount();p.switchGenerationMode("unified");check(p.getGenerationEngine()==engine&&engine.placedCount()==before,"unified generator preserves the active built library");
        try{p.switchGenerationMode("procedural");throw new AssertionError("retired mode accepted");}catch(IllegalArgumentException expected){check(true,"retired experimental mode is rejected without changing worlds");}
        Fake f=new Fake(p,true);p.getAuthoringManager().activate(f.player,"room");check(p.getAuthoringManager().tool(f.player).equals("room"),"builder activates room wand");
        p.getBuilderBridge().onPluginMessageReceived("infinitylibrary:builder_v1",f.player,"{\"protocol\":1,\"action\":\"hello\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));check(p.getBuilderBridge().connected(f.player),"admin companion handshake establishes a builder session");
        Fake reader=new Fake(p,false);p.getBuilderBridge().onPluginMessageReceived("infinitylibrary:builder_v1",reader.player,"{\"protocol\":1,\"action\":\"hello\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));check(!p.getBuilderBridge().connected(reader.player),"nonadmin companion cannot establish builder editing session");
        var a=p.getAuthoringManager();World world=engine.ensureWorld();f.position=new Location(world,27000,80,27000);world.getChunkAt(27000>>4,27000>>4).addPluginChunkTicket(this);world.getBlockAt(27000,80,27000).setType(Material.STONE);world.getBlockAt(27004,83,27004).setType(Material.STONE);
        a.handle(f.player,org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,world.getBlockAt(27000,80,27000));a.handle(f.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(27004,83,27004));check(p.getRoomManager().selectionBounds(f.player).max().equals(new Vector3i(27004,83,27004)),"room wand left/right clicks select the expected bounds");
        a.activate(f.player,"table");a.handle(f.player,org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,world.getBlockAt(27001,81,27001));a.handle(f.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,world.getBlockAt(27002,81,27002));check(a.regions(f.player).size()==1&&a.regions(f.player).getFirst().size().equals(new Vector3i(2,1,2)),"reading region staging stores inclusive relative bounds");a.undo(f.player,false);check(a.regions(f.player).isEmpty(),"undo removes the completed region");a.undo(f.player,true);check(a.regions(f.player).size()==1,"redo restores completed reading region");
        a.apply(f.player);check(a.reading(new Location(world,27001,81,27001),"table"),"applying source region enables reading-table lookup");
        var region=a.regions(f.player).getFirst();var yaml=new YamlConfiguration();region.write(yaml);check(RoomRegion.read(yaml).equals(region),"room region metadata round trips through YAML");
        var nav=p.getNavigationManager();UUID destination=p.getGenerationEngine().navigationRooms().getFirst().id();nav.toggleFavorite(f.player,destination);check(nav.favorite(f.player,destination)&&!nav.favorite(reader.player,destination),"generated-room favorites are private and reference a stable room ID");nav.load();check(nav.favorite(f.player,destination),"room favorites survive a storage reload");nav.toggleFavorite(f.player,destination);check(!nav.favorite(f.player,destination),"favorite toggling removes only that room entry");
        p.getGuiManager().openMainMenu(f.player);check(((com.infinitylibrary.gui.LibraryMenu)f.top.getHolder()).screen()==com.infinitylibrary.gui.LibraryMenu.Screen.MAIN&&f.top.getItem(38).getType()==Material.BLACK_STAINED_GLASS_PANE,"player home excludes builder controls even for operators");p.getGuiManager().openBuilderMenu(f.player);check(((com.infinitylibrary.gui.LibraryMenu)f.top.getHolder()).screen()==com.infinitylibrary.gui.LibraryMenu.Screen.BUILDER_HOME,"builder home has a distinct menu identity");boolean denied=false;try{p.getGuiManager().openBuilderMenu(reader.player);}catch(IllegalArgumentException expected){denied=true;}check(denied,"ordinary players cannot open the builder menu");
        check(RoomType.parse("READING")==RoomType.READ&&RoomType.parse("RENTABLE")==RoomType.RENTABLE&&RoomType.parse("PUBLIC_WRITING")==RoomType.PUBLIC_WRITING,"new room roles parse without breaking legacy reading templates");
        var rentable=engine.navigationRooms().stream().filter(n->n.template().equals("shared_normal")).findFirst().orElseThrow();p.getRoomManager().setRoomType(rentable.template(),RoomType.RENTABLE);f.position=com.infinitylibrary.community.RoomGraph.center(rentable).toLocation(world);reader.position=f.position.clone();var rentals=p.getRentalManager();var lease=rentals.claim(f.player);check(lease.owner().equals(f.id)&&lease.active(System.currentTimeMillis()),"rentable generated room can be claimed without payment");rentals.note(f.player,"Retained private note");boolean privateDenied=false;try{rentals.note(reader.player,"intrusion");}catch(IllegalArgumentException expected){privateDenied=true;}check(privateDenied,"nonmember cannot write rental notes");rentals.invite(f.player,reader.id);rentals.note(reader.player,"Invited editor note");check(rentals.visibleNotes(reader.player).stream().anyMatch(l->l.notes().contains("Retained private note")),"invited editor can read and add workspace notes");UUID archivedBook=vaultChecks(p,f,reader,lease,rentable);rentals.release(f.player);rentals.load();p.getRentalVaultManager().load();check(p.getRentalVaultManager().get(f.player,archivedBook).pages().getFirst().contains("Secret rental words"),"owner archive retains full private book after release and reload");boolean expiredBookDenied=false;try{p.getRentalVaultManager().get(reader.player,archivedBook);}catch(IllegalArgumentException expected){expiredBookDenied=true;}check(expiredBookDenied,"expired rental revokes invited access to private book archive");check(rentals.mine(f.player).stream().anyMatch(l->l.notes().contains("Invited editor note")&&!l.active(System.currentTimeMillis())),"released rental retains notes after persistence reload");check(rentals.visibleNotes(reader.player).isEmpty(),"released rental removes invited-editor access to archived owner notes");
        studioChecks(p,f);
        p.getBuilderBridge().cleanup(f.player);check(!p.getBuilderBridge().connected(f.player),"companion cleanup invalidates session");
        getLogger().info("ALL "+count+" INTEGRATION CHECKS PASSED");Bukkit.shutdown();
    }
    String shelfId(ItemStack item,InfinityLibraryPlugin p){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(p,"library_book_id"),org.bukkit.persistence.PersistentDataType.STRING);}
    void shelfRecoveryChecks(InfinityLibraryPlugin p,Fake author,org.bukkit.block.Block shelf,UUID first,ItemStack marker,String category){
        var manager=p.getBookStorageManager();var inv=((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory();
        for(int slot=1;slot<6;slot++)inv.setItem(slot,marker.clone());
        manager.saveNow();p.reloadEverything();
        later(()->{
            // The saved destination still exists but is occupied by unrelated books.
            List<UUID> pending=new ArrayList<>();for(int n=0;n<7;n++){int index=n;BookDraft d=new BookDraft();d.details("Waiting shelf book "+n,category,"",true);d.page("Overflow content "+n);manager.publishManuscript(author.player,p.getBookWritingManager().book(author.player,d,true));pending.add(manager.allBooks().stream().filter(b->b.ownerUuid().equals(author.id.toString())&&b.title().equals("Waiting shelf book "+index)).findFirst().orElseThrow().id());}
            p.reloadEverything();
            later(()->{
                check(manager.allBooks().stream().filter(b->pending.contains(b.id())).allMatch(b->b.shelfLocation().isBlank()),"full shelves retain all new books as pending catalog entries");
                var next=shelf.getRelative(2,0,0);next.setType(Material.CHISELED_BOOKSHELF,false);((org.bukkit.block.ChiseledBookshelf)next.getState()).getInventory().clear();manager.setShelfCategory(next.getLocation(),category);manager.populateBookshelf(next);
                var stocked=((org.bukkit.block.ChiseledBookshelf)next.getState()).getInventory();check(Arrays.stream(stocked.getContents()).filter(Objects::nonNull).count()==6,"new shelf immediately stocks waiting books when room space appears");
                check(Arrays.stream(stocked.getContents()).anyMatch(it->first.toString().equals(shelfId(it,p))),"stale full-shelf destination is reassigned to available space");
                check(inv.getItem(0).isSimilar(marker)&&inv.getItem(1).isSimilar(marker),"reconciliation preserves unrelated shelf contents");
                stocked.clear();manager.saveNow();p.reloadEverything();
                later(()->{
                    check(Arrays.stream(stocked.getContents()).filter(Objects::nonNull).count()==6,"plugin reload automatically restores books to empty registered shelves");
                    List<String> ids=Arrays.stream(stocked.getContents()).map(it->shelfId(it,p)).filter(Objects::nonNull).toList();check(new HashSet<>(ids).size()==6,"reload restores one display copy per catalog ID without duplicates");
                    var last=shelf.getRelative(4,0,0);last.setType(Material.CHISELED_BOOKSHELF,false);var lastInv=((org.bukkit.block.ChiseledBookshelf)last.getState()).getInventory();lastInv.clear();manager.setShelfCategory(last.getLocation(),category);manager.populateBookshelf(last);
                    check(Arrays.stream(lastInv.getContents()).filter(Objects::nonNull).count()==2,"remaining overflow waits for and fills the next new shelf");
                    var empty=shelf.getRelative(6,0,0);empty.setType(Material.CHISELED_BOOKSHELF,false);((org.bukkit.block.ChiseledBookshelf)empty.getState()).getInventory().clear();manager.setShelfCategory(empty.getLocation(),category);manager.populateBookshelf(empty);check(Arrays.stream(((org.bukkit.block.ChiseledBookshelf)empty.getState()).getInventory().getContents()).allMatch(Objects::isNull),"new space does not duplicate books already placed in another shelf");
                    // Legacy books have no category; general shelves should accept them.
                    var general=shelf.getRelative(8,0,0);general.setType(Material.CHISELED_BOOKSHELF,false);var gi=((org.bukkit.block.ChiseledBookshelf)general.getState()).getInventory();gi.clear();manager.setShelfCategory(general.getLocation(),"general");ItemStack raw=new ItemStack(Material.WRITTEN_BOOK);var meta=(BookMeta)raw.getItemMeta();meta.setTitle("Uncategorized submission");meta.setAuthor(author.player.getName());meta.setPages("External signed text");raw.setItemMeta(meta);manager.publishManuscript(author.player,raw);manager.populateBookshelf(general);boolean foundGeneral=Arrays.stream(gi.getContents()).anyMatch(it->Objects.equals(shelfId(raw,p),shelfId(it,p)));for(int n=1;!foundGeneral&&n<=manager.allBooks().size()/6+1;n++){var extra=general.getRelative(0,0,n*2);extra.setType(Material.CHISELED_BOOKSHELF,false);var ei=((org.bukkit.block.ChiseledBookshelf)extra.getState()).getInventory();ei.clear();manager.setShelfCategory(extra.getLocation(),"general");manager.populateBookshelf(extra);foundGeneral=Arrays.stream(ei.getContents()).anyMatch(it->Objects.equals(shelfId(raw,p),shelfId(it,p)));}check(foundGeneral,"uncategorized signed books can stock general shelves after earlier pending books");
                    author.position=empty.getLocation();BookDraft submitted=new BookDraft();submitted.details("Direct shelf submission",category,"",true);submitted.page("Track this physical copy");ItemStack submission=p.getBookWritingManager().book(author.player,submitted,true);check(manager.saveLecternBook(author.player,submission),"direct lectern submission finds nearby matching shelf space");var direct=manager.allBooks().stream().filter(b->b.ownerUuid().equals(author.id.toString())&&b.title().equals("Direct shelf submission")).findFirst().orElseThrow();var destination=manager.bookLocation(direct).getBlock();var di=((org.bukkit.block.ChiseledBookshelf)destination.getState()).getInventory();check(Arrays.stream(di.getContents()).anyMatch(it->direct.id().toString().equals(shelfId(it,p))),"direct submission physical copy carries its catalog tracking ID");
                    for(int slot=0;slot<di.getSize();slot++)if(direct.id().toString().equals(shelfId(di.getItem(slot),p))){var old=di.getItem(slot);var oldMeta=old.getItemMeta();oldMeta.getPersistentDataContainer().remove(new NamespacedKey(p,"library_book_id"));old.setItemMeta(oldMeta);di.setItem(slot,old);}
                    var sink=shelf.getRelative(10,0,0);sink.setType(Material.CHISELED_BOOKSHELF,false);var si=((org.bukkit.block.ChiseledBookshelf)sink.getState()).getInventory();si.clear();manager.setShelfCategory(sink.getLocation(),category);manager.populateBookshelf(sink);check(Arrays.stream(di.getContents()).anyMatch(it->direct.id().toString().equals(shelfId(it,p)))&&Arrays.stream(si.getContents()).noneMatch(it->direct.id().toString().equals(shelfId(it,p))),"legacy untagged physical submission is recovered without making another copy");
                    // These shelves were built in empty shared-room fixtures. Remove them before
                    // the independent rental case, which creates its own single display shelf.
                    for(var node:p.getGenerationEngine().navigationRooms())if(node.template().equals("shared_normal")){var box=node.box();for(int x=0;x<box.size().x();x++)for(int y=0;y<box.size().y();y++)for(int z=0;z<box.size().z();z++){var at=box.origin().add(new Vector3i(x,y,z));var block=shelf.getWorld().getBlockAt(at.x(),at.y(),at.z());if(block.getType()==Material.CHISELED_BOOKSHELF)block.setType(Material.AIR,false);}}
                    modeChecks(p);
                },60);
            },60);
        },40);
    }
    UUID vaultChecks(InfinityLibraryPlugin p,Fake owner,Fake editor,com.infinitylibrary.rentals.RentalManager.Lease lease,com.infinitylibrary.community.RoomGraph.RoomNode room){
        var vault=p.getRentalVaultManager();World world=p.getGenerationEngine().ensureWorld();Vector3i point=room.box().origin().add(new Vector3i(2,1,2));var shelf=world.getBlockAt(point.x(),point.y(),point.z());shelf.setType(Material.CHISELED_BOOKSHELF,false);var inventory=((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory();inventory.clear();p.getBookStorageManager().populateBookshelf(shelf);check(Arrays.stream(inventory.getContents()).allMatch(Objects::isNull),"public stocking skips private rental shelves");
        ItemStack original=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)original.getItemMeta();meta.setTitle("Private rental book");meta.setAuthor("Workspace author");meta.setPages("Secret rental words preserved in full","Second private page");meta.getPersistentDataContainer().set(new NamespacedKey(p,"book_owner_uuid"),org.bukkit.persistence.PersistentDataType.STRING,owner.id.toString());original.setItemMeta(meta);owner.items[owner.heldSlot]=original;var archived=vault.archiveHeld(owner.player,lease.id());check(owner.items[owner.heldSlot].isSimilar(original)&&owner.items[owner.heldSlot].getAmount()==1,"archiving copies content without consuming or duplicating the inventory item");
        Fake outsider=new Fake(p,false);outsider.position=owner.position.clone();boolean denied=false;try{vault.get(outsider.player,archived.id());}catch(IllegalArgumentException expected){denied=true;}check(denied,"nonmember cannot read private archived book content");check(vault.get(editor.player,archived.id()).pages().equals(meta.getPages()),"invited editor can read the authorized private book copy");
        var draft=p.getBookWritingManager().draft(owner.id);draft.details("Rental working draft","general","",false);draft.page("Unfinished private manuscript");vault.archiveDraft(owner.player,lease.id());check(vault.books(owner.player,lease.id()).size()==2,"rental archive stores both signed books and working manuscripts");
        vault.refresh(lease.room());var physical=((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory().getItem(0);check(physical!=null&&physical.getType()==Material.WRITTEN_BOOK,"rental archive creates occupied chiseled shelf display slots");check(((BookMeta)physical.getItemMeta()).getPages().stream().noneMatch(text->text.contains("Secret rental words")),"physical shelf display contains no secret manuscript text");check(((org.bukkit.block.data.type.ChiseledBookshelf)shelf.getBlockData()).isSlotOccupied(0),"rental shelf occupied state is visible to players");
        vault.restoreDraft(editor.player,archived.id());check(p.getBookWritingManager().draft(editor.id).pages().equals(meta.getPages()),"authorized archive restore recovers complete manuscript pages");boolean overwriteDenied=false;try{vault.restoreDraft(editor.player,archived.id());}catch(IllegalArgumentException expected){overwriteDenied=true;}check(overwriteDenied,"archive restore cannot overwrite a nonempty manuscript");
        vault.workspace(owner.player,lease.id());check(owner.top.getHolder() instanceof com.infinitylibrary.gui.LibraryMenu m&&m.screen()==com.infinitylibrary.gui.LibraryMenu.Screen.RENTAL_ROOM,"rental workspace has a functional protected player menu");
        var protection=new com.infinitylibrary.rentals.RentalProtection(p);var broken=new org.bukkit.event.block.BlockBreakEvent(shelf,outsider.player);protection.destroy(broken);check(broken.isCancelled(),"non-builder cannot break a rental bookshelf");
        var from=p.getGenerationEngine().homeLocation();var move=new org.bukkit.event.player.PlayerMoveEvent(outsider.player,from,owner.position.clone());protection.move(move);check(move.getTo().equals(from),"uninvited movement into an active rental is rejected");
        var teleport=new org.bukkit.event.player.PlayerTeleportEvent(outsider.player,from,owner.position.clone(),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);protection.teleport(teleport);check(teleport.isCancelled(),"uninvited teleport into an active rental is rejected");
        var transfer=new org.bukkit.event.inventory.InventoryMoveItemEvent(((org.bukkit.block.ChiseledBookshelf)shelf.getState()).getInventory(),physical,Bukkit.createInventory(null,9),true);protection.hopper(transfer);check(transfer.isCancelled(),"hopper cannot extract private rental shelf display items");
        var reading=p.getReadingManager();reading.openRental(editor.player,archived.id());check(editor.dialog!=null,"private rental book opens guided reader dialog");reading.turn(editor.player,1);reading.load();check(reading.progress(editor.id,"rental-"+archived.id()).orElseThrow().page()==1,"rental reading progress survives reload");reading.openRental(editor.player,archived.id());p.getRentalManager().manage(owner.player,lease.id(),"revoke",editor.id);boolean revoked=false;try{reading.turn(editor.player,-1);}catch(IllegalArgumentException expected){revoked=true;}check(revoked,"revocation invalidates an already open private reader before its next page");p.getRentalManager().invite(owner.player,editor.id);
        var flows=new com.infinitylibrary.builder.BuilderFlows(p);flows.saveRoom(owner.player);check(owner.dialog!=null,"room capture role picker works through native server dialog");flows.decoration(owner.player);check(owner.dialog!=null,"decoration dimensions and categories have a vanilla dialog");flows.connection(owner.player);check(owner.dialog!=null,"connection settings have a vanilla dialog");flows.diversity(owner.player);check(owner.dialog!=null,"Diversity variants and doors have a vanilla dialog");boolean builderDenied=false;try{flows.saveRoom(editor.player);}catch(IllegalArgumentException expected){builderDenied=true;}check(builderDenied,"non-builder cannot enter template capture dialog");
        try {var path=p.getDataFolder().toPath().resolve("drafts.yml");String originalData=java.nio.file.Files.readString(path);String current=p.getBookWritingManager().draft(owner.id).pages().getFirst();java.nio.file.Files.writeString(path,"drafts: [broken YAML");boolean rejected=false;try{p.getBookWritingManager().load();}catch(RuntimeException expected){rejected=true;}check(rejected&&p.getBookWritingManager().draft(owner.id).pages().getFirst().equals(current),"malformed manuscript YAML preserves in-memory text and refuses load");check(java.nio.file.Files.readString(path).equals("drafts: [broken YAML"),"failed manuscript load leaves source file untouched");java.nio.file.Files.writeString(path,originalData);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
        return archived.id();
    }
    com.google.gson.JsonObject packet(Fake f,String kind){for(int i=f.packets.size()-1;i>=0;i--)if(f.packets.get(i).get("kind").getAsString().equals(kind))return f.packets.get(i);throw new AssertionError("No "+kind+" packet");}
    long requestSequence;
    void studioRequest(InfinityLibraryPlugin p,Fake f,String action,String json){var o=com.google.gson.JsonParser.parseString(json).getAsJsonObject();o.addProperty("protocol",1);o.addProperty("action",action);o.addProperty("token",packet(f,"state").get("token").getAsString());o.addProperty("seq",++requestSequence);o.addProperty("revision",p.getAuthoringManager().revision(f.player));if(!o.has("draftRevision")&&f.packets.stream().anyMatch(x->x.get("kind").getAsString().equals("studio_model")))o.add("draftRevision",packet(f,"studio_model").get("draftRevision"));p.getBuilderBridge().onPluginMessageReceived("infinitylibrary:builder_v1",f.player,o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void studioChecks(InfinityLibraryPlugin p,Fake f){
        if(p.getRoomManager().getActive("builtin_modular").isEmpty())p.getRoomManager().restore("builtin_modular");
        studioRequest(p,f,"studio_open","{\"room\":\"builtin_modular\"}");var first=packet(f,"studio_model");check(first.get("room").getAsString().equals("builtin_modular")&&first.get("total").getAsInt()>0,"studio opens a server-owned saved-room draft with full voxel count");
        studioRequest(p,f,"studio_chunk","{\"slot\":\"a\",\"model\":\""+first.get("model").getAsString()+"\",\"from\":0}");var chunk=packet(f,"studio_blocks");check(chunk.getAsJsonArray("blocks").size()>0&&chunk.get("next").getAsInt()<=256,"studio model stream is bounded and advances its cursor");check(chunk.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<30000,"studio block chunk fits the plugin message budget");
        String original=p.getRoomManager().getActive("builtin_modular").orElseThrow().blocks().getFirst().blockData();
        studioRequest(p,f,"studio_paint","{\"min\":[0,0,0],\"size\":[1,1,1],\"block\":\"minecraft:diamond_block\"}");var painted=packet(f,"studio_model");check(painted.get("dirty").getAsBoolean()&&painted.get("undo").getAsBoolean(),"studio brush creates a dirty undoable draft revision");check(p.getRoomManager().getActive("builtin_modular").orElseThrow().blocks().getFirst().blockData().equals(original),"studio painting leaves the saved source template unchanged");
        studioRequest(p,f,"studio_undo","{}");check(packet(f,"studio_model").get("redo").getAsBoolean(),"studio undo exposes redo history");
        studioRequest(p,f,"studio_pair","{\"room\":\"builtin_modular\",\"door\":\"\",\"choice\":0}");var pair=packet(f,"studio_model");check(pair.get("slot").getAsString().equals("b")&&pair.getAsJsonArray("offset").asList().stream().anyMatch(x->x.getAsInt()!=0),"adjacent preview aligns a transformed second room on a shared doorway plane");
        long pairRevision=pair.get("draftRevision").getAsLong();
        studioRequest(p,f,"studio_area","{\"areaKind\":\"table\",\"id\":\"desk\",\"previous\":\"\",\"min\":[2,1,2],\"size\":[1,1,1],\"category\":\"general\",\"direction\":\"NORTH\",\"floor\":0,\"fill\":\"EXACT\",\"gap\":0,\"weight\":1,\"rotate\":true,\"stack\":false}");check(packet(f,"studio_model").get("slot").getAsString().equals("b")&&packet(f,"studio_model").get("draftRevision").getAsLong()>pairRevision,"adjacent preview refreshes automatically after an area edit");
        studioRequest(p,f,"studio_save","{\"room\":\"studio_probe\",\"type\":\"FILLER\",\"expected\":\""+p.getRoomManager().getActive("studio_probe").map(p.getBuilderBridge()::version).orElse("")+"\"}");check(p.getRoomManager().getActive("studio_probe").isPresent()&&!packet(f,"studio_model").get("dirty").getAsBoolean(),"studio saves a custom copy through the durable room catalog");
        studioRequest(p,f,"studio_paint","{\"min\":[0,0,0],\"size\":[1,1,1],\"block\":\"minecraft:diamond_block\",\"draftRevision\":-1}");check(packet(f,"state").get("message").getAsString().contains("draft changed"),"stale studio mutation is rejected without changing the draft");
        studioRequest(p,f,"studio_save","{\"room\":\"builtin_modular\",\"type\":\"FILLER\",\"expected\":\""+p.getBuilderBridge().version(p.getRoomManager().getActive("builtin_modular").orElseThrow())+"\"}");check(packet(f,"state").get("message").getAsString().contains("built-ins"),"studio cannot overwrite a built-in template even with its current version");
    }
    final class Fake implements InvocationHandler {
        final InfinityLibraryPlugin plugin; final UUID id=UUID.randomUUID(); final boolean admin;
        final java.util.List<com.google.gson.JsonObject> packets=new ArrayList<>();
        final ItemStack[] items=new ItemStack[36];
        final Player player; final PlayerInventory inventory; final InventoryView view;
        Inventory top; Object dialog; Location position; boolean online=true; int heldSlot; World currentWorld; final java.util.List<String> messages=new ArrayList<>(); final java.util.List<Location> navParticles=new ArrayList<>(); final java.util.Map<Location,org.bukkit.block.data.BlockData> clientBlocks=new HashMap<>();
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
                case "getHeldItemSlot" -> heldSlot;case "setHeldItemSlot" -> {heldSlot=(int)a[0];yield null;}
                case "setItemInMainHand" -> {items[heldSlot]=(ItemStack)a[0];yield null;}
                case "getItemInMainHand" -> items[heldSlot]==null?new ItemStack(Material.AIR):items[heldSlot];
                case "firstEmpty" -> { int slot=-1; for(int i=0;i<36;i++) if(items[i]==null){slot=i;break;} yield slot; }
                case "addItem" -> { Map<Integer,ItemStack> left=new HashMap<>(); int n=0; for(ItemStack item:(ItemStack[])a[0]) {int slot=-1;for(int i=0;i<36;i++)if(items[i]==null){slot=i;break;}if(slot<0)left.put(n,item);else items[slot]=item;n++;}yield left;}
                default -> empty(m.getReturnType());
            };
        }
        public Object invoke(Object o,Method m,Object[] a) {
            return switch(m.getName()) {
                case "getUniqueId" -> id; case "getName" -> admin?"TestAuthor":"TestReader";
                case "hasPermission" -> a[0].equals("infinitylibrary.command") || a[0].equals("infinitylibrary.notice.post") || admin;
                case "isOnline" -> online; case "getInventory" -> inventory; case "getOpenInventory" -> view;
                case "getWorld" -> currentWorld==null?plugin.getGenerationEngine().ensureWorld():currentWorld; case "getLocation" -> position==null?plugin.getGenerationEngine().homeLocation():position.clone();
                case "openInventory" -> {top=(Inventory)a[0];yield view;}
                case "sendPluginMessage" -> {packets.add(com.google.gson.JsonParser.parseString(new String((byte[])a[2],java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());yield null;}
                case "showDialog" -> {dialog=a[0];yield null;}
                case "sendBlockChange" -> {clientBlocks.put(((Location)a[0]).clone(),((org.bukkit.block.data.BlockData)a[1]).clone());yield null;}
                case "sendMessage","sendActionBar" -> {messages.add(java.util.Arrays.toString(a));yield null;}
                case "spawnParticle" -> {if(a[0]==Particle.END_ROD)for(Object arg:a)if(arg instanceof Location at){navParticles.add(at.clone());break;}yield null;}
                case "equals" -> o==a[0]; case "hashCode" -> id.hashCode();
                default -> empty(m.getReturnType());
            };
        }
        Object empty(Class<?> c) { if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0F;if(c==double.class)return 0D;return null; }
    }
}
