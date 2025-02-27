CREATE TABLE IF NOT EXISTS block (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    data jsonb NOT NULL,
    timestamp BIGINT NOT NULL,
    height BIGINT NOT NULL,
    create_date TIMESTAMP NOT NULL
);
