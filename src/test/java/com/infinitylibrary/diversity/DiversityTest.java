package com.infinitylibrary.diversity;

import com.infinitylibrary.model.*;
import com.infinitylibrary.props.PropArea;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiversityTest {
    private DiversityArea area(double original,List<DiversityVariant> variants){return new DiversityArea("reading",new Vector3i(1,1,1),new Vector3i(2,1,2),original,variants);}
    private DiversityVariant alternative(){return new DiversityVariant("garden",1,List.of(new RoomBlock(new Vector3i(1,0,1),"minecraft:gold_block")));}
    private Room room(DiversityArea area){return new Room("test",RoomType.FILLER,new Vector3i(11,7,11),List.of(new ConnectionPoint("north",new Vector3i(5,1,0),BlockFace.NORTH,3,3)),List.of(new RoomBlock(new Vector3i(1,1,1),"minecraft:stone")),List.of(),100,List.of(),List.of(area));}
    @Test void sourceDimensionsMustMatchEachAxisExactly(){
        DiversityArea area=area(1,List.of());assertDoesNotThrow(() -> area.requireSourceSize(new Vector3i(2,1,2)));
        assertThrows(IllegalArgumentException.class,() -> area.requireSourceSize(new Vector3i(1,2,2)));
        assertThrows(IllegalArgumentException.class,() -> area.requireSourceSize(new Vector3i(2,1,3)));
    }
    @Test void alternativesReplaceTargetUsingLocalSourceCoordinates(){
        var snapshot=DiversityComposition.resolve(room(area(0,List.of(alternative()))),new UUID(1,2));var patch=snapshot.patches().getFirst();
        assertEquals("garden",patch.choice());assertEquals(new Vector3i(2,1,2),patch.blocks().getFirst().position());assertTrue(patch.contains(new Vector3i(1,1,1)));
        assertTrue(patch.blocks().stream().noneMatch(b -> b.blockData().equals("minecraft:stone")));
    }
    @Test void originalCanBeChosenAndDisabledAlternativesFallBackSafely(){
        var disabled=new DiversityVariant("disabled",0,alternative().blocks());
        var patch=DiversityComposition.resolve(room(area(0,List.of(disabled))),new UUID(1,2)).patches().getFirst();
        assertEquals("default",patch.choice());assertEquals("minecraft:stone",patch.blocks().getFirst().blockData());
    }
    @Test void defaultAndAlternativeBothAppearAcrossRoomSeeds(){
        Room r=room(area(1,List.of(alternative())));Set<String> choices=new HashSet<>();Random random=new Random(42);
        for(int i=0;i<100;i++)choices.add(DiversityComposition.resolve(r,new UUID(random.nextLong(),random.nextLong())).patches().getFirst().choice());
        assertEquals(Set.of("default","garden"),choices);
    }
    @Test void emptyAlternativeStillKeepsAnExplicitClearingPatch(){
        var snapshot=DiversityComposition.resolve(room(area(0,List.of(new DiversityVariant("empty",1,List.of())))),new UUID(1,2));
        assertEquals(1,snapshot.patches().size());assertTrue(snapshot.patches().getFirst().blocks().isEmpty());assertEquals("empty",snapshot.patches().getFirst().choice());
    }
    @Test void savedRoomAndFrozenChoiceRoundTripWithoutRerolling() throws Exception {
        Room r=room(area(0,List.of(alternative())));var snapshot=DiversityComposition.resolve(r,new UUID(1,2));YamlConfiguration y=new YamlConfiguration();r.write(y.createSection("room"));snapshot.write(y.createSection("snapshot"));
        YamlConfiguration disk=new YamlConfiguration();disk.loadFromString(y.saveToString());assertEquals(r.diversityAreas(),Room.read("test",disk.getConfigurationSection("room")).diversityAreas());assertEquals(snapshot,DiversityComposition.read(disk.getConfigurationSection("snapshot")));
        var changed=room(area(1,List.of()));assertEquals("default",DiversityComposition.resolve(changed,new UUID(1,2)).patches().getFirst().choice());assertEquals("garden",snapshot.patches().getFirst().choice());
    }
    @Test void areasCannotOverlapDecorationsOrDoorsButCanChangeAnAwayWall(){
        Room r=room(area(1,List.of(alternative())));var decoration=new PropArea("deco",new Vector3i(1,1,1),new Vector3i(2,1,2),"tables",0,false);
        assertThrows(IllegalArgumentException.class,() -> Room.validateDiversityAreas(r.size(),r.connections(),List.of(decoration),List.of(),r.diversityAreas()));
        var doorway=new DiversityArea("door",new Vector3i(4,1,0),new Vector3i(2,2,2),1,List.of());
        assertThrows(IllegalArgumentException.class,() -> Room.validateDiversityAreas(r.size(),r.connections(),List.of(),List.of(),List.of(doorway)));
        var wall=new DiversityArea("wall",new Vector3i(0,1,1),new Vector3i(1,2,2),1,List.of());assertDoesNotThrow(() -> Room.validateDiversityAreas(r.size(),r.connections(),List.of(),List.of(),List.of(wall)));
    }
    @Test void malformedOrOversizedAlternativeDataIsRejected(){
        assertThrows(IllegalArgumentException.class,() -> area(1,List.of(new DiversityVariant("outside",1,List.of(new RoomBlock(new Vector3i(2,0,0),"minecraft:stone"))))));
        assertThrows(IllegalArgumentException.class,() -> new DiversityVariant("default",1,List.of()));
        assertThrows(IllegalArgumentException.class,() -> new DiversityVariant("bad",Double.NaN,List.of()));
        List<DiversityVariant> tooMany=new ArrayList<>();for(int i=0;i<17;i++)tooMany.add(new DiversityVariant("v"+i,1,List.of()));assertThrows(IllegalArgumentException.class,() -> area(1,tooMany));
    }
    @Test void legacyRoomWithoutDiversityStillLoads(){
        YamlConfiguration y=new YamlConfiguration();room(area(1,List.of())).write(y);y.set("diversity",null);assertTrue(Room.read("test",y).diversityAreas().isEmpty());
    }
}
