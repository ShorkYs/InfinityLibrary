package com.infinitylibrary.rentals;
import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Location;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.entity.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.*;
/** Structure protection and private-room access; managed archives never live in world inventories. */
public final class RentalProtection implements Listener {
 private final InfinityLibraryPlugin plugin;public RentalProtection(InfinityLibraryPlugin p){plugin=p;}
 private boolean library(Location l){return l!=null&&l.getWorld()!=null&&plugin.isLibraryWorld(l.getWorld().getName())&&plugin.getConfig().getBoolean("generation.protect-structures",true);}
 private boolean rental(Location l){return l!=null&&plugin.getRentalVaultManager().rental(l);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void destroy(BlockBreakEvent e){if(library(e.getBlock().getLocation())&&!e.getPlayer().hasPermission("infinitylibrary.admin")){e.setCancelled(true);e.getPlayer().sendMessage("Library structures are protected. Use the workspace to store books and drafts.");}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(library(e.getBlock().getLocation())&&!e.getPlayer().hasPermission("infinitylibrary.admin"))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.getTo()==null||e.getFrom().getBlockX()==e.getTo().getBlockX()&&e.getFrom().getBlockY()==e.getTo().getBlockY()&&e.getFrom().getBlockZ()==e.getTo().getBlockZ()&&e.getFrom().getWorld()==e.getTo().getWorld())return;if(plugin.getRentalVaultManager().blocked(e.getPlayer(),e.getTo()))e.setTo(plugin.getRentalVaultManager().blocked(e.getPlayer(),e.getFrom())?plugin.getGenerationEngine().homeLocation():e.getFrom());}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(e.getTo()!=null&&plugin.getRentalVaultManager().blocked(e.getPlayer(),e.getTo())){e.setCancelled(true);e.getPlayer().sendMessage("That rental room is private");}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(InventoryOpenEvent e){Location l=e.getInventory().getLocation();if(rental(l))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){if(rental(e.getSource().getLocation())||rental(e.getDestination().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(b->library(b.getLocation()));}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(b->library(b.getLocation()));}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->library(b.getLocation())||library(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->library(b.getLocation())||library(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(library(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fluid(BlockFromToEvent e){if(library(e.getBlock().getLocation())||library(e.getToBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void change(EntityChangeBlockEvent e){if(library(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(library(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(library(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler public void respawn(PlayerRespawnEvent e){if(plugin.getRentalVaultManager().blocked(e.getPlayer(),e.getRespawnLocation()))e.setRespawnLocation(plugin.getGenerationEngine().homeLocation());}
}
