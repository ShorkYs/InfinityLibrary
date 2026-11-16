package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExactDecorationSelectorTest {
    private Prop p(String id,String category,int x,int y,int z){return new Prop(id,category,new Vector3i(x,y,z),List.of(new RoomBlock(new Vector3i(0,0,0),"minecraft:stone")),1);}
    private PropArea a(boolean rotate){return new PropArea("a",new Vector3i(1,1,1),new Vector3i(3,3,1),"tables",0,rotate,false);}
    @Test void smallerDecorationsAreNeverPackedIntoAnExactArea(){
        assertTrue(new ExactDecorationSelector().select(a(true),List.of(p("small","tables",1,1,1),p("short","tables",3,2,1)),7).isEmpty());
    }
    @Test void matchingSizeAndCategoryAreBothRequired(){
        Prop correct=p("correct","tables",3,3,1);
        var result=new ExactDecorationSelector().select(a(false),List.of(p("wrong_category","plants",3,3,1),p("small","tables",2,3,1),correct),7);
        assertEquals("correct",result.orElseThrow().prop().id());assertEquals(new Vector3i(0,0,0),result.orElseThrow().offset());
    }
    @Test void sameVolumeWithDifferentHeightIsRejected(){
        assertTrue(new ExactDecorationSelector().select(a(true),List.of(p("wrong_axes","tables",3,1,3)),7).isEmpty());
    }
    @Test void optionalRotationStillHasToMatchEveryTransformedDimension(){
        Prop rotated=p("rotated","tables",1,3,3);
        assertTrue(new ExactDecorationSelector().select(a(false),List.of(rotated),7).isEmpty());
        var result=new ExactDecorationSelector().select(a(true),List.of(rotated),7).orElseThrow();assertEquals(a(true).size(),result.size());
    }
    @Test void disabledPropsAndWholeAreaEmptinessAreHonored(){
        Prop original=p("disabled","tables",3,3,1);Prop disabled=new Prop(original.id(),original.category(),original.size(),original.blocks(),0);
        assertTrue(new ExactDecorationSelector().select(a(false),List.of(disabled),7).isEmpty());
        var empty=new PropArea("a",a(false).min(),a(false).size(),"tables",100,false,false);
        assertTrue(new ExactDecorationSelector().select(empty,List.of(original),7).isEmpty());
    }
    @Test void oldAreasDefaultToExactAndPackedRemainsExplicit(){
        YamlConfiguration y=new YamlConfiguration();a(false).write(y);y.set("fill-mode",null);
        assertEquals(PropArea.FillMode.EXACT,PropArea.read("a",y).fillMode());
        y.set("fill-mode","PACKED");assertEquals(PropArea.FillMode.PACKED,PropArea.read("a",y).fillMode());
    }
    @Test void wildcardAndSeededChoicesOnlyUseEligibleExactDecorations(){
        var area=new PropArea("a",a(false).min(),a(false).size(),"*",0,false,false);
        var catalog=List.of(p("table","tables",3,3,1),p("plant","plants",3,3,1),p("small","tables",1,1,1));
        var selector=new ExactDecorationSelector();assertEquals(selector.select(area,catalog,12),selector.select(area,catalog,12));
        Set<String> choices=new HashSet<>();Random seeds=new Random(12);
        for(int i=0;i<100;i++){var chosen=selector.select(area,catalog,seeds.nextLong()).orElseThrow();assertNotEquals("small",chosen.prop().id());choices.add(chosen.prop().id());}
        assertEquals(Set.of("table","plant"),choices);
    }
}
