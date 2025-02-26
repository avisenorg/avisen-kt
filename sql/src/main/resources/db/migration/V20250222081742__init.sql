CREATE TABLE IF NOT EXISTS block (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    data VARCHAR(20000) NOT NULL,
    timestamp BIGINT NOT NULL,
    height BIGINT NOT NULL,
    create_date TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS network_peer (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(30) NOT NULL,
    type VARCHAR(12) NOT NULL
)
