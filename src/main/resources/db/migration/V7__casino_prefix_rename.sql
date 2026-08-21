-- Rename casino-domain tables with the casino_ prefix (sportsbook prep).
-- Sequences, indexes and FK constraints keep their historical names; behavior is unchanged.
ALTER TABLE providers RENAME TO casino_providers;
ALTER TABLE games RENAME TO casino_games;
ALTER TABLE game_variants RENAME TO casino_game_variants;
ALTER TABLE game_collections RENAME TO casino_game_collections;
ALTER TABLE game_favourites RENAME TO casino_game_favourites;
