package com.werewolfkill.game.service;

import com.werewolfkill.game.websocket.dto.GameUpdateMessage;
import com.werewolfkill.game.session.SessionManager;
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
    private SessionManager sessionManager;

    public void sendGameUpdate(UUID roomId, GameUpdateMessage message) {
        String destination = "/topic/game/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, (Object) message);  // ✅ FIXED: Cast to Object
    }

    public void sendGameUpdate(UUID roomId, Object message) {
        String destination = "/topic/game/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, (Object) message);  // ✅ FIXED: Cast to Object
    }

    public void sendPrivateMessage(String userId, String destination, Object message) {
        messagingTemplate.convertAndSendToUser(userId, destination, (Object) message);  // ✅ FIXED: Cast to Object
    }

    // ✅ Use SessionManager instead of querying database
    public void broadcastPlayerJoined(UUID roomId, UUID playerId, String username) {
        SessionManager.RoomSession session = sessionManager.getSession(roomId).orElse(null);
        if (session == null) return;
        
        SessionManager.PlayerInfo player = session.getPlayers().values().stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .orElse(null);
        
        boolean isHost = player != null && 
            player.getWebSocketSessionId().equals(session.getHostSessionId());
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_JOINED");
        message.put("playerId", playerId.toString());
        message.put("username", username);
        message.put("isHost", isHost);
        message.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/room/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, (Object) message);  // ✅ FIXED: Cast to Object
    }

    public void broadcastPlayerLeft(UUID roomId, UUID playerId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_LEFT");
        message.put("playerId", playerId.toString());
        
        String destination = "/topic/room/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, (Object) message);  // ✅ FIXED: Cast to Object
    }
}