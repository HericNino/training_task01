CREATE TABLE IF NOT EXISTS messages (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    raw_message      TEXT NOT NULL,
    transaction_type TEXT,
    transaction_id   TEXT,
    amount           TEXT,
    currency         TEXT,
    received_at      TEXT NOT NULL DEFAULT (datetime('now'))
);
