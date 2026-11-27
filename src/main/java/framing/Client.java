package framing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads framed messages from a file and sends them to the server over TCP.
 *
 * Usage: Client <inputFile> [host] [port]
 */
public class Client {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Client <inputFile> [host] [port]");
            System.exit(1);
        }

        Path inputFile = Path.of(args[0]);
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 9000;

        try {
            int sent = sendFile(inputFile, host, port);
            System.out.println("Sent " + sent + " frame(s) to " + host + ":" + port);
        } catch (FrameException e) {
            System.err.println("ERROR: malformed input file " + inputFile + ": " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Sends every frame from the file to the server, in the same order.
     * Frames are sent one by one, so if the file is broken in the middle,
     * the frames before the broken one are already sent.
     *
     * @return number of frames sent
     */
    public static int sendFile(Path inputFile, String host, int port) throws IOException, FrameException {
        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(inputFile));
             Socket socket = new Socket(host, port);
             OutputStream socketOut = new BufferedOutputStream(socket.getOutputStream())) {

            FrameReader reader = new FrameReader(fileIn);
            int count = 0;

            byte[] payload;
            while ((payload = reader.readFrame()) != null) {
                FrameWriter.writeFrame(socketOut, payload);
                socketOut.flush();
                count++;
            }

            return count;
        }
    }
}
