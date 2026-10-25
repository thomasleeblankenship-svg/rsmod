-- Address that clients connect to when switching to this world.
ALTER TABLE worlds ADD COLUMN host TEXT;

-- Client world list location id (e.g., 0 = United States, 1 = United Kingdom).
ALTER TABLE worlds ADD COLUMN location INTEGER NOT NULL DEFAULT 0;

-- Player count last reported by the world's heartbeat.
ALTER TABLE worlds ADD COLUMN population INTEGER NOT NULL DEFAULT 0;

-- Time of the world's last heartbeat. A world is considered online while this is recent.
ALTER TABLE worlds ADD COLUMN last_heartbeat TIMESTAMP;

UPDATE worlds SET host = '127.0.0.1' WHERE id = 1;
