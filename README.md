# Framed Message Client/Server

TCP client/server in Java. The client reads a binary file of length-prefixed frames and sends them over TCP; the server decodes each payload (ISO-8859-2), writes it to a UTF-8 text file and stores it in SQLite.

## Requirements

- Java 21+
- Maven 3.9+

## Build

```bash
mvn clean package
```

Runs all tests and produces `target/framed-message-1.0-jar-with-dependencies.jar` with the SQLite driver bundled in.

To run only the tests:

```bash
mvn test
```

## Running

Start the server first (terminal 1):

```bash
java -cp target/framed-message-1.0-jar-with-dependencies.jar framing.Server 9000 output.txt messages.db
```

Send a file from another terminal:

```bash
java -cp target/framed-message-1.0-jar-with-dependencies.jar framing.Client sample-input-ascii.bin localhost 9000
java -cp target/framed-message-1.0-jar-with-dependencies.jar framing.Client sample-input-hr.bin localhost 9000
```

The server stays up after each client and waits for the next one. Stop with Ctrl+C.

> On Java 24+ the SQLite driver prints a warning about a restricted method. It's harmless. Add `--enable-native-access=ALL-UNNAMED` after `java` to suppress it.

## Arguments

**Server:** `framing.Server [port] [outputFile] [databaseFile]`

All three are optional. Defaults: port `9000`, file `output.txt`, database `messages.db`. The output file is opened in append mode, so restarting the server won't overwrite earlier output.

**Client:** `framing.Client <inputFile> [host] [port]`

`inputFile` is required. Host defaults to `localhost`, port to `9000`. Exit code 0 on success, 1 on error (bad file, server not reachable, etc.).

## Wire format

```
+----------------+----------------+---------------------------+
| length (MSB)   | length (LSB)   | payload (length bytes)    |
+----------------+----------------+---------------------------+
```

Two-byte unsigned big-endian length followed by the payload. Valid payload: 1–4096 bytes. Zero length, length > 4096, a truncated header or truncated payload are all errors.

## Database

SQLite, table created automatically on server startup:

```sql
CREATE TABLE IF NOT EXISTS messages (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    raw_message      TEXT NOT NULL,
    transaction_type TEXT,
    transaction_id   TEXT,
    amount           TEXT,
    currency         TEXT,
    received_at      TEXT NOT NULL DEFAULT (datetime('now'))
);
```

```bash
sqlite3 messages.db "SELECT * FROM messages;"
```

## Project layout

```
src/main/java/framing/
    FrameReader.java      frame parsing, shared between client and server
    FrameWriter.java      writes a single frame (header + payload)
    FrameException.java   thrown on protocol violations
    Client.java           reads the file, sends frames to the server
    Server.java           receives frames, decodes, writes file and database
    Message.java          splits a decoded message into its fields
    MessageDatabase.java  SQLite access
src/main/resources/
    schema.sql
src/test/java/framing/
    FrameReaderTest.java
    MessageTest.java
    ClientServerIntegrationTest.java
```

See [DESIGN.md](DESIGN.md) for decisions and trade-offs.