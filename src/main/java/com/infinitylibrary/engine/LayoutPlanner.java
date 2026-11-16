package com.infinitylibrary.engine;

import com.infinitylibrary.model.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiFunction;

/** Pure bounded beam search: compare complete sibling layouts before committing blocks. */
public final class LayoutPlanner {
    public record Box(Vector3i origin, Vector3i size) {
        public boolean overlaps(Box other) {
            return origin.x() < (long)other.origin.x()+other.size.x() && (long)origin.x()+size.x()>other.origin.x()
                && origin.y() < (long)other.origin.y()+other.size.y() && (long)origin.y()+size.y()>other.origin.y()
                && origin.z() < (long)other.origin.z()+other.size.z() && (long)origin.z()+size.z()>other.origin.z();
        }
    }
    public record Candidate(Room room, String entranceId, RoomTransform transform, Vector3i origin) {
        public Box box() { return new Box(origin, transform.transformedSize(room.size())); }
        public ConnectionPoint worldDoor(ConnectionPoint cp) {
            ConnectionPoint t = cp.transform(transform,room.size());
            return new ConnectionPoint(cp.id(),origin.add(t.position()),t.direction(),t.width(),t.height(),t.floor());
        }
    }
    public record Result(Map<String,Candidate> neighbors, int continuationScore) {
        public Result { neighbors = Collections.unmodifiableMap(new LinkedHashMap<>(neighbors)); }
    }
    private record State(Map<String,Candidate> chosen, List<Box> boxes, int score) {}

    public Result plan(Map<String,List<Candidate>> options, List<Box> occupied, int beamWidth, int maxRooms,
                       Predicate<Candidate> worldClear, BiFunction<Candidate,List<Box>,Integer> continuationScore, String requiredDoor) {
        // Validate each candidate once, never on every search branch.
        Map<String,List<Candidate>> valid = new LinkedHashMap<>();
        Map<Candidate,Integer> scores = new HashMap<>();
        for (var entry : options.entrySet()) {
            List<Candidate> candidates = new ArrayList<>();
            for (Candidate c : entry.getValue()) {
                if (occupied.stream().noneMatch(b -> b.overlaps(c.box())) && worldClear.test(c)) {
                    candidates.add(c); scores.put(c, continuationScore.apply(c,occupied));
                }
            }
            valid.put(entry.getKey(),candidates);
        }
        List<String> doors = new ArrayList<>(valid.keySet());
        // Most constrained doors first, while the beam retains alternative arrangements.
        doors.sort(Comparator.<String>comparingInt(d -> d.equals(requiredDoor)?-1:valid.get(d).size()));
        List<State> beam = List.of(new State(Map.of(),List.of(),0));
        Comparator<State> bestFirst = Comparator.<State>comparingInt(s -> requiredDoor!=null&&s.chosen.containsKey(requiredDoor)?1:0).reversed()
            .thenComparing(Comparator.comparingInt((State s) -> s.chosen.size()).reversed())
            .thenComparing(Comparator.comparingInt((State s) -> s.score).reversed());
        for (String door : doors) {
            List<State> next = new ArrayList<>();
            for (State state : beam) {
                next.add(state); // A temporarily unfillable door remains open for a later attempt.
                if (state.chosen.size() >= maxRooms) continue;
                for (Candidate c : valid.get(door)) {
                    if (state.boxes.stream().anyMatch(b -> b.overlaps(c.box()))) continue;
                    Map<String,Candidate> chosen = new LinkedHashMap<>(state.chosen); chosen.put(door,c);
                    List<Box> boxes = new ArrayList<>(state.boxes); boxes.add(c.box());
                    next.add(new State(chosen,boxes,state.score+scores.get(c)));
                }
            }
            next.sort(bestFirst);
            beam = next.subList(0,Math.min(Math.max(1,beamWidth),next.size()));
        }
        List<State> evaluated = new ArrayList<>();
        for (State state : beam) {
            int score = 0;
            for (Candidate c : state.chosen.values()) {
                List<Box> others = new ArrayList<>(occupied);
                state.chosen.values().stream().filter(other -> other != c).forEach(other -> others.add(other.box()));
                score += continuationScore.apply(c,others);
            }
            evaluated.add(new State(state.chosen,state.boxes,score));
        }
        evaluated.sort(bestFirst);
        State best = evaluated.getFirst();
        return new Result(best.chosen,best.score);
    }

    /** Geometric one-step lookahead for every unused exit of the candidate. */
    public int continuationScore(Candidate candidate, Collection<Room> catalog, List<Box> occupied) {
        List<Box> obstacles = new ArrayList<>(occupied); obstacles.add(candidate.box());
        int score = 0;
        int exitsChecked = 0;
        for (ConnectionPoint exit : candidate.room.connections()) {
            if (exit.id().equals(candidate.entranceId)) continue;
            if(exitsChecked++>=16) break;
            int checks=0;
            ConnectionPoint target = candidate.worldDoor(exit);
            boolean fits = false;
            search: for (Room room : catalog) {
                if (room.spawnChance() <= 0) continue;
                for (ConnectionPoint cp : room.connections()) for (RoomTransform transform : RoomTransform.all()) {
                    if(++checks>512) break search;
                    ConnectionPoint entrance = cp.transform(transform,room.size());
                    if (!target.compatibleWith(entrance)) continue;
                    Vector3i origin = target.position().add(new Vector3i(target.direction().getModX(),target.direction().getModY(),target.direction().getModZ())).subtract(entrance.position());
                    Box box = new Box(origin,transform.transformedSize(room.size()));
                    if (obstacles.stream().noneMatch(b -> b.overlaps(box))) { fits=true; break search; }
                }
            }
            score += fits ? 4 : -3;
        }
        return score;
    }
}
