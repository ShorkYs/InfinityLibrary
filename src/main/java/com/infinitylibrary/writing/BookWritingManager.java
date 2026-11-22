package com.infinitylibrary.writing;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.*;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public final class BookWritingManager {
    private final InfinityLibraryPlugin plugin;
    private final Map<UUID,BookDraft> drafts = new HashMap<>();
    private final Map<UUID,UUID> dialogTokens = new HashMap<>();
    private final Path file;
    public BookWritingManager(InfinityLibraryPlugin plugin) {
        this.plugin=plugin; this.file=plugin.getDataFolder().toPath().resolve("drafts.yml");
    }
    public void load() {
        Map<UUID,BookDraft> next=new HashMap<>();
        YamlConfiguration y=com.infinitylibrary.community.AtomicYaml.read(file);
        var root=y.getConfigurationSection("drafts");
        if(root==null) {drafts.clear();return;}
        for(String id:root.getKeys(false)) try {
            var s=root.getConfigurationSection(id); BookDraft d=new BookDraft();
            d.details(s.getString("title",""),s.getString("category","general"),s.getString("tags",""),s.getBoolean("public"));
            d.replacePages(s.getStringList("pages")); d.move(s.getInt("page",0)); next.put(UUID.fromString(id),d);
        } catch(Exception e) { throw new IllegalStateException("Cannot read manuscript "+id+"; restore drafts.yml from backup",e); }
        drafts.clear();drafts.putAll(next);
    }
    public BookDraft draft(UUID id) { return drafts.computeIfAbsent(id,k -> new BookDraft()); }
    public void save() {
        YamlConfiguration y=new YamlConfiguration();
        for(var e:drafts.entrySet()) {
            String root="drafts."+e.getKey()+"."; BookDraft d=e.getValue();
            y.set(root+"title",d.title()); y.set(root+"category",d.category()); y.set(root+"tags",d.tags());
            y.set(root+"public",d.publicBook()); y.set(root+"pages",d.pages()); y.set(root+"page",d.current());
        }
        Path temp=file.resolveSibling("drafts.yml.tmp");
        try {
            Files.writeString(temp,y.saveToString());
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e) { throw new IllegalStateException("Unable to save manuscript. Try again before disconnecting.",e); }
    }
    public void invalidate(Player player) { dialogTokens.remove(player.getUniqueId()); }
    public void open(Player player) {
        if(plugin.getNativeBookEditor()!=null)plugin.getNativeBookEditor().cleanup(player);
        invalidate(player); BookDraft d=draft(player.getUniqueId());
        LibraryMenu menu=new LibraryMenu(player,LibraryMenu.Screen.WRITER,54,"Library • Writing Desk");
        Inventory inv=menu.getInventory(); fill(inv);
        icon(inv,4,Material.WRITABLE_BOOK,"§d"+(d.title().isBlank()?"Your manuscript":d.title()),"§7By "+player.getName(),"§7"+(d.publicBook()?"Public":"Private")+" · "+d.category());
        icon(inv,11,Material.NAME_TAG,"§bBook details","§7Title, category, tags and visibility");
        icon(inv,13,Material.FEATHER,"§aWrite in Book & Quill","§7Temporary native book editor","§7Right-click it, then Done to save");
        icon(inv,15,Material.WRITABLE_BOOK,"§bImport held book & quill","§7Copies its pages into this manuscript","§eOnly available while the manuscript is empty");
        icon(inv,20,Material.ARROW,"§ePrevious page");
        String preview=d.page().replace('\n',' '); if(preview.length()>60) preview=preview.substring(0,60)+"…";
        icon(inv,22,Material.PAPER,"§fPage "+(d.current()+1)+" / "+d.pages().size(),"§7"+(preview.isBlank()?"Empty page":preview),"§7"+d.page().length()+" / 1,024 characters");
        icon(inv,24,Material.ARROW,"§eNext page");
        icon(inv,29,Material.LIME_DYE,"§aInsert a new page","§7After the current page · maximum 100");
        icon(inv,31,Material.RED_DYE,"§cDelete this page","§7Opens a confirmation");
        icon(inv,33,Material.CLOCK,"§aSaved manuscript","§7Text is saved when you press Save","§7Resume after logout or server restart");
        icon(inv,35,Material.INK_SAC,"Bold page","Wrap current page in **bold** markup");
        icon(inv,37,Material.FEATHER,"Italic page","Wrap current page in _italic_ markup");
        icon(inv,39,Material.PAPER,"Insert divider","Append a divider to this page");
        icon(inv,41,Material.NAME_TAG,"Heading","Prefix this page with a heading marker");
        icon(inv,45,Material.CHEST,"§eMain menu");
        icon(inv,47,Material.BOOK,"§bPreview book","§7Read without spending a book & quill");
        icon(inv,51,Material.EMERALD,"§aFinish book","§7Sign a copy or publish to the library","§7Costs one blank book & quill");
        player.openInventory(inv);
    }
    public void handle(Player p,LibraryMenu.Screen screen,int slot) {
        BookDraft d=draft(p.getUniqueId());
        if(screen==LibraryMenu.Screen.WRITER) switch(slot) {
            case 11 -> detailsDialog(p);
            case 13 -> pageDialog(p);
            case 15 -> {
                if(!d.title().isBlank() || d.pages().stream().anyMatch(s -> !s.isBlank())) throw new IllegalArgumentException("Import needs an empty manuscript, so your existing text is preserved.");
                ItemStack held=p.getInventory().getItemInMainHand();
                if(held.getType()!=Material.WRITABLE_BOOK || !(held.getItemMeta() instanceof BookMeta m)) throw new IllegalArgumentException("Hold a book & quill first.");
                String owner=m.getPersistentDataContainer().get(new NamespacedKey(plugin,"book_owner_uuid"),PersistentDataType.STRING);
                if(owner!=null&&!owner.equals(p.getUniqueId().toString())) throw new IllegalArgumentException("Only its owner can import this draft.");
                d.replacePages(m.getPages()); save(); open(p);
            }
            case 20 -> { d.move(-1); save(); open(p); }
            case 24 -> { d.move(1); save(); open(p); }
            case 29 -> { d.addPage(); save(); open(p); }
            case 31 -> confirmation(p,true);
            case 35,37,39,41 -> {String old=d.page();String operation=slot==35?"bold":slot==37?"italic":slot==39?"divider":"heading";d.page(BookTypography.apply(old,operation));try{save();}catch(RuntimeException ex){d.page(old);throw ex;}open(p);}
            case 45 -> plugin.getGuiManager().openMainMenu(p);
            case 47 -> { p.closeInventory(); p.openBook(book(p,d,false)); }
            case 51 -> { d.validatePublish(); confirmation(p,false); }
        }
        else if(screen==LibraryMenu.Screen.CONFIRM_DELETE) {
            if(slot==11) { d.removePage(); save(); open(p); } else if(slot==15) open(p);
        } else if(screen==LibraryMenu.Screen.CONFIRM_PUBLISH) {
            if(slot==11) finish(p,false); else if(slot==15) finish(p,true); else if(slot==22) open(p);
        }
    }
    private void confirmation(Player p,boolean delete) {
        LibraryMenu menu=new LibraryMenu(p,delete?LibraryMenu.Screen.CONFIRM_DELETE:LibraryMenu.Screen.CONFIRM_PUBLISH,27,delete?"Delete this page?":"Finish your manuscript");
        Inventory inv=menu.getInventory(); fill(inv);
        if(delete) {
            icon(inv,11,Material.RED_CONCRETE,"§cDelete current page","§7This removes its saved text");
            icon(inv,15,Material.ARROW,"§eKeep page");
        } else {
            icon(inv,11,Material.WRITTEN_BOOK,"§aSign a copy","§7Receive a written book in your inventory","§7Costs one blank book & quill");
            icon(inv,15,Material.CHISELED_BOOKSHELF,"§aPublish to library","§7Save to the searchable book collection","§7Costs one blank book & quill; manuscript stays saved");
            icon(inv,22,Material.ARROW,"§eBack to writing");
        }
        p.openInventory(inv);
    }
    public ItemStack book(Player p,BookDraft d,boolean signed) {
        if(signed) d.validatePublish();
        ItemStack item=new ItemStack(Material.WRITTEN_BOOK); BookMeta m=(BookMeta)item.getItemMeta();
        m.setTitle(d.title().isBlank()?"Manuscript preview":d.title()); m.setAuthor(p.getName()); m.setPages(d.pages().stream().map(BookTypography::render).toList());
        var data=m.getPersistentDataContainer();
        data.set(new NamespacedKey(plugin,"book_owner_uuid"),PersistentDataType.STRING,p.getUniqueId().toString());
        data.set(new NamespacedKey(plugin,"book_owner_name"),PersistentDataType.STRING,p.getName());
        data.set(new NamespacedKey(plugin,"book_public"),PersistentDataType.BYTE,(byte)(d.publicBook()?1:0));
        data.set(new NamespacedKey(plugin,"book_category"),PersistentDataType.STRING,d.category());
        data.set(new NamespacedKey(plugin,"book_tags"),PersistentDataType.STRING,d.tags()); item.setItemMeta(m); return item;
    }
    private void finish(Player p,boolean publish) {
        BookDraft d=draft(p.getUniqueId()); ItemStack result=book(p,d,true);
        deliver(p,result,publish);plugin.getGuiManager().openMainMenu(p);
    }
    /** Shared books use the same durable publication/payment checks as personal manuscripts. */
    public void deliver(Player p,ItemStack result,boolean publish) {
        int payment=-1;
        for(int i=0;i<p.getInventory().getStorageContents().length;i++) {
            ItemStack item=p.getInventory().getItem(i);
            if(item==null||item.getType()!=Material.WRITABLE_BOOK||plugin.getNativeBookEditor().temporary(item)) continue;
            if(item.getItemMeta() instanceof BookMeta m && m.getPages().stream().anyMatch(text -> !text.isBlank())) continue;
            String owner=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"book_owner_uuid"),PersistentDataType.STRING);
            if(owner==null||owner.equals(p.getUniqueId().toString())) { payment=i; break; }
        }
        if(payment<0) throw new IllegalArgumentException("You need one blank book & quill. Claim your daily book from the main menu.");
        ItemStack blank=p.getInventory().getItem(payment);
        if(!publish && blank.getAmount()>1 && p.getInventory().firstEmpty()<0) throw new IllegalArgumentException("Make one empty inventory slot before signing.");
        save();
        if(publish) plugin.getBookStorageManager().publishManuscript(p,result);
        if(blank.getAmount()==1) p.getInventory().setItem(payment,null); else blank.setAmount(blank.getAmount()-1);
        if(!publish) plugin.getBookStorageManager().returnItem(p,result);
        p.sendMessage(Component.text(publish?"Book published. Your manuscript is still saved.":"Signed copy added to your inventory. Your manuscript is still saved."));
    }
    public void pageDialog(Player p) {plugin.getNativeBookEditor().personal(p);}
    public void detailsDialog(Player p) {
        BookDraft d=draft(p.getUniqueId());
        showDialog(p,"Book details",List.of(
            DialogInput.text("title",Component.text("Title (32 characters)")).initial(d.title()).maxLength(32).width(300).build(),
            DialogInput.text("category",Component.text("Category")).initial(d.category()).maxLength(64).width(300).build(),
            DialogInput.text("tags",Component.text("Tags (comma separated)")).initial(d.tags()).maxLength(256).width(300).build(),
            DialogInput.bool("public",Component.text("Public book"),d.publicBook(),"true","false")),r -> {
                d.details(r.getText("title"),r.getText("category"),r.getText("tags"),Boolean.TRUE.equals(r.getBoolean("public"))); save(); open(p);
            });
    }
    public void searchDialog(Player p) {
        showDialog(p,"Search the library",List.of(DialogInput.text("query",Component.text("Title, author, category or tag")).maxLength(120).width(300).build()),r -> {
            plugin.getBookStorageManager().search(p,Objects.requireNonNullElse(r.getText("query"),"")); plugin.getGuiManager().openMainMenu(p);
        });
    }
    private void showDialog(Player p,String title,List<DialogInput> inputs,Consumer<DialogResponseView> onSave) {
        UUID id=p.getUniqueId(),token=UUID.randomUUID(); dialogTokens.put(id,token); p.closeInventory();
        ActionButton save=ActionButton.create(Component.text("Save"),null,140,DialogAction.customClick((response,audience) -> {
            if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(id)) return;
            Bukkit.getScheduler().runTask(plugin,() -> {
                if(!p.isOnline()||!p.hasPermission("infinitylibrary.command")||!token.equals(dialogTokens.get(id))) return;
                dialogTokens.remove(id);
                try { onSave.accept(response); } catch(RuntimeException e) { p.sendMessage(Component.text(e.getMessage())); open(p); }
            });
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
        ActionButton cancel=ActionButton.create(Component.text("Cancel"),null,140,DialogAction.customClick((r,audience) -> {
            if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(id)) return;
            Bukkit.getScheduler().runTask(plugin,() -> { if(p.isOnline()&&token.equals(dialogTokens.get(id))) { invalidate(p); open(p); } });
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
        Dialog dialog=Dialog.create(factory -> factory.empty().base(DialogBase.builder(Component.text(title))
            .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).inputs(inputs).build())
            .type(DialogType.confirmation(save,cancel)));
        p.showDialog(dialog);
    }
    private void fill(Inventory inv) { for(int i=0;i<inv.getSize();i++) icon(inv,i,Material.BLACK_STAINED_GLASS_PANE," "); }
    public void icon(Inventory inv,int slot,Material material,String name,String... lore) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); inv.setItem(slot,item);
    }
}
