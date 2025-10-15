package com.werewolfkill.game.service;

import com.werewolfkill.game.websocket.dto.GameUpdateMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_JOINED");
        message.put("playerId", playerId.toString());
        message.put("username", username);
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Object) message);
    }

    /**
     * Broadcast player left event
     */
    public void broadcastPlayerLeft(UUID roomId, UUID playerId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_LEFT");
        message.put("playerId", playerId.toString());
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Object) message);
    }

    /**
     * Broadcast game started event
     */
    public void broadcastGameStarted(UUID roomId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STARTED");
        message.put("roomId", roomId.toString());
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Object) message);
    }
}