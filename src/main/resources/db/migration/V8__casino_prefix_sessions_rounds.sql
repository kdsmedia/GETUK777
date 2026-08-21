-- Continue the casino_ prefix rename (V7) for the session/round tables.
-- Sequences, indexes and FK constraints keep their historical names; behavior is unchanged.
ALTER TABLE sessions RENAME TO casino_sessions;
ALTER TABLE rounds RENAME TO casino_rounds;
