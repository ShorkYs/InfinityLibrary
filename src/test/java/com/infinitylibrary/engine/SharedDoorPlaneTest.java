package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedDoorPlaneTest {
    ConnectionPoint door(String id,int x,int y,int z,BlockFace face,int width,int height,int floor){return new ConnectionPoint(id,new Vector3i(x,y,z),face,width,height,floor);}
    Room cube(){return new Room("cube",RoomType.FILLER,new Vector3i(7,7,7),List.of(
        door("north",3,1,0,BlockFace.NORTH,2,3,0),door("south",3,1,6,BlockFace.SOUTH,2,3,0),
        door("east",6,1,3,BlockFace.EAST,2,3,0),door("west",0,1,3,BlockFace.WEST,2,3,0),
        door("up",3,6,3,BlockFace.UP,2,1,0),door("down",3,0,3,BlockFace.DOWN,2,1,0)),List.of(),List.of(),100);}
    LayoutPlanner.Box box(Room room,Vector3i origin,RoomTransform t){return new LayoutPlanner.Box(origin,t.transformedSize(room.size()),room.connections().stream().map(cp -> cp.transform(t,room.size()).offset(cp.id(),origin)).toList());}
    @Test void allFacesAndTransformsShareExactlyTheSameOpening(){
        Room r=cube();var parent=box(r,new Vector3i(0,0,0),RoomTransform.IDENTITY);
        for(ConnectionPoint target:r.connections())for(RoomTransform transform:RoomTransform.all()){
            ConnectionPoint entrance=r.connections().stream().map(cp -> cp.transform(transform,r.size())).filter(target::compatibleWith).findFirst().orElseThrow();
            Vector3i at=target.position().subtract(entrance.position());var next=box(r,at,transform);
            assertTrue(parent.overlaps(next));assertTrue(parent.sharesDoorPlane(next));assertFalse(parent.conflicts(next));assertFalse(next.conflicts(parent));
            ConnectionPoint actual=entrance.offset(entrance.id(),at);assertEquals(target.position(),actual.position());assertEquals(new HashSet<>(target.cells()),new HashSet<>(actual.cells()));assertTrue(TopologySearch.linked(target,actual));
        }
    }
    @Test void actualCandidateSearchHasNoOutwardBlockOffset(){
        Room r=cube();var parent=box(r,new Vector3i(0,0,0),RoomTransform.IDENTITY);ConnectionPoint target=r.connections().get(2);
        var search=new TopologySearch(List.of(r),64,10000,b -> TopologySearch.Fit.YES);
        var options=search.candidates(target,List.of(parent),12,null);assertFalse(options.isEmpty());
        for(var c:options){ConnectionPoint entry=c.connections().stream().filter(cp -> cp.id().equals(c.entranceId())).findFirst().orElseThrow();assertEquals(target.position(),c.worldDoor(entry).position());assertTrue(parent.sharesDoorPlane(c.box()));}
    }
    @Test void solidInteriorOverlapRemainsForbidden(){
        Room r=cube();var a=box(r,new Vector3i(0,0,0),RoomTransform.IDENTITY);var b=box(r,new Vector3i(5,0,0),RoomTransform.IDENTITY);
        assertTrue(a.conflicts(b));assertTrue(b.conflicts(a));assertTrue(a.conflicts(a));
    }
    @Test void unmatchedOrMisalignedSharedWallIsRejected(){
        var a=new LayoutPlanner.Box(new Vector3i(0,0,0),new Vector3i(7,7,7),List.of(door("out",6,1,3,BlockFace.EAST,2,3,0)));
        for(ConnectionPoint wrong:List.of(door("in",6,1,4,BlockFace.WEST,2,3,0),door("in",6,1,3,BlockFace.WEST,3,3,0),door("in",6,1,3,BlockFace.WEST,2,3,1),door("in",6,1,3,BlockFace.EAST,2,3,0))){
            assertTrue(a.conflicts(new LayoutPlanner.Box(new Vector3i(6,0,0),new Vector3i(7,7,7),List.of(wrong))));
        }
        assertTrue(a.conflicts(new LayoutPlanner.Box(new Vector3i(6,0,0),new Vector3i(7,7,7))));
    }
    @Test void tiledCornerPillarsCanBeSharedWithoutAllowingInteriorOverlap(){
        var a=new LayoutPlanner.Box(new Vector3i(0,0,0),new Vector3i(7,7,7));var corner=new LayoutPlanner.Box(new Vector3i(6,0,6),new Vector3i(7,7,7));
        assertTrue(a.overlaps(corner));assertTrue(a.sharesBoundary(corner));assertFalse(a.conflicts(corner));
        assertTrue(a.conflicts(new LayoutPlanner.Box(new Vector3i(5,0,5),new Vector3i(7,7,7))));
    }
    @Test void oldAdjacentPlanesAreNotMistakenForOverlappingLoops(){
        assertFalse(TopologySearch.linked(door("a",6,1,3,BlockFace.EAST,2,3,0),door("b",7,1,3,BlockFace.WEST,2,3,0)));
    }
    @Test void siblingPlannerCanReserveRoomsThatShareAParentWall(){
        Room r=cube();var parent=box(r,new Vector3i(0,0,0),RoomTransform.IDENTITY);var search=new TopologySearch(List.of(r),64,10000,b -> TopologySearch.Fit.YES);
        Map<String,List<LayoutPlanner.Candidate>> options=new LinkedHashMap<>();for(int index:List.of(0,2))options.put(r.connections().get(index).id(),search.candidates(r.connections().get(index),List.of(parent),4,null));
        var result=new LayoutPlanner().plan(options,List.of(parent),12,2,c -> true,(c,obs)->0,"north");assertEquals(2,result.neighbors().size());
    }
}
