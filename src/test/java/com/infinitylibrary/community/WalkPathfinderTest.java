package com.infinitylibrary.community;

import com.infinitylibrary.model.Vector3i;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkPathfinderTest {
    static class Grid implements WalkPathfinder.Terrain {
        final Set<Vector3i> solid=new HashSet<>(),ladders=new HashSet<>();int minX=-8,maxX=8,minZ=-8,maxZ=8;
        Grid(){for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)solid.add(new Vector3i(x,0,z));}
        public boolean clear(Vector3i p){return p.x()>=minX&&p.x()<=maxX&&p.z()>=minZ&&p.z()<=maxZ&&p.y()>=0&&p.y()<10&&!solid.contains(p);}
        public boolean climb(Vector3i p){return ladders.contains(p);}
        public boolean stand(Vector3i p){return clear(p)&&clear(p.add(new Vector3i(0,1,0)))&&(climb(p)||solid.contains(p.add(new Vector3i(0,-1,0))));}
    }
    WalkPathfinder solve(Grid g,Vector3i a,Vector3i b){var s=new WalkPathfinder(g,a,b,10000,30);for(int i=0;i<1000&&s.status()==WalkPathfinder.Status.SEARCHING;i++)s.advance(10);return s;}
    @Test void routeGoesAroundWallsInsteadOfDrawingThroughThem(){Grid g=new Grid();for(int z=-2;z<=2;z++)for(int y=1;y<=3;y++)g.solid.add(new Vector3i(2,y,z));var s=solve(g,new Vector3i(0,1,0),new Vector3i(4,1,0));assertEquals(WalkPathfinder.Status.FOUND,s.status());assertTrue(s.path().stream().anyMatch(p->Math.abs(p.z())>=3));assertTrue(s.path().stream().allMatch(g::stand));}
    @Test void enclosedGoalHasNoStraightLineFallback(){Grid g=new Grid();for(int x=3;x<=5;x++)for(int z=-1;z<=1;z++)if(x!=4||z!=0)for(int y=1;y<=4;y++)g.solid.add(new Vector3i(x,y,z));var s=solve(g,new Vector3i(0,1,0),new Vector3i(4,1,0));assertEquals(WalkPathfinder.Status.FAILED,s.status());assertTrue(s.path().isEmpty());}
    @Test void diagonalCornerCuttingIsNotAllowed(){Grid g=new Grid();g.minX=0;g.maxX=1;g.minZ=0;g.maxZ=1;for(int y=1;y<=4;y++){g.solid.add(new Vector3i(1,y,0));g.solid.add(new Vector3i(0,y,1));}assertEquals(WalkPathfinder.Status.FAILED,solve(g,new Vector3i(0,1,0),new Vector3i(1,1,1)).status());}
    @Test void workIsIncrementalAndObeysNodeLimit(){Grid g=new Grid();var s=new WalkPathfinder(g,new Vector3i(-8,1,-8),new Vector3i(8,1,8),5,30);assertEquals(2,s.advance(2));assertTrue(s.expanded()<=2);s.advance(100);assertEquals(WalkPathfinder.Status.FAILED,s.status());assertTrue(s.expanded()<=5);}
    @Test void walksUpOneBlockWithHeadroom(){Grid g=new Grid();g.minX=0;g.maxX=2;g.minZ=0;g.maxZ=0;g.solid.add(new Vector3i(1,1,0));g.solid.add(new Vector3i(2,1,0));var s=solve(g,new Vector3i(0,1,0),new Vector3i(2,2,0));assertEquals(WalkPathfinder.Status.FOUND,s.status());assertTrue(s.path().contains(new Vector3i(1,2,0)));}
    @Test void stepUpRequiresHeadroomAtPlayerPosition(){Grid g=new Grid();g.minX=0;g.maxX=2;g.minZ=0;g.maxZ=0;g.solid.add(new Vector3i(1,1,0));g.solid.add(new Vector3i(2,1,0));g.solid.add(new Vector3i(0,3,0));assertEquals(WalkPathfinder.Status.FAILED,solve(g,new Vector3i(0,1,0),new Vector3i(2,2,0)).status());}
    @Test void climbableColumnSupportsVerticalRoutes(){Grid g=new Grid();g.minX=g.maxX=g.minZ=g.maxZ=0;for(int y=1;y<=5;y++)g.ladders.add(new Vector3i(0,y,0));assertEquals(WalkPathfinder.Status.FOUND,solve(g,new Vector3i(0,1,0),new Vector3i(0,5,0)).status());g.ladders.clear();assertEquals(WalkPathfinder.Status.FAILED,solve(g,new Vector3i(0,1,0),new Vector3i(0,5,0)).status());}
    @Test void cannotDropThroughAFullSolidWall(){Grid g=new Grid();g.minX=0;g.maxX=2;g.minZ=g.maxZ=0;g.solid.add(new Vector3i(0,1,0));for(int y=1;y<=4;y++)g.solid.add(new Vector3i(1,y,0));assertEquals(WalkPathfinder.Status.FAILED,solve(g,new Vector3i(0,2,0),new Vector3i(2,1,0)).status());}
}
