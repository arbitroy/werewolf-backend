package com.werewolfkill.game.controller;

import com.werewolfkill.game.dto.ApiResponse;
import com.werewolfkill.game.dto.PlayerDTO;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.User;
import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.enums.RoomStatus;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.UserRepository;
import com.werewolfkill.game.repository.PlayerRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.werewolfkill.game.service.RoomService;
import com.werewolfkill.game.service.WebSocketService;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PlayerRoomRepository playerRoomRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Room>> createRoom(
            @RequestBody Map<String, Object> request) {
        try {
            Room room = new Room();
            room.setName((String) request.get("name"));
            room.setHostId(UUID.fromString((String) request.get("hostId")));
            room.setMaxPlayers((Integer) request.getOrDefault("maxPlayers", 8));
            room.setCurrentPlayers(1);
            room = roomRepository.save(room);

            // Add host as first player
            PlayerRoom hostPlayer = new PlayerRoom();
            hostPlayer.setPlayerId(room.getHostId());
            hostPlayer.setRoomId(room.getId());
            hostPlayer.setIsHost(true);
            playerRoomRepository.save(hostPlayer);

            return ResponseEntity.ok(
                    ApiResponse.success("Room created", room));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Room>>> getRooms() {
        List<Room> rooms = roomRepository.findByStatus(RoomStatus.WAITING);
        return ResponseEntity.ok(
                ApiResponse.success("Rooms retrieved", rooms));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Room>> getRoomDetails(@PathVariable UUID roomId) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));
            return ResponseEntity.ok(
                    ApiResponse.success("Room retrieved", room));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{roomId}/players")
    public ResponseEntity<ApiResponse<List<PlayerDTO>>> getRoomPlayers(@PathVariable UUID roomId) { // CHANGED:
                                                                                                    // PlayerDTO
        try {
            List<PlayerDTO> players = roomService.getRoomPlayers(roomId); // CHANGED: PlayerDTO
            return ResponseEntity.ok(
                    ApiResponse.success("Players retrieved", players));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/join")
    @Transactional
    public ResponseEntity<ApiResponse<Object>> joinRoom(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {
        try {
            System.out.println("🔵 Join room request - roomId: " + roomId + ", body: " + request);

            UUID playerId = UUID.fromString(request.get("playerId"));
            UUID roomUuid = UUID.fromString(roomId);

            // Check if player is already in room BEFORE joining
            boolean wasAlreadyInRoom = playerRoomRepository
                    .findByPlayerIdAndRoomId(playerId, roomUuid)
                    .isPresent();

            // Get username from database for WebSocket broadcast
            User user = userRepository.findById(playerId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("🔵 User found: " + user.getUsername() + ", already in room: " + wasAlreadyInRoom);

            // Add player to room via service (idempotent)
            roomService.joinRoom(roomUuid, playerId);

            System.out.println("🔵 Player added to room via service");

            // Only broadcast if this is a NEW join (not a duplicate)
            if (!wasAlreadyInRoom) {
                webSocketService.broadcastPlayerJoined(roomUuid, playerId, user.getUsername());
                System.out.println("✅ Join successful, broadcasting PLAYER_JOINED");
            } else {
                System.out.println("✅ Player was already in room, skipping broadcast");
            }

            return ResponseEntity.ok(
                    ApiResponse.success("Joined room", null));
        } catch (Exception e) {
            System.err.println("❌ Join room error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{roomId}/leave")
    @Transactional
    public ResponseEntity<ApiResponse<String>> leaveRoom(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID playerId = UUID.fromString(request.get("playerId"));
            roomService.leaveRoom(roomId, playerId);
            return ResponseEntity.ok(
                    ApiResponse.success("Left room successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}