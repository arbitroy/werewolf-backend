package com.werewolfkill.game.service;

import com.werewolfkill.game.dto.PlayerDTO;  // ADDED
import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.User;  // ADDED
import com.werewolfkill.game.model.enums.RoomStatus;
import com.werewolfkill.game.repository.PlayerRoomRepository;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.UserRepository;  // ADDED
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;  // ADDED
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PlayerRoomRepository playerRoomRepository;

    @Autowired  // ADDED
    private UserRepository userRepository;

    public List<Room> getAvailableRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING);
    }

    @Transactional
    public Room createRoom(String name, UUID hostId, Integer maxPlayers) {
        Room room = new Room();
        room.setName(name);
        room.setHostId(hostId);
        room.setMaxPlayers(maxPlayers != null ? maxPlayers : 8);
        room.setCurrentPlayers(1);
        room.setStatus(RoomStatus.WAITING);
        room = roomRepository.save(room);

        // Add host as first player
        PlayerRoom hostPlayer = new PlayerRoom();
        hostPlayer.setPlayerId(hostId);
        hostPlayer.setRoomId(room.getId());
        hostPlayer.setIsHost(true);
        playerRoomRepository.save(hostPlayer);

        return room;
    }

    @Transactional
    public void joinRoom(UUID roomId, UUID playerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RuntimeException("Game already started");
        }

        // Check if player already in room
        if (playerRoomRepository.findByPlayerIdAndRoomId(playerId, roomId).isPresent()) {
            throw new RuntimeException("Already in room");
        }

        PlayerRoom playerRoom = new PlayerRoom();
        playerRoom.setPlayerId(playerId);
        playerRoom.setRoomId(roomId);
        playerRoom.setIsHost(false);
        playerRoomRepository.save(playerRoom);

        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        roomRepository.save(room);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID playerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        PlayerRoom playerRoom = playerRoomRepository.findByPlayerIdAndRoomId(playerId, roomId)
                .orElseThrow(() -> new RuntimeException("Not in room"));

        playerRoomRepository.delete(playerRoom);

        room.setCurrentPlayers(room.getCurrentPlayers() - 1);

        // If host left and room is not empty, assign new host
        if (playerRoom.getIsHost() && room.getCurrentPlayers() > 0) {
            List<PlayerRoom> remainingPlayers = playerRoomRepository.findByRoomId(roomId);
            if (!remainingPlayers.isEmpty()) {
                PlayerRoom newHost = remainingPlayers.get(0);
                newHost.setIsHost(true);
                playerRoomRepository.save(newHost);
                room.setHostId(newHost.getPlayerId());
            }
        }

        // Delete room if empty
        if (room.getCurrentPlayers() == 0) {
            roomRepository.delete(room);
        } else {
            roomRepository.save(room);
        }
    }

    // CHANGED: Return PlayerDTO list with usernames
    public List<PlayerDTO> getRoomPlayers(UUID roomId) {
        List<PlayerRoom> playerRooms = playerRoomRepository.findByRoomId(roomId);
        List<PlayerDTO> players = new ArrayList<>();
        
        for (PlayerRoom pr : playerRooms) {
            User user = userRepository.findById(pr.getPlayerId()).orElse(null);
            PlayerDTO dto = new PlayerDTO();
            dto.setPlayerId(pr.getPlayerId().toString());
            dto.setUsername(user != null ? user.getUsername() : "Unknown");
            dto.setIsHost(pr.getIsHost());
            dto.setRole(pr.getRole() != null ? pr.getRole().toString() : null);
            dto.setStatus(pr.getStatus() != null ? pr.getStatus().toString() : "ALIVE");
            players.add(dto);
        }
        
        return players;
    }
}