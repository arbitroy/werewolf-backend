package com.werewolfkill.game.controller;

import com.werewolfkill.game.dto.ApiResponse;
import com.werewolfkill.game.dto.PlayerDTO;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.service.RoomService;
import com.werewolfkill.game.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RoomController - Uses NEW schema (created_by, no transient data)
 * Returns only persistent room metadata from DB
 * Session data (players, status) should be fetched via WebSocket or separate endpoints
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;
    
    @Autowired
    private SessionManager sessionManager;

    /**
     * GET /api/rooms - Get all rooms (persistent data only)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllRooms() {
        try {
            List<Room> rooms = roomService.getAvailableRooms();
            
            List<Map<String, Object>> roomData = rooms.stream().map(room -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", room.getId().toString());
                data.put("name", room.getName());
                data.put("createdBy", room.getCreatedBy().toString());
                data.put("maxPlayers", room.getMaxPlayers());
                data.put("createdAt", room.getCreatedAt().toString());
                data.put("gameMode", room.getGameMode());
                data.put("isPublic", room.getIsPublic());
                
                // Optional: Add session data if available
                sessionManager.getSession(room.getId()).ifPresent(session -> {
                    data.put("currentPlayers", session.getPlayers().size());
                    data.put("status", session.getCurrentPhase());
                });
                
                return data;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success("Rooms retrieved", roomData));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to retrieve rooms: " + e.getMessage()));
        }
    }

    /**
     * POST /api/rooms - Create new room
     * Request body: { "name": "...", "createdBy": "uuid", "maxPlayers": 8 }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoom(
            @RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String createdByStr = (String) request.get("createdBy");
            Integer maxPlayers = request.get("maxPlayers") != null 
                ? ((Number) request.get("maxPlayers")).intValue() 
                : 8;

            if (name == null || createdByStr == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("name and createdBy are required"));
            }

            UUID createdBy = UUID.fromString(createdByStr);
            Room room = roomService.createRoom(name, createdBy, maxPlayers);

            Map<String, Object> data = new HashMap<>();
            data.put("id", room.getId().toString());
            data.put("name", room.getName());
            data.put("createdBy", room.getCreatedBy().toString());
            data.put("maxPlayers", room.getMaxPlayers());
            data.put("createdAt", room.getCreatedAt().toString());
            data.put("gameMode", room.getGameMode());
            data.put("isPublic", room.getIsPublic());

            return ResponseEntity.ok(ApiResponse.success("Room created", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid UUID format"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to create room: " + e.getMessage()));
        }
    }

    /**
     * GET /api/rooms/{roomId} - Get room details
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoomDetails(
            @PathVariable String roomId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            Room room = roomService.getAvailableRooms().stream()
                .filter(r -> r.getId().equals(roomUuid))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Room not found"));

            Map<String, Object> data = new HashMap<>();
            data.put("id", room.getId().toString());
            data.put("name", room.getName());
            data.put("createdBy", room.getCreatedBy().toString());
            data.put("maxPlayers", room.getMaxPlayers());
            data.put("createdAt", room.getCreatedAt().toString());
            data.put("gameMode", room.getGameMode());
            data.put("isPublic", room.getIsPublic());

            // Add session data if available
            sessionManager.getSession(roomUuid).ifPresent(session -> {
                data.put("currentPlayers", session.getPlayers().size());
                data.put("status", session.getCurrentPhase());
            });

            return ResponseEntity.ok(ApiResponse.success("Room retrieved", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid room ID format"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to get room: " + e.getMessage()));
        }
    }

    /**
     * GET /api/rooms/{roomId}/players - Get players in room
     */
    @GetMapping("/{roomId}/players")
    public ResponseEntity<ApiResponse<List<PlayerDTO>>> getRoomPlayers(
            @PathVariable String roomId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            List<PlayerDTO> players = roomService.getRoomPlayers(roomUuid);
            
            return ResponseEntity.ok(ApiResponse.success("Players retrieved", players));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid room ID format"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to get players: " + e.getMessage()));
        }
    }

    /**
     * POST /api/rooms/{roomId}/join - Validate join (actual join via WebSocket)
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<String>> joinRoom(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            UUID playerId = UUID.fromString(request.get("playerId"));
            
            roomService.joinRoom(roomUuid, playerId);
            
            return ResponseEntity.ok(ApiResponse.success("Join validated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/rooms/{roomId}/leave - Validate leave (actual leave via WebSocket)
     */
    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<String>> leaveRoom(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            UUID playerId = UUID.fromString(request.get("playerId"));
            
            roomService.leaveRoom(roomUuid, playerId);
            
            return ResponseEntity.ok(ApiResponse.success("Leave validated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
}