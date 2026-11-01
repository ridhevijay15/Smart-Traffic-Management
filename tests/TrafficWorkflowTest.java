import java.util.List;

public class TrafficWorkflowTest {

    public static void main(String[] args) {
        verifiesAlternativesAndMidTripRerouting();
        verifiesUnreachableRequests();
        System.out.println("Traffic workflow tests passed.");
    }

    private static void verifiesAlternativesAndMidTripRerouting() {
        RoadNetwork network = new RoadNetwork();
        network.addRoad(new RoadSegment("R1", "A", "B", 2));
        network.addRoad(new RoadSegment("R2", "B", "D", 2));
        network.addRoad(new RoadSegment("R3", "A", "C", 3));
        network.addRoad(new RoadSegment("R4", "C", "D", 3));
        network.addRoad(new RoadSegment("R5", "C", "B", 1));
        network.addRoad(new RoadSegment("R6", "A", "D", 10));

        List<Route> alternatives = network.findFastestRoutes("A", "D", 2);
        check(alternatives.size() == 2, "Expected two route alternatives.");
        check(alternatives.get(0).getTotalTravelTime() == 4,
                "Fastest route should take four minutes.");
        check(alternatives.get(1).getTotalTravelTime() == 6,
                "Second route should take six minutes.");

        TrafficController controller = new TrafficController(network);
        controller.addVehicle(new EmergencyVehicle("V1", "Ambulance", "A"));
        RoutingRequest request = new RoutingRequest(
                "REQ-1", "V1", "A", "D", "Critical", 1, "AMBULANCE", "Test");
        check(controller.addRoutingRequest(request), "Request should be accepted.");
        controller.processRoutingRequests();

        ProcessedRoute processed = controller.getProcessedRoutesForVehicle("V1").get(0);
        check("ROUTE_READY".equals(request.getStatus()),
                "Processing should produce a route-ready request.");
        check("A".equals(controller.getVehiclePosition("V1")),
                "Calculating a route should not move the vehicle.");
        check(processed.getAlternativeRoute() != null,
                "A second route should be retained for dispatcher selection.");
        check(controller.selectRoute("REQ-1", 2),
                "Controller should be able to select the alternative.");
        check("ROUTE_SELECTED".equals(request.getStatus()),
                "Selecting a route should update request status.");

        check(controller.advanceTrip("REQ-1").contains("advanced to C"),
                "First advance should move the vehicle to C.");
        check("C".equals(controller.getVehiclePosition("V1")),
                "Vehicle position should update one road at a time.");
        check(controller.addTrafficEvent(new ClosureEvent("EV-1", "R4", 1)),
                "Closure event should be accepted.");
        check(controller.advanceTrip("REQ-1").contains("advanced to B"),
                "Closure ahead should cause a reroute through B.");
        check("B".equals(controller.getVehiclePosition("V1")),
                "Rerouting should start from the vehicle's current junction.");
        check(controller.advanceTrip("REQ-1").contains("reached D"),
                "Vehicle should complete the rerouted trip.");
        check("COMPLETED".equals(request.getStatus()),
                "Completed trip should have COMPLETED status.");
        check(controller.getProcessedTrafficEventHistory().size() == 1,
                "Applied traffic events should be available for history.");
    }

    private static void verifiesUnreachableRequests() {
        RoadNetwork network = new RoadNetwork();
        network.addJunction("A");
        network.addJunction("B");
        TrafficController controller = new TrafficController(network);
        controller.addVehicle(new EmergencyVehicle("V2", "Police Vehicle", "A"));
        RoutingRequest request = new RoutingRequest(
                "REQ-2", "V2", "A", "B", "High", 2, "POLICE", "Unreachable");
        check(controller.addRoutingRequest(request), "Request should be accepted.");
        controller.processRoutingRequests();
        check("UNREACHABLE".equals(request.getStatus()),
                "No-route requests should have UNREACHABLE status.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
