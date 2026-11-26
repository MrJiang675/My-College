CREATE TABLE IF NOT EXISTS college_workspace (
    owner_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    payload TEXT NOT NULL
);
