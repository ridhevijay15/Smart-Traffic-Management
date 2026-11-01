import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class TrafficController {

    private RoadNetwork roadNetwork;
    private Map<String, EmergencyVehicle> vehicles;
    private Map<String, RoutingRequest> requests;
    private PriorityQueue<RoutingRequest> requestQueue;
    private ArrayDeque<TrafficEvent> trafficEvents;
    private List<ProcessedRoute> lastProcessedRoutes;
    private List<ProcessedRoute> processedRouteHistory;
    private List<TrafficEvent> processedTrafficEventHistory;

    public TrafficController(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
        vehicles = new HashMap<>();
        requests = new HashMap<>();
        requestQueue = new PriorityQueue<>();
        trafficEvents = new ArrayDeque<>();
        lastProcessedRoutes = new ArrayList<>();
        processedRouteHistory = new ArrayList<>();
        processedTrafficEventHistory = new ArrayList<>();
    }

    public void addVehicle(EmergencyVehicle vehicle) {
        vehicles.put(vehicle.getVehicleId(), vehicle);
    }

    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    public List<EmergencyVehicle> getVehicles() {
        return new ArrayList<>(vehicles.values());
    }

    public int getPendingRequestCount() {
        return requestQueue.size();
    }

    public List<RoutingRequest> getPendingRequests() {
        PriorityQueue<RoutingRequest> pending = new PriorityQueue<>(requestQueue);
        List<RoutingRequest> requests = new ArrayList<>();
        while (!pending.isEmpty()) {
            requests.add(pending.poll());
        }
        return requests;
    }

    public int getPendingEventCount() {
        return trafficEvents.size();
    }

    public List<ProcessedRoute> getLastProcessedRoutes() {
        return new ArrayList<>(lastProcessedRoutes);
    }

    public List<ProcessedRoute> getProcessedRouteHistory() {
        return new ArrayList<>(processedRouteHistory);
    }

    public List<ProcessedRoute> getProcessedRoutesForVehicle(String vehicleId) {
        List<ProcessedRoute> routes = new ArrayList<>();
        for (ProcessedRoute processedRoute : processedRouteHistory) {
            if (processedRoute.getRequest().getVehicleId().equals(vehicleId)) {
                routes.add(processedRoute);
            }
        }
        return routes;
    }

    public void restoreProcessedRoute(ProcessedRoute processedRoute) {
        processedRouteHistory.add(processedRoute);
        requests.put(processedRoute.getRequest().getRequestId(),
                processedRoute.getRequest());
    }

    public void restoreTrafficEventHistory(List<TrafficEvent> events) {
        processedTrafficEventHistory.addAll(events);
    }

    public List<TrafficEvent> getProcessedTrafficEventHistory() {
        return new ArrayList<>(processedTrafficEventHistory);
    }

    public String getVehiclePosition(String vehicleId) {
        EmergencyVehicle vehicle = vehicles.get(vehicleId);
        return vehicle == null ? null : vehicle.getCurrentJunction();
    }

    public boolean setVehiclePosition(String vehicleId, String junction) {
        EmergencyVehicle vehicle = vehicles.get(vehicleId);

        if (vehicle == null) {
            System.out.println("Vehicle not found.");
            return false;
        }

        if (!roadNetwork.hasJunction(junction)) {
            System.out.println("Junction not found.");
            return false;
        }

        vehicle.updatePosition(junction);
        System.out.println(vehicleId + " is now at " + junction + ".");
        return true;
    }

    public void displayVehicles() {
        System.out.println("\n====== EMERGENCY VEHICLES ======");

        for (EmergencyVehicle vehicle : vehicles.values()) {
            System.out.println(vehicle);
        }
    }

    public boolean addRoutingRequest(RoutingRequest request) {
        if (requests.containsKey(request.getRequestId())) {
            System.out.println("Request ID already exists.");
            return false;
        }

        if (!vehicles.containsKey(request.getVehicleId())) {
            System.out.println("Vehicle not found.");
            return false;
        }

        if (!roadNetwork.hasJunction(request.getSource())) {
            System.out.println("Starting junction not found.");
            return false;
        }

        if (!roadNetwork.hasJunction(request.getDestination())) {
            System.out.println("Destination junction not found.");
            return false;
        }

        String severity = request.getSeverity();
        if (!severity.equalsIgnoreCase("Critical")
                && !severity.equalsIgnoreCase("High")
                && !severity.equalsIgnoreCase("Normal")) {
            System.out.println("Severity must be Critical, High, or Normal.");
            return false;
        }

        requests.put(request.getRequestId(), request);
        requestQueue.add(request);
        System.out.println("\nRouting request added:");
        System.out.println(request);
        return true;
    }

    public List<String> processRoutingRequests() {
        List<String> results = new ArrayList<>();
        lastProcessedRoutes.clear();
        System.out.println("\n====== PROCESSING REQUESTS ======");

        if (requestQueue.isEmpty()) {
            System.out.println("No routing requests to process.");
            results.add("No routing requests to process.");
            return results;
        }

        if (!trafficEvents.isEmpty()) {
            System.out.println("Applying pending traffic events first.");
            processTrafficEvents();
        }

        while (!requestQueue.isEmpty()) {
            RoutingRequest request = requestQueue.poll();
            EmergencyVehicle vehicle = vehicles.get(request.getVehicleId());

            if (vehicle == null) {
                request.setStatus("UNREACHABLE");
                String message = "Vehicle not found for request "
                        + request.getRequestId() + ".";
                System.out.println(message);
                results.add(message);
                addProcessedRoute(new ProcessedRoute(request, null));
                continue;
            }

            request.setSource(vehicle.getCurrentJunction());
            System.out.println("Processing: " + request);

            List<Route> routes = roadNetwork.findFastestRoutes(
                    vehicle.getCurrentJunction(), request.getDestination(), 2);

            if (routes.isEmpty()) {
                request.setStatus("UNREACHABLE");
                String message = "Destination is unreachable for request "
                        + request.getRequestId() + " ("
                        + request.getSeverity().toUpperCase() + " priority)"
                        + " | " + request.getArrivalDetails() + ".";
                System.out.println(message);
                results.add(message);
                addProcessedRoute(new ProcessedRoute(request, null, null, 0));
                continue;
            }

            Route route = routes.get(0);
            Route alternative = routes.size() > 1 ? routes.get(1) : null;
            request.setStatus("ROUTE_READY");
            addProcessedRoute(new ProcessedRoute(request, route, alternative, 0));
            route.displayRoute();
            if (alternative != null) {
                System.out.println("Alternative route: " + joinRoute(alternative)
                        + " | " + alternative.getTotalTravelTime() + " min");
            }
            String message = request.getRequestId() + " | "
                    + vehicle.getVehicleId() + ": " + joinRoute(route)
                    + " | " + route.getTotalTravelTime() + " min"
                    + " | " + request.getSeverity().toUpperCase() + " priority"
                    + " | " + request.getArrivalDetails()
                    + " | route ready; controller must start the trip.";
            System.out.println("Route ready for " + vehicle.getVehicleId()
                    + "; the trip has not started.");
            results.add(message);
        }
        return results;
    }

    public boolean selectRoute(String requestId, int routeChoice) {
        ProcessedRoute processed = findProcessedRoute(requestId);
        if (processed == null || !"ROUTE_READY".equals(
                processed.getRequest().getStatus())) {
            return false;
        }

        Route selected;
        if (routeChoice == 1) {
            selected = processed.getRoute();
        } else if (routeChoice == 2) {
            selected = processed.getAlternativeRoute();
        } else {
            return false;
        }
        if (selected == null) {
            return false;
        }
        if (routeChoice == 2) {
            processed.setAlternativeRoute(processed.getRoute());
        }
        processed.setRoute(selected);
        processed.getRequest().setStatus("ROUTE_SELECTED");
        return true;
    }

    public String advanceTrip(String requestId) {
        ProcessedRoute processed = findProcessedRoute(requestId);
        if (processed == null) {
            return "Routing request was not found.";
        }
        RoutingRequest request = processed.getRequest();
        if (!"ROUTE_READY".equals(request.getStatus())
                && !"ROUTE_SELECTED".equals(request.getStatus())
                && !"IN_PROGRESS".equals(request.getStatus())) {
            return "This trip is not ready to advance.";
        }

        boolean trafficUpdated = !trafficEvents.isEmpty();
        if (trafficUpdated) {
            processTrafficEvents();
        }

        EmergencyVehicle vehicle = vehicles.get(request.getVehicleId());
        if (vehicle == null) {
            request.setStatus("UNREACHABLE");
            processed.setRoute(null);
            processed.setAlternativeRoute(null);
            return "Vehicle not found; trip cannot continue.";
        }

        List<String> junctions = processed.getRoute().getJunctions();
        String currentJunction = vehicle.getCurrentJunction();
        if (trafficUpdated) {
            List<Route> replanned = roadNetwork.findFastestRoutes(
                    currentJunction, request.getDestination(), 2);
            if (replanned.isEmpty()) {
                request.setStatus("UNREACHABLE");
                processed.setRoute(null);
                processed.setAlternativeRoute(null);
                return "Road updates leave no route from " + currentJunction
                        + " to " + request.getDestination() + ".";
            }
            processed.setRoute(replanned.get(0));
            processed.setAlternativeRoute(
                    replanned.size() > 1 ? replanned.get(1) : null);
            processed.setCurrentStep(0);
            junctions = processed.getRoute().getJunctions();
        }
        if (processed.getCurrentStep() >= junctions.size()
                || !junctions.get(processed.getCurrentStep()).equals(currentJunction)) {
            List<Route> replanned = roadNetwork.findFastestRoutes(
                    currentJunction, request.getDestination(), 2);
            if (replanned.isEmpty()) {
                request.setStatus("UNREACHABLE");
                processed.setRoute(null);
                processed.setAlternativeRoute(null);
                return "No route remains from " + currentJunction + " to "
                        + request.getDestination() + ".";
            }
            processed.setRoute(replanned.get(0));
            processed.setAlternativeRoute(
                    replanned.size() > 1 ? replanned.get(1) : null);
            processed.setCurrentStep(0);
            junctions = processed.getRoute().getJunctions();
        }

        if (processed.getCurrentStep() + 1 >= junctions.size()) {
            request.setStatus("COMPLETED");
            return request.getRequestId() + " reached " + request.getDestination() + ".";
        }

        String nextJunction = junctions.get(processed.getCurrentStep() + 1);
        RoadSegment nextRoad = roadNetwork.getRoadBetween(currentJunction, nextJunction);
        if (nextRoad == null || nextRoad.isClosed()) {
            List<Route> replanned = roadNetwork.findFastestRoutes(
                    currentJunction, request.getDestination(), 2);
            if (replanned.isEmpty()) {
                request.setStatus("UNREACHABLE");
                processed.setRoute(null);
                processed.setAlternativeRoute(null);
                return "Road update leaves no route from " + currentJunction
                        + " to " + request.getDestination() + ".";
            }
            processed.setRoute(replanned.get(0));
            processed.setAlternativeRoute(
                    replanned.size() > 1 ? replanned.get(1) : null);
            return advanceTrip(requestId);
        }

        vehicle.updatePosition(nextJunction);
        processed.setCurrentStep(processed.getCurrentStep() + 1);
        request.setStatus(nextJunction.equals(request.getDestination())
                ? "COMPLETED" : "IN_PROGRESS");
        if ("COMPLETED".equals(request.getStatus())) {
            return request.getRequestId() + " reached " + request.getDestination() + ".";
        }
        return request.getRequestId() + " advanced to " + nextJunction
                + " (" + request.getStatus().replace('_', ' ') + ").";
    }

    private ProcessedRoute findProcessedRoute(String requestId) {
        for (ProcessedRoute processedRoute : processedRouteHistory) {
            if (processedRoute.getRequest().getRequestId().equals(requestId)) {
                return processedRoute;
            }
        }
        return null;
    }

    private String joinRoute(Route route) {
        StringBuilder path = new StringBuilder();
        for (String junction : route.getJunctions()) {
            if (path.length() > 0) {
                path.append(" -> ");
            }
            path.append(junction);
        }
        return path.toString();
    }

    private void addProcessedRoute(ProcessedRoute processedRoute) {
        lastProcessedRoutes.add(processedRoute);
        processedRouteHistory.add(processedRoute);
    }

    public boolean hasRoad(String roadId) {
        return roadNetwork.getRoad(roadId) != null;
    }

    public void reportInvalidRoadId(String roadId) {
        System.out.println("Road not found: " + roadId
                + ". Enter a road ID such as R7, not a junction ID such as J7.");
    }

    public boolean addTrafficEvent(TrafficEvent event) {
        if (!hasRoad(event.getRoadId())) {
            reportInvalidRoadId(event.getRoadId());
            return false;
        }

        trafficEvents.add(event);
        System.out.println("\nTraffic event added:");
        System.out.println("Event ID: " + event.getEventId());
        System.out.println(event.getDescription());
        return true;
    }

    public List<String> processTrafficEvents() {
        List<String> processedEvents = new ArrayList<>();
        System.out.println("\n====== PROCESSING TRAFFIC EVENTS ======");

        while (!trafficEvents.isEmpty()) {
            TrafficEvent event = trafficEvents.poll();
            System.out.println("Processing event: " + event.getDescription());
            event.applyToNetwork(roadNetwork);
            processedTrafficEventHistory.add(event);
            processedEvents.add(event.getDescription());
        }
        return processedEvents;
    }

    public void displayNetwork() {
        roadNetwork.displayNetwork();
    }

    public void displayRoadStatus() {
        roadNetwork.displayRoadStatus();
    }
}