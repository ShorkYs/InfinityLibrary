package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PropPersistenceTest {
    private final Vector3i size=new Vector3i(11,7,11);
    private final ConnectionPoint door=new ConnectionPoint("north",new Vector3i(5,1,0),BlockFace.NORTH,3,3);
    private PropArea area(String id,Vector3i min) {return new PropArea(id,min,new Vector3i(2,3,2),"library",15,true);}
    @Test void propAndCompositionRoundTripPreservesFrozenBlocks() throws Exception {
        Prop p=new Prop("shelf","library",new Vector3i(2,3,1),List.of(new RoomBlock(new Vector3i(1,2,0),"minecraft:bookshelf")),3);
        YamlConfiguration y=new YamlConfiguration();p.write(y.createSection("prop"));
        PropComposition composition=new PropComposition(List.of(area("corner",new Vector3i(1,1,1))),List.of(new RoomBlock(new Vector3i(2,2,2),"minecraft:bookshelf")));
        composition.write(y.createSection("composition"));YamlConfiguration loaded=new YamlConfiguration();loaded.loadFromString(y.saveToString());
        assertEquals(p,Prop.read("shelf",loaded.getConfigurationSection("prop")));
        assertEquals(composition,PropComposition.read(loaded.getConfigurationSection("composition")));
    }
    @Test void roomsRoundTripWithAreasAndReadLegacyRoomsWithoutAreas() throws Exception {
        Room room=new Room("room",RoomType.FILLER,size,List.of(door),List.of(),List.of(),100,List.of(area("corner",new Vector3i(1,1,1))));
        YamlConfiguration y=new YamlConfiguration();room.write(y.createSection("room"));
        Room loaded=Room.read("room",y.getConfigurationSection("room"));assertEquals(room.propAreas(),loaded.propAreas());
        y.set("room.prop-areas",null);assertTrue(Room.read("room",y.getConfigurationSection("room")).propAreas().isEmpty());
    }
    @Test void roomAreasCannotOverlapShellDoorwayOrEachOther() {
        assertThrows(IllegalArgumentException.class,() -> Room.validatePropAreas(size,List.of(door),List.of(area("shell",new Vector3i(0,1,1)))));
        assertThrows(IllegalArgumentException.class,() -> Room.validatePropAreas(size,List.of(door),List.of(area("door",new Vector3i(4,1,1)))));
        assertThrows(IllegalArgumentException.class,() -> Room.validatePropAreas(size,List.of(door),List.of(area("a",new Vector3i(1,1,1)),area("b",new Vector3i(2,1,2)))));
        assertDoesNotThrow(() -> Room.validatePropAreas(size,List.of(door),List.of(area("corner",new Vector3i(1,1,1)))));
    }
    @Test void invalidPropSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new PropArea("a",new Vector3i(1,1,1),new Vector3i(0,1,1),"library",15,true));
        assertThrows(IllegalArgumentException.class,() -> new PropArea("a",new Vector3i(1,1,1),new Vector3i(1,1,1),"library",Double.NaN,true));
        assertThrows(IllegalArgumentException.class,() -> new Prop("empty","library",new Vector3i(1,1,1),List.of(),1));
    }
    @Test void snapshotRejectsBlocksOutsideItsAreas() {
        YamlConfiguration y=new YamlConfiguration();area("corner",new Vector3i(1,1,1)).write(y.createSection("areas.corner"));
        y.set("blocks",List.of("9,2,9|minecraft:stone"));assertThrows(IllegalArgumentException.class,() -> PropComposition.read(y));
    }
}
