public class OpenRoadEvent extends TrafficEvent {

    public OpenRoadEvent(String eventId, String roadId, int arrivalOrder) {
        super(eventId, roadId, arrivalOrder);
    }

    @Override
    public void applyToNetwork(RoadNetwork network) {
        RoadSegment road = network.getRoad(getRoadId());

        if (road != null) {
            road.openRoad();
        } else {
            System.out.println("Road not found: " + getRoadId());
        }
    }

    @Override
    public String getDescription() {
        return "Road " + getRoadId() + " is OPEN";
    }
}
