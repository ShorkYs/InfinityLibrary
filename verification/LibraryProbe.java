import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import com.infinitylibrary.listener.GUIListener;
import com.infinitylibrary.writing.*;
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
            if(e.plannedCount()>0) check(true,"persisted sibling plans restored on process startup");
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
                    uiChecks(p);
                    e.setPaused(false);
                },40);
            },40);
        },40);
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
            getLogger().info("ALL "+count+" INTEGRATION CHECKS PASSED"); Bukkit.shutdown();
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
