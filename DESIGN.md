# Design notes

## How it's structured

```
input file --> Client --(TCP, raw frames)--> Server --> output.txt
                                                    \--> messages.db
```

The core parsing logic lives in `FrameReader`, which works on any `InputStream`. The client uses it on a file, the server uses it on a socket — same class, same rules, tested once. That way there's no risk of the two sides disagreeing on what a valid frame looks like.

## Assumptions

A few things that weren't entirely spelled out in the assignment:

- EOF is only valid right after a complete frame. If the stream ends mid-header or mid-payload, that's an error, not a silent truncation.
- The client sends raw bytes — it doesn't decode anything. Decoding is the server's job, as the assignment diagram shows.
- Because the client goes through `FrameReader` too, it validates frames before sending. An invalid frame in the input file is caught early with a clear error message (which frame number, what was wrong).
- Messages are `|`-separated. The fields are `transaction_type`, `transaction_id`, `amount`, `currency`. Shorter messages (like the `END`/`KRAJ` trailer) just get `NULL` for the missing columns. The original text always goes into `raw_message` as-is.
- Output file uses `\n` on all platforms, opened in append mode.

## Decisions I went back and forth on

**Streaming vs. validate-then-send.** The client sends frames one at a time as it reads them. If frame 5 is broken, frames 1–4 are already on the server. The alternative would be to read the whole file first, validate everything, and only then connect and send. I went with streaming because the assignment explicitly says to process frame by frame, and it avoids keeping the whole file in memory. The error output names the exact frame that failed, so it's clear what happened.

**Server behaviour on bad frames.** When the server gets an invalid frame, it logs the error and closes that connection. It can't safely continue on the same connection — once the length header is untrustworthy, we have no idea where the next frame starts. Everything received before the bad frame is kept. The server then goes back to waiting for the next client.

**Single-threaded server.** One client at a time. It keeps the output file and database writes ordered and simple. For real load you'd want a thread pool with a single writer thread, but that's not what's being tested here.

**Amount as TEXT.** SQLite's `REAL` type would silently mangle `100.50` into a floating point number. For anything money-related that's wrong. The raw string is kept. In PostgreSQL this would be `NUMERIC(15,2)`.

**Flush after every message.** Slower than batching, but at these volumes it doesn't matter — and it means a crash won't lose data that the server already acknowledged receiving.

**Database errors don't kill the server.** If an insert fails, it's logged and the message is still in the flat file. The connection stays up and the next messages come through fine.

**No read timeout.** A client that connects and then goes silent blocks the server indefinitely. `setSoTimeout` would fix this, but it's out of scope here.

**Payload content is not validated.** Any byte sequence is accepted (every byte has a meaning in ISO-8859-2). A payload with a newline in it would produce an extra line in the output file — something worth fixing in a real system, but not relevant for the given sample data.

## Dependencies

- `org.xerial:sqlite-jdbc` — SQLite driver, no installation required, database is a single file
- `org.junit.jupiter:junit-jupiter` — test-only

## Tests

- `FrameReaderTest` — parsing edge cases: valid frames, clean EOF, unsigned big-endian length, max length 4096, zero length, oversized, truncated header, truncated payload, writer round-trip
- `MessageTest` — field splitting
- `ClientServerIntegrationTest` — real client talking to a real server on a free port: both sample files, a file with a truncated frame (client error, earlier frames stored), and a zero-length frame injected via raw socket (server rejects it, earlier frames stored)