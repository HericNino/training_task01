package framing;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads length-prefixed frames from an InputStream.
 * The same class is used by the client (reading the file) and the server (reading the socket).
 *
 * Frame format:
 *   [2 bytes length, unsigned, big-endian] [payload of exactly "length" bytes]
 */
public class FrameReader {

    public static final int MIN_PAYLOAD_LENGTH = 1;
    public static final int MAX_PAYLOAD_LENGTH = 4096;

    private final InputStream in;
    private int frameNumber = 0;

    public FrameReader(InputStream in) {
        this.in = in;
    }

    /**
     * Reads the next frame and returns its payload bytes.
     *
     * @return the payload, or null if the stream ended cleanly right after a complete frame
     * @throws FrameException if the frame breaks the protocol rules
     */
    public byte[] readFrame() throws IOException, FrameException {
        int highByte = in.read();
        if (highByte == -1) {
            // EOF exactly between two frames
            return null;
        }
        frameNumber++;

        int lowByte = in.read();
        if (lowByte == -1) {
            throw new FrameException("Frame " + frameNumber + ": truncated header (only 1 of 2 length bytes)");
        }

        // read() returns 0-255, so this gives an unsigned value 0-65535 (MSB first)
        int length = (highByte << 8) | lowByte;

        if (length < MIN_PAYLOAD_LENGTH) {
            throw new FrameException("Frame " + frameNumber + ": zero-length payload is not allowed");
        }
        if (length > MAX_PAYLOAD_LENGTH) {
            throw new FrameException("Frame " + frameNumber + ": payload length " + length
                    + " is bigger than the maximum of " + MAX_PAYLOAD_LENGTH);
        }

        // readNBytes keeps reading until it has "length" bytes or the stream ends
        byte[] payload = in.readNBytes(length);
        if (payload.length < length) {
            throw new FrameException("Frame " + frameNumber + ": truncated payload (expected " + length
                    + " bytes, got " + payload.length + ")");
        }

        return payload;
    }
}
