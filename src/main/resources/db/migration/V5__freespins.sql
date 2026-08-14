-- `freespins` was never created by a migration: it only ever existed on brands where it had been
-- added by hand. OpenSessionUsecase calls findRedeemable() on EVERY session open, so on a brand
-- without the table every game launch fails with `relation "freespins" does not exist`.
CREATE TABLE IF NOT EXISTS freespins (
    id              BIGSERIAL PRIMARY KEY,
    reference_id    VARCHAR(255) NOT NULL,
    player_id       VARCHAR(255) NOT NULL,
    game_variant_id BIGINT       NOT NULL REFERENCES game_variants (id),
    currency        VARCHAR(8)   NOT NULL,
    spin_amount     BIGINT       NOT NULL,
    total_count     INT          NOT NULL,
    remaining_count INT          NOT NULL,
    start_at        TIMESTAMP    NOT NULL,
    end_at          TIMESTAMP    NOT NULL,
    cancelled_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL
);

-- The reference is the id shared with the provider; an inbound call resolves the grant by it.
CREATE UNIQUE INDEX IF NOT EXISTS freespins_reference_id_unique ON freespins (reference_id);

CREATE INDEX IF NOT EXISTS freespins_player_id_game_variant_id ON freespins (player_id, game_variant_id);
