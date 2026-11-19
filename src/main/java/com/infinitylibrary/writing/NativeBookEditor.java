package com.infinitylibrary.writing;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.community.SharedManuscript;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Real writable book sessions use empty hotbar slots; no real inventory item is displaced. */
public final class NativeBookEditor implements Listener {
    private record Stamp(String title,String category,String tags,boolean visibility,List<String> pages){static Stamp of(BookDraft d){return new Stamp(d.title(),d.category(),d.tags(),d.publicBook(),d.pages());}}
    private record Session(UUID token,int slot,int previousSlot,UUID shared,long revision,Stamp stamp){}
    private final InfinityLibraryPlugin plugin;private final NamespacedKey key;private final Map<UUID,Session> sessions=new HashMap<>();
    public NativeBookEditor(InfinityLibraryPlugin p){plugin=p;key=new NamespacedKey(p,"native_editor_session");}
    public boolean temporary(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.STRING);}
    public void personal(Player p){begin(p,null);}
    public void shared(Player p,UUID id){begin(p,id);}
    private void begin(Player p,UUID shared){if(!p.hasPermission("infinitylibrary.command"))throw new IllegalArgumentException("Missing library permission.");cleanup(p);int slot=-1;for(int i=0;i<9;i++){var item=p.getInventory().getItem(i);if(item==null||item.getType().isAir()){slot=i;break;}}if(slot<0)throw new IllegalArgumentException("Make one empty hotbar slot for the temporary Book & Quill.");BookDraft draft;long revision=0;if(shared==null)draft=plugin.getBookWritingManager().draft(p.getUniqueId());else{var d=plugin.getCollaborationManager().get(p,shared);draft=d.draft();revision=d.revision();}Session s=new Session(UUID.randomUUID(),slot,p.getInventory().getHeldItemSlot(),shared,revision,Stamp.of(draft));ItemStack item=new ItemStack(Material.WRITABLE_BOOK);BookMeta meta=(BookMeta)item.getItemMeta();meta.setPages(draft.pages());meta.setDisplayName("§dLibrary writing session");meta.setLore(List.of("§7Right-click to write. Done saves your manuscript.","§7Temporary editor; use the desk to sign/publish."));meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,s.token().toString());item.setItemMeta(meta);sessions.put(p.getUniqueId(),s);plugin.getManagedDialogs().invalidate(p);plugin.getBookWritingManager().invalidate(p);p.closeInventory();p.getInventory().setItem(slot,item);p.getInventory().setHeldItemSlot(slot);p.sendMessage("Right-click the temporary Book & Quill to write, then press Done. Pages are saved to your manuscript; signing/publishing remains in the writing desk.");}
    public void cleanup(Player p){Session s=sessions.remove(p.getUniqueId());for(int i=0;i<p.getInventory().getSize();i++)if(temporary(p.getInventory().getItem(i)))p.getInventory().setItem(i,null);if(s!=null&&p.getInventory().getHeldItemSlot()==s.slot())p.getInventory().setHeldItemSlot(s.previousSlot());}
    public void cleanupAll(){for(Player p:Bukkit.getOnlinePlayers())cleanup(p);sessions.clear();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void edit(PlayerEditBookEvent event){Player p=event.getPlayer();Session s=sessions.get(p.getUniqueId());var held=p.getInventory().getItem(event.getSlot());if(!temporary(held)&&!event.getPreviousBookMeta().getPersistentDataContainer().has(key,PersistentDataType.STRING))return;event.setCancelled(true);if(s==null||event.getSlot()!=s.slot()||!temporary(held)||!s.token().toString().equals(held.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING))||!p.hasPermission("infinitylibrary.command")){cleanup(p);p.sendMessage("This writing session expired. Reopen your manuscript.");return;}List<String> pages=List.copyOf(event.getNewBookMeta().getPages());BookDraft check=new BookDraft();try{check.replacePages(pages);if(s.shared()==null){BookDraft d=plugin.getBookWritingManager().draft(p.getUniqueId());if(!s.stamp().equals(Stamp.of(d)))throw new IllegalArgumentException("The manuscript changed while this book was open. Your submitted text remains in the temporary book; copy it before reopening the current manuscript.");var old=d.pages();int cursor=d.current();d.replacePages(pages);try{plugin.getBookWritingManager().save();}catch(RuntimeException ex){d.replacePages(old);d.move(cursor);throw ex;}}else plugin.getCollaborationManager().update(p,s.shared(),d->d.pages(p.getUniqueId(),s.revision(),pages));
            UUID shared=s.shared();Bukkit.getScheduler().runTask(plugin,()->{cleanup(p);if(p.isOnline()){p.sendMessage("Manuscript pages saved. Use Finish in the desk to sign or publish.");if(shared==null)plugin.getBookWritingManager().open(p);else try{plugin.getCollaborationManager().open(p,shared);}catch(IllegalArgumentException ex){p.sendMessage(ex.getMessage());}}});
        }catch(RuntimeException ex){p.sendMessage(ex.getMessage());BookMeta recovery=(BookMeta)held.getItemMeta();recovery.setPages(pages);held.setItemMeta(recovery);p.getInventory().setItem(s.slot(),held);}}
    @EventHandler(ignoreCancelled=true)public void click(InventoryClickEvent e){if(temporary(e.getCurrentItem())||temporary(e.getCursor())||(e.getHotbarButton()>=0&&temporary(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void drag(InventoryDragEvent e){if(temporary(e.getOldCursor()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void drop(PlayerDropItemEvent e){if(temporary(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent e){if(temporary(e.getMainHandItem())||temporary(e.getOffHandItem()))e.setCancelled(true);}
    @EventHandler public void death(PlayerDeathEvent e){e.getDrops().removeIf(this::temporary);cleanup(e.getEntity());}
    @EventHandler public void join(PlayerJoinEvent e){cleanup(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){cleanup(e.getPlayer());}
}
