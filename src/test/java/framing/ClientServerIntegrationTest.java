package framing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real server on a free port and sends data to it with the real client.
 */
class ClientServerIntegrationTest {

    @TempDir
    Path tempDir;

    private Path outputFile;
    private Path databaseFile;

    /**
     * Starts the server in a background thread so it handles exactly one client,
     * runs the given code (the "client side"), and waits for the server to finish.
     */
    private void runWithServer(ClientAction clientAction) throws Exception {
        outputFile = tempDir.resolve("output.txt");
        databaseFile = tempDir.resolve("messages.db");

        try (Server server = new Server(0, outputFile, databaseFile.toString())) {
            Thread serverThread = new Thread(() -> {
                try {
                    server.handleOneClient();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.start();

            clientAction.run(server.getPort());

            serverThread.join(5000);
        }
    }

    interface ClientAction {
        void run(int port) throws Exception;
    }

    @Test
    void sample1AsciiFileEndsUpInOutputFile() throws Exception {
        runWithServer(port -> {
            int sent = Client.sendFile(Path.of("sample-input-ascii.bin"), "localhost", port);
            assertEquals(3, sent);
        });

        List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
        assertEquals(List.of(
                "PAYMENT|12345|100.50|HRK",
                "REFUND|98765|42.00|EUR",
                "END"), lines);
    }

    @Test
    void sample2CroatianFileIsConvertedToUtf8AndStoredInDatabase() throws Exception {
        runWithServer(port -> {
            int sent = Client.sendFile(Path.of("sample-input-hr.bin"), "localhost", port);
            assertEquals(3, sent);
        });

        // readAllLines with UTF-8 would fail if the file was not valid UTF-8
        List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
        assertEquals(List.of(
                "UPLATA|ČĆŽŠĐ|100.50|HRK",
                "STORNO|žćčšđ|42.00|EUR",
                "KRAJ"), lines);

        // check the database rows too
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT transaction_type, transaction_id, amount, currency FROM messages ORDER BY id")) {

            assertTrue(rows.next());
            assertEquals("UPLATA", rows.getString("transaction_type"));
            assertEquals("ČĆŽŠĐ", rows.getString("transaction_id"));
            assertEquals("100.50", rows.getString("amount"));
            assertEquals("HRK", rows.getString("currency"));

            assertTrue(rows.next());
            assertEquals("STORNO", rows.getString("transaction_type"));
            assertEquals("žćčšđ", rows.getString("transaction_id"));
            assertEquals("42.00", rows.getString("amount"));
            assertEquals("EUR", rows.getString("currency"));

            assertTrue(rows.next());
            assertEquals("KRAJ", rows.getString("transaction_type"));
            assertNull(rows.getString("transaction_id"));

            assertFalse(rows.next(), "there should be exactly 3 rows");
        }
    }

    @Test
    void clientReportsTruncatedFileButSendsFramesBeforeIt() throws Exception {
        Path badFile = tempDir.resolve("bad.bin");
        // one valid frame "END", then a frame that says 16 bytes but has only 1
        Files.write(badFile, new byte[]{0x00, 0x03, 'E', 'N', 'D', 0x00, 0x10, 'A'});

        runWithServer(port -> {
            FrameException e = assertThrows(FrameException.class,
                    () -> Client.sendFile(badFile, "localhost", port));
            assertTrue(e.getMessage().contains("truncated payload"));
        });

        assertEquals(List.of("END"), Files.readAllLines(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    void serverRejectsZeroLengthFrameAndKeepsEarlierMessages() throws Exception {
        // here we skip the Client class and write raw bytes to the socket,
        // because the client would never send an invalid frame
        runWithServer(port -> {
            try (Socket socket = new Socket("localhost", port)) {
                OutputStream out = socket.getOutputStream();
                out.write(new byte[]{0x00, 0x03, 'E', 'N', 'D', 0x00, 0x00});
                out.flush();
            }
        });

        assertEquals(List.of("END"), Files.readAllLines(outputFile, StandardCharsets.UTF_8));
    }
}
