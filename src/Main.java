import java.util.Scanner;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {

        TrafficController controller = createController();

        if (args.length > 0 && args[0].equalsIgnoreCase("--web")) {
            TrafficWebServer.start(controller);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        if (args.length > 0 && args[0].equalsIgnoreCase("--dispatcher-console")) {
            DispatcherInterface.runConsole(controller, scanner);
            scanner.close();
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("--dispatcher-gui")) {
            DispatcherInterface.showGui(controller);
            return;
        }

        // Keeps track of the order in which requests arrive
        int arrivalCounter = 0;
        int eventArrivalCounter = 0;

        // ==========================================
        // MAIN MENU
        // ==========================================

        int choice;

        do {

            System.out.println("\n========================================");
            System.out.println("   SMART TRAFFIC MANAGEMENT SYSTEM");
            System.out.println("========================================");

            System.out.println("1. Display Road Network");
            System.out.println("2. Display Emergency Vehicles");
            System.out.println("3. Add Routing Request");
            System.out.println("4. Process Routing Requests");
            System.out.println("5. Add Congestion Event");
            System.out.println("6. Add Road Closure Event");
            System.out.println("7. Process Traffic Events");
            System.out.println("8. Display Road Status");
            System.out.println("9. Create Dispatcher Request (console)");
            System.out.println("10. Open Dispatcher Window (GUI)");
            System.out.println("0. Exit");

            System.out.print("\nEnter choice: ");

            choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {

                // ==================================
                // DISPLAY ROAD NETWORK
                // ==================================

                case 1:

                    controller.displayNetwork();

                    break;

                // ==================================
                // DISPLAY EMERGENCY VEHICLES
                // ==================================

                case 2:

                    controller.displayVehicles();

                    break;

                // ==================================
                // ADD ROUTING REQUEST
                // ==================================

                case 3:

                    System.out.print("Enter vehicle ID: ");
                    String vehicleId = scanner.nextLine();

                    if (controller.getVehiclePosition(vehicleId) == null) {
                        System.out.println("Vehicle not found.");
                        break;
                    }

                    System.out.print("Enter starting/current junction: ");
                    String source = scanner.nextLine();

                    if (!controller.setVehiclePosition(vehicleId, source)) {
                        break;
                    }

                    System.out.print("Enter destination junction: ");
                    String destination = scanner.nextLine();

                    System.out.print(
                            "Enter severity (Critical/High/Normal): ");

                    String severity = scanner.nextLine();

                    // Increase arrival order
                    arrivalCounter++;

                    int arrivalOrder = arrivalCounter;
                    String requestId = "REQ-" + arrivalCounter;

                    RoutingRequest request =
                            new RoutingRequest(
                                    requestId,
                                    vehicleId,
                                    source,
                                    destination,
                                    severity,
                                    arrivalOrder
                            );

                    controller.addRoutingRequest(request);

                    break;

                // ==================================
                // PROCESS ROUTING REQUESTS
                // ==================================

                case 4:

                    controller.processRoutingRequests();

                    break;

                // ==================================
                // ADD CONGESTION EVENT
                // ==================================

                case 5:

                    System.out.print("Enter road ID (for example, R7): ");
                    String congestionRoad = scanner.nextLine();

                                        if (!controller.hasRoad(congestionRoad)) {
                                                controller.reportInvalidRoadId(congestionRoad);
                                                break;
                                        }

                    System.out.print("Enter new travel time: ");
                    int newTime = scanner.nextInt();

                    scanner.nextLine();

                    int congestionArrivalOrder = ++eventArrivalCounter;
                    String congestionId = "EV-" + congestionArrivalOrder;

                    CongestionEvent congestionEvent =
                            new CongestionEvent(
                                    congestionId,
                                    congestionRoad,
                                    congestionArrivalOrder,
                                    newTime
                            );

                    controller.addTrafficEvent(
                            congestionEvent);

                    break;

                // ==================================
                // ADD ROAD CLOSURE EVENT
                // ==================================

                case 6:

                    System.out.print("Enter road ID to close (for example, R7): ");
                    String closureRoad = scanner.nextLine();

                                        if (!controller.hasRoad(closureRoad)) {
                                                controller.reportInvalidRoadId(closureRoad);
                                                break;
                                        }

                    int closureArrivalOrder = ++eventArrivalCounter;
                    String closureId = "EV-" + closureArrivalOrder;

                    ClosureEvent closureEvent =
                            new ClosureEvent(
                                    closureId,
                                    closureRoad,
                                    closureArrivalOrder
                            );

                    controller.addTrafficEvent(
                            closureEvent);

                    break;

                // ==================================
                // PROCESS TRAFFIC EVENTS
                // ==================================

                case 7:

                    controller.processTrafficEvents();

                    break;

                // ==================================
                // DISPLAY ROAD STATUS
                // ==================================

                case 8:

                    controller.displayRoadStatus();

                    break;

                case 9:

                    DispatcherInterface.runConsole(controller, scanner);

                    break;

                case 10:

                    DispatcherInterface.showGui(controller);

                    break;

                // ==================================
                // EXIT
                // ==================================

                case 0:

                    System.out.println(
                            "\nExiting Smart Traffic Management System...");

                    break;

                // ==================================
                // INVALID OPTION
                // ==================================

                default:

                    System.out.println(
                            "\nInvalid choice. Please try again.");
            }

        } while (choice != 0);

        scanner.close();
    }

    private static TrafficController createController() {
        RoadNetwork network = new RoadNetwork();

        for (int i = 1; i <= 10; i++) {
            network.addJunction("J" + i);
        }

        network.addRoad(new RoadSegment("R1", "J1", "J2", 5));
        network.addRoad(new RoadSegment("R2", "J2", "J3", 4));
        network.addRoad(new RoadSegment("R3", "J3", "J4", 6));
        network.addRoad(new RoadSegment("R4", "J4", "J5", 5));
        network.addRoad(new RoadSegment("R5", "J5", "J6", 7));
        network.addRoad(new RoadSegment("R6", "J6", "J7", 4));
        network.addRoad(new RoadSegment("R7", "J7", "J8", 6));
        network.addRoad(new RoadSegment("R8", "J8", "J9", 5));
        network.addRoad(new RoadSegment("R9", "J9", "J10", 4));
        network.addRoad(new RoadSegment("R10", "J1", "J4", 8));
        network.addRoad(new RoadSegment("R11", "J2", "J5", 7));
        network.addRoad(new RoadSegment("R12", "J3", "J6", 8));
        network.addRoad(new RoadSegment("R13", "J4", "J8", 9));
        network.addRoad(new RoadSegment("R14", "J6", "J9", 6));
        network.addRoad(new RoadSegment("R15", "J2", "J1", 5));
        network.addRoad(new RoadSegment("R16", "J3", "J2", 4));
        network.addRoad(new RoadSegment("R17", "J4", "J3", 6));
        network.addRoad(new RoadSegment("R18", "J5", "J4", 5));
        network.addRoad(new RoadSegment("R19", "J6", "J5", 7));
        network.addRoad(new RoadSegment("R20", "J7", "J6", 4));

        TrafficController controller = new TrafficController(network);
        controller.addVehicle(new EmergencyVehicle("V1", "Ambulance", "J2"));
        controller.addVehicle(new EmergencyVehicle("V2", "Fire Truck", "J5"));
        controller.addVehicle(new EmergencyVehicle("V3", "Police Vehicle", "J7"));
        controller.addVehicle(new EmergencyVehicle("V4", "Ambulance", "J1"));
        return controller;
    }
}