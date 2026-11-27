package framing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameReaderTest {

    private FrameReader readerFor(byte[] bytes) {
        return new FrameReader(new ByteArrayInputStream(bytes));
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void readsAllFramesFromSample1() throws Exception {
        // first two frames of sample 1, plus the END frame
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(bytes(0x00, 0x18));
        data.write("PAYMENT|12345|100.50|HRK".getBytes(StandardCharsets.US_ASCII));
        data.write(bytes(0x00, 0x16));
        data.write("REFUND|98765|42.00|EUR".getBytes(StandardCharsets.US_ASCII));
        data.write(bytes(0x00, 0x03));
        data.write("END".getBytes(StandardCharsets.US_ASCII));

        FrameReader reader = readerFor(data.toByteArray());

        assertEquals("PAYMENT|12345|100.50|HRK", new String(reader.readFrame(), StandardCharsets.US_ASCII));
        assertEquals("REFUND|98765|42.00|EUR", new String(reader.readFrame(), StandardCharsets.US_ASCII));
        assertEquals("END", new String(reader.readFrame(), StandardCharsets.US_ASCII));
        assertNull(reader.readFrame(), "should return null at clean EOF");
    }

    @Test
    void emptyInputIsCleanEof() throws Exception {
        assertNull(readerFor(new byte[0]).readFrame());
    }

    @Test
    void lengthIsUnsignedBigEndian() throws Exception {
        // 0x00C8 = 200. If the byte 0xC8 was treated as signed it would be negative.
        byte[] data = new byte[2 + 200];
        data[0] = 0x00;
        data[1] = (byte) 0xC8;

        assertEquals(200, readerFor(data).readFrame().length);

        // 0x0100 = 256 - checks that the first byte is the high (most significant) one
        byte[] data2 = new byte[2 + 256];
        data2[0] = 0x01;
        data2[1] = 0x00;

        assertEquals(256, readerFor(data2).readFrame().length);
    }

    @Test
    void acceptsMaximumLength4096() throws Exception {
        byte[] data = new byte[2 + 4096];
        data[0] = 0x10; // 0x1000 = 4096
        data[1] = 0x00;

        assertEquals(4096, readerFor(data).readFrame().length);
    }

    @Test
    void rejectsZeroLength() {
        FrameException e = assertThrows(FrameException.class, () -> readerFor(bytes(0x00, 0x00)).readFrame());
        assertTrue(e.getMessage().contains("zero-length"));
    }

    @Test
    void rejectsLengthAbove4096() {
        byte[] data = new byte[2 + 4097];
        data[0] = 0x10; // 0x1001 = 4097
        data[1] = 0x01;

        FrameException e = assertThrows(FrameException.class, () -> readerFor(data).readFrame());
        assertTrue(e.getMessage().contains("4097"));
    }

    @Test
    void rejectsTruncatedHeader() {
        FrameException e = assertThrows(FrameException.class, () -> readerFor(bytes(0x00)).readFrame());
        assertTrue(e.getMessage().contains("truncated header"));
    }

    @Test
    void rejectsTruncatedPayload() {
        // says 5 bytes, but only 3 follow
        FrameException e = assertThrows(FrameException.class,
                () -> readerFor(bytes(0x00, 0x05, 'A', 'B', 'C')).readFrame());
        assertTrue(e.getMessage().contains("expected 5 bytes, got 3"));
    }

    @Test
    void errorMessageContainsFrameNumber() throws Exception {
        FrameReader reader = readerFor(bytes(0x00, 0x01, 'A', 0x00, 0x00));
        reader.readFrame();

        FrameException e = assertThrows(FrameException.class, reader::readFrame);
        assertTrue(e.getMessage().startsWith("Frame 2"));
    }

    @Test
    void writerAndReaderRoundTrip() throws Exception {
        byte[] payload = "Hello".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FrameWriter.writeFrame(out, payload);

        assertArrayEquals(bytes(0x00, 0x05, 'H', 'e', 'l', 'l', 'o'), out.toByteArray());
        assertArrayEquals(payload, readerFor(out.toByteArray()).readFrame());
    }

    @Test
    void writerRejectsEmptyPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameWriter.writeFrame(new ByteArrayOutputStream(), new byte[0]));
    }
}
