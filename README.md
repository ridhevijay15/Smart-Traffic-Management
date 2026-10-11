# VegamTraffic

VegamTraffic is a small Java traffic-control simulation with a browser dashboard. It uses an in-memory, directed road network and sample emergency vehicles; it does not need a database, external API, or third-party Java library.

## Run the website

From the project folder, compile and start the local web server:

```text
javac -d out src\*.java
java -cp out Main --web
```

Open [http://localhost:8080](http://localhost:8080). Stop the server with `Ctrl+C`. The dashboard binds to the local computer and simulation changes reset when the program is restarted.

On first use, create the traffic-controller account. After that, registration
offers a choice between another traffic-controller account and an emergency
vehicle account. Each emergency vehicle can be registered to one account.
Accounts and login sessions are held in memory and reset when the server stops;
passwords are hashed, but this local classroom demo is not production
authentication.

Controllers can view and process requests and manage road events.
Emergency-vehicle accounts can only submit requests for their assigned vehicle;
they can choose a starting junction and destination.
After the controller processes a request, its route map, junction sequence,
and travel time appear in the vehicle's portal.

The controller website is organized into separate Dashboard, Dispatcher,
Roads & Events, and Vehicles pages using the left-side navigation.
After processing route requests, the Dispatcher page shows a route map image:
the selected directed roads and junctions are highlighted, alongside the
request's priority, arrival time, and calculated travel time. Unreachable
requests are shown as such instead of drawing a route.

To run the original menu-driven console program instead:

```text
java -cp out Main
```

## Emergency dispatcher

Emergency vehicle accounts choose a situation, starting junction, destination,
and incident description. The selected starting junction updates that vehicle's
simulated position when the request is accepted.
The server classifies severity from vehicle type and situation; the dispatcher
does not accept a manually entered severity. Those requests enter the same priority queue and are processed
Critical, High, then Normal, with arrival order breaking ties. Each request also
records and displays its local date and time alongside its arrival number.

The console menu also includes `9. Create Dispatcher Request (console)` and
`10. Open Dispatcher Window (GUI)`. To open either directly:

```text
java -cp out Main --dispatcher-console
java -cp out Main --dispatcher-gui
```

The console and Swing dispatchers use the same situation-to-severity mappings.
The console version uses numbered prompts, and the Swing window uses a simple
form with dropdowns.
Each request is assigned to a matching registered vehicle (the first available
unit of that type), and its chosen origin updates that unit's simulated position.

Example request creation:

```java
RoutingRequest request = DispatcherInterface.createRequest(
        controller,
        DispatcherRequestFactory.AMBULANCE,
        "Life-threatening (cardiac, stroke, not breathing)",
        "J2",
        "J8",
        "Patient is having difficulty breathing");

System.out.println(request.getSeverity());    // CRITICAL
System.out.println(request.getVehicleType()); // AMBULANCE
System.out.println(request.getDescription());
controller.addRoutingRequest(request);
```

## Included in this implementation

- Ten junctions, twenty directed road segments (including paired reverse segments), and four sample emergency vehicles.
- Manual vehicle-position updates and route requests with Critical, High, or Normal severity.
- Console and Swing emergency-dispatcher interfaces with situation-based severity classification.
- A priority queue orders route requests by severity and then arrival order.
- Congestion, road-close, and road-open updates wait in a FIFO queue and are applied before the next route calculation.
- Dijkstra's algorithm finds the fastest route through open roads; unreachable destinations are reported.
- Processed routes are shown as server-rendered SVG maps; no browser scripting is needed.
- The dashboard displays current road times and closures, vehicle locations, queue sizes, and route results.

## Java concepts used

- `EmergencyVehicle`, `RoadSegment`, `RoutingRequest`, `TrafficEvent`, `CongestionEvent`, `ClosureEvent`, `Route`, `RoadNetwork`, and `TrafficController` model the project entities.
- Encapsulation keeps road times, closure state, vehicle positions, and event processing inside their classes.
- `CongestionEvent` and `ClosureEvent` inherit from `TrafficEvent` and apply their own network updates.
- `HashMap` stores the road network and vehicles, `PriorityQueue` orders route requests, and `ArrayDeque` processes traffic events in arrival order.
- Dijkstra's algorithm uses a `PriorityQueue` to search the directed, weighted graph.

The website uses plain HTML forms and CSS. The built-in Java HTTP server renders the dashboard and processes form submissions using the same simulation classes as the console application. No JavaScript or third-party web framework is used.

## Try a demo

1. Register the controller account, log in, then register a vehicle account for V1.
2. Log in as V1 and request a Critical route to J8.
3. Log back in as the controller, review the incoming request, and process the queue.
4. Submit congestion and close/open road events, then apply them to the network.
5. Submit requests with different severities to see priority ordering and arrival-order tie breaking.
6. Close the roads needed to reach a destination to see an unreachable-route result.
