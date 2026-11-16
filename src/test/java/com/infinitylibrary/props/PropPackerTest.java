package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PropPackerTest {
    private final PropPacker packer=new PropPacker();
    private Prop prop(String id,int x,int y,int z) {return new Prop(id,"test",new Vector3i(x,y,z),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:stone")),1);}
    private PropArea area(int x,int y,int z,double gaps,boolean rotate) {return new PropArea("a",new Vector3i(1,1,1),new Vector3i(x,y,z),"test",gaps,rotate);}
    @Test void requestedFiveSmallPropsFillTwoByFiveByOne() {
        var result=packer.pack(area(2,5,1,0,false),List.of(prop("small",2,1,1)),42,8,200000);
        assertEquals(10,result.filledCells());assertEquals(5,result.placements().size());
    }
    @Test void requestedThreePlusTwoPropsFillTwoByFiveByOne() {
        var result=packer.pack(area(2,5,1,0,false),List.of(prop("three",2,3,1),prop("two",2,2,1)),42,16,200000);
        assertEquals(10,result.filledCells());assertEquals(List.of(2,3),result.placements().stream().map(p -> p.size().y()).sorted().toList());
    }
    @Test void sameMixWorksAlongHorizontalStrip() {
        var result=packer.pack(area(2,1,5,0,false),List.of(prop("three",2,1,3),prop("two",2,1,2)),99,16,200000);
        assertEquals(10,result.filledCells());assertEquals(2,result.placements().size());
    }
    @Test void gapsCanLeaveEntireAreaEmpty() {
        var result=packer.pack(area(2,5,1,100,true),List.of(prop("small",1,1,1)),42,8,200000);
        assertTrue(result.placements().isEmpty());assertEquals(10,result.intentionalGaps());
    }
    @Test void zeroWeightAndDifferentCategoryAreNotSelected() {
        var disabled=new Prop("disabled","test",new Vector3i(1,1,1),prop("p",1,1,1).blocks(),0);
        var other=new Prop("other","other",new Vector3i(1,1,1),prop("p",1,1,1).blocks(),1);
        assertTrue(packer.pack(area(2,1,2,0,true),List.of(disabled,other),1,8,200000).placements().isEmpty());
    }
    @Test void rotationMakesAnOtherwiseUnfittablePropFit() {
        Prop p=prop("rotated",2,1,3);
        assertTrue(packer.pack(area(3,1,2,0,false),List.of(p),1,8,200000).placements().isEmpty());
        var result=packer.pack(area(3,1,2,0,true),List.of(p),1,8,200000);
        assertEquals(6,result.filledCells());assertEquals(new Vector3i(3,1,2),result.placements().getFirst().size());
    }
    @Test void placementsStayInsideBoundsDoNotOverlapAndHaveBoundingBoxSupport() {
        Vector3i size=new Vector3i(5,4,6);
        var catalog=List.of(prop("small",1,1,1),prop("wide",2,2,1),prop("long",2,1,3));
        for(int seed=0;seed<40;seed++){
            var result=packer.pack(area(5,4,6,20,true),catalog,seed,8,200000);Set<Vector3i> occupied=new HashSet<>();int cells=0;
            for(var p:result.placements()) {
                Vector3i at=p.offset(),s=p.size();
                assertTrue(at.x()>=0&&at.y()>=0&&at.z()>=0&&at.x()+s.x()<=size.x()&&at.y()+s.y()<=size.y()&&at.z()+s.z()<=size.z());
                for(int x=at.x();x<at.x()+s.x();x++)for(int z=at.z();z<at.z()+s.z();z++)if(at.y()>0)assertTrue(occupied.contains(new Vector3i(x,at.y()-1,z)));
                for(int y=at.y();y<at.y()+s.y();y++)for(int z=at.z();z<at.z()+s.z();z++)for(int x=at.x();x<at.x()+s.x();x++){assertTrue(occupied.add(new Vector3i(x,y,z)));cells++;}
            }
            assertEquals(result.filledCells(),cells);
        }
    }
    @Test void repeatableSeedDoesNotDependOnCatalogOrdering() {
        var a=prop("a",1,1,1);var b=prop("b",2,1,1);PropArea area=area(5,2,5,15,true);
        assertEquals(packer.pack(area,List.of(a,b),12,8,200000),packer.pack(area,List.of(b,a),12,8,200000));
    }
    @Test void differentRoomSeedsProduceDifferentLayouts() {
        var catalog=List.of(prop("a",1,1,1),prop("b",2,1,1));Set<String> layouts=new HashSet<>();
        for(int seed=0;seed<20;seed++)layouts.add(packer.pack(area(5,1,5,15,true),catalog,seed,8,200000).placements().toString());
        assertTrue(layouts.size()>1);
    }
    @Test void floorModeNeverStacksFurniture() {
        var area=new PropArea("floor",new Vector3i(1,1,1),new Vector3i(4,5,4),"test",0,true,false);
        var result=packer.pack(area,List.of(prop("small",1,1,1),prop("tall",1,3,1)),42,8,200000);
        assertTrue(result.placements().stream().allMatch(p -> p.offset().y()==0));
        assertEquals(16,result.placements().size());
    }
    @Test void floorModeStillUsesTallPropsWithEnoughClearance() {
        var area=new PropArea("floor",new Vector3i(1,1,1),new Vector3i(2,5,1),"test",0,false,false);
        var result=packer.pack(area,List.of(prop("tall",2,3,1)),42,8,200000);
        assertEquals(1,result.placements().size());assertEquals(6,result.filledCells());
    }
    @Test void tinyWorkBudgetReturnsSafely() {
        assertDoesNotThrow(() -> packer.pack(area(20,5,20,0,true),List.of(prop("p",2,1,2)),1,32,1));
    }
}
