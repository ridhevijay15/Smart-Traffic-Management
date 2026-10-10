import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RoutingRequest implements Comparable<RoutingRequest> {

    private static final DateTimeFormatter REQUEST_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String requestId;
    private String vehicleId;
    private String source;
    private String destination;
    private String severity;
    private int arrivalOrder;
    private String vehicleType;
    private String description;
    private LocalDateTime requestedAt;

    public RoutingRequest(String requestId, String vehicleId, String source,
                          String destination, String severity, int arrivalOrder) {
        this(requestId, vehicleId, source, destination, severity, arrivalOrder,
                "", "");
    }

    public RoutingRequest(String requestId, String vehicleId, String source,
                          String destination, String severity, int arrivalOrder,
                          String vehicleType, String description) {
        this.requestId = requestId;
        this.vehicleId = vehicleId;
        this.source = source;
        this.destination = destination;
        this.severity = severity;
        this.arrivalOrder = arrivalOrder;
        this.vehicleType = vehicleType;
        this.description = description;
        this.requestedAt = LocalDateTime.now();
    }

    public String getRequestId() {
        return requestId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public String getSeverity() {
        return severity;
    }

    public int getArrivalOrder() {
        return arrivalOrder;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getRequestedAtFormatted() {
        return requestedAt.format(REQUEST_TIME_FORMAT);
    }

    public String getArrivalDetails() {
        return "Arrival #" + arrivalOrder + " at " + getRequestedAtFormatted();
    }

    private int getSeverityPriority() {
        if (severity.equalsIgnoreCase("Critical")) {
            return 1;
        } else if (severity.equalsIgnoreCase("High")) {
            return 2;
        }
        return 3;
    }

    @Override
    public int compareTo(RoutingRequest other) {
        int severityComparison = Integer.compare(
                getSeverityPriority(), other.getSeverityPriority());

        if (severityComparison != 0) {
            return severityComparison;
        }

        return Integer.compare(arrivalOrder, other.arrivalOrder);
    }

    @Override
    public String toString() {
        String requestDetails = requestId + " | Vehicle: " + vehicleId
                + " | " + source + " -> " + destination
                + " | Severity: " + severity
                + " | " + getArrivalDetails();
        if (!vehicleType.isEmpty()) {
            requestDetails += " | Type: " + vehicleType;
        }
        if (!description.isEmpty()) {
            requestDetails += " | Description: " + description;
        }
        return requestDetails;
    }
}