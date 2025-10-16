package com.werewolfkill.game.service;

import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.enums.RoomStatus;
import com.werewolfkill.game.repository.PlayerRoomRepository;
import com.werewolfkill.game.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PlayerRoomRepository playerRoomRepository;

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

    // Check if player already in room - if so, just return success (idempotent)
    Optional<PlayerRoom> existingPlayerRoom = playerRoomRepository.findByPlayerIdAndRoomId(playerId, roomId);
    if (existingPlayerRoom.isPresent()) {
        System.out.println("⚠️ Player " + playerId + " already in room " + roomId + " - skipping duplicate join");
        return; // Player already in room, this is OK (idempotent operation)
    }

    // Add new player to room
    PlayerRoom playerRoom = new PlayerRoom();
    playerRoom.setPlayerId(playerId);
    playerRoom.setRoomId(roomId);
    playerRoom.setIsHost(false);
    playerRoomRepository.save(playerRoom);

    room.setCurrentPlayers(room.getCurrentPlayers() + 1);
    roomRepository.save(room);
    
    System.out.println("✅ Player " + playerId + " successfully joined room " + roomId);
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

    public List<PlayerRoom> getRoomPlayers(UUID roomId) {
        return playerRoomRepository.findByRoomId(roomId);
    }
}