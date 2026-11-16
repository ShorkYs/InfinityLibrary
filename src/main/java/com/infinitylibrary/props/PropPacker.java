package com.infinitylibrary.props;

import com.infinitylibrary.model.*;
import java.util.*;

/** Seeded, bounded multi-attempt voxel packing. Entire prop bounds reserve space, including air. */
public final class PropPacker {
    public record Placement(Prop prop,Vector3i offset,Rotation rotation) {
        public Vector3i size() { return rotation.rotatedSize(prop.size()); }
    }
    public record Result(List<Placement> placements,int filledCells,int intentionalGaps) {
        public Result { placements=List.copyOf(placements); }
    }
    private record Option(Prop prop,Rotation rotation,Vector3i size) {}
    public Result pack(PropArea area,Collection<Prop> catalog,long seed,int attempts,int cellBudget) {
        int volume=(int)PropArea.volume(area.size());
        List<Option> options=new ArrayList<>();
        catalog.stream().sorted(Comparator.comparing(Prop::id)).filter(p -> p.weight()>0 && (area.category().equals("*")||area.category().equals(p.category()))).forEach(p -> {
            for(Rotation r:area.rotate()?Rotation.values():new Rotation[]{Rotation.NONE}) {
                Vector3i s=r.rotatedSize(p.size());
                if(s.x()<=area.size().x()&&s.y()<=area.size().y()&&s.z()<=area.size().z()) options.add(new Option(p,r,s));
            }
        });
        if(options.isEmpty()||area.gapPercent()==100) return new Result(List.of(),0,area.gapPercent()==100?(area.stack()?volume:area.size().x()*area.size().z()):0);
        Random random=new Random(seed);int[] budget={Math.max(1,Math.min(1000000,cellBudget))};
        Result best=new Result(List.of(),0,0); int target=(int)Math.round((area.stack()?volume:area.size().x()*area.size().z())*(1-area.gapPercent()/100));int bestScore=0,bestDistance=Integer.MAX_VALUE;
        for(int attempt=0;attempt<Math.max(1,Math.min(32,attempts))&&budget[0]>0;attempt++) {
            BitSet visited=new BitSet(volume),solid=new BitSet(volume); List<Placement> chosen=new ArrayList<>();int filled=0,gaps=0;
            for(int index=visited.nextClearBit(0);index<volume&&budget[0]>0;index=visited.nextClearBit(index+1)) {
                budget[0]--; Vector3i at=position(index,area.size());
                if(!area.stack()&&at.y()>0){visited.set(index);continue;}
                if(random.nextDouble()*100<area.gapPercent()) {visited.set(index);gaps++;continue;}
                Map<Prop,List<Option>> fitting=new LinkedHashMap<>();
                for(Option option:options) {
                    if(budget[0]<=0)break;
                    if(fits(at,option.size,area.size(),visited,solid,budget))fitting.computeIfAbsent(option.prop,k -> new ArrayList<>()).add(option);
                }
                if(fitting.isEmpty()){visited.set(index);continue;}
                double total=fitting.keySet().stream().mapToDouble(Prop::weight).sum();
                double pick=random.nextDouble()*total;Prop selectedProp=fitting.keySet().iterator().next();
                for(Prop prop:fitting.keySet()){selectedProp=prop;pick-=prop.weight();if(pick<=0)break;}
                List<Option> poses=fitting.get(selectedProp);Option selected=poses.get(random.nextInt(poses.size()));
                Vector3i size=selected.size;
                for(int y=at.y();y<at.y()+size.y();y++) for(int z=at.z();z<at.z()+size.z();z++) for(int x=at.x();x<at.x()+size.x();x++) {int cell=index(x,y,z,area.size());visited.set(cell);solid.set(cell);}
                filled+=(int)PropArea.volume(size);chosen.add(new Placement(selected.prop,at,selected.rotation));
            }
            Result result=new Result(chosen,filled,gaps);
            int coverage=area.stack()?filled:chosen.stream().mapToInt(p -> p.size().x()*p.size().z()).sum();
            int score=coverage+gaps,distance=Math.abs(coverage-target);
            if(score>bestScore || score==bestScore && distance<bestDistance) {best=result;bestScore=score;bestDistance=distance;}
        }
        return best;
    }
    private boolean fits(Vector3i at,Vector3i size,Vector3i bounds,BitSet used,BitSet solid,int[] budget) {
        if(at.x()+size.x()>bounds.x()||at.y()+size.y()>bounds.y()||at.z()+size.z()>bounds.z())return false;
        for(int y=at.y();y<at.y()+size.y();y++) for(int z=at.z();z<at.z()+size.z();z++) for(int x=at.x();x<at.x()+size.x();x++) {
            if(--budget[0]<0||used.get(index(x,y,z,bounds)))return false;
            if(y==at.y()&&y>0&&!solid.get(index(x,y-1,z,bounds)))return false;
        }
        return true;
    }
    private int index(int x,int y,int z,Vector3i size) { return (y*size.z()+z)*size.x()+x; }
    private Vector3i position(int index,Vector3i size) {return new Vector3i(index%size.x(),index/(size.x()*size.z()),(index/size.x())%size.z());}
}
