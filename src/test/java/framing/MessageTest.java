package framing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageTest {

    @Test
    void splitsTransactionIntoFields() {
        Message message = Message.fromText("UPLATA|ČĆŽŠĐ|100.50|HRK");

        assertEquals("UPLATA|ČĆŽŠĐ|100.50|HRK", message.rawText());
        assertEquals("UPLATA", message.transactionType());
        assertEquals("ČĆŽŠĐ", message.transactionId());
        assertEquals("100.50", message.amount());
        assertEquals("HRK", message.currency());
    }

    @Test
    void endMarkerHasOnlyType() {
        Message message = Message.fromText("END");

        assertEquals("END", message.transactionType());
        assertNull(message.transactionId());
        assertNull(message.amount());
        assertNull(message.currency());
    }
}
