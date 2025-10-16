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
    
    @Autowired
    private PlayerRoomRepository playerRoomRepository;

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
     * Broadcast player joined event with isHost flag
     */
    public void broadcastPlayerJoined(UUID roomId, UUID playerId, String username) {
        // Query database to get isHost status
        PlayerRoom playerRoom = playerRoomRepository
                .findByPlayerIdAndRoomId(playerId, roomId)
                .orElse(null);
        
        boolean isHost = playerRoom != null && playerRoom.getIsHost();
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_JOINED");
        message.put("playerId", playerId.toString());
        message.put("username", username);
        message.put("isHost", isHost);
        message.put("timestamp", System.currentTimeMillis());
        
        System.out.println("🔍 Broadcasting PLAYER_JOINED: " + message);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * Broadcast player left event
     */
    public void broadcastPlayerLeft(UUID roomId, UUID playerId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_LEFT");
        message.put("playerId", playerId.toString());
        message.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * Broadcast game started event
     */
    public void broadcastGameStarted(UUID roomId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STARTED");
        message.put("roomId", roomId.toString());
        message.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}