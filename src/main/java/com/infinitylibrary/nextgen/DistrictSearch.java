package com.infinitylibrary.nextgen;

import java.util.*;
import java.util.function.*;

/** Best-first search over whole immutable district proposals, not individual door placements. */
public final class DistrictSearch {
    public enum Status { COMPLETE, EXHAUSTED, BUDGET_REACHED }
    public record Result(Status status,Optional<DistrictPlan> plan,int evaluated){}
    private record Node(DistrictPlan plan,int depth,long sequence){}
    public Result search(DistrictPlan initial,Function<DistrictPlan,List<DistrictPlan>> expand,
                         Predicate<DistrictPlan> goal,ToLongFunction<DistrictPlan> score,
                         int maxStates,int maxDepth,int maxFrontier) {
        if(maxStates<1||maxDepth<0||maxFrontier<1)throw new IllegalArgumentException("Invalid search limits");
        Comparator<Node> order=Comparator.<Node>comparingLong(n->score.applyAsLong(n.plan)).reversed().thenComparingLong(Node::sequence);
        PriorityQueue<Node> queue=new PriorityQueue<>(order);Set<DistrictPlan> seen=new HashSet<>();queue.add(new Node(initial,0,0));seen.add(initial);int evaluated=0;long sequence=1;boolean truncated=false;
        while(!queue.isEmpty()&&evaluated<maxStates){Node node=queue.remove();evaluated++;if(goal.test(node.plan))return new Result(Status.COMPLETE,Optional.of(node.plan),evaluated);
            if(node.depth>=maxDepth){truncated=true;continue;}
            for(DistrictPlan next:expand.apply(node.plan)){if(!seen.add(next))continue;queue.add(new Node(next,node.depth+1,sequence++));if(queue.size()>maxFrontier){Node worst=queue.stream().max(order).orElseThrow();queue.remove(worst);truncated=true;}}
        }
        return new Result(!queue.isEmpty()||truncated?Status.BUDGET_REACHED:Status.EXHAUSTED,Optional.empty(),evaluated);
    }
}
