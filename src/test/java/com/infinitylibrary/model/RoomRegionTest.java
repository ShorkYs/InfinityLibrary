package com.infinitylibrary.model;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RoomRegionTest {
 @Test void inclusiveMinExclusiveEnd(){var r=new RoomRegion("table",new Vector3i(2,1,3),new Vector3i(2,1,2),"general");assertTrue(r.contains(new Vector3i(3,1,4)));assertFalse(r.contains(new Vector3i(4,1,4)));}
 @Test void rejectsInvalidKindsAndSizes(){assertThrows(IllegalArgumentException.class,()->new RoomRegion("command",new Vector3i(0,0,0),new Vector3i(1,1,1),"a"));assertThrows(IllegalArgumentException.class,()->new RoomRegion("seat",new Vector3i(0,0,0),new Vector3i(1000,1000,1000),"a"));}
}