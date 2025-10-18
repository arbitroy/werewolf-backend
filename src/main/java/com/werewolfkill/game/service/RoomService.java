package com.werewolfkill.game.service;

import com.werewolfkill.game.dto.PlayerDTO;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.User;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.UserRepository;
import com.werewolfkill.game.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SessionManager sessionManager;

    public List<Room> getAvailableRooms() {
        return roomRepository.findAll();
    }

    @Transactional
    public Room createRoom(String name, UUID createdBy, Integer maxPlayers) {
        Room room = new Room();
        room.setName(name);
        room.setCreatedBy(createdBy);
        room.setMaxPlayers(maxPlayers != null ? maxPlayers : 8);
        room.setCreatedAt(Instant.now());
        room.setGameMode("CLASSIC");
        room.setIsPublic(true);
        
        return roomRepository.save(room);
    }

    // ✅ joinRoom/leaveRoom are now handled by WebSocket + SessionManager
    // Keep these as no-ops for API backward compatibility
    @Transactional
    public void joinRoom(UUID roomId, UUID playerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        // Validation only - actual join happens via WebSocket
        int currentPlayers = sessionManager.getPlayerCount(roomId);
        if (currentPlayers >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }
        
        System.out.println("✅ Player " + playerId + " validated for room " + roomId);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID playerId) {
        // Validation only - actual leave happens via WebSocket
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        System.out.println("✅ Player " + playerId + " leaving room " + roomId);
    }

    // ✅ Get players from SessionManager instead of database
    public List<PlayerDTO> getRoomPlayers(UUID roomId) {
        return sessionManager.getSession(roomId)
            .map(session -> session.getPlayers().values().stream()
                .map(player -> {
                    User user = userRepository.findById(player.getPlayerId()).orElse(null);
                    PlayerDTO dto = new PlayerDTO();
                    dto.setPlayerId(player.getPlayerId().toString());
                    dto.setUsername(user != null ? user.getUsername() : "Unknown");
                    dto.setIsHost(player.getWebSocketSessionId().equals(session.getHostSessionId()));
                    dto.setRole(player.getRole() != null ? player.getRole().toString() : null);
                    dto.setStatus(player.getStatus().toString());
                    return dto;
                })
                .collect(Collectors.toList()))
            .orElse(new ArrayList<>());
    }
}