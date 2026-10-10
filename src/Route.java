import java.util.List;

public class Route {

    private List<String> junctions;
    private int totalTravelTime;

    public Route(List<String> junctions, int totalTravelTime) {
        this.junctions = junctions;
        this.totalTravelTime = totalTravelTime;
    }

    public List<String> getJunctions() {
        return junctions;
    }

    public int getTotalTravelTime() {
        return totalTravelTime;
    }

    public void displayRoute() {
        System.out.println("\n========== ROUTE RESULT ==========");
        System.out.print("Route: ");

        for (int i = 0; i < junctions.size(); i++) {
            System.out.print(junctions.get(i));

            if (i < junctions.size() - 1) {
                System.out.print(" -> ");
            }
        }

        System.out.println();
        System.out.println("Total Travel Time: "
                + totalTravelTime + " minutes");
    }
}