package com.werewolfkill.game.session;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.werewolfkill.game.model.enums.PlayerStatus;
import com.werewolfkill.game.model.enums.Role;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class SessionManager {
    
    private final Map<UUID, RoomSession> activeSessions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    
    // ✅ CRITICAL FIX: Inject SimpMessagingTemplate for broadcasting
    public SessionManager(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    // Inner classes
    public static class RoomSession {
        private UUID roomId;
        private String roomName;
        private Map<String, PlayerInfo> players;
        private String hostSessionId;
        private Instant sessionStartTime;
        private Instant lastActivity;
        private String currentPhase;
        
        public RoomSession() {
            this.players = new ConcurrentHashMap<>();
            this.currentPhase = "WAITING";
        }
        
        // Getters and setters
        public UUID getRoomId() { return roomId; }
        public String getRoomName() { return roomName; }
        public Map<String, PlayerInfo> getPlayers() { return players; }
        public String getHostSessionId() { return hostSessionId; }
        public Instant getSessionStartTime() { return sessionStartTime; }
        public Instant getLastActivity() { return lastActivity; }
        public String getCurrentPhase() { return currentPhase; }
        
        public void setRoomId(UUID roomId) { this.roomId = roomId; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public void setPlayers(Map<String, PlayerInfo> players) { this.players = players; }
        public void setHostSessionId(String hostSessionId) { this.hostSessionId = hostSessionId; }
        public void setSessionStartTime(Instant sessionStartTime) { this.sessionStartTime = sessionStartTime; }
        public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
    }
    
    public static class PlayerInfo {
        private String webSocketSessionId;
        private UUID playerId;
        private String username;
        private Role role;
        private PlayerStatus status;
        private Instant joinedAt;
        private Instant lastHeartbeat;
        
        public PlayerInfo() {}
        
        public PlayerInfo(String webSocketSessionId, UUID playerId, String username, 
                         Role role, PlayerStatus status, Instant joinedAt, Instant lastHeartbeat) {
            this.webSocketSessionId = webSocketSessionId;
            this.playerId = playerId;
            this.username = username;
            this.role = role;
            this.status = status;
            this.joinedAt = joinedAt;
            this.lastHeartbeat = lastHeartbeat;
        }
        
        // Getters
        public String getWebSocketSessionId() { return webSocketSessionId; }
        public UUID getPlayerId() { return playerId; }
        public String getUsername() { return username; }
        public Role getRole() { return role; }
        public PlayerStatus getStatus() { return status; }
        public Instant getJoinedAt() { return joinedAt; }
        public Instant getLastHeartbeat() { return lastHeartbeat; }
        
        // Setters
        public void setWebSocketSessionId(String webSocketSessionId) { this.webSocketSessionId = webSocketSessionId; }
        public void setPlayerId(UUID playerId) { this.playerId = playerId; }
        public void setUsername(String username) { this.username = username; }
        public void setRole(Role role) { this.role = role; }
        public void setStatus(PlayerStatus status) { this.status = status; }
        public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
        public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
    
    public RoomSession getOrCreateSession(UUID roomId, String roomName) {
        return activeSessions.computeIfAbsent(roomId, id -> {
            RoomSession session = new RoomSession();
            session.setRoomId(roomId);
            session.setRoomName(roomName);
            session.setSessionStartTime(Instant.now());
            session.setLastActivity(Instant.now());
            session.setPlayers(new ConcurrentHashMap<>());
            System.out.println("🆕 Created new session for room: " + roomName);
            return session;
        });
    }
    
    public Optional<RoomSession> getSession(UUID roomId) {
        return Optional.ofNullable(activeSessions.get(roomId));
    }
    
    public PlayerInfo addPlayer(UUID roomId, String webSocketSessionId, UUID playerId, String username) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalStateException("Session does not exist for room: " + roomId);
        }
        
        // Check if player already exists (reconnection)
        PlayerInfo existing = session.getPlayers().get(webSocketSessionId);
        if (existing != null) {
            existing.setLastHeartbeat(Instant.now());
            System.out.println("🔄 Player reconnected: " + username);
            return existing;
        }
        
        // Create new player
        PlayerInfo player = new PlayerInfo(
            webSocketSessionId,
            playerId,
            username,
            null,
            PlayerStatus.ALIVE,
            Instant.now(),
            Instant.now()
        );
        
        session.getPlayers().put(webSocketSessionId, player);
        session.setLastActivity(Instant.now());
        
        // ✅ CRITICAL: Assign host if this is the first player
        if (session.getHostSessionId() == null) {
            session.setHostSessionId(webSocketSessionId);
            System.out.println("👑 First player " + username + " assigned as host");
        }
        
        System.out.println("✅ Player " + username + " added to room " + roomId + 
                         " (Total: " + session.getPlayers().size() + ")");
        
        return player;
    }
    
    public void removePlayer(UUID roomId, String webSocketSessionId) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) return;
        
        PlayerInfo removed = session.getPlayers().remove(webSocketSessionId);
        if (removed == null) return;
        
        System.out.println("👋 Player " + removed.getUsername() + " removed from room " + roomId);
        
        // ✅ CRITICAL FIX: Check if removed player was host and reassign
        if (webSocketSessionId.equals(session.getHostSessionId())) {
            System.out.println("🔄 Host disconnected, reassigning...");
            reassignHost(session, roomId);
        }
        
        // Clean up empty sessions
        if (session.getPlayers().isEmpty()) {
            activeSessions.remove(roomId);
            System.out.println("🧹 Room " + roomId + " session destroyed (empty)");
        }
    }
    
    // ✅ CRITICAL FIX: Broadcast HOST_CHANGED event
    private void reassignHost(RoomSession session, UUID roomId) {
        if (session.getPlayers().isEmpty()) {
            session.setHostSessionId(null);
            System.out.println("⚠️ No players left to assign as host");
            return;
        }
        
        // Find the player who joined earliest (first to join becomes host)
        PlayerInfo newHost = session.getPlayers().values().stream()
            .min(Comparator.comparing(PlayerInfo::getJoinedAt))
            .orElse(null);
        
        if (newHost != null) {
            String oldHostId = session.getHostSessionId();
            session.setHostSessionId(newHost.getWebSocketSessionId());
            
            System.out.println("👑 New host assigned: " + newHost.getUsername() + 
                             " (joined at: " + newHost.getJoinedAt() + ")");
            
            // ✅ BROADCAST HOST_CHANGED event to all clients
            broadcastHostChanged(roomId, newHost);
        }
    }
    
    // ✅ NEW METHOD: Broadcast host change to all room participants
    private void broadcastHostChanged(UUID roomId, PlayerInfo newHost) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "HOST_CHANGED");
        message.put("roomId", roomId.toString());
        message.put("newHostId", newHost.getPlayerId().toString());
        message.put("newHostUsername", newHost.getUsername());
        message.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/room/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, message);
        
        System.out.println("📢 Broadcasted HOST_CHANGED: " + newHost.getUsername() + " is now host");
    }
    
    public void updateHeartbeat(UUID roomId, String webSocketSessionId) {
        RoomSession session = activeSessions.get(roomId);
        if (session != null) {
            PlayerInfo player = session.getPlayers().get(webSocketSessionId);
            if (player != null) {
                player.setLastHeartbeat(Instant.now());
                session.setLastActivity(Instant.now());
            }
        }
    }
    
    public int getPlayerCount(UUID roomId) {
        return getSession(roomId)
            .map(session -> session.getPlayers().size())
            .orElse(0);
    }
    
    public Map<UUID, RoomSession> getAllSessions() {
        return new HashMap<>(activeSessions);
    }
    
    public void destroySession(UUID roomId) {
        activeSessions.remove(roomId);
        System.out.println("🧹 Room " + roomId + " session destroyed");
    }
}