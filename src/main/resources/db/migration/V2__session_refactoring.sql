-- V2: Session Refactoring Migration
-- Remove transient session data from database

-- ============================================
-- BACKUP (Optional - for safety)
-- ============================================
CREATE TABLE IF NOT EXISTS rooms_backup_v2 AS 
SELECT * FROM rooms;

CREATE TABLE IF NOT EXISTS player_rooms_backup_v2 AS 
SELECT * FROM player_rooms;

-- ============================================
-- PHASE 1: Drop session-related columns from rooms
-- ============================================
ALTER TABLE rooms DROP COLUMN IF EXISTS current_players;
ALTER TABLE rooms DROP COLUMN IF EXISTS host_id;
ALTER TABLE rooms DROP COLUMN IF EXISTS status;

-- ============================================
-- PHASE 2: Add persistent metadata columns
-- ============================================
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS game_mode VARCHAR(50) DEFAULT 'CLASSIC';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE;

-- ============================================
-- PHASE 3: Migrate existing data
-- ============================================
-- Try to populate created_by from backup if possible
UPDATE rooms r
SET created_by = (
    SELECT player_id 
    FROM player_rooms_backup_v2 prb 
    WHERE prb.room_id = r.id 
    AND prb.is_host = true 
    LIMIT 1
)
WHERE created_by IS NULL;

-- For any rooms without a creator, use a default admin UUID or first player
-- You might need to adjust this based on your data
UPDATE rooms 
SET created_by = COALESCE(
    created_by, 
    '00000000-0000-0000-0000-000000000000'::uuid
)
WHERE created_by IS NULL;

-- Set created_at from current timestamp for existing rooms
UPDATE rooms 
SET created_at = CURRENT_TIMESTAMP 
WHERE created_at IS NULL;

-- ============================================
-- PHASE 4: Add constraints
-- ============================================
ALTER TABLE rooms ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN name SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET DEFAULT 8;

-- Add unique constraint on room name if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'rooms_name_unique'
    ) THEN
        ALTER TABLE rooms ADD CONSTRAINT rooms_name_unique UNIQUE (name);
    END IF;
END $$;

-- ============================================
-- PHASE 5: Drop player_rooms table entirely
-- ============================================
DROP TABLE IF EXISTS player_rooms CASCADE;

-- ============================================
-- VERIFICATION
-- ============================================
-- Check that all rooms have required fields
DO $$
DECLARE
    missing_data INTEGER;
BEGIN
    SELECT COUNT(*) INTO missing_data
    FROM rooms
    WHERE created_by IS NULL OR created_at IS NULL;
    
    IF missing_data > 0 THEN
        RAISE EXCEPTION 'Migration failed: % rooms have missing data', missing_data;
    END IF;
    
    RAISE NOTICE 'Migration successful: All rooms have required fields';
END $$;
