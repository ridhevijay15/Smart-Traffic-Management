public class CongestionEvent extends TrafficEvent {

    private int newTravelTime;

    public CongestionEvent(String eventId,
                           String roadId,
                           int arrivalOrder,
                           int newTravelTime) {

        super(eventId, roadId, arrivalOrder);

        this.newTravelTime = newTravelTime;
    }

    public int getNewTravelTime() {
        return newTravelTime;
    }

    @Override
    public void applyToNetwork(RoadNetwork network) {

        RoadSegment road = network.getRoad(getRoadId());

        if (road == null) {
            System.out.println("Road not found: " + getRoadId());
        } else if (newTravelTime <= 0) {
            System.out.println("Travel time must be positive. Update ignored.");
        } else {
            road.updateTravelTime(newTravelTime);
        }
    }

    @Override
    public String getDescription() {

        return "Congestion on " + getRoadId()
                + " | New travel time: "
                + newTravelTime + " minutes";
    }
}