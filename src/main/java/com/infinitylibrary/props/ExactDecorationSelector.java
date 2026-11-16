package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import java.util.*;

/** One full-size decoration, filtered by category and transformed bounding dimensions. */
public final class ExactDecorationSelector {
    public static List<Rotation> rotations(PropArea area,Prop prop) {
        if(prop.weight()<=0||!(area.category().equals("*")||area.category().equals(prop.category())))return List.of();
        return Arrays.stream(area.rotate()?Rotation.values():new Rotation[]{Rotation.NONE}).filter(r -> r.rotatedSize(prop.size()).equals(area.size())).toList();
    }
    public Optional<PropPacker.Placement> select(PropArea area,Collection<Prop> catalog,long seed) {
        Random random=new Random(seed);if(random.nextDouble()*100<area.gapPercent())return Optional.empty();
        List<Prop> fits=catalog.stream().filter(p -> !rotations(area,p).isEmpty()).sorted(Comparator.comparing(Prop::id)).toList();
        if(fits.isEmpty())return Optional.empty();double total=fits.stream().mapToDouble(Prop::weight).sum();double pick=random.nextDouble()*total;Prop selected=fits.getLast();
        for(Prop p:fits){pick-=p.weight();if(pick<=0){selected=p;break;}}
        List<Rotation> rotations=rotations(area,selected);
        return Optional.of(new PropPacker.Placement(selected,new Vector3i(0,0,0),rotations.get(random.nextInt(rotations.size()))));
    }
}
