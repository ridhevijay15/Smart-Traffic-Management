import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class TrafficWebServer {

    private static final String CONTROLLER = "CONTROLLER";
    private static final String VEHICLE = "VEHICLE";
    private static final int PASSWORD_ITERATIONS = 120000;
    private static final int PASSWORD_KEY_LENGTH = 256;

    private final TrafficController controller;
    private final DatabaseStore database;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, UserSession> sessions = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private int requestArrivalOrder;
    private int eventArrivalOrder;
    private String notice = "";
    private boolean noticeIsError;
    private List<String> noticeDetails = Collections.emptyList();
    private UserSession activeSession;

    private TrafficWebServer(TrafficController controller, DatabaseStore database) {
        this.controller = controller;
        this.database = database;
    }

    public static void start(TrafficController controller) throws IOException {
        DatabaseStore database = new DatabaseStore();
        try {
            database.initialize(controller);
        } catch (SQLException exception) {
            throw new IOException("Could not connect to MySQL. Check db.properties, "
                    + "start MySQL, and create the vegam_traffic database.", exception);
        }
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 8080), 0);
        TrafficWebServer webServer = new TrafficWebServer(controller, database);
        try {
            webServer.loadDatabaseState();
        } catch (SQLException exception) {
            throw new IOException("Could not load VegamTraffic data from MySQL.", exception);
        }
        server.createContext("/", webServer::handleRequest);
        server.start();
        System.out.println("VegamTraffic is running at http://localhost:8080 "
                + "with MySQL connected.");
    }

    private void loadDatabaseState() throws SQLException {
        for (Map.Entry<String, String> position : database.loadVehiclePositions().entrySet()) {
            if (!controller.setVehiclePosition(position.getKey(), position.getValue())) {
                throw new SQLException("Saved position is invalid for vehicle "
                        + position.getKey() + ".");
            }
        }
        for (DatabaseStore.AccountRecord record : database.loadAccounts()) {
            Account account = new Account(record.username, record.role, record.vehicleId,
                    record.salt, record.passwordHash);
            accounts.put(record.username.toLowerCase(Locale.ROOT), account);
        }
        for (RoutingRequest request : database.loadPendingRequests()) {
            if (!controller.addRoutingRequest(request)) {
                throw new SQLException("Could not restore pending request "
                        + request.getRequestId() + ".");
            }
        }
        for (ProcessedRoute route : database.loadProcessedRoutes()) {
            controller.restoreProcessedRoute(route);
        }
        requestArrivalOrder = database.getLargestArrivalOrder();
    }

    private synchronized void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/styles.css") && method.equals("GET")) {
            serveStyles(exchange);
            return;
        }

        activeSession = findSession(exchange);
        if (method.equals("POST") && path.equals("/login")) {
            login(exchange, readForm(exchange));
            activeSession = null;
            return;
        }
        if (method.equals("POST") && path.equals("/register")) {
            register(exchange, readForm(exchange));
            activeSession = null;
            return;
        }
        if (method.equals("POST") && path.equals("/logout")) {
            logout(exchange);
            activeSession = null;
            return;
        }
        if (method.equals("GET") && (path.equals("/login") || path.equals("/register"))) {
            if (activeSession != null) {
                redirect(exchange, homeFor(activeSession.account));
            } else {
                serveAuthPage(exchange, path.equals("/register")
                        ? "register.html" : "login.html");
            }
            activeSession = null;
            return;
        }
        if (activeSession == null) {
            redirect(exchange, "/login");
            return;
        }
        if (!isAllowed(activeSession.account, method, path)) {
            sendHtml(exchange, 403, "<h1>Access denied</h1><p>Your account cannot access this page or action.</p>");
            activeSession = null;
            return;
        }

        if (method.equals("GET")) {
            if (activeSession.account.role.equals(VEHICLE)) {
                serveVehicleRequestPage(exchange);
            } else {
                String pageFile = getPageFile(path);
                if (pageFile == null) {
                    sendHtml(exchange, 404, "<h1>Page not found</h1>");
                } else {
                    servePage(exchange, pageFile);
                }
            }
        } else if (method.equals("POST")) {
            handleForm(exchange, path);
            if (activeSession.account.role.equals(VEHICLE)) {
                serveVehicleRequestPage(exchange);
            } else {
                servePage(exchange, getActionPage(path));
            }
        } else {
            sendHtml(exchange, 404, "<h1>Page not found</h1>");
        }
        activeSession = null;
    }

    private boolean isAllowed(Account account, String method, String path) {
        if (account.role.equals(VEHICLE)) {
            if (method.equals("GET")) {
                return path.equals("/") || path.equals("/request") || path.equals("/dispatcher");
            }
            return method.equals("POST") && path.equals("/routes/add");
        }
        if (account.role.equals(CONTROLLER)) {
            if (method.equals("GET")) {
                return path.equals("/") || path.equals("/dashboard")
                        || path.equals("/dispatcher") || path.equals("/roads")
                        || path.equals("/vehicles");
            }
            return method.equals("POST")
                    && (path.equals("/routes/process")
                    || path.equals("/events/add")
                    || path.equals("/events/status")
                    || path.equals("/events/process"));
        }
        return false;
    }

    private String getPageFile(String path) {
        switch (path) {
            case "/":
            case "/dashboard":
                return "index.html";
            case "/dispatcher":
                return "dispatcher.html";
            case "/roads":
                return "roads.html";
            case "/vehicles":
                return "vehicles.html";
            case "/request":
                return "vehicle_request.html";
            default:
                return null;
        }
    }

    private String getActionPage(String action) {
        if (action.startsWith("/routes/")) {
            return "dispatcher.html";
        }
        if (action.startsWith("/events/")) {
            return "roads.html";
        }
        if (action.equals("/vehicles/position")) {
            return "vehicles.html";
        }
        return "index.html";
    }

    private void handleForm(HttpExchange exchange, String path) throws IOException {
        Map<String, String> form = readForm(exchange);
        if (path.equals("/routes/type")) {
            selectVehicleType(form);
        } else if (path.equals("/routes/add")) {
            if (activeSession.account.role.equals(VEHICLE)) {
                addVehicleRouteRequest(form);
            } else {
                addRouteRequest(form);
            }
        } else if (path.equals("/routes/process")) {
            processRouteRequests();
        } else if (path.equals("/vehicles/position")) {
            updateVehiclePosition(form);
        } else if (path.equals("/events/add")) {
            addTrafficEvent(form);
        } else if (path.equals("/events/status")) {
            addRoadStatusEvent(form);
        } else if (path.equals("/events/process")) {
            processTrafficEvents();
        } else {
            setNotice("That action is not available.", Collections.emptyList(), true);
        }
    }

    private void register(HttpExchange exchange, Map<String, String> form) throws IOException {
        String username = form.get("username");
        String password = form.get("password");
        String requestedRole = form.get("role");
        String vehicleId = form.get("vehicleId");
        if (username == null || !username.matches("[A-Za-z0-9_-]{3,24}")) {
            setNotice("Use a username with 3–24 letters, numbers, underscores, or hyphens.",
                    Collections.emptyList(), true);
            serveAuthPage(exchange, "register.html");
            return;
        }
        if (password == null || password.length() < 8) {
            setNotice("Password must contain at least 8 characters.",
                    Collections.emptyList(), true);
            serveAuthPage(exchange, "register.html");
            return;
        }

        String key = username.toLowerCase(Locale.ROOT);
        if (accounts.containsKey(key)) {
            setNotice("That username is already registered.",
                    Collections.emptyList(), true);
            serveAuthPage(exchange, "register.html");
            return;
        }

        String role;
        if (CONTROLLER.equals(requestedRole)) {
            role = CONTROLLER;
        } else if (VEHICLE.equals(requestedRole) && hasControllerAccount()) {
            role = VEHICLE;
            if (getVehicle(vehicleId) == null || isVehicleRegistered(vehicleId)) {
                setNotice("Select an emergency vehicle that has not been registered yet.",
                        Collections.emptyList(), true);
                serveAuthPage(exchange, "register.html");
                return;
            }
        } else {
            setNotice("Choose a valid account type. The first account must be a traffic controller.",
                    Collections.emptyList(), true);
            serveAuthPage(exchange, "register.html");
            return;
        }

        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] passwordHash = hashPassword(password.toCharArray(), salt);
        Account account = new Account(username, role,
                role.equals(VEHICLE) ? vehicleId : null, salt, passwordHash);
        try {
            database.saveAccount(account.username, account.role, account.vehicleId,
                    account.salt, account.passwordHash);
        } catch (SQLException exception) {
            reportDatabaseError("save account", exception);
            serveAuthPage(exchange, "register.html");
            return;
        }
        accounts.put(key, account);
        createSession(exchange, account);
        redirect(exchange, homeFor(account));
    }

    private void login(HttpExchange exchange, Map<String, String> form) throws IOException {
        String username = form.get("username");
        String password = form.get("password");
        String requestedRole = form.get("role");
        Account account = username == null ? null
                : accounts.get(username.toLowerCase(Locale.ROOT));
        if (account == null || password == null || !account.role.equals(requestedRole)
                || !passwordMatches(password.toCharArray(), account)) {
            setNotice("Account type, username, or password is incorrect.",
                    Collections.emptyList(), true);
            serveAuthPage(exchange, "login.html");
            return;
        }
        createSession(exchange, account);
        redirect(exchange, homeFor(account));
    }

    private void logout(HttpExchange exchange) throws IOException {
        if (activeSession != null) {
            sessions.remove(activeSession.id);
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                "VEGAM_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        redirect(exchange, "/login");
    }

    private void createSession(HttpExchange exchange, Account account) {
        byte[] token = new byte[32];
        random.nextBytes(token);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        UserSession session = new UserSession(id, account);
        sessions.put(id, session);
        activeSession = session;
        exchange.getResponseHeaders().add("Set-Cookie",
                "VEGAM_SESSION=" + id + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private UserSession findSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("VEGAM_SESSION")) {
                return sessions.get(pair[1]);
            }
        }
        return null;
    }

    private boolean hasControllerAccount() {
        for (Account account : accounts.values()) {
            if (account.role.equals(CONTROLLER)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVehicleRegistered(String vehicleId) {
        for (Account account : accounts.values()) {
            if (account.role.equals(VEHICLE) && account.vehicleId.equals(vehicleId)) {
                return true;
            }
        }
        return false;
    }

    private EmergencyVehicle getVehicle(String vehicleId) {
        for (EmergencyVehicle vehicle : controller.getVehicles()) {
            if (vehicle.getVehicleId().equals(vehicleId)) {
                return vehicle;
            }
        }
        return null;
    }

    private String homeFor(Account account) {
        return account.role.equals(CONTROLLER) ? "/" : "/request";
    }

    private byte[] hashPassword(char[] password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(
                password, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash account password.", exception);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    private boolean passwordMatches(char[] password, Account account) {
        byte[] submittedHash = hashPassword(password, account.salt);
        if (submittedHash.length != account.passwordHash.length) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < submittedHash.length; i++) {
            difference |= submittedHash[i] ^ account.passwordHash[i];
        }
        return difference == 0;
    }

    private void serveAuthPage(HttpExchange exchange, String fileName) throws IOException {
        try {
            String page = new String(Files.readAllBytes(Paths.get("web", fileName)),
                    StandardCharsets.UTF_8);
            page = page.replace("{{NOTICE}}", renderNotice())
                    .replace("{{REGISTER_TITLE}}", "Create an account")
                    .replace("{{REGISTER_HELP}}", hasControllerAccount()
                            ? "Choose Traffic Controller or register an emergency vehicle."
                            : "Create the traffic controller account first.")
                    .replace("{{ACCOUNT_FIELDS}}", renderAccountFields());
            sendHtml(exchange, 200, page);
        } catch (IOException exception) {
            sendHtml(exchange, 500, "<h1>Could not read web/"
                    + escapeHtml(fileName) + "</h1>");
        }
    }

    private String renderAccountFields() {
        boolean controllerExists = hasControllerAccount();
        StringBuilder options = new StringBuilder();
        for (EmergencyVehicle vehicle : controller.getVehicles()) {
            if (!isVehicleRegistered(vehicle.getVehicleId())) {
                options.append("<option value=\"")
                        .append(escapeHtml(vehicle.getVehicleId())).append("\">")
                        .append(escapeHtml(vehicle.getVehicleId())).append(" · ")
                        .append(escapeHtml(vehicle.getType())).append("</option>");
            }
        }
        String roleOptions = "<option value=\"" + CONTROLLER + "\">Traffic Controller</option>";
        if (controllerExists && options.length() > 0) {
            roleOptions += "<option value=\"" + VEHICLE + "\">Emergency Vehicle</option>";
        }
        String accountFields = "<label for=\"account-type\">Account type</label>"
                + "<select id=\"account-type\" name=\"role\" required>" + roleOptions + "</select>";
        if (!controllerExists) {
            return accountFields;
        }
        String vehicleOptions = "<option value=\"\">Select a vehicle</option>" + options;
        String helperText = options.length() == 0
                ? "All emergency vehicles already have accounts."
                : "Required only when registering an emergency vehicle.";
        return accountFields
                + "<label for=\"vehicle-id\">Emergency vehicle</label>"
                + "<select id=\"vehicle-id\" name=\"vehicleId\">" + vehicleOptions + "</select>"
                + "<p class=\"helper-text\">" + helperText + "</p>";
    }

    private void serveVehicleRequestPage(HttpExchange exchange) throws IOException {
        try {
            String page = new String(Files.readAllBytes(
                    Paths.get("web", "vehicle_request.html")), StandardCharsets.UTF_8);
            EmergencyVehicle vehicle = getVehicle(activeSession.account.vehicleId);
            if (vehicle == null) {
                sendHtml(exchange, 500, "<h1>Registered vehicle was not found.</h1>");
                return;
            }
            String vehicleType = getDispatcherType(vehicle.getType());
            List<String> junctions = controller.getRoadNetwork().getJunctions();
            Collections.sort(junctions, this::compareIds);
            page = page.replace("{{NOTICE}}", renderNotice())
                    .replace("{{ACCOUNT_SUMMARY}}",
                            escapeHtml(activeSession.account.username) + " · Emergency vehicle")
                    .replace("{{VEHICLE_NAME}}",
                            escapeHtml(vehicle.getVehicleId() + " · " + vehicle.getType()))
                    .replace("{{VEHICLE_POSITION}}",
                            escapeHtml(vehicle.getCurrentJunction()))
                    .replace("{{SITUATION_OPTIONS}}", renderSituationOptions(vehicleType))
                    .replace("{{JUNCTION_OPTIONS}}",
                            renderJunctionOptions(junctions, vehicle.getCurrentJunction()))
                    .replace("{{VEHICLE_ROUTE_RESULTS}}",
                            renderProcessedRoutes(
                                    controller.getProcessedRoutesForVehicle(vehicle.getVehicleId()))
                                    .replace("Processed journey map", "Your processed routes"));
            sendHtml(exchange, 200, page);
        } catch (IOException exception) {
            sendHtml(exchange, 500, "<h1>Could not read web/vehicle_request.html</h1>");
        }
    }

    private void addRouteRequest(Map<String, String> form) {
        synchronized (controller) {
            String vehicleType = form.get("vehicleType");
            String situation = form.get("situation");
            String source = form.get("source");
            String destination = form.get("destination");
            String description = form.get("description");
            try {
                int order = requestArrivalOrder + 1;
                RoutingRequest request = DispatcherRequestFactory.createRequest(
                        controller, "REQ-" + order, order, vehicleType,
                        situation, source, destination, description);
                EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                        controller, vehicleType);
                if (vehicle == null) {
                    throw new IllegalArgumentException(
                            "No available vehicle of the selected type.");
                }
                if (saveAndQueueRequest(request)) {
                    requestArrivalOrder = order;
                    setNotice(vehicleType + " request added as "
                                    + request.getSeverity() + " priority.",
                            Collections.singletonList(request.toString()), false);
                } else {
                    setNotice("The traffic controller rejected the routing request.",
                            Collections.emptyList(), true);
                }
            } catch (IllegalArgumentException exception) {
                setNotice(exception.getMessage(), Collections.emptyList(), true);
            } catch (SQLException exception) {
                reportDatabaseError("save routing request", exception);
            }
        }
    }

    private void selectVehicleType(Map<String, String> form) {
        String vehicleType = form.get("vehicleType");
        try {
            DispatcherRequestFactory.getSituations(vehicleType);
            setNotice(vehicleType + " situations loaded. Choose a situation and enter route details.",
                    Collections.emptyList(), false);
        } catch (IllegalArgumentException exception) {
            setNotice(exception.getMessage(), Collections.emptyList(), true);
        }
    }

    private void addVehicleRouteRequest(Map<String, String> form) {
        synchronized (controller) {
            EmergencyVehicle vehicle = getVehicle(activeSession.account.vehicleId);
            if (vehicle == null) {
                setNotice("Your registered emergency vehicle was not found.",
                        Collections.emptyList(), true);
                return;
            }

            String source = form.get("source");
            String destination = form.get("destination");
            String description = form.get("description");
            String vehicleType = getDispatcherType(vehicle.getType());
            String situation = form.get("situation");
            try {
                if (!controller.getRoadNetwork().hasJunction(source)
                        || !controller.getRoadNetwork().hasJunction(destination)) {
                    throw new IllegalArgumentException(
                            "Choose valid starting and destination junctions.");
                }
                if (description == null || description.trim().isEmpty()) {
                    throw new IllegalArgumentException("Enter an incident description.");
                }

                int order = requestArrivalOrder + 1;
                String severity = DispatcherRequestFactory.getSeverity(vehicleType, situation);
                RoutingRequest request = new RoutingRequest("REQ-" + order,
                        vehicle.getVehicleId(), source, destination, severity, order,
                        vehicleType, description.trim());
                if (!saveAndQueueRequest(request)) {
                    throw new IllegalArgumentException(
                            "The traffic controller rejected the routing request.");
                }
                requestArrivalOrder = order;
                vehicle.updatePosition(source);
                setNotice("Your routing request was sent to the traffic controller.",
                        Collections.emptyList(), false);
            } catch (IllegalArgumentException exception) {
                setNotice(exception.getMessage(), Collections.emptyList(), true);
            } catch (SQLException exception) {
                reportDatabaseError("save routing request", exception);
            }
        }
    }

    private boolean saveAndQueueRequest(RoutingRequest request) throws SQLException {
        database.saveRoutingRequest(request);
        if (controller.addRoutingRequest(request)) {
            return true;
        }
        database.deleteRoutingRequest(request.getRequestId());
        return false;
    }

    private String getDispatcherType(String vehicleModelType) {
        if (vehicleModelType.equalsIgnoreCase("Ambulance")) {
            return DispatcherRequestFactory.AMBULANCE;
        }
        if (vehicleModelType.equalsIgnoreCase("Fire Truck")) {
            return DispatcherRequestFactory.FIRE_TRUCK;
        }
        if (vehicleModelType.equalsIgnoreCase("Police Vehicle")) {
            return DispatcherRequestFactory.POLICE;
        }
        throw new IllegalArgumentException("Unsupported emergency vehicle type.");
    }

    private void processRouteRequests() {
        synchronized (controller) {
            List<String> results = controller.processRoutingRequests();
            try {
                database.saveProcessedRoutes(controller.getLastProcessedRoutes());
            } catch (SQLException exception) {
                reportDatabaseError("save processed routes", exception);
                return;
            }
            String message = results.size() == 1
                    && results.get(0).startsWith("No routing requests")
                    ? results.get(0)
                    : "Routing requests processed by severity, then arrival order.";
            setNotice(message, results, false);
        }
    }

    private void reportDatabaseError(String action, SQLException exception) {
        System.err.println("MySQL error while attempting to " + action + ": "
                + exception.getMessage());
        setNotice("Could not " + action + " in MySQL. Check the server output and database "
                        + "connection settings.",
                Collections.emptyList(), true);
    }

    private void updateVehiclePosition(Map<String, String> form) {
        synchronized (controller) {
            String vehicleId = form.get("vehicleId");
            String junction = form.get("junction");
            if (controller.getVehiclePosition(vehicleId) == null) {
                setNotice("Select a valid emergency vehicle.",
                        Collections.emptyList(), true);
                return;
            }
            if (!controller.getRoadNetwork().hasJunction(junction)) {
                setNotice("Select a valid junction.", Collections.emptyList(), true);
                return;
            }
            try {
                database.updateVehiclePosition(vehicleId, junction);
                controller.setVehiclePosition(vehicleId, junction);
                setNotice("Emergency vehicle location updated.",
                        Collections.emptyList(), false);
            } catch (SQLException exception) {
                reportDatabaseError("update vehicle position", exception);
            }
        }
    }

    private void addTrafficEvent(Map<String, String> form) {
        synchronized (controller) {
            String roadId = form.get("roadId");
            if (!controller.hasRoad(roadId)) {
                setNotice("Select a valid road.", Collections.emptyList(), true);
                return;
            }

            int order = eventArrivalOrder + 1;
            String eventId = "EV-" + order;
            int newTravelTime;
            try {
                newTravelTime = Integer.parseInt(form.get("travelTime"));
            } catch (NumberFormatException exception) {
                setNotice("Travel time must be a positive whole number.",
                        Collections.emptyList(), true);
                return;
            }
            if (newTravelTime <= 0) {
                setNotice("Travel time must be a positive whole number.",
                        Collections.emptyList(), true);
                return;
            }
            TrafficEvent event = new CongestionEvent(
                    eventId, roadId, order, newTravelTime);

            if (controller.addTrafficEvent(event)) {
                eventArrivalOrder = order;
                setNotice("Traffic event added to the FIFO event queue.",
                        Collections.emptyList(), false);
            } else {
                setNotice("The traffic event could not be added.",
                        Collections.emptyList(), true);
            }
        }
    }

    private void addRoadStatusEvent(Map<String, String> form) {
        synchronized (controller) {
            String roadId = form.get("roadId");
            String status = form.get("status");
            if (!controller.hasRoad(roadId)) {
                setNotice("Select a valid road.", Collections.emptyList(), true);
                return;
            }

            int order = eventArrivalOrder + 1;
            String eventId = "EV-" + order;
            TrafficEvent event;
            if ("close".equals(status)) {
                event = new ClosureEvent(eventId, roadId, order);
            } else if ("open".equals(status)) {
                event = new OpenRoadEvent(eventId, roadId, order);
            } else {
                setNotice("Choose whether to close or open the road.",
                        Collections.emptyList(), true);
                return;
            }

            if (controller.addTrafficEvent(event)) {
                eventArrivalOrder = order;
                setNotice("Road status event added to the FIFO event queue.",
                        Collections.emptyList(), false);
            } else {
                setNotice("The road status event could not be added.",
                        Collections.emptyList(), true);
            }
        }
    }

    private void processTrafficEvents() {
        synchronized (controller) {
            List<String> events = controller.processTrafficEvents();
            String message = events.isEmpty()
                    ? "No traffic events to process."
                    : events.size() + " traffic event(s) applied in arrival order.";
            setNotice(message, events, false);
        }
    }

    private void servePage(HttpExchange exchange, String fileName) throws IOException {
        try {
            String page = new String(
                    Files.readAllBytes(Paths.get("web", fileName)),
                    StandardCharsets.UTF_8);
            page = renderPage(page);
            sendHtml(exchange, 200, page);
        } catch (IOException exception) {
            sendHtml(exchange, 500, "<h1>Could not read web/"
                    + escapeHtml(fileName) + "</h1>");
        }
    }

    private String renderPage(String page) {
        synchronized (controller) {
            RoadNetwork network = controller.getRoadNetwork();
            List<String> junctions = network.getJunctions();
            List<RoadSegment> roads = network.getRoads();
            List<EmergencyVehicle> vehicles = controller.getVehicles();
            Collections.sort(junctions, this::compareIds);
            Collections.sort(roads, (first, second) ->
                    compareIds(first.getRoadId(), second.getRoadId()));
            Collections.sort(vehicles, (first, second) ->
                    compareIds(first.getVehicleId(), second.getVehicleId()));

            int openRoads = 0;
            for (RoadSegment road : roads) {
                if (!road.isClosed()) {
                    openRoads++;
                }
            }

            page = page.replace("{{NOTICE}}", renderNotice())
            .replace("{{ROUTE_VISUALIZATION}}", renderProcessedRoutes())
                    .replace("{{ACCOUNT_SUMMARY}}",
                            escapeHtml(activeSession.account.username) + " · Traffic controller")
                    .replace("{{JUNCTION_COUNT}}", Integer.toString(junctions.size()))
                    .replace("{{OPEN_ROAD_COUNT}}", Integer.toString(openRoads))
                    .replace("{{ROAD_SUMMARY}}",
                            (roads.size() - openRoads) + " closed · " + roads.size() + " total")
                    .replace("{{VEHICLE_COUNT}}", Integer.toString(vehicles.size()))
                    .replace("{{QUEUE_COUNT}}", Integer.toString(
                            controller.getPendingRequestCount()
                                    + controller.getPendingEventCount()))
                    .replace("{{PENDING_EVENTS}}",
                            Integer.toString(controller.getPendingEventCount()))
                    .replace("{{QUEUE_SUMMARY}}",
                            controller.getPendingRequestCount() + " route requests · "
                                    + controller.getPendingEventCount() + " traffic events")
                    .replace("{{PENDING_REQUEST_COUNT}}",
                            Integer.toString(controller.getPendingRequestCount()))
                    .replace("{{PENDING_REQUEST_LIST}}", renderPendingRequests())
                    .replace("{{ROAD_LIST}}", renderRoads(roads))
                    .replace("{{VEHICLE_LIST}}", renderVehicles(vehicles))
                    .replace("{{VEHICLE_OPTIONS}}", renderVehicleOptions(vehicles))
                    .replace("{{DISPATCHER_VEHICLE_OPTIONS}}",
                            renderDispatcherVehicleOptions())
                    .replace("{{ASSIGNED_UNIT}}", renderAssignedUnit())
                    .replace("{{JUNCTION_OPTIONS}}", renderJunctionOptions(junctions))
                    .replace("{{ROAD_OPTIONS}}", renderRoadOptions(roads));
            return page;
        }
    }

    private String renderNotice() {
        String message = activeSession == null ? notice : activeSession.notice;
        boolean isError = activeSession == null ? noticeIsError : activeSession.noticeIsError;
        List<String> details = activeSession == null
                ? noticeDetails : activeSession.noticeDetails;
        if (message.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"notice");
        if (isError) {
            html.append(" error");
        }
        html.append("\" role=\"status\">")
                .append(escapeHtml(message));
        if (!details.isEmpty()) {
            html.append("<ul>");
            for (String detail : details) {
                html.append("<li>").append(escapeHtml(detail)).append("</li>");
            }
            html.append("</ul>");
        }
        return html.append("</div>").toString();
    }

    private String renderPendingRequests() {
        List<RoutingRequest> requests = controller.getPendingRequests();
        if (requests.isEmpty()) {
            return "<p class=\"empty-queue\">No requests are waiting to be processed.</p>";
        }
        StringBuilder html = new StringBuilder();
        for (RoutingRequest request : requests) {
            html.append("<article class=\"pending-request\"><div><span class=\"route-priority ")
                    .append(request.getSeverity().toLowerCase())
                    .append("\">").append(escapeHtml(request.getSeverity().toUpperCase()))
                    .append("</span><strong>")
                    .append(escapeHtml(request.getRequestId())).append(" · ")
                    .append(escapeHtml(request.getVehicleId()))
                    .append("</strong></div><p>")
                    .append(escapeHtml(request.getSource())).append(" → ")
                    .append(escapeHtml(request.getDestination()))
                    .append("</p><small>")
                    .append(escapeHtml(request.getDescription()))
                    .append(" · Arrival #").append(request.getArrivalOrder())
                    .append(" at ").append(escapeHtml(request.getRequestedAtFormatted()))
                    .append("</small></article>");
        }
        return html.toString();
    }

    private String renderProcessedRoutes() {
        return renderProcessedRoutes(controller.getLastProcessedRoutes());
    }

    private String renderProcessedRoutes(List<ProcessedRoute> processedRoutes) {
        if (processedRoutes.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder(
                "<section class=\"processed-routes\"><div class=\"section-heading\">"
                        + "<p class=\"eyebrow\">ROUTE VISUALIZATION</p>"
                        + "<h2>Processed journey map</h2>"
                        + "<p class=\"subtitle\">Highlighted arrows show the selected directed route.</p>"
                        + "</div>");
        for (ProcessedRoute processed : processedRoutes) {
            RoutingRequest request = processed.getRequest();
            html.append("<article class=\"panel route-result-card\">")
                    .append("<div class=\"route-result-heading\"><div><span class=\"route-priority ")
                    .append(request.getSeverity().toLowerCase())
                    .append("\">").append(escapeHtml(request.getSeverity().toUpperCase()))
                    .append("</span><h3>").append(escapeHtml(request.getRequestId()))
                    .append(" · ").append(escapeHtml(request.getVehicleId()))
                    .append("</h3></div><span class=\"route-arrival\">")
                    .append(escapeHtml(request.getArrivalDetails()))
                    .append("</span></div>");

            if (processed.isReachable()) {
                Route route = processed.getRoute();
                html.append(renderRouteSvg(route))
                        .append("<p class=\"route-summary\"><strong>")
                        .append(escapeHtml(joinJunctions(route.getJunctions())))
                        .append("</strong><span>")
                        .append(route.getTotalTravelTime())
                        .append(" min travel time</span></p>");
            } else {
                html.append("<div class=\"unreachable-result\">")
                        .append("<span class=\"unreachable-icon\">!</span>")
                        .append("<div><strong>Destination unreachable</strong>")
                        .append("<p>No available route was found from ")
                        .append(escapeHtml(request.getSource())).append(" to ")
                        .append(escapeHtml(request.getDestination()))
                        .append(" using open roads.</p></div></div>");
            }
            html.append("</article>");
        }
        return html.append("</section>").toString();
    }

    private String renderRouteSvg(Route route) {
        Map<String, int[]> points = new HashMap<>();
        points.put("J1", new int[] {70, 76});
        points.put("J2", new int[] {210, 76});
        points.put("J3", new int[] {350, 76});
        points.put("J4", new int[] {490, 76});
        points.put("J5", new int[] {630, 76});
        points.put("J10", new int[] {70, 224});
        points.put("J9", new int[] {210, 224});
        points.put("J8", new int[] {350, 224});
        points.put("J7", new int[] {490, 224});
        points.put("J6", new int[] {630, 224});

        StringBuilder svg = new StringBuilder(
                "<svg class=\"route-map\" viewBox=\"0 0 700 300\" role=\"img\" "
                        + "aria-label=\"Fastest route map\" xmlns=\"http://www.w3.org/2000/svg\">"
                        + "<defs><marker id=\"route-arrow\" viewBox=\"0 0 10 10\" "
                        + "refX=\"8\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" "
                        + "orient=\"auto-start-reverse\"><path d=\"M 0 0 L 10 5 L 0 10 z\" "
                        + "class=\"route-arrow-head\"/></marker></defs>");

        for (RoadSegment road : controller.getRoadNetwork().getRoads()) {
            int[] from = points.get(road.getSource());
            int[] to = points.get(road.getDestination());
            if (from == null || to == null) {
                continue;
            }
            boolean selected = isRouteLeg(route, road);
            int startX = selected ? from[0] + direction(from[0], to[0]) * 20 : from[0];
            int startY = selected ? from[1] + direction(from[1], to[1]) * 20 : from[1];
            int endX = selected ? to[0] - direction(from[0], to[0]) * 22 : to[0];
            int endY = selected ? to[1] - direction(from[1], to[1]) * 22 : to[1];
            String cssClass = selected ? "route-edge" : "map-edge";
            if (road.isClosed()) {
                cssClass += " closed-edge";
            }
            svg.append("<line x1=\"").append(startX).append("\" y1=\"")
                    .append(startY).append("\" x2=\"").append(endX)
                    .append("\" y2=\"").append(endY).append("\" class=\"")
                    .append(cssClass).append("\"");
            if (selected) {
                svg.append(" marker-end=\"url(#route-arrow)\"");
            }
            svg.append("/>");
        }

        for (Map.Entry<String, int[]> point : points.entrySet()) {
            int[] position = point.getValue();
            boolean onRoute = route.getJunctions().contains(point.getKey());
            svg.append("<g class=\"map-junction");
            if (onRoute) {
                svg.append(" route-junction");
            }
            svg.append("\"><circle cx=\"").append(position[0])
                    .append("\" cy=\"").append(position[1])
                    .append("\" r=\"17\"/><text x=\"").append(position[0])
                    .append("\" y=\"").append(position[1] + 4)
                    .append("\">").append(escapeHtml(point.getKey()))
                    .append("</text></g>");
        }
        return svg.append("</svg>").toString();
    }

    private int direction(int from, int to) {
        if (from == to) {
            return 0;
        }
        return from < to ? 1 : -1;
    }

    private boolean isRouteLeg(Route route, RoadSegment road) {
        List<String> junctions = route.getJunctions();
        for (int i = 0; i < junctions.size() - 1; i++) {
            if (junctions.get(i).equals(road.getSource())
                    && junctions.get(i + 1).equals(road.getDestination())) {
                return true;
            }
        }
        return false;
    }

    private String joinJunctions(List<String> junctions) {
        StringBuilder path = new StringBuilder();
        for (String junction : junctions) {
            if (path.length() > 0) {
                path.append(" → ");
            }
            path.append(junction);
        }
        return path.toString();
    }

    private String renderRoads(List<RoadSegment> roads) {
        StringBuilder html = new StringBuilder();
        for (RoadSegment road : roads) {
            String status = road.isClosed() ? "closed" : "open";
            html.append("<div class=\"road-row\"><span class=\"road-id\">")
                    .append(escapeHtml(road.getRoadId()))
                    .append("</span><span class=\"road-connection\">")
                    .append(escapeHtml(road.getSource())).append(" → ")
                    .append(escapeHtml(road.getDestination()))
                    .append("</span><span class=\"road-time\">")
                    .append(road.getTravelTime())
                    .append(" min</span><span class=\"road-status ")
                    .append(status)
                    .append("\"><i class=\"status-dot\"></i>")
                    .append(road.isClosed() ? "CLOSED" : "OPEN")
                    .append("</span></div>");
        }
        return html.toString();
    }

    private String renderVehicles(List<EmergencyVehicle> vehicles) {
        StringBuilder html = new StringBuilder();
        for (EmergencyVehicle vehicle : vehicles) {
            String icon = vehicle.getType().equals("Ambulance") ? "+" : "◆";
            html.append("<article class=\"vehicle-card\"><span class=\"vehicle-icon\">")
                    .append(icon)
                    .append("</span><span class=\"vehicle-info\"><strong>")
                    .append(escapeHtml(vehicle.getVehicleId())).append(" · ")
                    .append(escapeHtml(vehicle.getType()))
                    .append("</strong><small>Current location: ")
                    .append(escapeHtml(vehicle.getCurrentJunction()))
                    .append("</small></span></article>");
        }
        return html.toString();
    }

    private String renderVehicleOptions(List<EmergencyVehicle> vehicles) {
        StringBuilder html = new StringBuilder();
        for (EmergencyVehicle vehicle : vehicles) {
            html.append("<option value=\"")
                    .append(escapeHtml(vehicle.getVehicleId())).append("\">")
                    .append(escapeHtml(vehicle.getVehicleId())).append(" · ")
                    .append(escapeHtml(vehicle.getType())).append(" · ")
                    .append(escapeHtml(vehicle.getCurrentJunction()))
                    .append("</option>");
        }
        return html.toString();
    }

    private String renderDispatcherVehicleOptions() {
        StringBuilder html = new StringBuilder();
        String[] vehicleTypes = {
                DispatcherRequestFactory.AMBULANCE,
                DispatcherRequestFactory.FIRE_TRUCK,
                DispatcherRequestFactory.POLICE
        };
        for (String vehicleType : vehicleTypes) {
            html.append("<option value=\"").append(vehicleType).append("\"");
            if (vehicleType.equals(DispatcherRequestFactory.AMBULANCE)) {
                html.append(" selected");
            }
            html.append(">").append(vehicleType.replace('_', ' '))
                    .append("</option>");
        }
        return html.toString();
    }

    private String renderSituationOptions(String vehicleType) {
        StringBuilder html = new StringBuilder();
        List<String> situations = DispatcherRequestFactory.getSituations(vehicleType);
        for (String situation : situations) {
            html.append("<option value=\"").append(escapeHtml(situation))
                    .append("\">").append(escapeHtml(situation))
                    .append(" — ")
                    .append(DispatcherRequestFactory.getSeverity(vehicleType, situation))
                    .append("</option>");
        }
        return html.toString();
    }

    private String renderAssignedUnit() {
        EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                controller, DispatcherRequestFactory.AMBULANCE);
        if (vehicle == null) {
            return "<p class=\"assigned-unit unavailable\">No available "
                    + "AMBULANCE"
                    + " is registered.</p>";
        }
        return "<p class=\"assigned-unit\">Assigned unit: <strong>"
                + escapeHtml(vehicle.getVehicleId())
                + "</strong> · Current position: <strong>"
                + escapeHtml(vehicle.getCurrentJunction()) + "</strong></p>";
    }

    private String renderJunctionOptions(List<String> junctions) {
        return renderJunctionOptions(junctions, null);
    }

    private String renderJunctionOptions(List<String> junctions, String selectedJunction) {
        StringBuilder html = new StringBuilder();
        for (String junction : junctions) {
            html.append("<option value=\"").append(escapeHtml(junction)).append("\"");
            if (junction.equals(selectedJunction)) {
                html.append(" selected");
            }
            html.append(">").append(escapeHtml(junction)).append("</option>");
        }
        return html.toString();
    }

    private String renderRoadOptions(List<RoadSegment> roads) {
        StringBuilder html = new StringBuilder();
        for (RoadSegment road : roads) {
            html.append("<option value=\"").append(escapeHtml(road.getRoadId()))
                    .append("\">").append(escapeHtml(road.getRoadId()))
                    .append(" · ").append(escapeHtml(road.getSource()))
                    .append(" → ").append(escapeHtml(road.getDestination()))
                    .append("</option>");
        }
        return html.toString();
    }

    private void serveStyles(HttpExchange exchange) throws IOException {
        try {
            byte[] content = Files.readAllBytes(Paths.get("web", "styles.css"));
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        } catch (IOException exception) {
            sendHtml(exchange, 500, "/* Could not read web/styles.css */");
        }
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        InputStream input = exchange.getRequestBody();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            body.write(buffer, 0, bytesRead);
        }

        Map<String, String> values = new HashMap<>();
        String[] fields = new String(body.toByteArray(), StandardCharsets.UTF_8).split("&");
        for (String field : fields) {
            String[] pair = field.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            String value = pair.length > 1
                    ? URLDecoder.decode(pair[1], "UTF-8") : "";
            values.put(key, value);
        }
        return values;
    }

    private int compareIds(String first, String second) {
        int firstNumber = Integer.parseInt(first.replaceAll("\\D", ""));
        int secondNumber = Integer.parseInt(second.replaceAll("\\D", ""));
        return Integer.compare(firstNumber, secondNumber);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void setNotice(String message, List<String> details, boolean isError) {
        if (activeSession == null) {
            notice = message;
            noticeDetails = details;
            noticeIsError = isError;
        } else {
            activeSession.notice = message;
            activeSession.noticeDetails = details;
            activeSession.noticeIsError = isError;
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private void sendHtml(HttpExchange exchange, int status, String html)
            throws IOException {
        byte[] content = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private static class Account {
        private final String username;
        private final String role;
        private final String vehicleId;
        private final byte[] salt;
        private final byte[] passwordHash;

        private Account(String username, String role, String vehicleId,
                        byte[] salt, byte[] passwordHash) {
            this.username = username;
            this.role = role;
            this.vehicleId = vehicleId;
            this.salt = salt;
            this.passwordHash = passwordHash;
        }
    }

    private static class UserSession {
        private final String id;
        private final Account account;
        private String notice = "";
        private boolean noticeIsError;
        private List<String> noticeDetails = Collections.emptyList();

        private UserSession(String id, Account account) {
            this.id = id;
            this.account = account;
        }
    }
}
