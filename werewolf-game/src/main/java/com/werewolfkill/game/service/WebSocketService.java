package com.werewolfkill.game.service;

import com.werewolfkill.game.websocket.dto.GameUpdateMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class WebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Send game update to all players in a room
     */
    public void sendGameUpdate(UUID roomId, GameUpdateMessage message) {
        messagingTemplate.convertAndSend("/topic/game/" + roomId, message);
    }

    /**
     * Send private message to specific user
     */
    public void sendPrivateMessage(String userId, String destination, Object message) {
        messagingTemplate.convertAndSendToUser(userId, destination, message);
    }

    /**
     * Broadcast player joined event
     */
    public void broadcastPlayerJoined(UUID roomId, UUID playerId, String username) {
        Map<String, Object> message = Map.of(
                "type", "PLAYER_JOINED",
                "playerId", playerId.toString(),
                "username", username
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Map<String, ?>) message);
    }

    /**
     * Broadcast player left event
     */
    public void broadcastPlayerLeft(UUID roomId, UUID playerId) {
        Map<String, Object> message = Map.of(
                "type", "PLAYER_LEFT",
                "playerId", playerId.toString()
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Map<String, ?>) message);
    }

    /**
     * Broadcast game started event
     */
    public void broadcastGameStarted(UUID roomId) {
        Map<String, Object> message = Map.of(
                "type", "GAME_STARTED",
                "roomId", roomId.toString()
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}