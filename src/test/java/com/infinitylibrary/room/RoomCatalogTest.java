package com.infinitylibrary.room;

import com.infinitylibrary.model.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomCatalogTest {
    private Room room(String id,RoomType type,int width) {
        return new Room(id,type,new Vector3i(width,5,5),List.of(new ConnectionPoint("door",new Vector3i(0,1,2),BlockFace.WEST,1,2)),List.of(),List.of(),100);
    }
    private RoomCatalog catalog(Map<String,Room> defaults,YamlConfiguration data) {
        RoomCatalog c=new RoomCatalog();c.load(defaults,data,message -> fail(message));return c;
    }
    @Test void removedDefaultStaysHiddenAfterSerializationAndReload() throws Exception {
        var defaults=Map.of("builtin_test",room("builtin_test",RoomType.FILLER,5));var c=catalog(defaults,new YamlConfiguration());
        c.remove(List.of("builtin_test"));YamlConfiguration disk=new YamlConfiguration();disk.loadFromString(c.write().saveToString());
        var loaded=catalog(defaults,disk);assertFalse(loaded.isActive("builtin_test"));assertTrue(loaded.active().isEmpty());
        assertTrue(loaded.get("builtin_test").isPresent());assertTrue(loaded.archivedIds().contains("builtin_test"));
    }
    @Test void archivedGeometrySurvivesChangesToPackagedDefaults() {
        var original=Map.of("builtin_test",room("builtin_test",RoomType.FILLER,5));var c=catalog(original,new YamlConfiguration());c.remove(List.of("builtin_test"));
        var newer=Map.of("builtin_test",room("builtin_test",RoomType.FILLER,11));var loaded=catalog(newer,c.write());
        assertEquals(5,loaded.get("builtin_test").orElseThrow().size().x());assertFalse(loaded.isActive("builtin_test"));
    }
    @Test void restoringDefaultRemovesItsTombstoneAndPersistsItsRole() {
        var defaults=Map.of("builtin_start",room("builtin_start",RoomType.STARTING,5));var c=catalog(defaults,new YamlConfiguration());
        c.remove(List.of("builtin_start"));c.restore("builtin_start");var loaded=catalog(defaults,c.write());
        assertTrue(loaded.isActive("builtin_start"));assertEquals(RoomType.STARTING,loaded.get("builtin_start").orElseThrow().type());assertTrue(loaded.archivedIds().isEmpty());
    }
    @Test void removedCustomTemplateRemainsReadableAndCanBeRestored() {
        YamlConfiguration y=new YamlConfiguration();room("custom",RoomType.STARTING,7).write(y.createSection("rooms.custom"));var c=catalog(Map.of(),y);
        c.remove(List.of("custom"));var loaded=catalog(Map.of(),c.write());assertFalse(loaded.isActive("custom"));assertEquals(7,loaded.get("custom").orElseThrow().size().x());
        loaded.restore("custom");assertTrue(loaded.isActive("custom"));
    }
    @Test void batchRemovalValidatesEveryIdBeforeChangingAnything() {
        var c=catalog(Map.of("a",room("a",RoomType.FILLER,5)),new YamlConfiguration());
        assertThrows(IllegalArgumentException.class,() -> c.remove(List.of("a","missing")));assertTrue(c.isActive("a"));assertTrue(c.archivedIds().isEmpty());
    }
    @Test void legacyRoomFilesWithoutArchiveMetadataStillLoad() {
        var original=room("builtin_start",RoomType.FILLER,9);YamlConfiguration y=new YamlConfiguration();original.write(y.createSection("rooms.builtin_start"));
        var c=catalog(Map.of("builtin_start",room("builtin_start",RoomType.STARTING,11)),y);
        assertEquals(9,c.get("builtin_start").orElseThrow().size().x());assertEquals(RoomType.FILLER,c.get("builtin_start").orElseThrow().type());
    }
    @Test void activeRecordWinsOverDuplicateArchiveEntry() {
        YamlConfiguration y=new YamlConfiguration();room("a",RoomType.STARTING,7).write(y.createSection("rooms.a"));room("a",RoomType.FILLER,5).write(y.createSection("archived-rooms.a"));
        var c=catalog(Map.of(),y);assertEquals(RoomType.STARTING,c.get("a").orElseThrow().type());assertFalse(c.archivedIds().contains("a"));
    }
    @Test void startingTypeParsesCaseInsensitivelyAndRoundTrips() {
        assertEquals(RoomType.STARTING,RoomType.parse("starting"));YamlConfiguration y=new YamlConfiguration();room("entrance",RoomType.STARTING,7).write(y.createSection("room"));
        assertEquals(RoomType.STARTING,Room.read("entrance",y.getConfigurationSection("room")).type());
        assertThrows(IllegalArgumentException.class,() -> RoomType.parse("unknown"));
    }
    @Test void malformedCatalogRejectsReloadWithoutDiscardingActiveOrArchivedRooms() {
        var c=catalog(Map.of("a",room("a",RoomType.FILLER,5),"b",room("b",RoomType.FILLER,7)),new YamlConfiguration());c.remove(List.of("b"));String before=c.write().saveToString();var bad=new YamlConfiguration();bad.set("rooms.broken.type","NOT_A_ROLE");
        assertThrows(RuntimeException.class,()->c.load(Map.of(),bad,message->{}));assertEquals(before,c.write().saveToString());
    }
}
