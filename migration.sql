-- migration.sql
-- Run this to clean up your database

-- Remove session-related columns from rooms table
ALTER TABLE rooms DROP COLUMN IF EXISTS current_players;
ALTER TABLE rooms DROP COLUMN IF EXISTS host_id;
ALTER TABLE rooms DROP COLUMN IF EXISTS status;

-- Add new columns for persistent metadata
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS game_mode VARCHAR(50) DEFAULT 'CLASSIC';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE;

-- Drop the entire player_rooms table (it's all transient data!)
DROP TABLE IF EXISTS player_rooms CASCADE;

-- Make room name unique
ALTER TABLE rooms ADD CONSTRAINT rooms_name_unique UNIQUE (name);