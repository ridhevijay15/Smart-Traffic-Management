public abstract class TrafficEvent {

    private String eventId;
    private String roadId;
    private int arrivalOrder;

    public TrafficEvent(String eventId,
                         String roadId,
                         int arrivalOrder) {

        this.eventId = eventId;
        this.roadId = roadId;
        this.arrivalOrder = arrivalOrder;
    }

    public String getEventId() {
        return eventId;
    }

    public String getRoadId() {
        return roadId;
    }

    public int getArrivalOrder() {
        return arrivalOrder;
    }

    public abstract void applyToNetwork(RoadNetwork network);

    public abstract String getDescription();
}