package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.gui.LibraryMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    public GUIListener(InfinityLibraryPlugin plugin) { this.plugin = plugin; }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof LibraryMenu menu) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p) || !menu.owner().equals(p.getUniqueId()) || e.getRawSlot()<0 || e.getRawSlot()>=menu.getInventory().getSize()) return;
            if(e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT && !(menu.screen()==LibraryMenu.Screen.ROOMS&&e.getClick()==ClickType.SHIFT_RIGHT)) return;
            int slot=e.getRawSlot();
            plugin.getServer().getScheduler().runTask(plugin,() -> {
                if(!p.isOnline() || !p.hasPermission("infinitylibrary.command") || p.getOpenInventory().getTopInventory().getHolder()!=menu) return;
                try {
                    if(java.util.Set.of(LibraryMenu.Screen.RENTALS,LibraryMenu.Screen.RENTAL_ROOM,LibraryMenu.Screen.RENTAL_BOOKS,LibraryMenu.Screen.RENTAL_BOOK,LibraryMenu.Screen.RENTAL_NOTES).contains(menu.screen()))plugin.getRentalVaultManager().handle(p,menu,slot);
                    else if(menu.screen()==LibraryMenu.Screen.BUILDER_HOME)plugin.getGuiManager().builderMenuAction(p,slot);
                    else if(menu.screen()==LibraryMenu.Screen.BUILDER_TOOLS)plugin.getGuiManager().builderAction(p,menu,slot,e.getClick()==ClickType.RIGHT);
                    else if(menu.screen()==LibraryMenu.Screen.MAIN) plugin.getGuiManager().mainMenuAction(p,slot);
                    else if(java.util.Set.of(LibraryMenu.Screen.ROOMS,LibraryMenu.Screen.ROOM_DETAILS,LibraryMenu.Screen.CONFIRM_DEFAULT_ROOMS).contains(menu.screen())) plugin.getGuiManager().roomMenuAction(p,menu,slot,e.getClick()==ClickType.SHIFT_RIGHT);
                    else if(menu.screen()==LibraryMenu.Screen.PROPS) plugin.getPropManager().menuAction(p,slot);
                    else if(menu.screen()==LibraryMenu.Screen.NOTICEBOARD)plugin.getNoticeboardManager().handle(p,menu,slot);
                    else if(menu.screen()==LibraryMenu.Screen.READ_CATALOG||menu.screen()==LibraryMenu.Screen.READING_HISTORY)plugin.getReadingManager().handle(p,menu,slot,e.getClick()==ClickType.RIGHT);
                    else if(menu.screen()==LibraryMenu.Screen.NAVIGATION||menu.screen()==LibraryMenu.Screen.NAV_ROOMS)plugin.getNavigationManager().handle(p,menu,slot,e.getClick()==ClickType.RIGHT);
                    else if(menu.screen()==LibraryMenu.Screen.PREVIEW)plugin.getPreviewManager().handle(p,slot);
                    else if(java.util.Set.of(LibraryMenu.Screen.COLLAB_LIST,LibraryMenu.Screen.COLLAB_EDIT,LibraryMenu.Screen.COLLAB_PUBLISH).contains(menu.screen()))plugin.getCollaborationManager().handle(p,menu,slot);
                    else plugin.getBookWritingManager().handle(p,menu.screen(),slot);
                } catch(RuntimeException ex) { p.sendMessage(ChatColor.RED+ex.getMessage()); }
            });
            return;
        }
        String title = e.getView().getTitle();
        if (title.equals(plugin.getGuiManager().statsTitle())) {
            e.setCancelled(true);
            if(e.getRawSlot()==22 && e.getWhoClicked() instanceof Player p) plugin.getServer().getScheduler().runTask(plugin,() -> plugin.getGuiManager().openMainMenu(p));
            return;
        }
        if (title.equals(plugin.getGuiManager().lecternBookEditorTitle())) {
            handleLecternBookEditorClick(e);
            return;
        }
        if (title.equals(plugin.getGuiManager().bookshelfReturnTitle())) {
            handleBookshelfReturnClick(e);
            return;
        }
        if (!title.equals(plugin.getGuiManager().lecternTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getRawSlot() == 13) {
            boolean given = plugin.getBookStorageManager().giveDailyWritableBook(player);
            player.sendMessage(given ? ChatColor.LIGHT_PURPLE + "A private writable library book was added to your inventory." : ChatColor.RED + "You already claimed today's writable book.");
            player.closeInventory();
        } else if (e.getRawSlot() == 11) {
            player.closeInventory();
            plugin.getBookStorageManager().beginSearchPrompt(player);
        } else if (e.getRawSlot() == 15) {
            plugin.getGuiManager().openStats(player);
        }
    }

    private void handleLecternBookEditorClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) { e.setCancelled(true); return; }
        int rawSlot = e.getRawSlot();
        if (rawSlot == 22) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) { e.setCancelled(true); return; }
            ItemStack cursor = e.getCursor();
            ItemStack hotbar = e.getHotbarButton() >= 0 ? player.getInventory().getItem(e.getHotbarButton()) : null;
            if (hotbar != null && !hotbar.getType().isAir() && hotbar.getType() != Material.WRITTEN_BOOK) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only signed written books can be submitted here.");
                return;
            }
            if (cursor != null && !cursor.getType().isAir() && cursor.getType() != Material.WRITTEN_BOOK) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only signed written books can be submitted here.");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGuiManager().refreshLecternBookEditor(player));
            return;
        }
        if (rawSlot >= e.getView().getTopInventory().getSize()) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (rawSlot < 0) return;
        ItemStack book = e.getView().getTopInventory().getItem(22);
        try {
            switch (rawSlot) {
                case 9 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "title");
                case 10 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "author");
                case 11 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "category");
                case 12 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "tags");
                case 13 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "rating");
                case 14 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "comments");
                case 15 -> { plugin.getBookStorageManager().toggleLecternBookVisibility(player, book); plugin.getGuiManager().refreshLecternBookEditor(player); }
                case 17 -> {
                    boolean placedInShelf = plugin.getBookStorageManager().saveLecternBook(player, book);
                    e.getView().getTopInventory().setItem(22, null);
                    player.closeInventory();
                    player.sendMessage(placedInShelf ? ChatColor.GREEN + "Book saved into a nearby chiseled bookshelf." : ChatColor.GREEN + "Book saved to the library bookshelf pool.");
                }
            }
        } catch (IllegalArgumentException ex) { player.sendMessage(ChatColor.RED + ex.getMessage()); }
    }


    private void handleBookshelfReturnClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) { e.setCancelled(true); return; }
        int rawSlot = e.getRawSlot();
        if (rawSlot == 13) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) { e.setCancelled(true); return; }
            ItemStack cursor = e.getCursor();
            ItemStack hotbar = e.getHotbarButton() >= 0 ? player.getInventory().getItem(e.getHotbarButton()) : null;
            if ((cursor != null && !cursor.getType().isAir() && cursor.getType() != Material.WRITTEN_BOOK) || (hotbar != null && !hotbar.getType().isAir() && hotbar.getType() != Material.WRITTEN_BOOK)) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only signed written books can be returned here.");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGuiManager().refreshBookshelfReturn(player));
            return;
        }
        if (rawSlot >= e.getView().getTopInventory().getSize()) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (rawSlot < 0) return;
        ItemStack book = e.getView().getTopInventory().getItem(13);
        try {
            switch (rawSlot) {
                case 11 -> plugin.getBookStorageManager().beginReturnBookEdit(player, book, "rating");
                case 15 -> plugin.getBookStorageManager().beginReturnBookEdit(player, book, "comment");
                case 17 -> {
                    boolean placed = plugin.getBookStorageManager().returnBookToLibrary(player, book);
                    e.getView().getTopInventory().setItem(13, null);
                    player.closeInventory();
                    player.sendMessage(placed ? ChatColor.GREEN + "Book returned to a nearby chiseled bookshelf." : ChatColor.GREEN + "Book returned to the library pool.");
                }
            }
        } catch (IllegalArgumentException ex) { player.sendMessage(ChatColor.RED + ex.getMessage()); }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof LibraryMenu) { e.setCancelled(true); return; }
        String title = e.getView().getTitle();
        if (title.equals(plugin.getGuiManager().statsTitle()) || title.equals(plugin.getGuiManager().roomEditorTitle()) || title.equals(plugin.getGuiManager().roomDetailsTitle()) || title.equals(plugin.getGuiManager().lecternTitle())) {
            if (e.getRawSlots().stream().anyMatch(slot -> slot < e.getView().getTopInventory().getSize())) e.setCancelled(true);
            return;
        }
        if (e.getView().getTitle().equals(plugin.getGuiManager().lecternBookEditorTitle())) {
            handleEditorDrag(e, 22, true);
            return;
        }
        if (e.getView().getTitle().equals(plugin.getGuiManager().bookshelfReturnTitle())) handleEditorDrag(e, 13, false);
    }


    private void handleEditorDrag(InventoryDragEvent e, int bookSlot, boolean lecternEditor) {
        boolean touchesProtectedSlot = e.getRawSlots().stream().anyMatch(slot -> slot < e.getView().getTopInventory().getSize() && slot != bookSlot);
        ItemStack submitted = e.getNewItems().get(bookSlot);
        boolean putsInvalidBookInSubmitSlot = submitted != null && submitted.getType() != Material.WRITTEN_BOOK;
        if (touchesProtectedSlot || putsInvalidBookInSubmitSlot) e.setCancelled(true);
        else plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.getWhoClicked() instanceof Player player) {
                if (lecternEditor) plugin.getGuiManager().refreshLecternBookEditor(player);
                else plugin.getGuiManager().refreshBookshelfReturn(player);
            }
        });
    }

    @EventHandler public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent event){if(event.getPlayer() instanceof Player p&&event.getInventory().getHolder() instanceof LibraryMenu){plugin.getManagedDialogs().invalidate(p);plugin.getBookWritingManager().invalidate(p);}}
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (e.getView().getTitle().equals(plugin.getGuiManager().lecternBookEditorTitle())) {
            if (plugin.getBookStorageManager().hasPendingLecternEdit(player)) return;
            plugin.getBookStorageManager().returnItem(player, e.getInventory().getItem(22));
            return;
        }
        if (e.getView().getTitle().equals(plugin.getGuiManager().bookshelfReturnTitle())) {
            if (plugin.getBookStorageManager().hasPendingReturnEdit(player)) return;
            plugin.getBookStorageManager().returnItem(player, e.getInventory().getItem(13));
        }
    }
}
