-- How close a misspelt word has to be to a catalog word before the strict search accepts it
-- (pg_trgm's `<%`). The default 0.6 only forgives a single wrong letter in a long word; 0.45
-- covers what players actually type ("bonanca", "starbrust") without dragging in noise.
--
-- Set on the database, NOT through Hikari's connectionInitSql: with `autoCommit = false` and
-- Hikari's default `isolateInternalQueries = false`, an init statement is never committed and
-- hands Exposed a connection with a transaction already open — every query on a freshly opened
-- pooled connection then dies with "Cannot change transaction isolation level in the middle of a
-- transaction". On the database it needs no statement per connection at all, and psql sees the
-- same threshold the engine does.
-- The `PERFORM` is load-bearing: until a pg_trgm function runs in this session, its library is not
-- loaded and its parameters are not registered, so Postgres cannot tell that this one is USERSET
-- and refuses the ALTER for anyone but a superuser ("permission denied to set parameter"). One
-- call registers it, and the database owner may then set it.
DO $$
BEGIN
    PERFORM similarity('a', 'a');

    EXECUTE format('ALTER DATABASE %I SET pg_trgm.word_similarity_threshold = 0.45', current_database());
END
$$;
