import java.util.Arrays;
import java.util.List;

public class DispatcherRequestFactory {

    public static final String AMBULANCE = "AMBULANCE";
    public static final String FIRE_TRUCK = "FIRE_TRUCK";
    public static final String POLICE = "POLICE";

    private DispatcherRequestFactory() {
    }

    public static List<String> getSituations(String vehicleType) {
        if (vehicleType == null) {
            throw new IllegalArgumentException("Choose a supported vehicle type.");
        }
        switch (vehicleType) {
            case AMBULANCE:
                return Arrays.asList(
                        "Life-threatening (cardiac, stroke, not breathing)",
                        "Serious injury (accident, bleeding, fracture)",
                        "Routine transfer (scheduled, stable)");
            case FIRE_TRUCK:
                return Arrays.asList(
                        "Active threat (people trapped, fire active)",
                        "Fire emergency (rescue needed)",
                        "Routine (inspection, maintenance)");
            case POLICE:
                return Arrays.asList(
                        "Active crime (armed, hostage, in progress)",
                        "Urgent response (accident, theft)",
                        "Routine (report, traffic, violation)");
            default:
                throw new IllegalArgumentException("Choose a supported vehicle type.");
        }
    }

    public static String getSeverity(String vehicleType, String situation) {
        List<String> situations = getSituations(vehicleType);
        int situationIndex = situations.indexOf(situation);
        switch (situationIndex) {
            case 0:
                return "CRITICAL";
            case 1:
                return "HIGH";
            case 2:
                return "NORMAL";
            default:
                throw new IllegalArgumentException(
                        "Choose a situation for the selected vehicle type.");
        }
    }

    public static String getVehicleModelType(String vehicleType) {
        switch (vehicleType) {
            case AMBULANCE:
                return "Ambulance";
            case FIRE_TRUCK:
                return "Fire Truck";
            case POLICE:
                return "Police Vehicle";
            default:
                throw new IllegalArgumentException("Choose a supported vehicle type.");
        }
    }

    public static RoutingRequest createRequest(TrafficController controller,
                                               String requestId,
                                               int arrivalOrder,
                                               String vehicleType,
                                               String situation,
                                               String source,
                                               String destination,
                                               String description) {
        String severity = getSeverity(vehicleType, situation);
        if (!controller.getRoadNetwork().hasJunction(source)) {
            throw new IllegalArgumentException("Origin junction was not found.");
        }
        if (!controller.getRoadNetwork().hasJunction(destination)) {
            throw new IllegalArgumentException("Destination junction was not found.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a short incident description.");
        }

        EmergencyVehicle vehicle = findVehicle(controller, vehicleType);
        if (vehicle == null) {
            throw new IllegalArgumentException(
                    "No " + getVehicleModelType(vehicleType)
                            + " is registered in the traffic controller.");
        }

        return new RoutingRequest(requestId, vehicle.getVehicleId(),
                source, destination, severity, arrivalOrder,
                vehicleType, description.trim());
    }

    public static EmergencyVehicle findVehicle(TrafficController controller,
                                               String vehicleType) {
        String modelType = getVehicleModelType(vehicleType);
        EmergencyVehicle selectedVehicle = null;
        for (EmergencyVehicle vehicle : controller.getVehicles()) {
            if (vehicle.isAvailable()
                    && vehicle.getType().equalsIgnoreCase(modelType)
                    && (selectedVehicle == null
                    || vehicle.getVehicleId().compareTo(selectedVehicle.getVehicleId()) < 0)) {
                selectedVehicle = vehicle;
            }
        }
        return selectedVehicle;
    }
}
