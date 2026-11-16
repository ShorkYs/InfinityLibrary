import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
public class LibraryProbe extends JavaPlugin {
    void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); getLogger().info("PASS " + message); }
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var p = (InfinityLibraryPlugin) Bukkit.getPluginManager().getPlugin("InfinityLibraryPlugin");
                var e = p.getGenerationEngine();
                e.resetToStart();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        check(e.placedCount()==1 && e.queuedBlocks()==0,"start room completed");
                        check(e.homeLocation().getBlock().isEmpty(),"home has standing space");
                        check(e.placeRoomImmediately("builtin_book"),"compatible room attaches");
                        check(e.queuedBlocks()>0,"manual placement respects queue");
                        p.reloadEverything();
                        check(e.placedCount()==2 && e.queuedBlocks()>0,"reload recovers pending room");
                        e.setPaused(true); p.reloadEverything(); check(e.isPaused(),"pause persists through reload");
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            check(e.queuedBlocks()==0,"recovered placement finishes");
                            check(java.nio.file.Files.exists(com.infinitylibrary.storage.BackupManager.create(p)),"plugin data backup created");
                            e.setPaused(false);
                            getLogger().info("ALL INTEGRATION CHECKS PASSED"); Bukkit.shutdown();
                        },40);
                    } catch (Throwable t) { t.printStackTrace(); Bukkit.shutdown(); }
                },40);
            } catch (Throwable t) { t.printStackTrace(); Bukkit.shutdown(); }
        },40);
    }
}
