package framing;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a frame (2-byte big-endian length + payload) to an OutputStream.
 */
public class FrameWriter {

    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        int length = payload.length;
        if (length < FrameReader.MIN_PAYLOAD_LENGTH || length > FrameReader.MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Payload length must be between 1 and 4096, was " + length);
        }

        out.write((length >> 8) & 0xFF); // high byte first (big-endian)
        out.write(length & 0xFF);        // then low byte
        out.write(payload);
    }
}
