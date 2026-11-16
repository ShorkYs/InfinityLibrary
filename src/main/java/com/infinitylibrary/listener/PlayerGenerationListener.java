package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class PlayerGenerationListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    public PlayerGenerationListener(InfinityLibraryPlugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onMove(PlayerMoveEvent event) {
        if(event.isCancelled()||event.getTo()==null||event.getFrom().getWorld()==event.getTo().getWorld()&&event.getFrom().getBlockX()==event.getTo().getBlockX()&&event.getFrom().getBlockY()==event.getTo().getBlockY()&&event.getFrom().getBlockZ()==event.getTo().getBlockZ())return;
        plugin.getGenerationEngine().tickPlayer(event.getPlayer(),event.getTo());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onTeleport(PlayerTeleportEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,() -> {if(event.getPlayer().isOnline())plugin.getGenerationEngine().tickPlayer(event.getPlayer());});
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,() -> {if(event.getPlayer().isOnline())plugin.getGenerationEngine().tickPlayer(event.getPlayer());});
    }
}
