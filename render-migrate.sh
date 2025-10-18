#!/bin/bash
# Render.com migration script

set -e

echo "🔵 Running Render database migration..."

# Render provides DATABASE_URL in format: postgresql://user:pass@host:port/db
if [ -z "$DATABASE_URL" ]; then
    echo "❌ ERROR: DATABASE_URL not set"
    exit 1
fi

echo "📄 Applying migration..."

# Use psql with DATABASE_URL directly
psql $DATABASE_URL << 'EOF'
-- Session State Refactoring Migration

BEGIN;

-- Backup existing data
CREATE TABLE IF NOT EXISTS rooms_backup AS SELECT * FROM rooms;

-- Drop session-related columns
ALTER TABLE rooms DROP COLUMN IF EXISTS current_players CASCADE;
ALTER TABLE rooms DROP COLUMN IF EXISTS host_id CASCADE;
ALTER TABLE rooms DROP COLUMN IF EXISTS status CASCADE;

-- Add persistent metadata columns
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS game_mode VARCHAR(50) DEFAULT 'CLASSIC';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE;

-- Drop player_rooms table
DROP TABLE IF EXISTS player_rooms CASCADE;

-- Add constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'rooms_name_unique'
    ) THEN
        ALTER TABLE rooms ADD CONSTRAINT rooms_name_unique UNIQUE (name);
    END IF;
END $$;

ALTER TABLE rooms ALTER COLUMN name SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET NOT NULL;
ALTER TABLE rooms ALTER COLUMN max_players SET DEFAULT 8;

COMMIT;

-- Verification
SELECT COUNT(*) as room_count FROM rooms;
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'room%';

EOF

if [ $? -eq 0 ]; then
    echo "✅ Migration completed successfully!"
else
    echo "❌ Migration failed!"
    exit 1
fi
