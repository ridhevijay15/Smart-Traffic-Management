# VegamTraffic

VegamTraffic is a small Java traffic-control simulation with a browser dashboard. It uses an in-memory, directed road network and sample emergency vehicles. The website uses MySQL to save user accounts, vehicle positions, routing requests, and processed routes.

## Project brief

This project simulates the traffic desk for the fictional city of Vegam. Dispatchers receive emergency routing requests and manually entered road-closure and congestion reports. A request's shortest route is not necessarily its fastest route, and a road update can make a previously usable route unreachable. The system therefore applies traffic events in arrival order before calculating the next route, then finds the fastest reachable route through the currently open roads.

The simulation uses synthetic city data and manually entered updates. It does not connect to live GPS, traffic sensors, traffic lights, or external traffic services. Route priority determines which request is processed first; it does not simulate signal pre-emption, collision avoidance, or real-time vehicle movement.

## Requirements coverage

| Project requirement | Implementation |
| --- | --- |
| Use 10–15 junctions, four emergency vehicles, and at least 20 traffic/routing events | The sample network has 10 junctions, 20 directed road segments, and four vehicles. Requests and traffic events can be entered repeatedly through the console or website. |
| Model directed roads with positive travel times; represent two-way roads as paired edges | The sample network is directed and includes reverse road segments where specified. |
| Assign Critical, High, or Normal severity and order requests by severity, then arrival | The dispatcher classifies severity from vehicle type and situation; the priority queue uses arrival order to break ties. |
| Manually enter vehicle positions, destinations, closures, and congestion updates | Available through the controller's Vehicles, Dispatcher, and Roads & Events pages. The console also supports simulation operations. |
| Apply traffic events FIFO before the next route calculation | Congestion and open/close events are queued and applied before route requests are calculated. |
| Recalculate from the vehicle's current simulated junction and report unreachable destinations | Routing uses the current vehicle position and open roads. Unreachable requests are reported instead of displaying a route. |
| Demonstrate closure during travel, equal-severity requests, unreachable routes, and reversed one-way access | Equal-severity ordering, unreachable destinations, and directed-road behavior can be demonstrated. Vehicle movement is simulated as a completed route, not continuous travel, so a closure cannot interrupt a vehicle mid-route; applying a closure before processing demonstrates its effect on the next route calculation. |

## Connect MySQL and run the website

1. Install MySQL Server and start it.
2. In MySQL Workbench, create the project database:

   ```sql
   CREATE DATABASE vegam_traffic;
   ```

3. Download the **Platform Independent ZIP** for MySQL Connector/J and copy its
   `mysql-connector-j-*.jar` file into the project's `lib` folder. The driver
   JAR is not committed to Git.
4. Create `db.properties` from the example and enter your local MySQL password:

   ```powershell
   Copy-Item db.properties.example db.properties
   ```

   `db.properties` is ignored by Git. Never commit your database password.
   You can also override these settings with `VEGAM_DB_URL`, `VEGAM_DB_USER`,
   and `VEGAM_DB_PASSWORD` environment variables.
5. From the project folder, compile and run the website:

   ```powershell
   javac -encoding UTF-8 -d out src\*.java
   java -cp "out;lib\*" Main --web
   ```

The application creates its tables the first time it connects. Open
[http://localhost:8080](http://localhost:8080). Stop the server with `Ctrl+C`.
Accounts, vehicle positions, pending requests, and processed route results
remain in MySQL when the server restarts. Log in again after a restart because
web sessions are kept in memory. The road network and traffic event simulation
are still reset when the Java program restarts.

To run the original menu-driven console program (which does not use the
database):

```powershell
javac -encoding UTF-8 -d out src\*.java
java -cp out Main
```

On first use, create the traffic-controller account. After that, registration
offers a choice between another traffic-controller account and an emergency
vehicle account. Each emergency vehicle can be registered to one account.
Passwords are hashed before they are stored. This local classroom demo is not
production authentication.

Controllers can view and process requests and manage road events.
They can also manually update an emergency vehicle's simulated position from
the Vehicles page; the position is saved in MySQL.
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
- Manual vehicle-position updates, destinations, and route requests with Critical, High, or Normal severity.
- Console and Swing emergency-dispatcher interfaces with situation-based severity classification.
- A priority queue orders route requests by severity and then arrival order.
- Congestion, road-close, and road-open updates wait in a FIFO queue and are applied before the next route calculation.
- Dijkstra's algorithm finds the fastest route through open roads; unreachable destinations are reported.
- Processed routes are shown as server-rendered SVG maps; no browser scripting is needed.
- The dashboard displays current road times and closures, vehicle locations, queue sizes, and route results.

## Data structures and algorithms

- A directed, weighted adjacency-list graph represents junctions and road travel times. A road closure excludes that edge from route search without deleting the road.
- A `PriorityQueue<RoutingRequest>` processes higher-severity requests first and uses arrival order to break ties.
- An `ArrayDeque<TrafficEvent>` applies congestion and road-status changes in first-in, first-out order.
- `HashMap` instances provide lookup by vehicle, request, junction, and road identifier.
- Dijkstra's algorithm uses a Java `PriorityQueue` as a min-heap to find the minimum-travel-time route through open roads.

These structures fit the simulation's needs: graph traversal supports directed routing, queues provide the required processing order, and maps make entity lookup direct. The current routing implementation is `RoadNetwork.findFastestRoute`; it does not use a separate `RoutePlanner` class.

## Deliverables and verification

- **Working Java application and source code:** included in `src`.
- **Problem analysis, assumptions, and processing rules:** summarized above and in the sections below.
- **Data-structure and algorithm justification:** summarized above and in Java concepts below.
- **Sample input and test results:** use the demo steps below as manual scenarios. No automated test suite or recorded test-results document is currently included.
- **UML class diagram and screenshot-based project report:** not currently included in the repository; add them separately if they are required for submission.

For a manual demonstration, include normal routing, equal-severity requests to show arrival-order tie-breaking, a closure or congestion event applied before route processing, an unreachable destination after closing needed roads, and a one-way route attempt in each direction.

## Java concepts used

- `EmergencyVehicle`, `RoadSegment`, `RoutingRequest`, `TrafficEvent`, `CongestionEvent`, `ClosureEvent`, `Route`, `RoadNetwork`, and `TrafficController` model the project entities.
- Encapsulation keeps road times, closure state, vehicle positions, and event processing inside their classes.
- `CongestionEvent` and `ClosureEvent` inherit from `TrafficEvent` and apply their own network updates.
- `HashMap` stores vehicles, requests, and roads; `PriorityQueue` orders route requests and supports Dijkstra's min-heap; and `ArrayDeque` processes traffic events in arrival order.
- Dijkstra's algorithm uses a `PriorityQueue` to search the directed, weighted graph.

**Future enhancements:** add automated tests and recorded test results, create the required UML diagram and project report, and optionally persist road and traffic-event state across server restarts. Continuous travel and mid-route rerouting would require a time-stepped vehicle simulation and are outside the current model.

The website uses plain HTML forms and CSS. The built-in Java HTTP server renders the dashboard and processes form submissions using the same simulation classes as the console application. No JavaScript or third-party web framework is used.

## Try a demo

1. Register the controller account, log in, then register a vehicle account for V1.
2. Log in as V1 and request a Critical route to J8.
3. Log back in as the controller, review the incoming request, and process the queue.
4. Submit congestion and close/open road events, then apply them to the network.
5. Submit requests with different severities to see priority ordering and arrival-order tie breaking.
6. Close the roads needed to reach a destination to see an unreachable-route result.
