package com.infinitylibrary.storage;

import com.infinitylibrary.InfinityLibraryPlugin;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Backs up plugin YAML data. World region files require a server/world backup. */
public final class BackupManager {
    private BackupManager() {}
    public static Path create(InfinityLibraryPlugin plugin) {
        plugin.getGenerationEngine().save();
        plugin.getRoomManager().save();
        plugin.getBookStorageManager().saveNow();
        Path root = plugin.getDataFolder().toPath();
        try {
            Path directory = Files.createDirectories(root.resolve("backups"));
            Path target = directory.resolve("library-" + Instant.now().toString().replace(':','-') + ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW));
                 var files = Files.list(root)) {
                for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                    zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            return target;
        } catch (IOException ex) { throw new IllegalStateException("Plugin data backup failed: " + ex.getMessage(), ex); }
    }
}
