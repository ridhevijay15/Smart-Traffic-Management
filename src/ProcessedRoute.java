public class ProcessedRoute {

    private RoutingRequest request;
    private Route route;

    public ProcessedRoute(RoutingRequest request, Route route) {
        this.request = request;
        this.route = route;
    }

    public RoutingRequest getRequest() {
        return request;
    }

    public Route getRoute() {
        return route;
    }

    public boolean isReachable() {
        return route != null;
    }
}
