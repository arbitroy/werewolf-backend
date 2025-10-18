package com.werewolfkill.game.session;  // ✅ FIXED: Removed "main.java."

import com.werewolfkill.game.model.enums.Role;
import com.werewolfkill.game.model.enums.PlayerStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    
    private final ConcurrentHashMap<UUID, RoomSession> activeSessions = new ConcurrentHashMap<>();
    
    // ✅ Inner class with MANUAL getters/setters
    public static class RoomSession {
        private UUID roomId;
        private String roomName;
        private ConcurrentHashMap<String, PlayerInfo> players;
        private String hostSessionId;
        private Instant sessionStartTime;
        private Instant lastActivity;
        private String currentPhase;
        
        public RoomSession() {
            this.players = new ConcurrentHashMap<>();
            this.currentPhase = "WAITING";
        }
        
        // Getters
        public UUID getRoomId() { return roomId; }
        public String getRoomName() { return roomName; }
        public ConcurrentHashMap<String, PlayerInfo> getPlayers() { return players; }
        public String getHostSessionId() { return hostSessionId; }
        public Instant getSessionStartTime() { return sessionStartTime; }
        public Instant getLastActivity() { return lastActivity; }
        public String getCurrentPhase() { return currentPhase; }
        
        // Setters
        public void setRoomId(UUID roomId) { this.roomId = roomId; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public void setPlayers(ConcurrentHashMap<String, PlayerInfo> players) { this.players = players; }
        public void setHostSessionId(String hostSessionId) { this.hostSessionId = hostSessionId; }
        public void setSessionStartTime(Instant sessionStartTime) { this.sessionStartTime = sessionStartTime; }
        public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
    }
    
    // ✅ Inner class with MANUAL getters/setters
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
        
        PlayerInfo existing = session.getPlayers().get(webSocketSessionId);
        if (existing != null) {
            existing.setLastHeartbeat(Instant.now());
            return existing;
        }
        
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
        
        if (session.getHostSessionId() == null) {
            session.setHostSessionId(webSocketSessionId);
            System.out.println("👑 " + username + " is now the host");
        }
        
        System.out.println("✅ Added player: " + username + " to room " + roomId);
        return player;
    }
    
    public void removePlayer(UUID roomId, String webSocketSessionId) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) return;
        
        PlayerInfo removed = session.getPlayers().remove(webSocketSessionId);
        if (removed == null) return;
        
        System.out.println("❌ Removed player: " + removed.getUsername());
        
        // Transfer host if needed
        if (webSocketSessionId.equals(session.getHostSessionId()) && !session.getPlayers().isEmpty()) {
            String newHost = session.getPlayers().keys().nextElement();
            session.setHostSessionId(newHost);
            PlayerInfo newHostPlayer = session.getPlayers().get(newHost);
            System.out.println("👑 New host: " + newHostPlayer.getUsername());
        }
        
        // Clean up empty sessions
        if (session.getPlayers().isEmpty()) {
            activeSessions.remove(roomId);
            System.out.println("🧹 Cleaned up empty session for room: " + roomId);
        }
    }
    
    public void updateHeartbeat(UUID roomId, String webSocketSessionId) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) return;
        
        PlayerInfo player = session.getPlayers().get(webSocketSessionId);
        if (player != null) {
            player.setLastHeartbeat(Instant.now());
            session.setLastActivity(Instant.now());
        }
    }
    
    public int getPlayerCount(UUID roomId) {
        return getSession(roomId)
            .map(session -> session.getPlayers().size())
            .orElse(0);
    }
    
    public List<PlayerInfo> getAlivePlayers(UUID roomId) {
        return getSession(roomId)
            .map(session -> session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .toList())
            .orElse(Collections.emptyList());
    }
    
    public Map<UUID, RoomSession> getAllSessions() {
        return new HashMap<>(activeSessions);
    }
}