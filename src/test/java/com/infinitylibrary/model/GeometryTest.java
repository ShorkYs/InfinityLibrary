package com.infinitylibrary.model;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    @Test void transformsAreBijectiveAndStayInsideRotatedBounds() {
        Vector3i size = new Vector3i(7,3,9);
        for (RoomTransform t : RoomTransform.all()) {
            var points = new HashSet<Vector3i>();
            Vector3i bounds = t.transformedSize(size);
            for (int x=0;x<size.x();x++) for(int y=0;y<size.y();y++) for(int z=0;z<size.z();z++) {
                Vector3i v = t.transform(new Vector3i(x,y,z),size);
                assertTrue(v.x()>=0 && v.x()<bounds.x() && v.y()>=0 && v.y()<bounds.y() && v.z()>=0 && v.z()<bounds.z());
                assertTrue(points.add(v));
            }
            assertEquals(189,points.size());
        }
    }
    @Test void transformedConnectionsKeepOppositeDirectionsAndDimensions() {
        var a = new ConnectionPoint("a",new Vector3i(0,1,4),BlockFace.WEST,3,3);
        var b = new ConnectionPoint("b",new Vector3i(6,1,4),BlockFace.EAST,3,3);
        for (RoomTransform t : RoomTransform.all()) assertTrue(a.transform(t,new Vector3i(7,6,9)).compatibleWith(b.transform(t,new Vector3i(7,6,9))));
        assertFalse(a.compatibleWith(new ConnectionPoint("c",new Vector3i(0,0,0),BlockFace.EAST,2,3)));
    }
    @Test void invalidConnectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPoint("a",new Vector3i(0,0,0),BlockFace.NORTH_EAST,3,3));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPoint("a",new Vector3i(0,0,0),BlockFace.NORTH,0,3));
    }
}
