-- The wide net of the search: does any word of the row start close enough to the typed word?
-- Compared over the word's leading `length(needle)` characters, so it answers both a badly
-- misspelt whole word ("startbust" -> starburst, distance 2) and a half-typed one ("blakj" ->
-- blackjack, distance 2) with the same test.
--
-- Deliberately NOT indexable — Levenshtein has no GIN operator class. It is only ever reached
-- when the indexed strict search answered nothing at all (see SearchIndex.matches), so the
-- sequential scan it costs is the price of not showing the player an empty screen.
CREATE OR REPLACE FUNCTION casino_search_close(haystack text, needle text, max_distance int) RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM unnest(string_to_array(casino_search_norm(haystack), ' ')) AS word
        WHERE levenshtein_less_equal(left(word, length(needle)), needle, max_distance) <= max_distance
    )
$$;
