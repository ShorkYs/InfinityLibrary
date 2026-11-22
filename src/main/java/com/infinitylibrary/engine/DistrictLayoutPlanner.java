package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import java.util.*;
import java.util.function.*;

/** Searches complete multi-generation reservations before any room is committed to the world. */
public final class DistrictLayoutPlanner {
    public record Socket(UUID owner,String door,ConnectionPoint world,int depth){}
    public record Edge(UUID parent,String door,LayoutPlanner.Candidate candidate){}
    public record Result(List<Edge> edges,boolean limited,int states) {public Result{edges=List.copyOf(edges);}}
    @FunctionalInterface public interface Candidates {List<LayoutPlanner.Candidate> at(Socket socket,List<LayoutPlanner.Box> obstacles);}
    private record State(List<Edge> edges,List<Socket> open,int loops,long score){}
    public Result plan(List<Socket> roots,List<LayoutPlanner.Box> occupied,List<ConnectionPoint> external,
                       Candidates candidates,BiFunction<ConnectionPoint,List<LayoutPlanner.Box>,TopologySearch.Fit> continuation,
                       Predicate<List<LayoutPlanner.Candidate>> preserves,int maxRooms,int depth,int beam,int budget,long seed){
        return plan(roots,occupied,external,candidates,continuation,preserves,maxRooms,depth,beam,budget,seed,true);
    }
    public Result plan(List<Socket> roots,List<LayoutPlanner.Box> occupied,List<ConnectionPoint> external,Candidates candidates,BiFunction<ConnectionPoint,List<LayoutPlanner.Box>,TopologySearch.Fit> continuation,Predicate<List<LayoutPlanner.Candidate>> preserves,int maxRooms,int depth,int beam,int budget,long seed,boolean allowTerminal){
        if(roots.isEmpty()||maxRooms<1)return new Result(List.of(),false,0);
        final int maxDepth=Math.max(1,Math.min(8,depth));beam=Math.max(1,Math.min(16,beam));budget=Math.max(1,Math.min(2048,budget));
        List<State> frontier=new ArrayList<>(List.of(new State(List.of(),List.copyOf(roots),0,0)));State best=null;int states=0;boolean limited=false;Random random=new Random(seed);
        for(int round=0;round<maxRooms&&!frontier.isEmpty()&&states<budget;round++){
            List<State> next=new ArrayList<>();
            for(State state:frontier){
                Socket socket=state.open().stream().filter(s->s.depth()<maxDepth).findFirst().orElse(null);if(socket==null)continue;
                List<LayoutPlanner.Box> obstacles=new ArrayList<>(occupied);state.edges().forEach(e->obstacles.add(e.candidate().box()));
                List<LayoutPlanner.Candidate> choices=new ArrayList<>(candidates.at(socket,obstacles));
                Map<LayoutPlanner.Candidate,Double> ordering=new HashMap<>();for(var c:choices)ordering.put(c,-Math.log(Math.max(1e-12,random.nextDouble()))/Math.max(.001,c.room().spawnChance()));
                choices.sort(Comparator.<LayoutPlanner.Candidate>comparingLong(c->-priority(c,state,socket)).thenComparingDouble(ordering::get));Map<String,List<LayoutPlanner.Candidate>> groups=new LinkedHashMap<>();for(var c:choices)groups.computeIfAbsent(c.room().id(),k->new ArrayList<>()).add(c);if(groups.values().stream().anyMatch(g->g.size()>8))limited=true;choices=new ArrayList<>();for(int pass=0;pass<8;pass++)for(var group:groups.values())if(pass<group.size())choices.add(group.get(pass));
                int tried=0;for(var raw:choices){var candidate=new LayoutPlanner.Candidate(raw.room(),raw.entranceId(),raw.transform(),raw.origin(),UUID.nameUUIDFromBytes((seed+"/"+raw.room().id()+"/"+raw.origin()+"/"+raw.transform()+"/"+raw.diversity().patches().stream().map(p->p.areaId()+":"+p.choice()).toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),raw.diversity());if(states>=budget){limited=true;break;}if(tried++>=Math.max(8,beam*4)){limited=true;break;}states++;
                    if(obstacles.stream().anyMatch(b->b.conflicts(candidate.box())))continue;
                    List<Edge> edges=new ArrayList<>(state.edges());edges.add(new Edge(socket.owner(),socket.door(),candidate));
                    List<Socket> open=new ArrayList<>(state.open());open.remove(socket);int loops=state.loops();
                    for(var cp:candidate.connections())if(!cp.id().equals(candidate.entranceId())){ConnectionPoint world=candidate.worldDoor(cp);Socket match=open.stream().filter(s->TopologySearch.linked(world,s.world())).findFirst().orElse(null);if(match!=null){open.remove(match);loops++;}else if(external.stream().anyMatch(p->TopologySearch.linked(world,p)))loops++;else open.add(new Socket(candidate.instanceId(),cp.id(),world,socket.depth()+1));}
                    List<LayoutPlanner.Candidate> chosen=edges.stream().map(Edge::candidate).toList();if(!preserves.test(chosen))continue;
                    List<LayoutPlanner.Box> all=new ArrayList<>(occupied);chosen.forEach(c->all.add(c.box()));boolean safe=true;
                    for(Socket exit:open){var fit=continuation.apply(exit.world(),all);if(fit!=TopologySearch.Fit.YES){if(fit==TopologySearch.Fit.UNKNOWN)limited=true;safe=false;break;}}
                    if(!safe||open.isEmpty()&&!allowTerminal)continue;
                    long score=loops*20000L;Map<String,Integer> templates=new HashMap<>();Set<RoomType> roles=new HashSet<>();for(var e:edges){int repeated=templates.merge(e.candidate().room().id(),1,Integer::sum);score+=quality(e.candidate())/repeated;if(roles.add(e.candidate().room().type()))score+=2000;}score+=edges.size()*200L;score-=open.size()*800L;for(var exit:open){long nearest=Long.MAX_VALUE;for(var other:open)if(other!=exit&&exit.world().compatibleWith(other.world())){var delta=exit.world().position().subtract(other.world().position());nearest=Math.min(nearest,Math.abs((long)delta.x())+Math.abs((long)delta.y())+Math.abs((long)delta.z()));}if(nearest!=Long.MAX_VALUE)score-=Math.min(100,nearest)*8;}
                    State child=new State(List.copyOf(edges),List.copyOf(open),loops,score);next.add(child);if((open.isEmpty()||edges.size()>=Math.min(maxRooms,roots.size()+1))&&(best==null||child.score()>best.score()))best=child;
                }
            }
            next.sort(Comparator.comparingLong(State::score).reversed());if(next.size()>beam)next=new ArrayList<>(next.subList(0,beam));frontier=next;
        }
        limited|=states>=budget;return new Result(best==null?List.of():best.edges(),limited,states);
    }
    private static long priority(LayoutPlanner.Candidate c,State state,Socket socket){long repeats=state.edges().stream().filter(e->e.candidate().room().id().equals(c.room().id())).count();long result=quality(c)/(1+repeats);if(state.edges().stream().noneMatch(e->e.candidate().room().type()==c.room().type()))result+=2000;result-=Math.max(0,c.connections().size()-2)*800L;long nearest=Long.MAX_VALUE;for(var door:c.connections())if(!door.id().equals(c.entranceId()))for(var target:state.open())if(target!=socket&&c.worldDoor(door).compatibleWith(target.world())){var delta=c.worldDoor(door).position().subtract(target.world().position());long distance=Math.abs((long)delta.x())+Math.abs((long)delta.y())+Math.abs((long)delta.z());if(distance==0)result+=20000;nearest=Math.min(nearest,distance);}if(nearest!=Long.MAX_VALUE)result-=Math.min(100,nearest)*8;return result;}
    private static long quality(LayoutPlanner.Candidate c){long volume=(long)c.room().size().x()*c.room().size().y()*c.room().size().z();return (c.room().type()==RoomType.FILLER?0:6000)+Math.min(3000,volume)+Math.max(0,c.connections().size()-1)*80L-(c.connections().size()==1?3000:0);}
}
