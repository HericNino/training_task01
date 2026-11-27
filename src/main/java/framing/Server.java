package framing;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;

/**
 * Receives framed messages over TCP, decodes them from ISO-8859-2 and
 * stores them in a UTF-8 text file (one message per line) and in a SQLite database.
 *
 * Usage: Server [port] [outputFile] [databaseFile]
 */
public class Server implements AutoCloseable {

    private static final Charset INPUT_CHARSET = Charset.forName("ISO-8859-2");

    private final ServerSocket serverSocket;
    private final BufferedWriter outputWriter;
    private final MessageDatabase database;

    public Server(int port, Path outputFile, String databaseFile) throws IOException, SQLException {
        serverSocket = new ServerSocket(port);
        outputWriter = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        database = new MessageDatabase(databaseFile);
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        Path outputFile = Path.of(args.length > 1 ? args[1] : "output.txt");
        String databaseFile = args.length > 2 ? args[2] : "messages.db";

        try (Server server = new Server(port, outputFile, databaseFile)) {
            System.out.println("Server listening on port " + server.getPort());
            System.out.println("Output file: " + outputFile.toAbsolutePath());
            System.out.println("Database:    " + Path.of(databaseFile).toAbsolutePath());
            server.run();
        } catch (IOException | SQLException e) {
            System.err.println("ERROR: could not start server: " + e.getMessage());
            System.exit(1);
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Serves clients one after another until the server is closed.
     */
    public void run() {
        while (!serverSocket.isClosed()) {
            try {
                handleOneClient();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("ERROR: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Waits for one client, reads all of its frames and then closes the connection.
     * If a frame is invalid, the error is reported and the connection is closed,
     * but the messages received before it are kept.
     */
    public void handleOneClient() throws IOException {
        try (Socket client = serverSocket.accept()) {
            String clientName = client.getRemoteSocketAddress().toString();
            System.out.println("Client connected: " + clientName);

            FrameReader reader = new FrameReader(new BufferedInputStream(client.getInputStream()));
            int count = 0;

            try {
                byte[] payload;
                while ((payload = reader.readFrame()) != null) {
                    processMessage(payload);
                    count++;
                }
                System.out.println("Client " + clientName + " finished, received " + count + " message(s)");
            } catch (FrameException e) {
                System.err.println("ERROR: invalid frame from " + clientName + ": " + e.getMessage()
                        + ". Closing connection (" + count + " message(s) were stored before the error)");
            }
        }
    }

    private void processMessage(byte[] payload) throws IOException {
        String text = new String(payload, INPUT_CHARSET);

        // 1) flat file - flush right away so the line is on disk even if the server crashes later
        outputWriter.write(text);
        outputWriter.write("\n");
        outputWriter.flush();

        // 2) database
        try {
            database.save(Message.fromText(text));
        } catch (SQLException e) {
            System.err.println("ERROR: could not save message to database: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        outputWriter.close();
        try {
            database.close();
        } catch (SQLException e) {
            System.err.println("ERROR: could not close database: " + e.getMessage());
        }
    }
}
