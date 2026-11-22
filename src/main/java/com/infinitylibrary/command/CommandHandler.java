package com.infinitylibrary.command;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final InfinityLibraryPlugin plugin;
    public CommandHandler(InfinityLibraryPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length==0) { if(sender instanceof Player p) plugin.getGuiManager().openMainMenu(p); else help(sender); return true; }
        if(args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "rent" -> player(sender,p->plugin.getRentalManager().command(p,args));
                case "builder", "tools" -> adminPlayer(sender,p->{if(args.length<2){plugin.getGuiManager().openBuilderMenu(p);return;}switch(args[1].toLowerCase(Locale.ROOT)){case "undo"->plugin.getAuthoringManager().undo(p,false);case "redo"->plugin.getAuthoringManager().undo(p,true);case "cancel"->plugin.getAuthoringManager().cancel(p);case "apply"->plugin.getAuthoringManager().apply(p);case "clear"->plugin.getAuthoringManager().clear(p);case "load"->{require(args,3,"/il builder load <roomId>");plugin.getAuthoringManager().load(p,args[2]);}default->plugin.getAuthoringManager().activate(p,args[1]);}});
                case "generationmode" -> {requireAdmin(sender);require(args,2,"/il generationmode <district>");plugin.switchGenerationMode(args[1].toLowerCase(Locale.ROOT));msg(sender,"Active mode: "+args[1]+" | World: "+plugin.getGenerationEngine().worldName()+". Use /il home to enter it; existing rooms are retained.");}
                case "shelves" -> {requireAdmin(sender);plugin.getBookStorageManager().requestShelfScan();msg(sender,"Loaded library chunks queued for bookshelf registration and filling.");}
                case "preview" -> adminPlayer(sender,p->{require(args,2,"/il preview <roomId|stop|menu>");if(args[1].equalsIgnoreCase("stop"))plugin.getPreviewManager().stop(p);else if(args[1].equalsIgnoreCase("menu"))plugin.getPreviewManager().menu(p);else plugin.getPreviewManager().start(p,args[1]);});
                case "previewvariant" -> adminPlayer(sender,p->{require(args,3,"/il previewvariant <area> <variant|default>");plugin.getPreviewManager().variant(p,args[1],args[2]);});
                case "notices", "noticeboard" -> player(sender,p->{if(args.length>1){requireAdmin(sender);if(!Set.of("bind","unbind").contains(args[1].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Use /il noticeboard bind|unbind while looking at a block.");var block=p.getTargetBlockExact(6);if(block==null)throw new IllegalArgumentException("Look at the noticeboard block within six blocks.");plugin.getNoticeboardManager().bind(p,block.getLocation(),args[1].equalsIgnoreCase("bind"));msg(p,"Noticeboard binding updated.");}else plugin.getNoticeboardManager().browser(p,0);});
                case "notice" -> player(sender,p->{if(args.length<2)plugin.getNoticeboardManager().browser(p,0);else switch(args[1].toLowerCase(Locale.ROOT)){case "post"->plugin.getNoticeboardManager().compose(p);case "remove"->{require(args,3,"/il notice remove <id>");plugin.getNoticeboardManager().remove(p,UUID.fromString(args[2]));}case "pin","unpin"->{require(args,3,"/il notice pin|unpin <id>");plugin.getNoticeboardManager().pin(p,UUID.fromString(args[2]),args[1].equalsIgnoreCase("pin"));}default->throw new IllegalArgumentException("Use /il notice post|remove|pin|unpin.");}});
                case "read" -> player(sender,p->{if(args.length<2)plugin.getReadingManager().browser(p,0,false);else plugin.getReadingManager().open(p,UUID.fromString(args[1]));});
                case "readheld" -> player(sender,p->{if(!plugin.getReadingManager().openItem(p,p.getInventory().getItemInMainHand()))throw new IllegalArgumentException("Hold a signed book first.");});
                case "reading" -> player(sender,p->plugin.getReadingManager().browser(p,0,true));
                case "collaborate", "collab" -> player(sender,p->{if(args.length<2){plugin.getCollaborationManager().browser(p,0);return;}switch(args[1].toLowerCase(Locale.ROOT)){case "create"->{require(args,3,"/il collaborate create <title>");var d=plugin.getCollaborationManager().create(p,String.join(" ",Arrays.copyOfRange(args,2,args.length)));plugin.getCollaborationManager().open(p,d.id());msg(p,"Shared manuscript ID: "+d.id());}case "open"->{require(args,3,"/il collaborate open <id>");plugin.getCollaborationManager().open(p,UUID.fromString(args[2]));}case "invite"->{require(args,4,"/il collaborate invite <id> <player>");plugin.getCollaborationManager().invite(p,UUID.fromString(args[2]),args[3]);msg(p,"Editor invited.");}case "revoke"->{require(args,4,"/il collaborate revoke <id> <editorUUID>");plugin.getCollaborationManager().revoke(p,UUID.fromString(args[2]),UUID.fromString(args[3]));msg(p,"Editor removed.");}default->throw new IllegalArgumentException("Use /il collaborate create|open|invite|revoke.");}});
                case "navigate", "nav" -> player(sender,p->{if(args.length<2){plugin.getNavigationManager().browser(p);return;}switch(args[1].toLowerCase(Locale.ROOT)){case "favorites"->plugin.getNavigationManager().favorites(p,0);case "here"->plugin.getNavigationManager().currentRoom(p);case "stop"->plugin.getNavigationManager().stop(p);case "book"->{require(args,3,"/il navigate book <bookUUID>");plugin.getNavigationManager().book(p,UUID.fromString(args[2]));}case "rooms"->plugin.getNavigationManager().rooms(p,0);case "room"->{if(args.length<3)plugin.getNavigationManager().rooms(p,0);else plugin.getNavigationManager().room(p,UUID.fromString(args[2]));}case "bookmark"->{require(args,3,"/il navigate bookmark <name>");plugin.getNavigationManager().bookmarkRoute(p,args[2]);}default->plugin.getNavigationManager().destination(p,args[1]);}});
                case "bookmark" -> player(sender,p->{require(args,2,"/il bookmark <name> or /il bookmark remove <name>");if(args[1].equalsIgnoreCase("remove")){require(args,3,"/il bookmark remove <name>");plugin.getNavigationManager().removeBookmark(p,args[2]);}else plugin.getNavigationManager().bookmark(p,args[1]);msg(p,"Bookmarks updated.");});
                case "removevariation" -> {requireAdmin(sender);require(args,3,"/il removevariation <roomId> <legacyVariationId>");plugin.getRoomManager().removeVariation(args[1],args[2]);msg(sender,"Legacy variation removed for future rooms.");}
                case "listvariations" -> {require(args,2,"/il listvariations <roomId>");var room=plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found"));msg(sender,"Legacy variations: "+String.join(", ",room.variations().stream().map(VariationArea::id).toList()));}
                case "diversitywand", "diversity" -> adminPlayer(sender,p -> {plugin.getBookStorageManager().returnItem(p,plugin.getDiversityManager().wand());msg(p,"Select the whole room; Diversity clicks 1–2 mark a target, clicks 3–4 capture its equally sized variation.");});
                case "diversityconnection" -> adminPlayer(sender,p -> {require(args,4,"/il diversityconnection <areaId> <variantId> <direction> [floor] [id]");plugin.getBookStorageManager().returnItem(p,plugin.getDiversityManager().connectionWand(p,args[1],args[2],args[3],args.length>4?Integer.parseInt(args[4]):0,args.length>5?args[5]:"door"));});
                case "removediversityconnection" -> adminPlayer(sender,p -> {require(args,4,"/il removediversityconnection <stagedAreaId> <variantId> <id>");plugin.getDiversityManager().removeConnection(p,args[1],args[2],args[3]);msg(p,"Connection removed from staging. Save/apply Diversity.");});
                case "reopendeadends" -> {requireAdmin(sender);plugin.getGenerationEngine().replanFutureRooms();msg(sender,"Dead-end walls reopened and future plans cleared. Unfillable doors are checked again automatically.");}
                case "diversitymode" -> adminPlayer(sender,p -> {require(args,3,"/il diversitymode <areaId> <variantId> [weight]");plugin.getDiversityManager().mode(p,args[1],args[2],args.length>3?Double.parseDouble(args[3]):1);msg(p,"Diversity settings updated. Select target corners, then equally sized source corners.");});
                case "diversityvariant" -> adminPlayer(sender,p -> {require(args,3,"/il diversityvariant <stagedAreaId> <newVariantId> [weight]");plugin.getDiversityManager().variant(p,args[1],args[2],args.length>3?Double.parseDouble(args[3]):1);});
                case "diversityempty" -> adminPlayer(sender,p -> {require(args,3,"/il diversityempty <stagedAreaId> <variantId> [weight]");plugin.getDiversityManager().empty(p,args[1],args[2],args.length>3?Double.parseDouble(args[3]):1);msg(p,"Empty alternative staged. Apply Diversity or save the room.");});
                case "diversityload" -> adminPlayer(sender,p -> {require(args,2,"/il diversityload <roomId>");plugin.getDiversityManager().load(p,args[1]);msg(p,"Diversity loaded into staging. Add alternatives with /il diversityvariant <areaId> <variantId>.");});
                case "applydiversity" -> adminPlayer(sender,p -> {require(args,2,"/il applydiversity <roomId>");plugin.getDiversityManager().apply(p,args[1]);msg(p,"Room Diversity saved for future room instances.");});
                case "cleardiversity" -> adminPlayer(sender,p -> {plugin.getDiversityManager().clear(p);msg(p,"Diversity staging cleared.");});
                case "listdiversity" -> {require(args,2,"/il listdiversity <roomId>");var room=plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found"));for(var a:room.diversityAreas())msg(sender,a.id()+" | min "+a.min()+" | size "+a.size()+" | original weight "+a.originalWeight()+" | alternatives: "+String.join(", ",a.variants().stream().map(v -> v.id()+" ("+v.weight()+", doors "+v.connections().size()+")").toList()));}
                case "diversitydefault" -> {requireAdmin(sender);require(args,4,"/il diversitydefault <roomId> <areaId> <originalWeight; 0 always alternatives>");plugin.getDiversityManager().originalWeight(args[1],args[2],Double.parseDouble(args[3]));msg(sender,"Original/default choice weight updated for future rooms.");}
                case "removediversity" -> {requireAdmin(sender);require(args,3,"/il removediversity <roomId> <areaId> [variantId]");plugin.getDiversityManager().remove(args[1],args[2],args.length>3?args[3]:null);msg(sender,"Diversity updated for future rooms.");}
                case "decocategory" -> adminPlayer(sender,p -> {require(args,2,"/il decocategory <category|*>");plugin.getPropManager().category(p,args[1]);});
                case "setdecocategory" -> {requireAdmin(sender);require(args,4,"/il setdecocategory <roomId> <areaId> <category|*>");plugin.getPropManager().updateArea(args[1],args[2],args[3],null);msg(sender,"Decoration category updated for future rooms.");}
                case "decofill" -> {requireAdmin(sender);require(args,4,"/il decofill <roomId> <areaId> <exact|packed>");plugin.getPropManager().updateArea(args[1],args[2],null,com.infinitylibrary.props.PropArea.FillMode.valueOf(args[3].toUpperCase(Locale.ROOT)));msg(sender,"Decoration selection mode updated for future rooms.");}
                case "propwand", "decowand" -> adminPlayer(sender,p -> { plugin.getBookStorageManager().returnItem(p,plugin.getPropManager().wand());msg(p,"Select the room bounds, then click two interior corners per prop area."); });
                case "propmode" -> adminPlayer(sender,p -> {
                    require(args,2,"/il propmode <category|*> [gapPercent] [prefix] [rotate|fixed] [floor|stack] [exact|packed]");
                    String orientation=args.length>4?args[4]:"rotate";
                    if(!Set.of("rotate","fixed").contains(orientation.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Orientation must be rotate or fixed.");
                    String fill=args.length>5?args[5]:"floor";if(!Set.of("floor","stack").contains(fill.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Fill mode must be floor or stack.");
                    plugin.getPropManager().mode(p,args[1],args.length>2?Double.parseDouble(args[2]):15,args.length>3?args[3]:"prop",orientation.equalsIgnoreCase("rotate"),fill.equalsIgnoreCase("stack"),args.length>6?com.infinitylibrary.props.PropArea.FillMode.valueOf(args[6].toUpperCase(Locale.ROOT)):com.infinitylibrary.props.PropArea.FillMode.EXACT);msg(p,"Prop area settings updated.");
                });
                case "clearpropareas" -> adminPlayer(sender,p -> {plugin.getPropManager().clear(p);msg(p,"Staged prop areas cleared.");});
                case "saveprop", "savedeco" -> adminPlayer(sender,p -> {require(args,3,"/il saveprop <id> <category> [weight]");var prop=plugin.getPropManager().capture(p,args[1],args[2],args.length>3?Double.parseDouble(args[3]):1);msg(p,"Saved prop "+prop.id()+" | X,Y,Z size "+prop.size());});
                case "props", "decos" -> adminPlayer(sender,p -> plugin.getPropManager().browser(p));
                case "listprops", "listdecos" -> msg(sender,"Props: "+String.join(", ",plugin.getPropManager().all().stream().filter(prop -> args.length<2||prop.category().equals(args[1])).map(prop -> prop.id()+" ["+prop.size()+"]").toList()));
                case "listpropareas" -> {require(args,2,"/il listpropareas <roomId>");var room=plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found"));for(var a:room.propAreas())msg(sender,a.id()+" | "+a.min()+" | size "+a.size()+" | "+a.category()+" | "+a.fillMode()+" | empty/gaps "+a.gapPercent()+"% | "+(a.rotate()?"rotate":"fixed")+" | "+(a.stack()?"stack":"floor"));}
                case "propinfo" -> {require(args,2,"/il propinfo <id>");var prop=plugin.getPropManager().get(args[1]);msg(sender,prop.id()+" | "+prop.category()+" | X,Y,Z size "+prop.size()+" | weight "+prop.weight());}
                case "propweight" -> {requireAdmin(sender);require(args,3,"/il propweight <id> <weight; 0 disables>");plugin.getPropManager().weight(args[1],Double.parseDouble(args[2]));msg(sender,"Prop weight updated for future room decoration.");}
                case "deleteprop" -> {requireAdmin(sender);require(args,2,"/il deleteprop <id>");plugin.getPropManager().delete(args[1]);msg(sender,"Prop removed from future decoration; existing room snapshots are unchanged.");}
                case "applypropareas" -> adminPlayer(sender,p -> {
                    require(args,2,"/il applypropareas <roomId>");var room=plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found"));
                    var bounds=plugin.getRoomManager().selectionBounds(p);
                    if(!bounds.max().subtract(bounds.min()).add(new Vector3i(1,1,1)).equals(room.size()))throw new IllegalArgumentException("Selected room dimensions must match the saved template.");
                    var areas=new ArrayList<>(room.propAreas());var added=plugin.getPropManager().staged(p);if(added.isEmpty())throw new IllegalArgumentException("No staged prop areas.");areas.addAll(added);
                    plugin.getRoomManager().updatePropAreas(room.id(),areas);plugin.getPropManager().clear(p);msg(p,"Prop areas saved for future instances of "+room.id());
                });
                case "removeproparea" -> {requireAdmin(sender);require(args,3,"/il removeproparea <roomId> <areaId>");var room=plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found"));var areas=room.propAreas().stream().filter(a -> !a.id().equals(args[2])).toList();if(areas.size()==room.propAreas().size())throw new IllegalArgumentException("Area not found");plugin.getRoomManager().updatePropAreas(room.id(),areas);msg(sender,"Area removed for future rooms.");}
                case "baseroom" -> {requireAdmin(sender);require(args,2,"/il baseroom <roomId|all>");plugin.getRoomManager().chooseBase(args[1]);msg(sender,"Expansion template updated. The active STARTING room is unchanged.");}
                case "startroom", "setstartroom" -> {requireAdmin(sender);require(args,2,"/il startroom <STARTING-room-id>");plugin.getRoomManager().chooseStart(args[1]);msg(sender,"Active STARTING template: "+args[1]+". Existing buildings and /il home stay where they are until /il reset confirm.");}
                case "roomtype" -> {requireAdmin(sender);require(args,3,"/il roomtype <id> <STARTING|FILLER|READING|RENTABLE|PUBLIC_WRITING>");RoomType type=RoomType.parse(args[2]);plugin.getRoomManager().setRoomType(args[1],type);msg(sender,"Room type updated: "+args[1]+" -> "+type+(type==RoomType.STARTING?". Selected as the entrance for the next reset; excluded from expansion.":"."));}
                case "removedefaults" -> {requireAdmin(sender);require(args,2,"/il removedefaults confirm (archives defaults; choose your custom start/base first)");if(!args[1].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("Use /il removedefaults confirm");int removed=plugin.getRoomManager().removeDefaults();msg(sender,"Removed "+removed+" default templates from future generation and menus. Built rooms remain. /il restoreroom <id> undoes template removal.");}
                case "restoreroom" -> {requireAdmin(sender);require(args,2,"/il restoreroom <id>");plugin.getRoomManager().restore(args[1]);msg(sender,"Template restored: "+args[1]);}
                case "listarchivedrooms" -> {requireAdmin(sender);msg(sender,"Archived templates: "+String.join(", ",plugin.getRoomManager().archivedIds()));}
                case "backup" -> { requireAdmin(sender); msg(sender, "Plugin YAML backup saved: " + com.infinitylibrary.storage.BackupManager.create(plugin).getFileName() + ". World blocks require a separate world backup."); }
                case "status" -> {msg(sender,"Mode: "+"district"+" | Templates: "+plugin.getRoomManager().allRooms().size()+" | Placed rooms: "+plugin.getGenerationEngine().placedCount()+" | Planned rooms: "+plugin.getGenerationEngine().plannedCount()+" | Future-parent plans: "+plugin.getGenerationEngine().futurePlanCount()+" | Queued operations: "+plugin.getGenerationEngine().queuedBlocks()+" | Paused: "+plugin.getGenerationEngine().isPaused());msg(sender,"Automatic: "+plugin.getGenerationEngine().explorationStatus()+" | Base: "+plugin.getConfig().getString("generation.base-room-id","builtin_modular")+" | Eligible: "+plugin.getGenerationEngine().eligibleTemplates()+" | Closed exits: "+plugin.getGenerationEngine().closedExitCount()+" | Open exits: "+plugin.getGenerationEngine().openExitCount());}
                case "diagnose" -> {requireAdmin(sender);player(sender,p -> plugin.getGenerationEngine().diagnose(p).forEach(line -> msg(p,line)));}
                case "pause", "resume" -> { requireAdmin(sender); plugin.getGenerationEngine().setPaused(args[0].equalsIgnoreCase("pause")); msg(sender, "Automatic expansion " + (plugin.getGenerationEngine().isPaused() ? "paused" : "resumed") + ". Queued construction will finish."); }
                case "roominfo" -> { require(args, 2, "/il roominfo <id>"); Room r = plugin.getRoomManager().get(args[1]).orElseThrow(() -> new IllegalArgumentException("Room not found.")); msg(sender, r.id() + " | " + r.type() + " | Size: " + r.size() + " | Diversity areas: " + r.diversityAreas().size() + " | Prop areas: " + r.propAreas().size() + " | Blocks: " + r.blocks().size() + " | Connections: " + r.connections().size() + " | Chance: " + r.spawnChance()); for(ConnectionPoint cp:r.connections())msg(sender,"Exit "+cp.id()+": "+cp.direction()+" | "+cp.width()+"x"+cp.height()+" | floor "+cp.floor()+" | local "+cp.position()); }
                case "search" -> player(sender, p -> { require(args, 2, "/il search <title, author, category or tag>"); plugin.getBookStorageManager().search(p, String.join(" ", Arrays.copyOfRange(args,1,args.length))); });
                case "menu", "gui" -> player(sender, p -> plugin.getGuiManager().openMainMenu(p));
                case "write", "writer" -> player(sender, p -> plugin.getBookWritingManager().open(p));
                case "stats" -> { if (sender instanceof Player p) plugin.getGuiManager().openStats(p); else msg(sender, "Players only."); }
                case "roomgui" -> player(sender, p -> plugin.getGuiManager().openRoomEditor(p));
                case "wand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createWand()); msg(p, "Selection wand added. Use /il wandmode pos1 or /il wandmode pos2, then click blocks while holding it."); });
                case "connectionwand", "connwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createConnectionWand()); msg(p, "Connection wand added. Click 2 points on one outer room face to auto-detect connection direction and size. Optional: /il connectionmode [prefix]"); });
                case "variationwand", "varwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createVariationWand()); msg(p, "Variation wand added. Use /il variationmode <category> <chancePercent> [prefix], then click 2 points."); });
                case "bookshelfcategorywand", "shelfcatwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createBookshelfCategoryWand()); msg(p, "Bookshelf category wand added. Use /il bookshelfcategorymode <category>, then click 2 corners."); });
                case "readingtablewand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createReadingTableWand()); msg(p, "Reading table wand added. Click 2 corners to mark table blocks."); });
                case "readingseatwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createReadingSeatWand()); msg(p, "Reading seat wand added. Click 2 corners to mark seat blocks."); });
                case "wandmode" -> adminPlayer(sender, p -> { require(args, 2, "/il wandmode <pos1|pos2>"); plugin.getSelectionManager().setMode(p, args[1]); });
                case "connectionmode", "connmode" -> adminPlayer(sender, p -> setConnectionMode(p, args));
                case "pos1" -> adminPlayer(sender, p -> { plugin.getRoomManager().setPos1(p); msg(p, "Selection position 1 set."); });
                case "pos2" -> adminPlayer(sender, p -> { plugin.getRoomManager().setPos2(p); msg(p, "Selection position 2 set."); });
                case "clearconnections" -> adminPlayer(sender, p -> { plugin.getSelectionManager().clearStagedConnections(p); msg(p, "Staged connections cleared."); });
                case "clearvariations" -> adminPlayer(sender, p -> { plugin.getSelectionManager().clearStagedVariations(p); msg(p, "Staged variations cleared."); });
                case "variationmode", "varmode" -> adminPlayer(sender, p -> setVariationMode(p, args));
                case "bookshelfcategorymode", "shelfcatmode" -> adminPlayer(sender, p -> { require(args, 2, "/il bookshelfcategorymode <category>"); plugin.getSelectionManager().setBookshelfCategoryMode(p, args[1]); });
                case "addconnection" -> adminPlayer(sender, p -> addConnection(p, args));
                case "saveroom", "editroom", "savedefault" -> adminPlayer(sender, p -> saveRoom(p, args));
                case "applyvariations" -> adminPlayer(sender, p -> applyVariations(p, args));
                case "setrange" -> adminPlayer(sender, p -> setRange(p, args));
                case "genrange" -> adminPlayer(sender, p -> generateRange(p));
                case "toggleblocking" -> adminPlayer(sender, p -> toggleBlocking(p));
                case "setblocker" -> adminPlayer(sender, p -> setBlocker(p, args));
                case "deleteroom" -> { requireAdmin(sender); require(args, 2, "/il deleteroom <id>"); plugin.getRoomManager().delete(args[1]); msg(sender, "Template removed: " + args[1] + ". Existing rooms retain their archived data."); }
                case "listrooms" -> msg(sender, "Rooms: " + String.join(", ", plugin.getRoomManager().allRooms().stream().map(Room::id).toList()));
                case "roomchance" -> adminPlayer(sender, p -> setRoomChance(p, args));
                case "reset" -> { requireAdmin(sender); require(args, 2, "/il reset confirm (removes generated rooms; back up your world first)"); if (!args[1].equalsIgnoreCase("confirm")) throw new IllegalArgumentException("Use /il reset confirm"); com.infinitylibrary.storage.BackupManager.create(plugin); plugin.getGenerationEngine().resetToStart(); msg(sender, "Infinity Library reset to only the starting room."); }
                case "home", "start" -> player(sender, this::teleportStart);
                case "setstart" -> adminPlayer(sender, p -> { plugin.getConfig().set(plugin.getGenerationEngine().startConfigKey()+"x", p.getLocation().getBlockX()); plugin.getConfig().set(plugin.getGenerationEngine().startConfigKey()+"y", p.getLocation().getBlockY()); plugin.getConfig().set(plugin.getGenerationEngine().startConfigKey()+"z", p.getLocation().getBlockZ()); plugin.saveConfig(); msg(p, "Start location updated."); });
                case "book" -> player(sender, p -> bookCommand(p, args));
                case "bookmeta" -> player(sender, p -> bookMetaCommand(p, args));
                case "shelfcategory" -> player(sender, p -> shelfCategoryCommand(p, args));
                case "reload" -> { requireAdmin(sender); plugin.reloadEverything(); msg(sender, "Infinity Library reloaded live."); }
                default -> help(sender);
            }
        } catch (Exception ex) { msg(sender, "&c" + ex.getMessage()); }
        return true;
    }

    private void addConnection(Player p, String[] args) {
        require(args, 6, "/il addconnection <id> <x,y,z> <NORTH|EAST|SOUTH|WEST|UP|DOWN> <width> <height> [floor]");
        int floor = args.length >= 7 ? Integer.parseInt(args[6]) : 0;
        ConnectionPoint cp = new ConnectionPoint(args[1], Vector3i.parse(args[2]), BlockFace.valueOf(args[3].toUpperCase(Locale.ROOT)), Integer.parseInt(args[4]), Integer.parseInt(args[5]), floor);
        plugin.getSelectionManager().addStagedConnection(p, cp);
        msg(p, "Connection staged: " + cp.id());
    }

    private void setConnectionMode(Player p, String[] args) {
        String prefix = args.length >= 2 ? args[1] : "conn";
        plugin.getSelectionManager().setConnectionPrefix(p, prefix);
    }


    private void setVariationMode(Player p, String[] args) {
        require(args, 3, "/il variationmode <category> <chancePercent> [prefix]");
        String prefix = args.length >= 4 ? args[3] : "var";
        plugin.getSelectionManager().setVariationSettings(p, args[1], Double.parseDouble(args[2]), prefix);
        msg(p, "Variation mode set: category=" + args[1] + " chance=" + args[2] + "% prefix=" + prefix);
    }

    private void saveRoom(Player p, String[] args) {
        require(args, 3, "/il saveroom <id> <STARTING|FILLER|READING|RENTABLE|PUBLIC_WRITING>");
        List<ConnectionPoint> cps = plugin.getSelectionManager().stagedConnections(p);
        Room room = plugin.getRoomManager().capture(p, args[1], RoomType.parse(args[2]), cps);
        plugin.getSelectionManager().clearStagedConnections(p);
        plugin.getSelectionManager().clearStagedVariations(p);
        plugin.getPropManager().clear(p);
        plugin.getDiversityManager().clear(p);
        if(room.type()==RoomType.STARTING){msg(p,"STARTING room saved and selected: "+room.id()+". It will be used on the next /il reset confirm; it is not expanded as a normal room.");return;}
        boolean generatedNow = plugin.getGenerationEngine().placeRoomImmediately(room.id());
        msg(p, "Room saved live: " + room.id() + " (" + room.type() + ")." + (generatedNow ? " Spawned instantly into the active library." : " It will be used automatically on future expansions."));
    }

    private void applyVariations(Player p, String[] args) {
        require(args, 2, "/il applyvariations <roomId>");
        List<VariationArea> vars = plugin.getSelectionManager().stagedVariations(p);
        if (vars.isEmpty()) throw new IllegalArgumentException("No staged variations to apply.");
        plugin.getRoomManager().appendVariations(args[1], vars);
        plugin.getSelectionManager().clearStagedVariations(p);
        msg(p, "Applied " + vars.size() + " variation area(s) to room " + args[1] + ".");
    }

    private void setRange(Player p, String[] args) {
        require(args, 2, "/il setrange <1-5>");
        int range = Integer.parseInt(args[1]);
        if (range < 1 || range > 5) throw new IllegalArgumentException("Range must be between 1 and 5");
        plugin.getConfig().set("generation.manual-range", range);
        plugin.saveConfig();
        msg(p, "Manual generation range set to " + range + " room(s).");
    }

    private void generateRange(Player p) {
        int range = plugin.getConfig().getInt("generation.manual-range", 1);
        int generated = plugin.getGenerationEngine().generateAroundPlayer(p, range);
        msg(p, "Generated " + generated + " room(s) around your position.");
    }

    private void toggleBlocking(Player p) {
        boolean enabled = plugin.getConfig().getBoolean("generation.path-blocking.enabled", true);
        plugin.getConfig().set("generation.path-blocking.enabled", !enabled);
        plugin.saveConfig();
        msg(p, "Path blocking is now " + (!enabled ? "enabled" : "disabled") + ".");
    }

    private void setBlocker(Player p, String[] args) {
        require(args, 2, "/il setblocker <material>");
        Material m = Material.matchMaterial(args[1]);
        if (m == null || !m.isBlock()) throw new IllegalArgumentException("Invalid block material");
        plugin.getConfig().set("generation.path-blocking.material", m.name());
        plugin.saveConfig();
        msg(p, "Path blocker material set to " + m.name() + ".");
    }


    private void setRoomChance(Player p, String[] args) {
        require(args, 3, "/il roomchance <roomId> <0-100>");
        double chance = Double.parseDouble(args[2]);
        if (!Double.isFinite(chance) || chance < 0 || chance > 100) throw new IllegalArgumentException("Chance must be 0-100");
        plugin.getRoomManager().setRoomChance(args[1], chance);
        msg(p, "Room chance set: " + args[1] + " => " + chance + "%");
    }

    private void teleportStart(Player player) {
        player.teleport(plugin.getGenerationEngine().homeLocation());
        msg(player, "Returned to the Infinity Library starting room.");
    }

    private void bookCommand(Player player, String[] args) {
        require(args, 2, "/il book <public|private|edit>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "public" -> { plugin.getBookStorageManager().setHeldBookPublic(player, true); msg(player, "Held library book is now public."); }
            case "private" -> { plugin.getBookStorageManager().setHeldBookPublic(player, false); msg(player, "Held library book is now private."); }
            case "edit" -> { plugin.getBookStorageManager().convertHeldWrittenBookToEditable(player); msg(player, "Converted your linked written book back into an editable writable book."); }
            default -> throw new IllegalArgumentException("/il book <public|private|edit>");
        }
    }


    private void shelfCategoryCommand(Player player, String[] args) {
        require(args, 2, "/il shelfcategory <category>");
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != Material.CHISELED_BOOKSHELF) throw new IllegalArgumentException("Look at a chiseled bookshelf within 6 blocks.");
        plugin.getBookStorageManager().setShelfCategory(target.getLocation(), args[1]);
        msg(player, "Shelf category set to " + args[1] + ".");
    }

    private void bookMetaCommand(Player player, String[] args) {
        require(args, 5, "/il bookmeta <category> <rating> <tags_csv> <comments>");
        plugin.getBookStorageManager().setHeldBookMetadata(player, args[1], args[3], args[2], String.join(" ", Arrays.copyOfRange(args,4,args.length)));
        msg(player, "Book metadata saved (category/rating/tags/comments).");
    }

    private void adminPlayer(CommandSender s, PlayerAction action) { requireAdmin(s); player(s, action); }
    private void player(CommandSender s, PlayerAction action) { if (!(s instanceof Player p)) throw new IllegalArgumentException("Players only."); action.run(p); }
    private void requireAdmin(CommandSender s) { if (!s.hasPermission("infinitylibrary.admin")) throw new IllegalArgumentException("Missing permission infinitylibrary.admin"); }
    private void require(String[] args, int n, String usage) { if (args.length < n) throw new IllegalArgumentException(usage); }
    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&5[InfinityLibrary] &f" + m)); }
    private void help(CommandSender s) { msg(s, "Commands: /il rent, builder, tools, generationmode, shelves, preview, notices, reading, read, readheld, collaborate, navigate, bookmark, diversityconnection, removediversityconnection, reopendeadends, diagnose, removevariation, listvariations, diversitywand, diversitymode, diversityvariant, diversityempty, diversityload, applydiversity, listdiversity, diversitydefault, removediversity, cleardiversity, decowand, savedeco, decocategory, setdecocategory, decofill, startroom, roomtype, removedefaults confirm, restoreroom, listarchivedrooms, props, propwand, propmode, saveprop, listprops, listpropareas, propinfo, propweight, deleteprop, applypropareas, removeproparea, clearpropareas, baseroom, menu, write, stats, backup, status, search <query>, roominfo <id>, pause, resume, gui, roomgui, home, wand, connectionwand, variationwand, bookshelfcategorywand, readingtablewand, readingseatwand, wandmode, connectionmode, variationmode, bookshelfcategorymode, pos1, pos2, addconnection, clearconnections, clearvariations, saveroom, applyvariations, savedefault, editroom, deleteroom, listrooms, setrange, genrange, toggleblocking, setblocker, reset confirm, setstart, book, bookmeta, shelfcategory, roomchance, reload"); }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("rent","builder","tools","generationmode","shelves","preview","previewvariant","notices","noticeboard","notice","read","readheld","reading","collaborate","collab","navigate","nav","bookmark","diversityconnection","removediversityconnection","reopendeadends","diagnose","removevariation","listvariations","diversity","diversitywand","diversitymode","diversityvariant","diversityempty","diversityload","applydiversity","listdiversity","diversitydefault","removediversity","cleardiversity","decowand","savedeco","decos","listdecos","decocategory","setdecocategory","decofill","startroom","setstartroom","roomtype","removedefaults","restoreroom","listarchivedrooms","propwand","propmode","clearpropareas","saveprop","props","listprops","listpropareas","propinfo","propweight","deleteprop","applypropareas","removeproparea","baseroom","menu","write","writer","backup","status","pause","resume","roominfo","search","gui","roomgui","stats","home","start","wand","connectionwand","connwand","variationwand","varwand","bookshelfcategorywand","shelfcatwand","readingtablewand","readingseatwand","wandmode","connectionmode","connmode","variationmode","varmode","bookshelfcategorymode","shelfcatmode","pos1","pos2","addconnection","clearconnections","clearvariations","saveroom","applyvariations","savedefault","editroom","deleteroom","listrooms","setrange","genrange","toggleblocking","setblocker","reset","setstart","book","bookmeta","shelfcategory","roomchance","reload").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("wandmode")) return List.of("pos1", "pos2");
        if (args.length == 2 && (args[0].equalsIgnoreCase("connectionmode") || args[0].equalsIgnoreCase("connmode"))) return List.of("conn");
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) return List.of("public", "private", "edit");
        if (args.length == 3 && (args[0].equalsIgnoreCase("saveroom") || args[0].equalsIgnoreCase("editroom") || args[0].equalsIgnoreCase("savedefault"))) return Arrays.stream(RoomType.values()).map(Enum::name).filter(t -> t.startsWith(args[2].toUpperCase(Locale.ROOT))).toList();
        if(args.length==2&&Set.of("listvariations","removevariation","diversityload","applydiversity","listdiversity","diversitydefault","removediversity","setdecocategory","decofill").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getRoomManager().allRooms().stream().map(Room::id).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==3&&Set.of("diversitydefault","removediversity").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getRoomManager().getActive(args[1]).map(r -> r.diversityAreas().stream().map(com.infinitylibrary.diversity.DiversityArea::id).filter(id -> id.startsWith(args[2])).toList()).orElse(List.of());
        if(args.length==3&&Set.of("setdecocategory","decofill").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getRoomManager().getActive(args[1]).map(r -> r.propAreas().stream().map(com.infinitylibrary.props.PropArea::id).filter(id -> id.startsWith(args[2])).toList()).orElse(List.of());
        if(args.length==4&&args[0].equalsIgnoreCase("decofill"))return List.of("exact","packed");
        if(args.length==2 && Set.of("startroom","setstartroom").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getRoomManager().allRooms().stream().filter(r -> r.type()==RoomType.STARTING).map(Room::id).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==2 && args[0].equalsIgnoreCase("roomtype"))return plugin.getRoomManager().allRooms().stream().map(Room::id).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("roomtype"))return Arrays.stream(RoomType.values()).map(Enum::name).filter(t -> t.startsWith(args[2].toUpperCase(Locale.ROOT))).toList();
        if(args.length==2 && args[0].equalsIgnoreCase("restoreroom"))return plugin.getRoomManager().archivedIds().stream().filter(id -> id.startsWith(args[1])).toList();
        if(args.length==2 && args[0].equalsIgnoreCase("removedefaults"))return List.of("confirm");
        if(args.length==2 && args[0].equalsIgnoreCase("baseroom"))return java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),plugin.getRoomManager().allRooms().stream().filter(r -> r.type()!=RoomType.STARTING).map(Room::id)).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==2 && Set.of("propinfo","propweight","deleteprop").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getPropManager().all().stream().map(com.infinitylibrary.props.Prop::id).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==2 && Set.of("baseroom","applypropareas","removeproparea","listpropareas").contains(args[0].toLowerCase(Locale.ROOT)))return plugin.getRoomManager().allRooms().stream().map(Room::id).filter(id -> id.startsWith(args[1])).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("removeproparea"))return plugin.getRoomManager().get(args[1]).map(room -> room.propAreas().stream().map(com.infinitylibrary.props.PropArea::id).filter(id -> id.startsWith(args[2])).toList()).orElse(List.of());
        if(args.length==2&&args[0].equalsIgnoreCase("generationmode"))return List.of("district").stream().filter(x->x.startsWith(args[1])).toList();
        if(args.length==2&&Set.of("navigate","nav").contains(args[0].toLowerCase(Locale.ROOT)))return List.of("entrance","reading","writing","rentable","books","rooms","favorites","here","room","book","bookmark","stop").stream().filter(x->x.startsWith(args[1])).toList();
        if(args.length==2&&Set.of("collaborate","collab").contains(args[0].toLowerCase(Locale.ROOT)))return List.of("create","open","invite","revoke").stream().filter(x->x.startsWith(args[1])).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("preview"))return java.util.stream.Stream.concat(java.util.stream.Stream.of("stop","menu"),plugin.getRoomManager().allRooms().stream().map(Room::id)).filter(x->x.startsWith(args[1])).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("notice"))return List.of("post","remove","pin","unpin").stream().filter(x->x.startsWith(args[1])).toList();
        if(args.length==4&&args[0].equalsIgnoreCase("diversityconnection"))return List.of("NORTH","EAST","SOUTH","WEST","UP","DOWN").stream().filter(x -> x.startsWith(args[3].toUpperCase(Locale.ROOT))).toList();
        if (args.length == 2 && Set.of("roominfo", "deleteroom", "roomchance", "applyvariations").contains(args[0].toLowerCase(Locale.ROOT))) return plugin.getRoomManager().allRooms().stream().map(Room::id).filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
    private interface PlayerAction { void run(Player player); }
}
