public class ProcessedRoute {

    private RoutingRequest request;
    private Route route;
    private Route alternativeRoute;
    private int currentStep;

    public ProcessedRoute(RoutingRequest request, Route route) {
        this(request, route, null, 0);
    }

    public ProcessedRoute(RoutingRequest request, Route route,
                         Route alternativeRoute, int currentStep) {
        this.request = request;
        this.route = route;
        this.alternativeRoute = alternativeRoute;
        this.currentStep = currentStep;
    }

    public RoutingRequest getRequest() {
        return request;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
        currentStep = 0;
    }

    public Route getAlternativeRoute() {
        return alternativeRoute;
    }

    public void setAlternativeRoute(Route alternativeRoute) {
        this.alternativeRoute = alternativeRoute;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public boolean isReachable() {
        return route != null;
    }
}
