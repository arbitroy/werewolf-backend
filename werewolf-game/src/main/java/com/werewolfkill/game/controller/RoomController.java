package com.werewolfkill.game.controller;

import com.werewolfkill.game.dto.ApiResponse;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.enums.RoomStatus;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.PlayerRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                ApiResponse.success("Room created", room)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Room>>> getRooms() {
        List<Room> rooms = roomRepository.findByStatus(RoomStatus.WAITING);
        return ResponseEntity.ok(
            ApiResponse.success("Rooms retrieved", rooms)
        );
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<String>> joinRoom(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> request) {
        try {
            Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
            
            if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
                throw new RuntimeException("Room is full");
            }
            
            UUID playerId = UUID.fromString(request.get("playerId"));
            
            PlayerRoom playerRoom = new PlayerRoom();
            playerRoom.setPlayerId(playerId);
            playerRoom.setRoomId(roomId);
            playerRoomRepository.save(playerRoom);
            
            room.setCurrentPlayers(room.getCurrentPlayers() + 1);
            roomRepository.save(room);
            
            return ResponseEntity.ok(
                ApiResponse.success("Joined room", null)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
}