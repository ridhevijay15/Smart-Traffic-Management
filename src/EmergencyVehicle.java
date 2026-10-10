public class EmergencyVehicle {

    private String vehicleId;
    private String type;
    private String currentJunction;
    private boolean available;

    public EmergencyVehicle(String vehicleId,
                            String type,
                            String currentJunction) {

        this.vehicleId = vehicleId;
        this.type = type;
        this.currentJunction = currentJunction;
        this.available = true;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getType() {
        return type;
    }

    public String getCurrentJunction() {
        return currentJunction;
    }

    public boolean isAvailable() {
        return available;
    }

    public void updatePosition(String newJunction) {
        this.currentJunction = newJunction;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {

        return vehicleId
                + " | Type: " + type
                + " | Location: " + currentJunction
                + " | Available: " + available;
    }
}