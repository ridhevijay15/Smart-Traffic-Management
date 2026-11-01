import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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
        return findFastestRoute(source, destination,
                Collections.emptySet(), Collections.emptySet());
    }

    public List<Route> findFastestRoutes(String source, String destination, int limit) {
        List<Route> routes = new ArrayList<>();
        if (limit <= 0) {
            return routes;
        }

        Route fastest = findFastestRoute(source, destination);
        if (fastest == null) {
            return routes;
        }
        routes.add(fastest);

        PriorityQueue<Route> candidates = new PriorityQueue<>((first, second) -> {
            int timeComparison = Integer.compare(
                    first.getTotalTravelTime(), second.getTotalTravelTime());
            return timeComparison != 0
                    ? timeComparison
                    : routeKey(first).compareTo(routeKey(second));
        });
        Set<String> candidateKeys = new HashSet<>();
        Set<String> acceptedKeys = new HashSet<>();
        acceptedKeys.add(routeKey(fastest));

        while (routes.size() < limit) {
            Route previous = routes.get(routes.size() - 1);
            List<String> previousJunctions = previous.getJunctions();
            for (int spurIndex = 0; spurIndex < previousJunctions.size() - 1; spurIndex++) {
                List<String> rootPath = new ArrayList<>(
                        previousJunctions.subList(0, spurIndex + 1));
                String spurJunction = rootPath.get(rootPath.size() - 1);
                Set<String> excludedRoads = new HashSet<>();

                for (Route accepted : routes) {
                    List<String> acceptedJunctions = accepted.getJunctions();
                    if (acceptedJunctions.size() > spurIndex + 1
                            && acceptedJunctions.subList(0, spurIndex + 1).equals(rootPath)) {
                        RoadSegment sharedEdge = getRoadBetween(
                                acceptedJunctions.get(spurIndex),
                                acceptedJunctions.get(spurIndex + 1));
                        if (sharedEdge != null) {
                            excludedRoads.add(sharedEdge.getRoadId());
                        }
                    }
                }

                Set<String> excludedJunctions = new HashSet<>(rootPath);
                excludedJunctions.remove(spurJunction);
                Route spur = findFastestRoute(spurJunction, destination,
                        excludedRoads, excludedJunctions);
                if (spur == null) {
                    continue;
                }

                List<String> combinedJunctions = new ArrayList<>(rootPath);
                combinedJunctions.addAll(spur.getJunctions().subList(1,
                        spur.getJunctions().size()));
                String key = String.join("->", combinedJunctions);
                if (acceptedKeys.contains(key) || !candidateKeys.add(key)) {
                    continue;
                }

                candidates.add(new Route(combinedJunctions,
                        getPathTravelTime(rootPath) + spur.getTotalTravelTime()));
            }

            if (candidates.isEmpty()) {
                break;
            }
            Route next = candidates.poll();
            candidateKeys.remove(routeKey(next));
            routes.add(next);
            acceptedKeys.add(routeKey(next));
        }
        return routes;
    }

    private Route findFastestRoute(String source, String destination,
                                   Set<String> excludedRoads,
                                   Set<String> excludedJunctions) {
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
                if (road.isClosed() || excludedRoads.contains(road.getRoadId())
                        || excludedJunctions.contains(road.getDestination())) {
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

    public RoadSegment getRoadBetween(String source, String destination) {
        for (RoadSegment road : getOutgoingRoads(source)) {
            if (road.getDestination().equals(destination)) {
                return road;
            }
        }
        return null;
    }

    private int getPathTravelTime(List<String> junctions) {
        int travelTime = 0;
        for (int i = 0; i < junctions.size() - 1; i++) {
            RoadSegment road = getRoadBetween(junctions.get(i), junctions.get(i + 1));
            if (road == null || road.isClosed()) {
                throw new IllegalStateException("Route contains an unavailable road.");
            }
            travelTime += road.getTravelTime();
        }
        return travelTime;
    }

    private static String routeKey(Route route) {
        return String.join("->", route.getJunctions());
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