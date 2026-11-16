package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LayoutPlannerTest {
    private final LayoutPlanner planner=new LayoutPlanner();
    private LayoutPlanner.Candidate candidate(String id,int x,int width) {
        Room room=new Room(id,RoomType.FILLER,new Vector3i(width,3,3),
            List.of(new ConnectionPoint("in",new Vector3i(0,1,1),BlockFace.WEST,1,1)),List.of(),List.of(),100);
        return new LayoutPlanner.Candidate(room,"in",RoomTransform.IDENTITY,new Vector3i(x,0,0));
    }
    @Test void keepsAlternativeForFirstDoorSoBothSiblingRoomsFit() {
        var greedy=candidate("blocks-second",0,6); var good=candidate("leaves-space",-3,2); var second=candidate("second",4,2);
        Map<String,List<LayoutPlanner.Candidate>> doors=new LinkedHashMap<>();
        doors.put("a",List.of(greedy,good)); doors.put("b",List.of(second));
        var result=planner.plan(doors,List.of(),8,10,c -> true,(c,obs) -> c==greedy?100:0,"a");
        assertEquals(2,result.neighbors().size()); assertSame(good,result.neighbors().get("a"));
        assertFalse(result.neighbors().get("a").box().overlaps(result.neighbors().get("b").box()));
    }
    @Test void respectsTerrainReservationsAndRoomCapWithoutMarkingDoorsConsumed() {
        var blocked=candidate("reserved",0,3); var clear=candidate("clear",10,3);
        var result=planner.plan(Map.of("a",List.of(blocked),"b",List.of(clear)),
            List.of(new LayoutPlanner.Box(new Vector3i(0,0,0),new Vector3i(3,3,3))),8,1,c -> true,(c,obs) -> 0,"b");
        assertEquals(Set.of("b"),result.neighbors().keySet());
        assertTrue(planner.plan(Map.of("a",List.of(clear)),List.of(),8,0,c -> true,(c,obs) -> 0,"a").neighbors().isEmpty());
        assertTrue(planner.plan(Map.of("a",List.of(clear)),List.of(),8,10,c -> false,(c,obs) -> 0,"a").neighbors().isEmpty());
    }
    @Test void scoresLookaheadWithAllSelectedSiblingObstacles() {
        var a=candidate("a",0,2); var b=candidate("b",4,2);
        AtomicInteger withSibling=new AtomicInteger();
        var result=planner.plan(Map.of("a",List.of(a),"b",List.of(b)),List.of(),4,2,c -> true,(c,obs) -> {
            if(obs.contains(c==a?b.box():a.box())) withSibling.incrementAndGet(); return 0;
        },null);
        assertEquals(2,result.neighbors().size()); assertTrue(withSibling.get()>=2);
    }
    @Test void lookaheadDistinguishesOpenExitFromBlockedExit() {
        Room passage=new Room("passage",RoomType.FILLER,new Vector3i(3,3,3),List.of(
            new ConnectionPoint("in",new Vector3i(0,1,1),BlockFace.WEST,1,1),
            new ConnectionPoint("out",new Vector3i(2,1,1),BlockFace.EAST,1,1)),List.of(),List.of(),100);
        var c=new LayoutPlanner.Candidate(passage,"in",RoomTransform.IDENTITY,new Vector3i(0,0,0));
        assertEquals(4,planner.continuationScore(c,List.of(passage),List.of()));
        assertEquals(-3,planner.continuationScore(c,List.of(passage),List.of(new LayoutPlanner.Box(new Vector3i(3,-10,-10),new Vector3i(3,30,30)))));
    }
    @Test void worldChecksAreNotRepeatedForEveryBeamBranch() {
        var a=candidate("a",0,2); var b=candidate("b",4,2); AtomicInteger checked=new AtomicInteger();
        planner.plan(Map.of("a",List.of(a),"b",List.of(b)),List.of(),8,2,c -> {checked.incrementAndGet();return true;},(c,obs) -> 0,null);
        assertEquals(2,checked.get());
    }
}
