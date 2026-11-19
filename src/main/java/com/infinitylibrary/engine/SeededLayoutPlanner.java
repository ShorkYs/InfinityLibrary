package com.infinitylibrary.engine;

import java.util.*;
import java.util.function.*;

/** Alternative bounded randomized depth-first backtracking; the original beam planner is unchanged. */
public final class SeededLayoutPlanner {
    private int remaining;private Map<String,LayoutPlanner.Candidate> best=Map.of();private long bestScore=Long.MIN_VALUE;
    public LayoutPlanner.Result plan(Map<String,List<LayoutPlanner.Candidate>> input,List<LayoutPlanner.Box> occupied,int maxRooms,Predicate<LayoutPlanner.Candidate> clear,BiFunction<LayoutPlanner.Candidate,List<LayoutPlanner.Box>,Integer> continuation,String required,Predicate<Collection<LayoutPlanner.Candidate>> preserve,long seed,int budget){
        remaining=Math.max(1,Math.min(10000,budget));best=Map.of();bestScore=Long.MIN_VALUE;Random random=new Random(seed);Map<String,List<LayoutPlanner.Candidate>> options=new LinkedHashMap<>();
        for(var e:input.entrySet()){List<LayoutPlanner.Candidate> list=new ArrayList<>();for(var c:e.getValue())if(occupied.stream().noneMatch(b->b.conflicts(c.box()))&&clear.test(c))list.add(c);list.sort(Comparator.comparing(c->c.room().id()+"/"+c.origin()+"/"+c.transform()+"/"+c.entranceId()));Collections.shuffle(list,random);options.put(e.getKey(),list);}
        List<String> doors=new ArrayList<>(options.keySet());Collections.sort(doors);Collections.shuffle(doors,random);doors.sort(Comparator.comparingInt(d->d.equals(required)?-1:options.get(d).size()));search(0,doors,options,new LinkedHashMap<>(),occupied,maxRooms,continuation,required,preserve);return new LayoutPlanner.Result(best,(int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,bestScore)));
    }
    private void search(int at,List<String> doors,Map<String,List<LayoutPlanner.Candidate>> options,Map<String,LayoutPlanner.Candidate> chosen,List<LayoutPlanner.Box> occupied,int cap,BiFunction<LayoutPlanner.Candidate,List<LayoutPlanner.Box>,Integer> continuation,String required,Predicate<Collection<LayoutPlanner.Candidate>> preserve){
        if(remaining--<=0)return;long score=chosen.size()*100000L+(required!=null&&chosen.containsKey(required)?10000000L:0);for(var c:chosen.values()){List<LayoutPlanner.Box> other=new ArrayList<>(occupied);chosen.values().stream().filter(x->x!=c).forEach(x->other.add(x.box()));score+=continuation.apply(c,other)*10L+c.connections().size();}if(score>bestScore){bestScore=score;best=new LinkedHashMap<>(chosen);}if(at>=doors.size()||chosen.size()>=cap)return;
        String door=doors.get(at);for(var c:options.get(door)){if(remaining<=0)break;if(chosen.values().stream().anyMatch(x->x.box().conflicts(c.box())))continue;chosen.put(door,c);if(preserve.test(chosen.values()))search(at+1,doors,options,chosen,occupied,cap,continuation,required,preserve);chosen.remove(door);}if(remaining>0)search(at+1,doors,options,chosen,occupied,cap,continuation,required,preserve);
    }
}
