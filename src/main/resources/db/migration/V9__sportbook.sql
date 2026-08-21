CREATE TABLE sportbook_sessions (
    id             BIGSERIAL PRIMARY KEY,
    token          VARCHAR(255) NOT NULL,
    external_token VARCHAR(255),
    player_id      VARCHAR(255) NOT NULL,
    currency       VARCHAR(10)  NOT NULL,
    aggregator_id  BIGINT       NOT NULL REFERENCES aggregators (id),
    data           JSON         NOT NULL,
    created_at     TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX sportbook_sessions_token_unique ON sportbook_sessions (token);

CREATE INDEX sportbook_sessions_player_id ON sportbook_sessions (player_id);

CREATE TABLE bets (
    id          BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    player_id   VARCHAR(255) NOT NULL,
    session_id  BIGINT       NOT NULL REFERENCES sportbook_sessions (id),
    currency    VARCHAR(10)  NOT NULL,
    bet_amount  BIGINT       NOT NULL,
    win_amount  BIGINT       NOT NULL DEFAULT 0,
    type        VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    selections  JSON         NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- Settlement callbacks resolve the bet by the aggregator's bet id.
CREATE UNIQUE INDEX bets_external_id_unique ON bets (external_id);

CREATE INDEX bets_player_id ON bets (player_id);
