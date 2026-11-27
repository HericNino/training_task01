package framing;

/**
 * One decoded message, split into its fields.
 * Example: "PAYMENT|12345|100.50|HRK" -> type=PAYMENT, id=12345, amount=100.50, currency=HRK
 *
 * Messages with fewer fields (like the "END" marker) just have the missing fields set to null.
 */
public record Message(String rawText, String transactionType, String transactionId, String amount, String currency) {

    public static Message fromText(String text) {
        // -1 keeps empty fields, e.g. "A||B" gives ["A", "", "B"]
        String[] fields = text.split("\\|", -1);

        return new Message(
                text,
                getField(fields, 0),
                getField(fields, 1),
                getField(fields, 2),
                getField(fields, 3));
    }

    private static String getField(String[] fields, int index) {
        if (index < fields.length) {
            return fields[index];
        }
        return null;
    }
}
