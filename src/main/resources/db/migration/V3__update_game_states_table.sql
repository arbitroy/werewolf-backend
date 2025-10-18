-- V3: Comprehensive schema fix
-- Fixes all schema issues including rooms, game_states, and cleanup from Hibernate update mode

-- ============================================
-- PART 1: Fix ROOMS table
-- ============================================

-- Add columns if they don't exist (might have been partially added by Hibernate)
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS game_mode VARCHAR(50);
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_public BOOLEAN;

-- Set default values for existing NULL rows BEFORE adding NOT NULL constraint
UPDATE rooms 
SET created_by = '00000000-0000-0000-0000-000000000000'::uuid 
WHERE created_by IS NULL;

UPDATE rooms 
SET created_at = CURRENT_TIMESTAMP 
WHERE created_at IS NULL;

UPDATE rooms 
SET game_mode = 'CLASSIC' 
WHERE game_mode IS NULL;

UPDATE rooms 
SET is_public = true 
WHERE is_public IS NULL;

-- Now safely apply NOT NULL constraints
ALTER TABLE rooms ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN name SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET DEFAULT 8;

-- Add unique constraint on room name
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'rooms_name_unique'
    ) THEN
        ALTER TABLE rooms ADD CONSTRAINT rooms_name_unique UNIQUE (name);
    END IF;
END $$;

-- ============================================
-- PART 2: Create/Fix GAME_STATES table
-- ============================================

-- Create the table if it doesn't exist at all
CREATE TABLE IF NOT EXISTS game_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id VARCHAR(255) NOT NULL,
    phase VARCHAR(255) NOT NULL DEFAULT 'WAITING',
    day_number INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT false
);

-- If table exists but is missing columns, add them
ALTER TABLE game_states ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE game_states ADD COLUMN IF NOT EXISTS room_id VARCHAR(255);
ALTER TABLE game_states ADD COLUMN IF NOT EXISTS phase VARCHAR(255);
ALTER TABLE game_states ADD COLUMN IF NOT EXISTS day_number INTEGER DEFAULT 0;
ALTER TABLE game_states ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT false;

-- Update any NULL values with defaults
UPDATE game_states SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE game_states SET day_number = 0 WHERE day_number IS NULL;
UPDATE game_states SET is_active = false WHERE is_active IS NULL;
UPDATE game_states SET phase = 'WAITING' WHERE phase IS NULL;
UPDATE game_states SET room_id = '00000000-0000-0000-0000-000000000000' WHERE room_id IS NULL;

-- Add primary key if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'game_states_pkey' 
        AND conrelid = 'game_states'::regclass
    ) THEN
        ALTER TABLE game_states ADD PRIMARY KEY (id);
    END IF;
END $$;

-- Apply NOT NULL constraints
ALTER TABLE game_states ALTER COLUMN room_id SET NOT NULL;
ALTER TABLE game_states ALTER COLUMN phase SET NOT NULL;
ALTER TABLE game_states ALTER COLUMN day_number SET NOT NULL;
ALTER TABLE game_states ALTER COLUMN is_active SET NOT NULL;

-- ============================================
-- PART 3: Ensure USERS table is correct
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

-- ============================================
-- PART 4: Drop player_rooms table (transient session data)
-- ============================================

DROP TABLE IF EXISTS player_rooms CASCADE;

-- ============================================
-- PART 5: Create performance indexes
-- ============================================

CREATE INDEX IF NOT EXISTS idx_rooms_created_by ON rooms(created_by);
CREATE INDEX IF NOT EXISTS idx_game_states_room_id ON game_states(room_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- ============================================
-- PART 6: Verification
-- ============================================

DO $$
DECLARE
    rooms_null_count INTEGER;
    game_states_col_count INTEGER;
    rooms_col_count INTEGER;
BEGIN
    -- Verify rooms table has no NULL values in required fields
    SELECT COUNT(*) INTO rooms_null_count
    FROM rooms
    WHERE created_by IS NULL OR created_at IS NULL OR name IS NULL;
    
    IF rooms_null_count > 0 THEN
        RAISE EXCEPTION 'Migration failed: % rooms have NULL values in required fields', rooms_null_count;
    END IF;
    
    -- Verify game_states has all required columns
    SELECT COUNT(*) INTO game_states_col_count
    FROM information_schema.columns
    WHERE table_name = 'game_states'
    AND column_name IN ('id', 'room_id', 'phase', 'day_number', 'is_active');
    
    IF game_states_col_count < 5 THEN
        RAISE EXCEPTION 'Migration failed: game_states table missing required columns (found % of 5)', game_states_col_count;
    END IF;
    
    -- Verify rooms has all required columns
    SELECT COUNT(*) INTO rooms_col_count
    FROM information_schema.columns
    WHERE table_name = 'rooms'
    AND column_name IN ('id', 'name', 'created_by', 'created_at', 'max_players', 'game_mode', 'is_public');
    
    IF rooms_col_count < 7 THEN
        RAISE EXCEPTION 'Migration failed: rooms table missing required columns (found % of 7)', rooms_col_count;
    END IF;
    
    RAISE NOTICE '====================================';
    RAISE NOTICE '✅ V3 Migration completed successfully!';
    RAISE NOTICE '====================================';
    RAISE NOTICE 'Rooms table: % rows migrated', (SELECT COUNT(*) FROM rooms);
    RAISE NOTICE 'Game states table: % rows', (SELECT COUNT(*) FROM game_states);
    RAISE NOTICE 'Users table: % users', (SELECT COUNT(*) FROM users);
    RAISE NOTICE '====================================';
END $$;