package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import com.infinitylibrary.diversity.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TopologySearchTest {
    Room room(String id,int width,int doorWidth){return new Room(id,RoomType.FILLER,new Vector3i(width,5,5),List.of(new ConnectionPoint("in",new Vector3i(0,1,2),BlockFace.WEST,doorWidth,2)),List.of(),List.of(),100);}
    ConnectionPoint exit(){return new ConnectionPoint("exit",new Vector3i(4,1,2),BlockFace.EAST,1,2);}
    TopologySearch search(List<Room> rooms){return new TopologySearch(rooms,64,200000,b -> TopologySearch.Fit.YES);}
    @Test void considersEveryCatalogRoomBeyondOldFirstTwelveCandidates(){
        List<Room> rooms=new ArrayList<>();for(int i=0;i<30;i++)rooms.add(room("r"+i,5,1));
        var candidates=search(rooms).candidates(exit(),List.of(),1,null);
        assertEquals(30,candidates.size());assertEquals(30,candidates.stream().map(c -> c.room().id()).distinct().count());
    }
    @Test void continuationChecksCatalogBeyondOld512TransformCutoff(){
        List<Room> rooms=new ArrayList<>();for(int i=0;i<40;i++)rooms.add(room("wrong"+i,5,3));rooms.add(room("fits",5,1));
        assertEquals(TopologySearch.Fit.YES,search(rooms).continuation(exit(),List.of(),List.of()));
    }
    @Test void terrainObstructionIsDifferentFromBudgetExhaustion(){
        var blocked=new TopologySearch(List.of(room("r",5,1)),64,200000,b -> TopologySearch.Fit.NO);
        assertEquals(TopologySearch.Fit.NO,blocked.continuation(exit(),List.of(),List.of()));
        var unknown=new TopologySearch(List.of(room("r",5,1)),64,200000,b -> TopologySearch.Fit.UNKNOWN);
        assertEquals(TopologySearch.Fit.UNKNOWN,unknown.continuation(exit(),List.of(),List.of()));
        var limited=new TopologySearch(List.of(room("r",5,1)),64,0,b -> TopologySearch.Fit.YES);
        assertEquals(TopologySearch.Fit.UNKNOWN,limited.continuation(exit(),List.of(),List.of()));
    }
    @Test void compatibleAdjacentExitCountsAsLoopEvenWhenNoNewRoomFits(){
        var other=new ConnectionPoint("other",new Vector3i(4,1,2),BlockFace.WEST,1,2);
        var search=new TopologySearch(List.of(room("r",5,1)),64,200000,b -> TopologySearch.Fit.NO);
        assertEquals(TopologySearch.Fit.YES,search.continuation(exit(),List.of(),List.of(other)));
        assertEquals(TopologySearch.Fit.NO,search.continuation(exit(),List.of(),List.of(other.offset("wrong",new Vector3i(1,0,0)))));
    }
    @Test void siblingBeamRejectsPlacementThatStrandsAnotherParentsDoor(){
        Room r=room("greedy",5,3);var greedy=new LayoutPlanner.Candidate(r,"in",RoomTransform.IDENTITY,new Vector3i(5,0,0));var safe=new LayoutPlanner.Candidate(r,"in",RoomTransform.IDENTITY,new Vector3i(30,0,0));
        var search=search(List.of(room("continuation",5,1)));List<ConnectionPoint> frontier=List.of(exit());
        assertEquals(TopologySearch.Fit.YES,search.continuation(exit(),List.of(),frontier));
        assertFalse(search.preserves(frontier,frontier,List.of(),List.of(greedy)));
        assertTrue(search.preserves(frontier,frontier,List.of(),List.of(safe)));
        var result=new LayoutPlanner().plan(Map.of("requested",List.of(greedy,safe)),List.of(),8,1,c -> true,(c,boxes)->c==greedy?100:0,"requested",chosen -> search.preserves(frontier,frontier,List.of(),chosen));
        assertSame(safe,result.neighbors().get("requested"));
    }
    Room variantRoom(){
        ConnectionPoint optional=new ConnectionPoint("side",new Vector3i(0,0,1),BlockFace.EAST,1,2);
        DiversityArea a=new DiversityArea("wall",new Vector3i(4,1,1),new Vector3i(1,2,3),1,List.of(new DiversityVariant("open",1,List.of(),List.of(optional)),new DiversityVariant("closed",1,List.of())));
        return new Room("variant",RoomType.FILLER,new Vector3i(5,5,5),List.of(new ConnectionPoint("north",new Vector3i(2,1,0),BlockFace.NORTH,1,2)),List.of(),List.of(),100,List.of(),List.of(a));
    }
    @Test void topologyChoicesIncludeVariantDoorAndClosedAlternativeBeforePlacement(){
        Room r=variantRoom();var choices=DiversityComposition.choices(r,64);assertTrue(choices.complete());assertEquals(3,choices.values().size());
        assertTrue(choices.values().stream().anyMatch(c -> c.connections(r).stream().anyMatch(cp -> cp.id().equals("div_wall_side"))));
        assertTrue(choices.values().stream().anyMatch(c -> c.patches().getFirst().choice().equals("closed")&&c.connections(r).size()==1));
        var target=new ConnectionPoint("target",new Vector3i(10,1,2),BlockFace.WEST,1,2);
        var candidates=search(List.of(r)).candidates(target,List.of(),64,null);
        assertTrue(candidates.stream().anyMatch(c -> c.entranceId().equals("div_wall_side")&&c.diversity().patches().getFirst().choice().equals("open")));
        assertTrue(candidates.stream().allMatch(c -> c.connections().stream().anyMatch(cp -> cp.id().equals(c.entranceId()))));
    }
    @Test void frozenVariantDoorsSurviveSnapshotAndTemplateChanges() throws Exception {
        Room r=variantRoom();var c=DiversityComposition.choices(r,64).values().stream().filter(x -> x.patches().getFirst().choice().equals("open")).findFirst().orElseThrow();var y=new YamlConfiguration();c.write(y.createSection("snapshot"));var loaded=DiversityComposition.read(y.getConfigurationSection("snapshot"));assertEquals(c,loaded);
        var placed=new PlacedRoom(UUID.randomUUID(),r.id(),new Vector3i(0,0,0),r.size());placed.diversity(c);placed.connections(c.connections(r));Room changed=room("variant",5,1);assertEquals(c.connections(r),placed.connections(changed));
    }
    @Test void closedVariationReplacesOriginalDoorOnlyInsideItsArea(){
        Room r=variantRoom();var a=r.diversityAreas().getFirst();var original=new ConnectionPoint("east",new Vector3i(4,1,2),BlockFace.EAST,1,2);
        Room withDoor=new Room("r",RoomType.FILLER,r.size(),List.of(original,r.connections().getFirst()),List.of(),List.of(),100,List.of(),List.of(a));
        var choices=DiversityComposition.choices(withDoor,64).values();
        assertEquals(2,choices.stream().filter(c -> c.patches().getFirst().choice().equals("default")).findFirst().orElseThrow().connections(withDoor).size());
        assertEquals(List.of(r.connections().getFirst()),choices.stream().filter(c -> c.patches().getFirst().choice().equals("closed")).findFirst().orElseThrow().connections(withDoor));
    }
    @Test void incompleteTopologyEnumerationCannotProveADeadEnd(){
        Room r=variantRoom();var search=new TopologySearch(List.of(r),1,200000,b -> TopologySearch.Fit.NO);assertEquals(TopologySearch.Fit.UNKNOWN,search.continuation(exit(),List.of(),List.of()));
    }
    @Test void invalidVariantDoorIsRejectedOutsideTargetOrOuterRoomBoundary(){
        assertThrows(IllegalArgumentException.class,() -> new DiversityArea("bad",new Vector3i(0,0,0),new Vector3i(1,1,1),1,List.of(new DiversityVariant("v",1,List.of(),List.of(new ConnectionPoint("bad",new Vector3i(1,0,0),BlockFace.EAST,1,1))))));
        var area=new DiversityArea("interior",new Vector3i(2,1,1),new Vector3i(1,2,3),1,variantRoom().diversityAreas().getFirst().variants());
        assertThrows(IllegalArgumentException.class,() -> new Room("bad",RoomType.FILLER,new Vector3i(5,5,5),room("x",5,1).connections(),List.of(),List.of(),100,List.of(),List.of(area)));
    }
    @Test void openingCellsHaveExactEvenWidthAndVerticalSquare(){
        assertEquals(8,new ConnectionPoint("even",new Vector3i(3,1,0),BlockFace.NORTH,4,2).cells().size());
        assertEquals(16,new ConnectionPoint("up",new Vector3i(3,4,3),BlockFace.UP,4,1).cells().size());
        assertEquals(16,new HashSet<>(new ConnectionPoint("up",new Vector3i(3,4,3),BlockFace.DOWN,4,1).cells()).size());
    }
    @Test void transformedOpeningCellsStayExactForEvenWidths(){
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.UP)){
            var cp=new ConnectionPoint("test",new Vector3i(3,1,3),face,4,2);var size=new Vector3i(8,6,8);
            for(RoomTransform t:RoomTransform.all())assertEquals(new HashSet<>(cp.cells().stream().map(p -> t.transform(p,size)).toList()),new HashSet<>(cp.transform(t,size).cells()));
        }
    }
    @Test void unknownPreservationDefersInsteadOfApprovingAPlacement(){
        var search=new TopologySearch(List.of(room("r",5,1)),64,200000,b -> TopologySearch.Fit.UNKNOWN);
        assertFalse(search.preserves(List.of(exit()),List.of(exit()),List.of(),List.of(new LayoutPlanner.Candidate(room("large",5,3),"in",RoomTransform.IDENTITY,new Vector3i(5,0,0)))));
    }
}
