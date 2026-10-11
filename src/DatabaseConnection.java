import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Properties settings = new Properties();
        if (Files.exists(Paths.get("db.properties"))) {
            try (InputStream input = Files.newInputStream(Paths.get("db.properties"))) {
                settings.load(input);
            } catch (IOException exception) {
                throw new SQLException("Could not read db.properties.", exception);
            }
        }

        String url = setting("VEGAM_DB_URL", settings, "url",
                "jdbc:mysql://localhost:3306/vegam_traffic"
                        + "?allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("VEGAM_DB_USER", settings, "username", "root");
        String password = setting("VEGAM_DB_PASSWORD", settings, "password", "");
        return DriverManager.getConnection(url, username, password);
    }

    private static String setting(String environmentVariable, Properties settings,
                                  String property, String defaultValue) {
        String value = System.getenv(environmentVariable);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return settings.getProperty(property, defaultValue).trim();
    }
}
