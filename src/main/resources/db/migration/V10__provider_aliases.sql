-- Other spellings of the same vendor, as other aggregators name it. The sync collapses a feed's
-- provider onto an existing row when the spelling matches one of these, so a vendor cannot land
-- in the catalog twice (GamingFlow's `pragmatic` next to OneGameHub's `pragmatic_play`).
ALTER TABLE casino_providers
    ADD COLUMN aliases JSON NOT NULL DEFAULT '[]';
