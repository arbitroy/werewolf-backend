package com.werewolfkill.game.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.repository.PlayerRoomRepository;

import java.util.Map;
import java.util.UUID;

@Controller
public class GameWebSocketHandler {

    @Autowired
    private PlayerRoomRepository playerRoomRepository;

    @MessageMapping("/game.join")
    @SendTo("/topic/game")
    public Map<String, Object> joinGame(Map<String, String> message) {
        return Map.of(
                "type", "PLAYER_JOINED",
                "playerId", message.get("playerId"),
                "roomId", message.get("roomId"));
    }

    @MessageMapping("/room/{roomId}/join")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> handleJoinRoom(@DestinationVariable String roomId,
            Map<String, String> message) {

        // Get player info from database to check if they're the host
        UUID playerId = UUID.fromString(message.get("playerId"));
        UUID roomUuid = UUID.fromString(roomId);

        // Query player_rooms table to get isHost status
        PlayerRoom playerRoom = playerRoomRepository
                .findByPlayerIdAndRoomId(playerId, roomUuid)
                .orElse(null);

        boolean isHost = playerRoom != null && playerRoom.getIsHost();

        // Broadcast player joined event
        return Map.of(
                "type", "PLAYER_JOINED",
                "playerId", message.get("playerId"),
                "username", message.get("username"),
                "isHost", isHost,
                "timestamp", System.currentTimeMillis());
    }

    @MessageMapping("/room/{roomId}/leave")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> handleLeaveRoom(@DestinationVariable String roomId,
            Map<String, String> message) {
        // Broadcast player left event
        return Map.of(
                "type", "PLAYER_LEFT",
                "playerId", message.get("playerId"),
                "timestamp", System.currentTimeMillis());
    }
}