package com.infinitylibrary.community;

import com.infinitylibrary.model.Vector3i;
import java.util.*;

/** Incremental cardinal A*: no straight-line fallback through walls and no diagonal corner cutting. */
public final class WalkPathfinder {
    public interface Terrain {boolean stand(Vector3i p);boolean clear(Vector3i p);boolean climb(Vector3i p);}
    public enum Status { SEARCHING, FOUND, FAILED }
    private record Node(Vector3i pos,int g,int f){}
    private final Terrain terrain;private final Vector3i goal;private final int maxNodes,maxRange;
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::f).thenComparingInt(Node::g));
    private final Map<Vector3i,Integer> costs=new HashMap<>();private final Map<Vector3i,Vector3i> previous=new HashMap<>();
    private final Vector3i start;private Status status=Status.SEARCHING;private List<Vector3i> path=List.of();private int expanded;
    public WalkPathfinder(Terrain terrain,Vector3i start,Vector3i goal,int maxNodes,int maxRange){this.terrain=terrain;this.start=start;this.goal=goal;this.maxNodes=maxNodes;this.maxRange=maxRange;if(!terrain.stand(start)||!terrain.stand(goal)){status=Status.FAILED;return;}costs.put(start,0);open.add(new Node(start,0,heuristic(start)));}
    private int heuristic(Vector3i p){return Math.abs(p.x()-goal.x())+Math.abs(p.z()-goal.z())+Math.abs(p.y()-goal.y());}
    public Status status(){return status;}public int expanded(){return expanded;}public List<Vector3i> path(){return path;}
    public int advance(int budget){int used=0;while(status==Status.SEARCHING&&used<budget){
        if(open.isEmpty()||expanded>=maxNodes){status=Status.FAILED;break;}Node n=open.poll();used++;if(n.g()!=costs.getOrDefault(n.pos(),Integer.MAX_VALUE))continue;expanded++;
        if(n.pos().equals(goal)){List<Vector3i> result=new ArrayList<>();for(Vector3i p=goal;p!=null;p=previous.get(p))result.add(p);Collections.reverse(result);path=List.copyOf(result);status=Status.FOUND;break;}
        for(Vector3i p:neighbors(n.pos())){if(Math.abs(p.x()-start.x())>maxRange||Math.abs(p.z()-start.z())>maxRange||Math.abs(p.y()-start.y())>maxRange)continue;int g=n.g()+1+Math.abs(p.y()-n.pos().y());if(g>=costs.getOrDefault(p,Integer.MAX_VALUE))continue;costs.put(p,g);previous.put(p,n.pos());open.add(new Node(p,g,g+heuristic(p)));}
    }return used;}
    private List<Vector3i> neighbors(Vector3i p){List<Vector3i> out=new ArrayList<>();for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){Vector3i q=p.add(new Vector3i(d[0],0,d[1]));if(terrain.stand(q)){out.add(q);continue;}Vector3i up=q.add(new Vector3i(0,1,0));if(terrain.stand(up)&&terrain.clear(p.add(new Vector3i(0,2,0)))){out.add(up);continue;}
        // Drop at most two blocks, but never tunnel through a solid cell on the way down.
        if(terrain.clear(q))for(int drop=1;drop<=2;drop++){Vector3i down=q.add(new Vector3i(0,-drop,0));if(terrain.stand(down)){out.add(down);break;}if(!terrain.clear(down))break;}
    }
    for(int dy:new int[]{-1,1}){Vector3i q=p.add(new Vector3i(0,dy,0));if((terrain.climb(p)||terrain.climb(q))&&terrain.stand(q))out.add(q);}return out;}
}
