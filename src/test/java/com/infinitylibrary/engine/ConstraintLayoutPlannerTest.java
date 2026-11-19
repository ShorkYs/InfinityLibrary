package com.infinitylibrary.engine;
import com.infinitylibrary.model.*;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ConstraintLayoutPlannerTest {
    LayoutPlanner.Candidate c(String id,int x,int size){return new LayoutPlanner.Candidate(new Room(id,RoomType.FILLER,new Vector3i(size,3,3),List.of(new ConnectionPoint("in",new Vector3i(0,1,1),BlockFace.WEST,1,1)),List.of(),List.of(),100),"in",RoomTransform.IDENTITY,new Vector3i(x,0,0));}
    LayoutPlanner.Result run(Map<String,List<LayoutPlanner.Candidate>> options,long seed,int budget){return new ConstraintLayoutPlanner().plan(options,List.of(),8,x->true,(x,b)->0,"a",x->true,seed,budget);}
    @Test void stableSeedReproducesTheSameLayoutChoice(){var options=Map.of("a",List.of(c("one",0,3),c("two",10,3)));assertEquals(run(options,42,100).neighbors(),run(options,42,100).neighbors());}
    @Test void seedsCanExploreDifferentAlternatives(){var options=Map.of("a",List.of(c("one",0,3),c("two",10,3),c("three",20,3)));Set<String> choices=new HashSet<>();for(int i=0;i<100;i++)choices.add(run(options,i,100).neighbors().get("a").room().id());assertTrue(choices.size()>1);}
    @Test void backtrackingFindsNonConflictingSiblingArrangement(){var bad=c("bad",0,6);var good=c("good",-3,2);var other=c("other",4,2);var r=run(Map.of("a",List.of(bad,good),"b",List.of(other)),9,100);assertEquals(2,r.neighbors().size());assertSame(good,r.neighbors().get("a"));}
    @Test void honorsTerrainCapAndFrontierPreservation(){var option=c("one",0,3);var p=new ConstraintLayoutPlanner();assertTrue(p.plan(Map.of("a",List.of(option)),List.of(),1,x->false,(x,b)->0,"a",x->true,1,100).neighbors().isEmpty());assertTrue(p.plan(Map.of("a",List.of(option)),List.of(),0,x->true,(x,b)->0,"a",x->true,1,100).neighbors().isEmpty());assertTrue(p.plan(Map.of("a",List.of(option)),List.of(),1,x->true,(x,b)->0,"a",x->false,1,100).neighbors().isEmpty());}
    @Test void budgetIsStrictlyBounded(){var p=new ConstraintLayoutPlanner();var options=Map.of("a",List.of(c("one",0,3),c("two",10,3)),"b",List.of(c("three",20,3)));p.plan(options,List.of(),8,x->true,(x,b)->0,"a",x->true,42,4);assertTrue(p.nodes()<=4);}
    @Test void zeroSearchBudgetReturnsWithoutUnboundedRecursion(){assertNotNull(run(Map.of("a",List.of(c("one",0,3))),1,0));}
}
