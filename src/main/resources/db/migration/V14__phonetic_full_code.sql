-- `dmetaphone` truncates its code to four characters, which is fine for a short word and useless
-- for a long one: "startbust", "street" and "stardom" all collapse to STRT, so searching for
-- "startbust" answered eighteen unrelated Street games and not Starburst (STRP). `metaphone` with
-- an explicit output length keeps the whole code — STRTBST / STRT / STRTM — and the words stop
-- colliding, while the misspellings this branch exists for still land on the same code:
-- rulet/roulette = RLT, gaets/gates = KTS, krown/crown = KRN.
--
-- A word that is all digits ("1000") has no code at all and is dropped, as before.
CREATE OR REPLACE FUNCTION casino_search_phonetic(value text) RETURNS text[]
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT coalesce(array_agg(DISTINCT lower(metaphone(word, 16))), '{}')
    FROM unnest(string_to_array(casino_search_norm(value), ' ')) AS word
    WHERE length(word) >= 4 AND metaphone(word, 16) <> ''
$$;

-- The indexes hold codes produced by the OLD body; replacing an IMMUTABLE function does not
-- rebuild them, so they must be rebuilt explicitly or they keep answering with dmetaphone codes.
REINDEX INDEX casino_games_search_phonetic_idx;
REINDEX INDEX casino_providers_search_phonetic_idx;
REINDEX INDEX collections_search_phonetic_idx;
REINDEX INDEX aggregators_search_phonetic_idx;
