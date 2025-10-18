package com.werewolfkill.game.service;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    /**
     * Get all rooms (persistent metadata only)
     */
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    /**
     * Get room by ID
     */
    public Optional<Room> getRoomById(UUID roomId) {
        return roomRepository.findById(roomId);
    }

    /**
     * Create a new room (persists configuration only)
     */
    @Transactional
    public Room createRoom(String name, UUID createdBy, Integer maxPlayers) {
        // Check for duplicate names
        if (roomRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Room name already exists");
        }
        
        Room room = new Room();
        room.setName(name);
        room.setCreatedBy(createdBy);
        room.setMaxPlayers(maxPlayers != null ? maxPlayers : 8);
        room.setCreatedAt(Instant.now());
        room.setGameMode("CLASSIC");
        room.setIsPublic(true);
        
        Room saved = roomRepository.save(room);
        System.out.println("✅ Room persisted to DB: " + name);
        
        return saved;
    }

    /**
     * Delete a room (only deletes metadata - session is separate)
     */
    @Transactional
    public void deleteRoom(UUID roomId) {
        roomRepository.deleteById(roomId);
        System.out.println("🗑️ Room deleted from DB: " + roomId);
    }

    /**
     * Update room settings
     */
    @Transactional
    public Room updateRoomSettings(UUID roomId, Integer maxPlayers, String gameMode) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        
        if (maxPlayers != null) {
            room.setMaxPlayers(maxPlayers);
        }
        if (gameMode != null) {
            room.setGameMode(gameMode);
        }
        
        return roomRepository.save(room);
    }

    // ❌ REMOVED: joinRoom() - now handled by WebSocket + SessionManager
    // ❌ REMOVED: leaveRoom() - now handled by WebSocket + SessionManager
    // ❌ REMOVED: getRoomPlayers() - now query SessionManager
}