package com.infinitylibrary.engine;

import java.util.*;
import java.util.function.*;

/** Bounded minimum-remaining-values constraint search across a neighborhood's open sockets. */
public final class ConstraintLayoutPlanner {
    private int budget,nodes;private Map<String,LayoutPlanner.Candidate> best=Map.of();private long bestScore=Long.MIN_VALUE,bestPrimary=Long.MIN_VALUE;
    public int nodes(){return nodes;}
    public LayoutPlanner.Result plan(Map<String,List<LayoutPlanner.Candidate>> input,List<LayoutPlanner.Box> occupied,int maxRooms,Predicate<LayoutPlanner.Candidate> clear,BiFunction<LayoutPlanner.Candidate,List<LayoutPlanner.Box>,Integer> continuation,String required,Predicate<Collection<LayoutPlanner.Candidate>> preserve,long seed,int maxNodes){
        budget=Math.max(1,Math.min(4096,maxNodes));nodes=0;best=Map.of();bestScore=Long.MIN_VALUE;bestPrimary=Long.MIN_VALUE;Random random=new Random(seed);Map<String,List<LayoutPlanner.Candidate>> domains=new LinkedHashMap<>();
        for(var e:input.entrySet()){List<LayoutPlanner.Candidate> candidates=new ArrayList<>();for(var c:e.getValue())if(occupied.stream().noneMatch(b->b.conflicts(c.box()))&&clear.test(c))candidates.add(c);candidates.sort(Comparator.comparing(c->c.room().id()+"/"+c.origin()+"/"+c.transform()+"/"+c.entranceId()));Map<LayoutPlanner.Candidate,Double> order=new HashMap<>();for(var c:candidates)order.put(c,-Math.log(Math.max(1e-12,random.nextDouble()))/Math.max(.0001,c.room().spawnChance()));candidates.sort(Comparator.comparingDouble(order::get));domains.put(e.getKey(),candidates);}
        search(domains,new LinkedHashMap<>(),occupied,maxRooms,continuation,required,preserve);return new LayoutPlanner.Result(best,(int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,bestScore)));
    }
    private void search(Map<String,List<LayoutPlanner.Candidate>> domains,Map<String,LayoutPlanner.Candidate> chosen,List<LayoutPlanner.Box> occupied,int cap,BiFunction<LayoutPlanner.Candidate,List<LayoutPlanner.Box>,Integer> continuation,String required,Predicate<Collection<LayoutPlanner.Candidate>> preserve){
        if(nodes>=budget)return;nodes++;long score=chosen.size()*100000L+(required!=null&&chosen.containsKey(required)?10000000L:0);if(score>=bestPrimary){long detailed=score;for(var c:chosen.values()){List<LayoutPlanner.Box> other=new ArrayList<>(occupied);chosen.values().stream().filter(x->x!=c).forEach(x->other.add(x.box()));detailed+=continuation.apply(c,other)*10L+c.connections().size();}if(score>bestPrimary||detailed>bestScore){bestPrimary=score;bestScore=detailed;best=new LinkedHashMap<>(chosen);}}
        if(domains.isEmpty()||chosen.size()>=cap||nodes>=budget)return;
        String socket=domains.containsKey(required)?required:domains.keySet().stream().min(Comparator.comparingInt(k->domains.get(k).size())).orElseThrow();
        for(var candidate:domains.get(socket)){if(nodes>=budget)break;if(chosen.values().stream().anyMatch(c->c.box().conflicts(candidate.box())))continue;chosen.put(socket,candidate);
            if(preserve.test(chosen.values())){Map<String,List<LayoutPlanner.Candidate>> next=new LinkedHashMap<>();domains.forEach((key,list)->{if(!key.equals(socket))next.put(key,list.stream().filter(c->!c.box().conflicts(candidate.box())).toList());});search(next,chosen,occupied,cap,continuation,required,preserve);}chosen.remove(socket);
        }
        if(nodes<budget){Map<String,List<LayoutPlanner.Candidate>> skip=new LinkedHashMap<>(domains);skip.remove(socket);search(skip,chosen,occupied,cap,continuation,required,preserve);}
    }
}
