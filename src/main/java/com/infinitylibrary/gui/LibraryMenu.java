package com.infinitylibrary.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Holder identity prevents another inventory with a matching title from invoking actions. */
public final class LibraryMenu implements InventoryHolder {
    public enum Screen { MAIN, WRITER, CONFIRM_PUBLISH, CONFIRM_DELETE }
    private final Inventory inventory;
    private final Screen screen;
    private final UUID owner;
    public LibraryMenu(Player player, Screen screen, int size, String title) {
        this.owner=player.getUniqueId(); this.screen=screen;
        this.inventory=Bukkit.createInventory(this,size,title);
    }
    public Screen screen() { return screen; }
    public UUID owner() { return owner; }
    @Override public Inventory getInventory() { return inventory; }
}
