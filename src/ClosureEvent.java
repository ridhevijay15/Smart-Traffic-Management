public class ClosureEvent extends TrafficEvent {

    public ClosureEvent(String eventId,
                        String roadId,
                        int arrivalOrder) {

        super(eventId, roadId, arrivalOrder);
    } 

    @Override
    public void applyToNetwork(RoadNetwork network) {

        RoadSegment road = network.getRoad(getRoadId());

        if (road != null) {
            road.closeRoad();
        } else {
            System.out.println("Road not found: " + getRoadId());
        }
    }

    @Override
    public String getDescription() {

        return "Road " + getRoadId() + " is CLOSED";
    }
}