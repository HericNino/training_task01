package framing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Stores received messages into a SQLite database file.
 * The table definition is in src/main/resources/schema.sql.
 */
public class MessageDatabase implements AutoCloseable {

    private static final String INSERT_SQL =
            "INSERT INTO messages (raw_message, transaction_type, transaction_id, amount, currency) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private final Connection connection;

    public MessageDatabase(String databaseFile) throws SQLException, IOException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        createTable();
    }

    private void createTable() throws SQLException, IOException {
        String schema;
        try (InputStream in = MessageDatabase.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IOException("schema.sql not found on the classpath");
            }
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(schema);
        }
    }

    public void save(Message message) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_SQL)) {
            insert.setString(1, message.rawText());
            insert.setString(2, message.transactionType());
            insert.setString(3, message.transactionId());
            insert.setString(4, message.amount());
            insert.setString(5, message.currency());
            insert.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
