package com.infinitylibrary;

import com.infinitylibrary.command.CommandHandler;
import com.infinitylibrary.engine.GenerationEngine;
import com.infinitylibrary.gui.GUIManager;
import com.infinitylibrary.listener.BookListener;
import com.infinitylibrary.listener.GUIListener;
import com.infinitylibrary.listener.PlayerGenerationListener;
import com.infinitylibrary.room.RoomManager;
import com.infinitylibrary.selection.SelectionManager;
import com.infinitylibrary.storage.BookStorageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfinityLibraryPlugin extends JavaPlugin {
    private com.infinitylibrary.writing.BookWritingManager bookWritingManager;
    private BookListener bookListener;
    private RoomManager roomManager;
    private GenerationEngine generationEngine;
    private BookStorageManager bookStorageManager;
    private GUIManager guiManager;
    private SelectionManager selectionManager;

    @Override public void onEnable() {
        saveDefaultConfig();
        roomManager = new RoomManager(this); roomManager.load();
        bookStorageManager = new BookStorageManager(this); bookStorageManager.load();
        selectionManager = new SelectionManager(this);
        bookWritingManager = new com.infinitylibrary.writing.BookWritingManager(this); bookWritingManager.load();
        guiManager = new GUIManager(this);
        generationEngine = new GenerationEngine(this, roomManager); generationEngine.start();
        CommandHandler handler = new CommandHandler(this);
        PluginCommand command = getCommand("infinitylibrary");
        if (command != null) { command.setExecutor(handler); command.setTabCompleter(handler); }
        getServer().getPluginManager().registerEvents(new PlayerGenerationListener(this), this);
        bookListener = new BookListener(this);
        getServer().getPluginManager().registerEvents(bookListener, this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getLogger().info("InfinityLibraryPlugin enabled.");
    }

    @Override public void onDisable() {
        if (bookWritingManager != null) try { bookWritingManager.save(); } catch(RuntimeException ex) { getLogger().severe(ex.getMessage()); }
        if (bookListener != null) bookListener.cleanupAll();
        if (generationEngine != null) generationEngine.stop();
        if (bookStorageManager != null) bookStorageManager.saveNow();
    }

    public void reloadEverything() {
        generationEngine.stop();
        bookStorageManager.saveNow();
        reloadConfig();
        roomManager.load();
        bookStorageManager.load();
        generationEngine.start();
    }

    public com.infinitylibrary.writing.BookWritingManager getBookWritingManager() { return bookWritingManager; }
    public RoomManager getRoomManager() { return roomManager; }
    public GenerationEngine getGenerationEngine() { return generationEngine; }
    public BookStorageManager getBookStorageManager() { return bookStorageManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public SelectionManager getSelectionManager() { return selectionManager; }
}
