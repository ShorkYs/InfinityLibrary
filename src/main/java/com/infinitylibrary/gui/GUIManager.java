package com.infinitylibrary.gui;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {
    private final InfinityLibraryPlugin plugin;
    private final com.infinitylibrary.builder.BuilderFlows flows;
    private final Map<UUID, String> selectedRoom = new ConcurrentHashMap<>();
    public GUIManager(InfinityLibraryPlugin plugin) { this.plugin = plugin;this.flows=new com.infinitylibrary.builder.BuilderFlows(plugin); }

    public void openBuilderTools(Player p){if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Builder permission required.");LibraryMenu m=new LibraryMenu(p,LibraryMenu.Screen.BUILDER_TOOLS,27,"Library • Builder tools");int slot=0;for(String tool:com.infinitylibrary.builder.AuthoringManager.TOOLS){m.entry(slot,tool);plugin.getBookWritingManager().icon(m.getInventory(),slot++,Material.BLAZE_ROD,tool,"Left: get wand · Right: settings");}plugin.getBookWritingManager().icon(m.getInventory(),18,Material.ARROW,"Undo");plugin.getBookWritingManager().icon(m.getInventory(),20,Material.ARROW,"Redo");plugin.getBookWritingManager().icon(m.getInventory(),22,Material.BARRIER,"Cancel unfinished selection");plugin.getBookWritingManager().icon(m.getInventory(),26,Material.ARROW,"Builder menu");p.openInventory(m.getInventory());}
    public void builderAction(Player p,LibraryMenu menu,int slot){builderAction(p,menu,slot,false);}
    public void builderAction(Player p,LibraryMenu menu,int slot,boolean settings){if(settings&&menu.entry(slot)!=null){flows.configure(p,menu.entry(slot));return;}if(slot==26)openBuilderMenu(p);else if(slot==18)plugin.getAuthoringManager().undo(p,false);else if(slot==20)plugin.getAuthoringManager().undo(p,true);else if(slot==22)plugin.getAuthoringManager().cancel(p);else if(menu.entry(slot)!=null){plugin.getAuthoringManager().activate(p,menu.entry(slot));p.closeInventory();}}
    public void openMainMenu(Player player) {
        if (!player.hasPermission("infinitylibrary.command")) throw new IllegalArgumentException("Missing permission infinitylibrary.command");
        plugin.getBookWritingManager().invalidate(player);plugin.getManagedDialogs().invalidate(player);
        LibraryMenu menu = new LibraryMenu(player, LibraryMenu.Screen.MAIN, 54, "Infinity Library • Main Menu");
        Inventory inv=menu.getInventory();
        for(int i=0;i<54;i++) inv.setItem(i,item(Material.BLACK_STAINED_GLASS_PANE," ",List.of()));
        var writer=plugin.getBookWritingManager();
        writer.icon(inv,4,Material.ENCHANTED_BOOK,"§dInfinity Library","§7Books: "+plugin.getBookStorageManager().totalBooks(),"§7Rooms: "+plugin.getGenerationEngine().placedCount()+" · Planned: "+plugin.getGenerationEngine().plannedCount());
        writer.icon(inv,10,Material.OAK_SIGN,"§eCommunity noticeboard","§7Announcements, events and recent public books");
        writer.icon(inv,12,Material.BOOK,"§bBook collection","§7Read books with saved page progress");
        writer.icon(inv,14,Material.CLOCK,"§aContinue reading","§7Your saved pages and finished books");
        writer.icon(inv,16,Material.MAP,"§bParticle navigation","§7Walking trails and personal bookmarks");
        writer.icon(inv,18,Material.OAK_DOOR,"Rental workspace","Private shelves, books, drafts, notes and collaborators");
        writer.icon(inv,26,Material.FILLED_MAP,"Where am I?","Show this generated room address");
        writer.icon(inv,36,Material.NETHER_STAR,"Favorite rooms","Right-click a generated room to favorite it");
        writer.icon(inv,28,Material.WRITABLE_BOOK,"§dCollaborative books","§7Shared manuscripts with invited editors");
        writer.icon(inv,20,Material.WRITABLE_BOOK,"§aWriting desk","§7Write pages, set a title and preview","§7Continue your saved manuscript");
        writer.icon(inv,22,Material.COMPASS,"§bSearch books","§7Search title, author, category or tags");
        writer.icon(inv,24,Material.BOOKSHELF,"§dLibrary statistics","§7Books, contributors and your activity");
        writer.icon(inv,30,Material.ENDER_PEARL,"§bReturn to entrance","§7Teleport to the starting room");
        writer.icon(inv,32,Material.LECTERN,"§aSubmit a signed book","§7Open the existing book submission editor");
        writer.icon(inv,34,Material.CHISELED_BOOKSHELF,"§aReturn a book","§7Rate and return a library book");
        writer.icon(inv,40,Material.FEATHER,"§eClaim daily book & quill","§7Used when signing or publishing a manuscript");
        writer.icon(inv,49,Material.BARRIER,"§cClose menu");
        player.openInventory(inv);
    }
    public void mainMenuAction(Player player,int slot) {
        switch(slot) {
            case 10 -> plugin.getNoticeboardManager().browser(player,0);
            case 12 -> plugin.getReadingManager().browser(player,0,false);
            case 14 -> plugin.getReadingManager().browser(player,0,true);
            case 16 -> plugin.getNavigationManager().browser(player);
            case 18 -> plugin.getRentalVaultManager().browser(player,0);
            case 26 -> plugin.getNavigationManager().currentRoom(player);
            case 36 -> plugin.getNavigationManager().favorites(player,0);
            case 28 -> plugin.getCollaborationManager().browser(player,0);
            case 20 -> plugin.getBookWritingManager().open(player);
            case 22 -> plugin.getBookWritingManager().searchDialog(player);
            case 24 -> openStats(player);
            case 30 -> { player.teleport(plugin.getGenerationEngine().homeLocation()); player.closeInventory(); }
            case 32 -> openLecternBookEditor(player);
            case 34 -> openBookshelfReturn(player,null);
            case 40 -> { boolean given=plugin.getBookStorageManager().giveDailyWritableBook(player); player.sendMessage(given?"Book & quill added to your inventory.":"Unable to claim: check your daily limit and inventory space."); openMainMenu(player); }
            case 49 -> player.closeInventory();
        }
    }

    public void openBuilderMenu(Player p){
        if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Builder permission required.");
        LibraryMenu m=new LibraryMenu(p,LibraryMenu.Screen.BUILDER_HOME,27,"Infinity Library • Builder");var w=plugin.getBookWritingManager();
        w.icon(m.getInventory(),10,Material.BLAZE_ROD,"Selection tools","Room, doors, regions, undo and cancel");
        w.icon(m.getInventory(),11,Material.WRITABLE_BOOK,"Save selected room","Native capture dialog and role selection");w.icon(m.getInventory(),13,Material.ARMOR_STAND,"Decoration settings");w.icon(m.getInventory(),15,Material.OAK_DOOR,"Door settings");
        w.icon(m.getInventory(),12,Material.BRICKS,"Room catalog","Capture and configure room roles");
        w.icon(m.getInventory(),14,Material.ARMOR_STAND,"Decoration catalog","Categories and exact/packed areas");
        w.icon(m.getInventory(),16,Material.PRISMARINE_SHARD,"Room Diversity","Same-size variations and variant doors");
        w.icon(m.getInventory(),20,Material.REDSTONE_TORCH,plugin.getGenerationEngine().isPaused()?"Resume exploration":"Pause exploration");
        w.icon(m.getInventory(),18,Material.COMPASS,"Generation diagnostics","Checks the nearby exits and template pool");
        w.icon(m.getInventory(),24,Material.CHEST_MINECART,"Back up plugin data","YAML backup; back up the world separately");
        w.icon(m.getInventory(),26,Material.CHISELED_BOOKSHELF,"Refresh loaded bookshelves");
        w.icon(m.getInventory(),22,Material.CHEST,"Player menu");p.openInventory(m.getInventory());
    }
    public void builderMenuAction(Player p,int slot){
        if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Builder permission required.");
        switch(slot){case 18->{for(String line:plugin.getGenerationEngine().diagnose(p))p.sendMessage(line);}case 24->{p.sendMessage("Data backup: "+com.infinitylibrary.storage.BackupManager.create(plugin));}case 26->{plugin.getBookStorageManager().requestShelfScan();p.sendMessage("Loaded bookshelves queued for refresh");}case 10->openBuilderTools(p);case 11->flows.saveRoom(p);case 13->flows.decoration(p);case 15->flows.connection(p);case 12->openRoomEditor(p);case 14->plugin.getPropManager().browser(p);case 16->flows.diversity(p);case 20->{var e=plugin.getGenerationEngine();e.setPaused(!e.isPaused());openBuilderMenu(p);}case 22->openMainMenu(p);}
    }

    public void openStats(Player player) {
        int size = plugin.getConfig().getInt("gui.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, statsTitle());
        Material filler = material("gui.filler-material", Material.BLACK_STAINED_GLASS_PANE);
        for (int i=0;i<size;i++) inv.setItem(i, item(filler, " ", List.of()));
        inv.setItem(plugin.getConfig().getInt("gui.total-books-slot", 11), item(material("gui.total-books-material", Material.WRITTEN_BOOK), "&dTotal Books", List.of("&7Stored written books: &f" + plugin.getBookStorageManager().totalBooks())));
        inv.setItem(plugin.getConfig().getInt("gui.contributors-slot", 13), item(material("gui.contributors-material", Material.PLAYER_HEAD), "&bContributors", List.of("&7Unique contributors: &f" + plugin.getBookStorageManager().totalContributors())));
        inv.setItem(plugin.getConfig().getInt("gui.player-stats-slot", 15), item(material("gui.player-stats-material", Material.BOOK), "&aYour Stats", List.of("&7Books contributed: &f" + plugin.getBookStorageManager().playerCount(player.getUniqueId()))));
        inv.setItem(22,item(Material.ARROW,"&eMain menu",List.of()));
        player.openInventory(inv);
    }

    public void openLectern(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, lecternTitle());
        for (int i = 0; i < 27; i++) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(13, item(Material.WRITABLE_BOOK, "&dClaim Daily Writable Book", List.of("&7One per player per day.", "&7Books start private and linked to you.")));
        inv.setItem(11, item(Material.COMPASS, "&bSearch Stored Books", List.of("&7Click, then type a title in chat.", "&7Particles show the shelf location.")));
        inv.setItem(15, item(Material.ENDER_EYE, "&aLibrary Stats", List.of("&7Open the stats interface.")));
        player.openInventory(inv);
    }

    public String statsTitle() { return color(plugin.getConfig().getString("gui.title", "&5Infinity Library")); }
    public String lecternTitle() { return color(plugin.getConfig().getString("lectern-gui.title", "&5Library Lectern")); }
    public String lecternBookEditorTitle() { return color(plugin.getConfig().getString("lectern-gui.book-editor-title", "&5Library Book Editor")); }

    public void openLecternBookEditor(Player player) { openLecternBookEditor(player, null); }
    public void openLecternBookEditor(Player player, ItemStack book) {
        Inventory inv = Bukkit.createInventory(null, 27, lecternBookEditorTitle());
        drawLecternBookEditor(inv, player, book);
        player.openInventory(inv);
    }
    public void refreshLecternBookEditor(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!player.getOpenInventory().getTitle().equals(lecternBookEditorTitle())) return;
        drawLecternBookEditor(inv, player, inv.getItem(22));
    }

    private void drawLecternBookEditor(Inventory inv, Player player, ItemStack book) {
        for (int i = 0; i < 27; i++) if (i != 22) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        if (book == null || book.getType().isAir()) {
            inv.setItem(4, item(Material.LECTERN, "&dSubmit a Written Book", List.of("&7Put a written book in the", "&7middle bottom slot below.")));
            inv.setItem(22, null);
            return;
        }
        if (book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) {
            inv.setItem(4, item(Material.BARRIER, "&cWritten Books Only", List.of("&7Remove this item and place", "&7a signed written book here.")));
            inv.setItem(22, book);
            return;
        }
        inv.setItem(22, book);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        inv.setItem(4, item(Material.WRITTEN_BOOK, "&d" + value(meta.getTitle(), "Untitled"), List.of("&7Author: &f" + value(meta.getAuthor(), "Unknown"), "&7Pages: &f" + meta.getPageCount())));
        inv.setItem(9, item(Material.NAME_TAG, "&bEdit Title", List.of("&7Current: &f" + value(meta.getTitle(), "Untitled"))));
        inv.setItem(10, item(Material.PLAYER_HEAD, "&bEdit Author", List.of("&7Current: &f" + value(meta.getAuthor(), player.getName()))));
        inv.setItem(11, item(Material.BOOKSHELF, "&aEdit Category", List.of("&7Current: &f" + metadata(pdc, "book_category", "general"))));
        inv.setItem(12, item(Material.PAPER, "&aEdit Tags", List.of("&7Current: &f" + metadata(pdc, "book_tags", "none"), "&7Use comma separated tags.")));
        inv.setItem(13, item(Material.EXPERIENCE_BOTTLE, "&eEdit Rating", List.of("&7Current: &f" + metadata(pdc, "book_rating", "unrated"))));
        inv.setItem(14, item(Material.OAK_SIGN, "&eEdit Comments", List.of("&7Current: &f" + metadata(pdc, "book_comments", "none"))));
        inv.setItem(15, item(Material.ENDER_EYE, "&6Toggle Visibility", List.of("&7Current: &f" + (plugin.getBookStorageManager().isPublicBook(book) ? "Public" : "Private"))));
        inv.setItem(17, item(Material.EMERALD_BLOCK, "&aSave To Library", List.of("&7Stores this book so it can", "&7appear in chiseled bookshelves.")));
    }

    private String metadata(PersistentDataContainer pdc, String key, String fallback) { return value(plugin.getBookStorageManager().bookMetadataValue(pdc, key), fallback); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public String bookshelfReturnTitle() { return color(plugin.getConfig().getString("bookshelf-return-gui.title", "&5Return Library Book")); }
    public void openBookshelfReturn(Player player, ItemStack book) {
        Inventory inv = Bukkit.createInventory(null, 27, bookshelfReturnTitle());
        drawBookshelfReturn(inv, player, book);
        player.openInventory(inv);
    }
    public void refreshBookshelfReturn(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!player.getOpenInventory().getTitle().equals(bookshelfReturnTitle())) return;
        drawBookshelfReturn(inv, player, inv.getItem(13));
    }
    private void drawBookshelfReturn(Inventory inv, Player player, ItemStack book) {
        for (int i = 0; i < 27; i++) if (i != 13) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        if (book == null || book.getType().isAir()) {
            inv.setItem(4, item(Material.CHISELED_BOOKSHELF, "&dReturn A Library Book", List.of("&7Drag a written book into", "&7the middle slot below.")));
            inv.setItem(13, null);
            return;
        }
        if (book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) {
            inv.setItem(4, item(Material.BARRIER, "&cWritten Books Only", List.of("&7Remove this item and place", "&7a signed written book here.")));
            inv.setItem(13, book);
            return;
        }
        inv.setItem(13, book);
        inv.setItem(4, item(Material.WRITTEN_BOOK, "&d" + value(meta.getTitle(), "Untitled"), List.of("&7Author: &f" + value(meta.getAuthor(), "Unknown"), "&7Average rating: &e" + plugin.getBookStorageManager().averageRatingText(book))));
        inv.setItem(11, item(Material.EXPERIENCE_BOTTLE, "&eAdd Rating", List.of("&7Type a 1-5 rating in chat.", "&7Average: &e" + plugin.getBookStorageManager().averageRatingText(book))));
        inv.setItem(15, item(Material.OAK_SIGN, "&aAdd Comment", List.of("&7Add a comment for this book.")));
        inv.setItem(17, item(Material.EMERALD_BLOCK, "&aReturn To Library", List.of("&7Stores this book back", "&7into the library.")));
    }

    public String roomEditorTitle() { return color("&5Room Editor"); }
    public void openRoomEditor(Player player) {
        if(!player.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Missing permission infinitylibrary.admin");
        LibraryMenu menu=new LibraryMenu(player,LibraryMenu.Screen.ROOMS,54,roomEditorTitle());Inventory inv=menu.getInventory();
        for(int i=0;i<54;i++)inv.setItem(i,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(10,item(Material.EMERALD_BLOCK,"&aCreate New Room",List.of("&7Select bounds and stage exits, then","&e/il saveroom <id> <type>","&7Types: STARTING, FILLER, BOOK, READ")));
        inv.setItem(12,item(Material.BARRIER,"&cRemove Default Rooms",List.of("&7Choose a custom start/base first.","&7Opens a confirmation; built rooms stay.")));
        inv.setItem(14,item(Material.CHEST,"&eRestore Removed Template",List.of("&e/il restoreroom <id>","&7Archived templates: "+plugin.getRoomManager().archivedIds().size())));
        int slot=19;
        for(var room:plugin.getRoomManager().allRooms()) {
            if(slot>=54)break;menu.entry(slot,room.id());
            inv.setItem(slot++,item(room.type()==com.infinitylibrary.model.RoomType.STARTING?Material.NETHER_STAR:Material.BOOK,"&b"+room.id(),List.of(
                "&7Type: &f"+room.type(),"&7Connections: &f"+room.connections().size(),"&7Prop areas: &f"+room.propAreas().size(),"&7Diversity areas: &f"+room.diversityAreas().size(),
                room.id().equals(plugin.getRoomManager().activeStartId())?"&aActive start for the next reset":"&7Click for room controls",
                plugin.getRoomManager().isDefault(room.id())?"&7Default template":"&7Custom template","&cShift+right click to remove")));
        }
        player.openInventory(inv);
    }
    public String roomDetailsTitle() {return color("&5Room Details");}
    public void openRoomDetails(Player player,String roomId) {
        if(!player.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Missing permission infinitylibrary.admin");
        var room=plugin.getRoomManager().getActive(roomId).orElseThrow(() -> new IllegalArgumentException("Active room not found"));
        selectedRoom.put(player.getUniqueId(),roomId);
        LibraryMenu menu=new LibraryMenu(player,LibraryMenu.Screen.ROOM_DETAILS,27,roomDetailsTitle());menu.entry(0,roomId);Inventory inv=menu.getInventory();
        for(int i=0;i<27;i++)inv.setItem(i,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(4,item(Material.BOOK,"&b"+roomId,List.of("&7Type: "+room.type(),"&7Active start: "+plugin.getRoomManager().activeStartId())));
        inv.setItem(9,item(Material.SPYGLASS,"&bPreview Room",List.of("&7Client-only blocks; the world is unchanged.","&7Rotate, mirror, reroll and inspect doors.")));
        inv.setItem(11,item(Material.WOODEN_AXE,"&eEdit Selected Area",List.of("&7Select bounds and save a new room ID.")));
        inv.setItem(13,item(Material.SLIME_BALL,"&aAdd Variations",List.of("&e/il applyvariations "+roomId)));
        inv.setItem(15,item(Material.BARRIER,"&cRemove Template",List.of("&7Built rooms keep their archived data.")));
        inv.setItem(18,item(Material.NETHER_STAR,"&aUse As STARTING Room",List.of("&7Sets STARTING type and selects it.","&7Takes effect on the next library reset.","&7Never used for random expansion.")));
        inv.setItem(20,item(Material.BRICKS,"&eUse As Expansion Base",List.of("&7Leaves the STARTING choice unchanged.","&7STARTING rooms cannot be a base.")));
        inv.setItem(22,item(Material.ARROW,"&eRoom List",List.of()));
        inv.setItem(24,item(Material.NAME_TAG,"&eChange Room Type",List.of("&e/il roomtype "+roomId+" <type>","&7STARTING, FILLER, READING, RENTABLE or PUBLIC_WRITING")));
        inv.setItem(25,item(Material.PRISMARINE_SHARD,"&dRoom Diversity",List.of("&e/il diversityload "+roomId,"&e/il applydiversity "+roomId,"&7Optional doors: /il diversityconnection")));
        player.openInventory(inv);
    }
    public void roomMenuAction(Player p,LibraryMenu menu,int slot,boolean remove) {
        if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Missing permission infinitylibrary.admin");
        var manager=plugin.getRoomManager();
        if(menu.screen()==LibraryMenu.Screen.ROOMS) {
            if(slot==10){p.closeInventory();p.sendMessage("Select bounds and exits, then /il saveroom <id> <STARTING|FILLER|READING|RENTABLE|PUBLIC_WRITING>. STARTING is selected for the next reset.");return;}
            if(slot==12){openDefaultRemoval(p);return;}
            if(slot==14){p.sendMessage("Restore with /il restoreroom <id>. Archived: "+String.join(", ",manager.archivedIds()));return;}
            String id=menu.entry(slot);if(id==null)return;
            if(remove){manager.delete(id);p.sendMessage("Template removed; built rooms retained. /il restoreroom "+id+" restores it.");openRoomEditor(p);}
            else openRoomDetails(p,id);
        } else if(menu.screen()==LibraryMenu.Screen.ROOM_DETAILS) {
            String id=menu.entry(0);
            switch(slot) {
                case 9 -> plugin.getPreviewManager().start(p,id);
                case 11 -> {p.closeInventory();p.sendMessage("Select bounds/exits, then save with a new room ID. Use STARTING for an entrance template.");}
                case 13 -> p.sendMessage("/il applyvariations "+id);
                case 15 -> {manager.delete(id);openRoomEditor(p);}
                case 18 -> {manager.setRoomType(id,com.infinitylibrary.model.RoomType.STARTING);p.sendMessage("STARTING room selected for the next /il reset confirm. Existing rooms are unchanged.");openRoomDetails(p,id);}
                case 20 -> {manager.chooseBase(id);p.sendMessage("Expansion base selected; the start room is unchanged.");openRoomDetails(p,id);}
                case 22 -> openRoomEditor(p);
                case 25 -> {plugin.getBookStorageManager().returnItem(p,plugin.getDiversityManager().wand());p.sendMessage("Select this authoring room, then /il diversityload "+id+" and /il diversityvariant <area> <variant>, or capture a new target/source pair.");}
                case 24 -> {List<com.infinitylibrary.community.ManagedDialogs.Button> buttons=new java.util.ArrayList<>();for(var type:List.of(com.infinitylibrary.model.RoomType.FILLER,com.infinitylibrary.model.RoomType.READ,com.infinitylibrary.model.RoomType.RENTABLE,com.infinitylibrary.model.RoomType.PUBLIC_WRITING,com.infinitylibrary.model.RoomType.STARTING))buttons.add(new com.infinitylibrary.community.ManagedDialogs.Button(type==com.infinitylibrary.model.RoomType.READ?"READING":type.name(),r->{if(!p.hasPermission("infinitylibrary.admin"))throw new IllegalArgumentException("Builder permission required");manager.setRoomType(id,type);openRoomDetails(p,id);}));buttons.add(new com.infinitylibrary.community.ManagedDialogs.Button("Cancel",r->openRoomDetails(p,id)));plugin.getManagedDialogs().show(p,"Choose room role","Room roles control navigation and rental/writing interactions. Existing template geometry is retained.",List.of(),buttons);}
            }
        } else if(menu.screen()==LibraryMenu.Screen.CONFIRM_DEFAULT_ROOMS) {
            if(slot==11){int count=manager.removeDefaults();p.sendMessage("Removed "+count+" default templates from generation/menus. Built rooms remain.");openRoomEditor(p);}
            else if(slot==15)openRoomEditor(p);
        }
    }
    private void openDefaultRemoval(Player p) {
        LibraryMenu menu=new LibraryMenu(p,LibraryMenu.Screen.CONFIRM_DEFAULT_ROOMS,27,"Remove default templates?");var ui=plugin.getBookWritingManager();Inventory inv=menu.getInventory();
        for(int i=0;i<27;i++)ui.icon(inv,i,Material.GRAY_STAINED_GLASS_PANE," ");
        ui.icon(inv,4,Material.BOOK,"§eDefault template removal","§7Choose a custom STARTING room and base first.","§7Existing buildings are retained.","§7Removed templates can be restored.");
        ui.icon(inv,11,Material.RED_CONCRETE,"§cConfirm removal");ui.icon(inv,15,Material.ARROW,"§eCancel");p.openInventory(inv);
    }
    public String selectedRoomId(Player player) { return selectedRoom.get(player.getUniqueId()); }
    private Material material(String path, Material fallback) { Material m = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name())); return m == null ? fallback : m; }
    private ItemStack item(Material mat, String name, List<String> lore) { ItemStack stack = new ItemStack(mat); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(color(name)); meta.setLore(lore.stream().map(this::color).toList()); stack.setItemMeta(meta); return stack; }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
