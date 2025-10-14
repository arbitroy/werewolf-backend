-- Create tables with proper indexes

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host_id UUID NOT NULL,
    max_players INTEGER DEFAULT 8,
    current_players INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (host_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS player_rooms (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    room_id UUID NOT NULL,
    role VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    is_host BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES users(id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    UNIQUE(player_id, room_id)
);

CREATE TABLE IF NOT EXISTS game_states (
    id UUID PRIMARY KEY,
    room_id UUID UNIQUE NOT NULL,
    phase VARCHAR(20) NOT NULL,
    time_remaining INTEGER DEFAULT 0,
    last_event TEXT,
    winner VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

-- Indexes for better performance
CREATE INDEX idx_rooms_status ON rooms(status);
CREATE INDEX idx_player_rooms_room ON player_rooms(room_id);
CREATE INDEX idx_player_rooms_player ON player_rooms(player_id);
CREATE INDEX idx_game_states_room ON game_states(room_id);