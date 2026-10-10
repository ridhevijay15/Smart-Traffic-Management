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

    public TrafficController(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
        vehicles = new HashMap<>();
        requests = new HashMap<>();
        requestQueue = new PriorityQueue<>();
        trafficEvents = new ArrayDeque<>();
        lastProcessedRoutes = new ArrayList<>();
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

    public int getPendingEventCount() {
        return trafficEvents.size();
    }

    public List<ProcessedRoute> getLastProcessedRoutes() {
        return new ArrayList<>(lastProcessedRoutes);
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
                String message = "Vehicle not found for request "
                        + request.getRequestId() + ".";
                System.out.println(message);
                results.add(message);
                lastProcessedRoutes.add(new ProcessedRoute(request, null));
                continue;
            }

            request.setSource(vehicle.getCurrentJunction());
            System.out.println("Processing: " + request);

            Route route = roadNetwork.findFastestRoute(
                    vehicle.getCurrentJunction(),
                    request.getDestination()
            );

            if (route == null) {
                String message = "Destination is unreachable for request "
                        + request.getRequestId() + " ("
                        + request.getSeverity().toUpperCase() + " priority)"
                        + " | " + request.getArrivalDetails() + ".";
                System.out.println(message);
                results.add(message);
                lastProcessedRoutes.add(new ProcessedRoute(request, null));
                continue;
            }

            route.displayRoute();
            lastProcessedRoutes.add(new ProcessedRoute(request, route));
            vehicle.updatePosition(request.getDestination());
            StringBuilder path = new StringBuilder();
            for (String junction : route.getJunctions()) {
                if (path.length() > 0) {
                    path.append(" -> ");
                }
                path.append(junction);
            }
            String message = request.getRequestId() + " | "
                    + vehicle.getVehicleId() + ": " + path
                    + " | " + route.getTotalTravelTime() + " min"
                    + " | " + request.getSeverity().toUpperCase() + " priority"
                    + " | " + request.getArrivalDetails();
            System.out.println(vehicle.getVehicleId()
                    + " reached " + request.getDestination() + ".");
            results.add(message);
        }
        return results;
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