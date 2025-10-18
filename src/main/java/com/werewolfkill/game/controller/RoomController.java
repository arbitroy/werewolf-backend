package com.werewolfkill.game.controller;

import com.werewolfkill.game.dto.ApiResponse;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.service.RoomService;
import com.werewolfkill.game.session.SessionManager;
import com.werewolfkill.game.session.SessionManager.PlayerInfo;
import com.werewolfkill.game.session.SessionManager.RoomSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private SessionManager sessionManager;

    /**
     * Get all rooms with live session info
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRooms() {
        List<Room> rooms = roomService.getAllRooms();

        // Augment with live session data
        List<Map<String, Object>> roomsWithSessionInfo = rooms.stream()
            .map(room -> {
                Map<String, Object> roomData = new HashMap<>();
                roomData.put("id", room.getId().toString());
                roomData.put("name", room.getName());
                roomData.put("maxPlayers", room.getMaxPlayers());
                roomData.put("gameMode", room.getGameMode());
                roomData.put("isPublic", room.getIsPublic());
                roomData.put("createdAt", room.getCreatedAt().toString());

                // Add live session data
                int currentPlayers = sessionManager.getPlayerCount(room.getId());
                roomData.put("currentPlayers", currentPlayers);
                roomData.put("isActive", currentPlayers > 0);

                return roomData;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Rooms retrieved", roomsWithSessionInfo));
    }

    /**
     * Get room details with live players
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoomDetails(@PathVariable UUID roomId) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Room not found"));
        }

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("id", room.getId().toString());
        roomData.put("name", room.getName());
        roomData.put("maxPlayers", room.getMaxPlayers());
        roomData.put("gameMode", room.getGameMode());

        // Add live session data
        Optional<RoomSession> session = sessionManager.getSession(roomId);
        if (session.isPresent()) {
            RoomSession s = session.get();
            roomData.put("currentPlayers", s.getPlayers().size());
            PlayerInfo host = s.getPlayers().get(s.getHostSessionId());
            roomData.put("hostUsername", host != null ? host.getUsername() : null);
            roomData.put("currentPhase", s.getCurrentPhase());
        } else {
            roomData.put("currentPlayers", 0);
            roomData.put("hostUsername", null);
            roomData.put("currentPhase", "WAITING");
        }

        return ResponseEntity.ok(ApiResponse.success("Room retrieved", roomData));
    }

    /**
     * Create a new room
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Room>> createRoom(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            UUID createdBy = UUID.fromString((String) request.get("hostId"));
            Integer maxPlayers = (Integer) request.getOrDefault("maxPlayers", 8);

            Room room = roomService.createRoom(name, createdBy, maxPlayers);

            return ResponseEntity.ok(ApiResponse.success("Room created", room));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a room (and its session)
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<String>> deleteRoom(@PathVariable UUID roomId) {
        try {
            // Destroy session first
            sessionManager.destroySession(roomId);

            // Then delete from database
            roomService.deleteRoom(roomId);

            return ResponseEntity.ok(ApiResponse.success("Room deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }


}