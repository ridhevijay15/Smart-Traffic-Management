public class RoadSegment {

    private String roadId;
    private String source;
    private String destination;
    private int travelTime;
    private boolean closed;

    public RoadSegment(String roadId, String source,
                       String destination, int travelTime) {

        this.roadId = roadId;
        this.source = source;
        this.destination = destination;
        this.travelTime = travelTime;
        this.closed = false;
    }

    public String getRoadId() {
        return roadId;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public int getTravelTime() {
        return travelTime;
    }

    public boolean isClosed() {
        return closed;
    }

    public void updateTravelTime(int newTravelTime) {
        this.travelTime = newTravelTime;
    }

    public void closeRoad() {
        this.closed = true;
    }

    public void openRoad() {
        this.closed = false;
    }

    @Override
    public String toString() {

        String status;

        if (closed) {
            status = "CLOSED";
        } else {
            status = "OPEN";
        }

        return roadId + ": "
                + source + " -> "
                + destination
                + " | Time: " + travelTime + " min"
                + " | Status: " + status;
    }
}