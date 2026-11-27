package framing;

/**
 * Thrown when the data does not follow the framing protocol
 * (zero length, too long, truncated header, truncated payload).
 */
public class FrameException extends Exception {

    public FrameException(String message) {
        super(message);
    }
}
