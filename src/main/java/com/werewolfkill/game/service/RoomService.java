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
import java.util.Optional;
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

        Room savedRoom = roomRepository.save(room);

        // ✅ FIX: Pre-create session when room is created
        sessionManager.getOrCreateSession(savedRoom.getId(), savedRoom.getName());
        System.out.println("✅ Pre-created session for room: " + savedRoom.getId());

        return savedRoom;
    }

    // ✅ joinRoom/leaveRoom are now handled by WebSocket + SessionManager
    // Keep these as no-ops for API backward compatibility
    @Transactional
    public void joinRoom(UUID roomId, UUID playerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // ✅ Check if game is already in progress
        Optional<SessionManager.RoomSession> sessionOpt = sessionManager.getSession(roomId);
        if (!sessionManager.canAcceptPlayers(roomId, room.getMaxPlayers())) {
            throw new RuntimeException("Cannot join this room");
        }
        if (sessionOpt.isPresent()) {
            SessionManager.RoomSession session = sessionOpt.get();

            // ✅ CRITICAL: Block joins if game has started
            String currentPhase = session.getCurrentPhase();
            if (currentPhase != null && !currentPhase.equals("WAITING")) {
                throw new RuntimeException("Cannot join - game is already in progress");
            }

            // Check max players
            if (session.getPlayers().size() >= room.getMaxPlayers()) {
                throw new RuntimeException("Room is full");
            }
        }

        System.out.println("✅ Player " + playerId + " validated for room " + roomId);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID playerId) {
        // Verify room exists
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // ✅ Optional: Clean up any database records if needed
        // For now, this is just validation since WebSocket handles actual leaving
        System.out.println("📝 Leave room REST API called for player " + playerId);

        // Note: Actual player removal happens via WebSocket in GameWebSocketHandler
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