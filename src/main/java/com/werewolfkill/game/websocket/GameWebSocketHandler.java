package com.werewolfkill.game.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameWebSocketHandler {

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
        // Broadcast player joined event
        return Map.of(
                "type", "PLAYER_JOINED",
                "playerId", message.get("playerId"),
                "username", message.get("username"),
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