package com.infinitylibrary.community;

import com.infinitylibrary.model.*;
import com.infinitylibrary.engine.LayoutPlanner;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomGraphTest {
    RoomGraph.RoomNode node(String id,int x,RoomType type,List<ConnectionPoint> doors){return new RoomGraph.RoomNode(UUID.nameUUIDFromBytes(id.getBytes()),id,type,new LayoutPlanner.Box(new Vector3i(x,0,0),new Vector3i(7,5,7)),doors);}
    ConnectionPoint door(String id,int x,BlockFace face){return new ConnectionPoint(id,new Vector3i(x,1,3),face,3,3);}
    @Test void followsSharedDoorPlanes(){var a=node("a",0,RoomType.STARTING,List.of(door("e",6,BlockFace.EAST)));var b=node("b",6,RoomType.READ,List.of(door("w",6,BlockFace.WEST)));var graph=new RoomGraph(List.of(a,b));assertEquals(List.of(new Vector3i(6,1,3)),graph.route(a.id(),b.id()).orElseThrow());assertEquals(b,graph.closestReachable(a.id(),RoomType.READ).orElseThrow());}
    @Test void oldAdjacentDoorLayoutsRemainNavigable(){var a=node("a",0,RoomType.STARTING,List.of(door("e",6,BlockFace.EAST)));var b=node("b",7,RoomType.READ,List.of(door("w",7,BlockFace.WEST)));var graph=new RoomGraph(List.of(a,b));assertTrue(graph.route(a.id(),b.id()).isPresent());assertTrue(graph.route(b.id(),a.id()).isPresent());}
    @Test void closedOrUngeneratedExitCannotBecomeARoute(){var a=node("a",0,RoomType.STARTING,List.of(door("e",6,BlockFace.EAST)));var b=node("b",6,RoomType.READ,List.of());assertTrue(new RoomGraph(List.of(a,b)).route(a.id(),b.id()).isEmpty());}
    @Test void matchingCoordinatesNeedCompatibleDoorDimensionsAndFloor(){var a=node("a",0,RoomType.STARTING,List.of(door("e",6,BlockFace.EAST)));var b=node("b",6,RoomType.READ,List.of(new ConnectionPoint("w",new Vector3i(6,1,3),BlockFace.WEST,3,3,1)));assertTrue(new RoomGraph(List.of(a,b)).route(a.id(),b.id()).isEmpty());}
    @Test void sameRoomNeedsNoDoorAndUnreachableTypesStayUnavailable(){var a=node("a",0,RoomType.STARTING,List.of());var b=node("b",20,RoomType.READ,List.of());var graph=new RoomGraph(List.of(a,b));assertEquals(List.of(),graph.route(a.id(),a.id()).orElseThrow());assertTrue(graph.closestReachable(a.id(),RoomType.READ).isEmpty());assertEquals(a,graph.containing(new Vector3i(2,1,2)).orElseThrow());}
}
