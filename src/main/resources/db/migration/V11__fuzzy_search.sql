-- Typo-tolerant catalog search. Two building blocks, both immutable so they can be indexed:
--   casino_search_norm     — lower-cased, punctuation collapsed to single spaces. Trigram-indexed,
--                            which serves both `LIKE '%part%'` (a fragment from either side) and
--                            pg_trgm's `<%` word-similarity operator (a misspelt word).
--   casino_search_phonetic — double-metaphone code per word >= 4 chars. Catches the misspellings
--                            trigrams cannot: "rulet" -> Roulette, "gaets" -> Gates, "olimp" -> Olympus.
--
-- The indexed expressions below are mirrored EXACTLY by SearchIndexes.kt — the planner matches an
-- expression index structurally, so the column list and its order must stay in sync on both sides.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

CREATE OR REPLACE FUNCTION casino_search_norm(value text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT btrim(regexp_replace(lower(value), '[^[:alnum:]]+', ' ', 'g')) $$;

CREATE OR REPLACE FUNCTION casino_search_phonetic(value text) RETURNS text[]
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT coalesce(array_agg(DISTINCT lower(dmetaphone(word))), '{}')
    FROM unnest(string_to_array(casino_search_norm(value), ' ')) AS word
    WHERE length(word) >= 4 AND dmetaphone(word) <> ''
$$;

CREATE INDEX IF NOT EXISTS casino_games_search_trgm_idx ON casino_games
    USING gin (casino_search_norm(name || ' ' || identity) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS casino_games_search_phonetic_idx ON casino_games
    USING gin (casino_search_phonetic(name || ' ' || identity));

CREATE INDEX IF NOT EXISTS casino_providers_search_trgm_idx ON casino_providers
    USING gin (casino_search_norm(name || ' ' || identity || ' ' || CAST(aliases AS TEXT)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS casino_providers_search_phonetic_idx ON casino_providers
    USING gin (casino_search_phonetic(name || ' ' || identity || ' ' || CAST(aliases AS TEXT)));

CREATE INDEX IF NOT EXISTS collections_search_trgm_idx ON collections
    USING gin (casino_search_norm(CAST(name AS TEXT) || ' ' || identity) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS collections_search_phonetic_idx ON collections
    USING gin (casino_search_phonetic(CAST(name AS TEXT) || ' ' || identity));

CREATE INDEX IF NOT EXISTS aggregators_search_trgm_idx ON aggregators
    USING gin (casino_search_norm(identity) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS aggregators_search_phonetic_idx ON aggregators
    USING gin (casino_search_phonetic(identity));
