import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class RoadNetwork {

    private Map<String, List<RoadSegment>> adjacencyList;
    private Map<String, RoadSegment> roads;

    public RoadNetwork() {
        adjacencyList = new HashMap<>();
        roads = new HashMap<>();
    }

    public void addJunction(String junctionId) {
        adjacencyList.putIfAbsent(junctionId, new ArrayList<>());
    }

    public void addRoad(RoadSegment road) {
        addJunction(road.getSource());
        addJunction(road.getDestination());
        adjacencyList.get(road.getSource()).add(road);
        roads.put(road.getRoadId(), road);
    }

    public RoadSegment getRoad(String roadId) {
        return roads.get(roadId);
    }

    public boolean hasJunction(String junctionId) {
        return adjacencyList.containsKey(junctionId);
    }

    public List<String> getJunctions() {
        return new ArrayList<>(adjacencyList.keySet());
    }

    public List<RoadSegment> getRoads() {
        return new ArrayList<>(roads.values());
    }

    public Route findFastestRoute(String source, String destination) {
        if (!hasJunction(source) || !hasJunction(destination)) {
            return null;
        }

        Map<String, Integer> travelTimes = new HashMap<>();
        Map<String, String> previousJunctions = new HashMap<>();
        PriorityQueue<JunctionTime> queue = new PriorityQueue<>();

        travelTimes.put(source, 0);
        queue.add(new JunctionTime(source, 0));

        while (!queue.isEmpty()) {
            JunctionTime current = queue.poll();

            if (current.travelTime != travelTimes.get(current.junction)) {
                continue;
            }

            if (current.junction.equals(destination)) {
                break;
            }

            for (RoadSegment road : getOutgoingRoads(current.junction)) {
                if (road.isClosed()) {
                    continue;
                }

                int newTravelTime = current.travelTime + road.getTravelTime();
                String nextJunction = road.getDestination();

                if (!travelTimes.containsKey(nextJunction)
                        || newTravelTime < travelTimes.get(nextJunction)) {
                    travelTimes.put(nextJunction, newTravelTime);
                    previousJunctions.put(nextJunction, current.junction);
                    queue.add(new JunctionTime(nextJunction, newTravelTime));
                }
            }
        }

        if (!travelTimes.containsKey(destination)) {
            return null;
        }

        List<String> junctions = new ArrayList<>();
        String junction = destination;

        while (junction != null) {
            junctions.add(junction);
            junction = previousJunctions.get(junction);
        }

        Collections.reverse(junctions);
        return new Route(junctions, travelTimes.get(destination));
    }

    public List<RoadSegment> getOutgoingRoads(String junction) {
        return adjacencyList.getOrDefault(junction, new ArrayList<>());
    }

    public void displayNetwork() {
        System.out.println("\n========== ROAD NETWORK ==========");

        for (String junction : adjacencyList.keySet()) {
            System.out.println("\n" + junction + ":");

            for (RoadSegment road : adjacencyList.get(junction)) {
                System.out.println("   " + road);
            }
        }
    }

    public void displayRoadStatus() {
        System.out.println("\n========== ROAD STATUS ==========");

        for (RoadSegment road : roads.values()) {
            System.out.println(road);
        }
    }

    private static class JunctionTime implements Comparable<JunctionTime> {

        private String junction;
        private int travelTime;

        private JunctionTime(String junction, int travelTime) {
            this.junction = junction;
            this.travelTime = travelTime;
        }

        @Override
        public int compareTo(JunctionTime other) {
            int timeComparison = Integer.compare(travelTime, other.travelTime);
            return timeComparison != 0
                    ? timeComparison
                    : junction.compareTo(other.junction);
        }
    }
}