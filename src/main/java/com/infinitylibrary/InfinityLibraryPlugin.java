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
    private com.infinitylibrary.props.PropManager propManager;
    private com.infinitylibrary.diversity.DiversityManager diversityManager;
    private com.infinitylibrary.community.ManagedDialogs managedDialogs;
    private com.infinitylibrary.community.CollaborationManager collaborationManager;
    private com.infinitylibrary.community.ReadingManager readingManager;
    private com.infinitylibrary.community.NoticeboardManager noticeboardManager;
    private com.infinitylibrary.community.NavigationManager navigationManager;
    private com.infinitylibrary.community.PreviewManager previewManager;
    private com.infinitylibrary.builder.AuthoringManager authoringManager;
    private com.infinitylibrary.builder.BuilderBridge builderBridge;
    private com.infinitylibrary.rentals.RentalVaultManager rentalVault;
    public com.infinitylibrary.rentals.RentalVaultManager getRentalVaultManager(){return rentalVault;}
    private com.infinitylibrary.rentals.RentalManager rentalManager;
    public com.infinitylibrary.rentals.RentalManager getRentalManager(){return rentalManager;}
    private BookListener bookListener;
    private RoomManager roomManager;
    private GenerationEngine generationEngine;
    private boolean proceduralMode;
    private com.infinitylibrary.writing.NativeBookEditor nativeBookEditor;
    private BookStorageManager bookStorageManager;
    private GUIManager guiManager;
    private SelectionManager selectionManager;

    private boolean initialized;
    @Override public void onEnable() {
        initialized=false;
        saveDefaultConfig();
        if(com.infinitylibrary.community.AtomicYaml.read(getDataFolder().toPath().resolve("config.yml")).getInt("generation.schema",0)<4){
            try{com.infinitylibrary.storage.BackupManager.existingData(this);}catch(RuntimeException ex){throw new IllegalStateException("Cannot back up existing plugin data before v4 migration",ex);}
            if("builtin_modular".equals(getConfig().getString("generation.base-room-id")))getConfig().set("generation.base-room-id","");
            getConfig().set("generation.mode","district");getConfig().set("generation.schema",4);saveConfig();
        }
        propManager = new com.infinitylibrary.props.PropManager(this); propManager.load();
        roomManager = new RoomManager(this); roomManager.load();
        bookStorageManager = new BookStorageManager(this); bookStorageManager.load();
        selectionManager = new SelectionManager(this);
        diversityManager = new com.infinitylibrary.diversity.DiversityManager(this);
        authoringManager=new com.infinitylibrary.builder.AuthoringManager(this);
        bookWritingManager = new com.infinitylibrary.writing.BookWritingManager(this); bookWritingManager.load();
        guiManager = new GUIManager(this);
        generationEngine=new GenerationEngine(this,roomManager);generationEngine.start();
        managedDialogs=new com.infinitylibrary.community.ManagedDialogs(this);
        collaborationManager=new com.infinitylibrary.community.CollaborationManager(this);
        readingManager=new com.infinitylibrary.community.ReadingManager(this);
        noticeboardManager=new com.infinitylibrary.community.NoticeboardManager(this);
        rentalManager=new com.infinitylibrary.rentals.RentalManager(this);
        navigationManager=new com.infinitylibrary.community.NavigationManager(this);
        previewManager=new com.infinitylibrary.community.PreviewManager(this);
        nativeBookEditor=new com.infinitylibrary.writing.NativeBookEditor(this);getServer().getPluginManager().registerEvents(nativeBookEditor,this);
        rentalVault=new com.infinitylibrary.rentals.RentalVaultManager(this);
        bookStorageManager.startShelfService();
        builderBridge=new com.infinitylibrary.builder.BuilderBridge(this);
        CommandHandler handler = new CommandHandler(this);
        PluginCommand command = getCommand("infinitylibrary");
        if (command != null) { command.setExecutor(handler); command.setTabCompleter(handler); }
        getServer().getPluginManager().registerEvents(new PlayerGenerationListener(this), this);
        bookListener = new BookListener(this);
        getServer().getPluginManager().registerEvents(bookListener, this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.infinitylibrary.rentals.RentalProtection(this),this);
        initialized=true;
        getLogger().info("InfinityLibraryPlugin enabled.");
    }

    @Override public void onDisable() {
        if(!initialized){org.bukkit.Bukkit.getScheduler().cancelTasks(this);return;}
        initialized=false;
        if(rentalVault!=null)rentalVault.shutdown();
        if(builderBridge!=null)builderBridge.shutdown();
        if(nativeBookEditor!=null)nativeBookEditor.cleanupAll();
        if(previewManager!=null)previewManager.shutdown();
        if(navigationManager!=null)navigationManager.shutdown();
        if(managedDialogs!=null)managedDialogs.clear();
        if (bookWritingManager != null) try { bookWritingManager.save(); } catch(RuntimeException ex) { getLogger().severe(ex.getMessage()); }
        if (bookListener != null) bookListener.cleanupAll();
        if (generationEngine != null&&generationEngine.isRunning()) generationEngine.stop();

        if(bookStorageManager!=null)bookStorageManager.stopShelfService();
        if (bookStorageManager != null) bookStorageManager.saveNow();
    }

    public void reloadEverything() {
        previewManager.clear();navigationManager.clearRoutes();managedDialogs.clear();
        for(var player:org.bukkit.Bukkit.getOnlinePlayers()){builderBridge.cleanup(player);authoringManager.clear(player);}authoringManager.clearGenerated();
        nativeBookEditor.cleanupAll();getGenerationEngine().stop();
        bookStorageManager.saveNow();
        reloadConfig();
        propManager.load();
        roomManager.load();
        bookStorageManager.load();
        generationEngine.start();
        rentalManager.load();rentalVault.load();collaborationManager.load();readingManager.load();noticeboardManager.load();navigationManager.load();
    }

    public com.infinitylibrary.community.ManagedDialogs getManagedDialogs(){return managedDialogs;}
    public com.infinitylibrary.community.CollaborationManager getCollaborationManager(){return collaborationManager;}
    public com.infinitylibrary.community.ReadingManager getReadingManager(){return readingManager;}
    public com.infinitylibrary.community.NoticeboardManager getNoticeboardManager(){return noticeboardManager;}
    public com.infinitylibrary.community.NavigationManager getNavigationManager(){return navigationManager;}
    public com.infinitylibrary.community.PreviewManager getPreviewManager(){return previewManager;}
    public com.infinitylibrary.writing.BookWritingManager getBookWritingManager() { return bookWritingManager; }
    public com.infinitylibrary.props.PropManager getPropManager() { return propManager; }
    public com.infinitylibrary.diversity.DiversityManager getDiversityManager() {return diversityManager;}
    public RoomManager getRoomManager() { return roomManager; }
    public com.infinitylibrary.builder.AuthoringManager getAuthoringManager(){return authoringManager;}
    public com.infinitylibrary.builder.BuilderBridge getBuilderBridge(){return builderBridge;}
    public BookStorageManager getBookStorageManager(){return bookStorageManager;}
    public GUIManager getGuiManager(){return guiManager;}
    public SelectionManager getSelectionManager(){return selectionManager;}
    public GenerationEngine getGenerationEngine(){return generationEngine;}
    public com.infinitylibrary.writing.NativeBookEditor getNativeBookEditor(){return nativeBookEditor;}
    public boolean isLibraryWorld(String name){return name.equals(generationEngine.worldName());}
    public boolean isRoomInUse(String id){return generationEngine.usesRoom(id);}
    public void switchGenerationMode(String mode){if(!java.util.Set.of("district","unified","templates").contains(mode))throw new IllegalArgumentException("Generation uses the district planner. Alternate world modes are retired; old experimental files are untouched.");}
}
