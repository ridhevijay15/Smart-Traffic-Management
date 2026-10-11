import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseStore {

    public void initialize(TrafficController controller) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vehicles ("
                    + "vehicle_id VARCHAR(20) PRIMARY KEY, "
                    + "vehicle_type VARCHAR(30) NOT NULL, "
                    + "current_junction VARCHAR(20) NOT NULL, "
                    + "available BOOLEAN NOT NULL DEFAULT TRUE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                    + "account_id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "username VARCHAR(24) NOT NULL UNIQUE, "
                    + "password_hash VARCHAR(255) NOT NULL, "
                    + "`role` VARCHAR(20) NOT NULL, "
                    + "vehicle_id VARCHAR(20) UNIQUE, "
                    + "CONSTRAINT fk_account_vehicle FOREIGN KEY (vehicle_id) "
                    + "REFERENCES vehicles(vehicle_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS routing_requests ("
                    + "request_id VARCHAR(30) PRIMARY KEY, "
                    + "vehicle_id VARCHAR(20) NOT NULL, "
                    + "source_junction VARCHAR(20) NOT NULL, "
                    + "destination_junction VARCHAR(20) NOT NULL, "
                    + "severity VARCHAR(20) NOT NULL, "
                    + "description TEXT NOT NULL, "
                    + "arrival_order INT NOT NULL, "
                    + "requested_at TIMESTAMP NOT NULL, "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', "
                    + "CONSTRAINT fk_request_vehicle FOREIGN KEY (vehicle_id) "
                    + "REFERENCES vehicles(vehicle_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS processed_routes ("
                    + "request_id VARCHAR(30) PRIMARY KEY, "
                    + "route_path TEXT NOT NULL, "
                    + "travel_time INT NOT NULL, "
                    + "processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "CONSTRAINT fk_processed_request FOREIGN KEY (request_id) "
                    + "REFERENCES routing_requests(request_id))");
        }
        saveInitialVehicles(controller.getVehicles());
    }

    private void saveInitialVehicles(List<EmergencyVehicle> vehicles) throws SQLException {
        String sql = "INSERT INTO vehicles (vehicle_id, vehicle_type, current_junction) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "vehicle_type = VALUES(vehicle_type)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (EmergencyVehicle vehicle : vehicles) {
                statement.setString(1, vehicle.getVehicleId());
                statement.setString(2, vehicle.getType());
                statement.setString(3, vehicle.getCurrentJunction());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public List<AccountRecord> loadAccounts() throws SQLException {
        List<AccountRecord> accounts = new ArrayList<>();
        String sql = "SELECT username, `role`, vehicle_id, password_hash FROM accounts";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String[] passwordParts = results.getString("password_hash").split(":", 2);
                accounts.add(new AccountRecord(results.getString("username"),
                        results.getString("role"), results.getString("vehicle_id"),
                        Base64.getDecoder().decode(passwordParts[0]),
                        Base64.getDecoder().decode(passwordParts[1])));
            }
        }
        return accounts;
    }

    public void saveAccount(String username, String role, String vehicleId,
                            byte[] salt, byte[] passwordHash) throws SQLException {
        String encodedPassword = Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(passwordHash);
        String sql = "INSERT INTO accounts "
                + "(username, `role`, vehicle_id, password_hash) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, role);
            statement.setString(3, vehicleId);
            statement.setString(4, encodedPassword);
            statement.executeUpdate();
        }
    }

    public void saveRoutingRequest(RoutingRequest request) throws SQLException {
        String sql = "INSERT INTO routing_requests "
                + "(request_id, vehicle_id, source_junction, destination_junction, "
                + "severity, description, arrival_order, requested_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement positionStatement = connection.prepareStatement(
                         "UPDATE vehicles SET current_junction = ? WHERE vehicle_id = ?");
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                positionStatement.setString(1, request.getSource());
                positionStatement.setString(2, request.getVehicleId());
                positionStatement.executeUpdate();
                statement.setString(1, request.getRequestId());
                statement.setString(2, request.getVehicleId());
                statement.setString(3, request.getSource());
                statement.setString(4, request.getDestination());
                statement.setString(5, request.getSeverity());
                statement.setString(6, request.getDescription());
                statement.setInt(7, request.getArrivalOrder());
                statement.setTimestamp(8, Timestamp.valueOf(request.getRequestedAt()));
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void deleteRoutingRequest(String requestId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM routing_requests WHERE request_id = ?")) {
            statement.setString(1, requestId);
            statement.executeUpdate();
        }
    }

    public List<RoutingRequest> loadPendingRequests() throws SQLException {
        List<RoutingRequest> requests = new ArrayList<>();
        String sql = "SELECT r.*, v.vehicle_type FROM routing_requests r "
                + "JOIN vehicles v ON v.vehicle_id = r.vehicle_id "
                + "WHERE r.status = 'PENDING' "
                + "ORDER BY arrival_order";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                requests.add(readRequest(results));
            }
        }
        return requests;
    }

    public List<ProcessedRoute> loadProcessedRoutes() throws SQLException {
        List<ProcessedRoute> routes = new ArrayList<>();
        String sql = "SELECT r.*, v.vehicle_type, p.route_path, p.travel_time "
                + "FROM routing_requests r "
                + "JOIN vehicles v ON v.vehicle_id = r.vehicle_id "
                + "LEFT JOIN processed_routes p ON p.request_id = r.request_id "
                + "WHERE r.status = 'PROCESSED' "
                + "ORDER BY arrival_order";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                RoutingRequest request = readRequest(results);
                String routePath = results.getString("route_path");
                Route route = routePath == null || routePath.isEmpty() ? null
                        : new Route(Arrays.asList(routePath.split(",")),
                                results.getInt("travel_time"));
                routes.add(new ProcessedRoute(request, route));
            }
        }
        return routes;
    }

    public int getLargestArrivalOrder() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(arrival_order), 0) FROM routing_requests");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getInt(1);
        }
    }

    private RoutingRequest readRequest(ResultSet results) throws SQLException {
        Timestamp requestedAt = results.getTimestamp("requested_at");
        String vehicleType = results.getString("vehicle_type");
        if ("Ambulance".equalsIgnoreCase(vehicleType)) {
            vehicleType = DispatcherRequestFactory.AMBULANCE;
        } else if ("Fire Truck".equalsIgnoreCase(vehicleType)) {
            vehicleType = DispatcherRequestFactory.FIRE_TRUCK;
        } else if ("Police Vehicle".equalsIgnoreCase(vehicleType)) {
            vehicleType = DispatcherRequestFactory.POLICE;
        }
        return new RoutingRequest(results.getString("request_id"),
                results.getString("vehicle_id"), results.getString("source_junction"),
                results.getString("destination_junction"), results.getString("severity"),
                results.getInt("arrival_order"), vehicleType,
                results.getString("description"), requestedAt.toLocalDateTime());
    }

    public Map<String, String> loadVehiclePositions() throws SQLException {
        Map<String, String> positions = new HashMap<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT vehicle_id, current_junction FROM vehicles");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                positions.put(results.getString("vehicle_id"),
                        results.getString("current_junction"));
            }
        }
        return positions;
    }

    public void updateVehiclePosition(String vehicleId, String junction)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE vehicles SET current_junction = ? WHERE vehicle_id = ?")) {
            statement.setString(1, junction);
            statement.setString(2, vehicleId);
            statement.executeUpdate();
        }
    }

    public void saveProcessedRoutes(List<ProcessedRoute> routes) throws SQLException {
        String sql = "UPDATE routing_requests SET source_junction = ?, "
                + "status = 'PROCESSED' WHERE request_id = ?";
        String routeSql = "INSERT INTO processed_routes "
                + "(request_id, route_path, travel_time) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE route_path = VALUES(route_path), "
                + "travel_time = VALUES(travel_time), processed_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 PreparedStatement routeStatement = connection.prepareStatement(routeSql);
                 PreparedStatement vehicleStatement = connection.prepareStatement(
                         "UPDATE vehicles SET current_junction = ? WHERE vehicle_id = ?")) {
                for (ProcessedRoute processed : routes) {
                    RoutingRequest request = processed.getRequest();
                    Route route = processed.getRoute();
                    statement.setString(1, request.getSource());
                    statement.setString(2, request.getRequestId());
                    statement.addBatch();
                    routeStatement.setString(1, request.getRequestId());
                    routeStatement.setString(2, route == null ? ""
                            : String.join(",", route.getJunctions()));
                    routeStatement.setInt(3, route == null ? 0 : route.getTotalTravelTime());
                    routeStatement.addBatch();
                    if (route != null) {
                        vehicleStatement.setString(1, request.getDestination());
                        vehicleStatement.setString(2, request.getVehicleId());
                        vehicleStatement.addBatch();
                    }
                }
                statement.executeBatch();
                routeStatement.executeBatch();
                vehicleStatement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static class AccountRecord {
        public final String username;
        public final String role;
        public final String vehicleId;
        public final byte[] salt;
        public final byte[] passwordHash;

        private AccountRecord(String username, String role, String vehicleId,
                              byte[] salt, byte[] passwordHash) {
            this.username = username;
            this.role = role;
            this.vehicleId = vehicleId;
            this.salt = salt;
            this.passwordHash = passwordHash;
        }
    }
}
