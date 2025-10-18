package main.java.com.werewolfkill.game.session;

import com.werewolfkill.game.model.enums.Role;
import com.werewolfkill.game.model.enums.PlayerStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SessionManager {
    
    private final ConcurrentHashMap<UUID, RoomSession> activeSessions = new ConcurrentHashMap<>();
    
    @Data
    public static class RoomSession {
        private UUID roomId;
        private String roomName;
        private ConcurrentHashMap<String, PlayerInfo> players = new ConcurrentHashMap<>();
        private String hostSessionId;
        private Instant sessionStartTime;
        private Instant lastActivity;
        private String currentPhase = "WAITING"; // WAITING, NIGHT, DAY, VOTING
    }
    
    @Data
    @AllArgsConstructor
    public static class PlayerInfo {
        private String webSocketSessionId;
        private UUID playerId;
        private String username;
        private Role role;
        private PlayerStatus status;
        private Instant joinedAt;
        private Instant lastHeartbeat;
    }
    
    /**
     * Get or create a session for a room
     */
    public RoomSession getOrCreateSession(UUID roomId, String roomName) {
        return activeSessions.computeIfAbsent(roomId, id -> {
            RoomSession session = new RoomSession();
            session.setRoomId(roomId);
            session.setRoomName(roomName);
            session.setSessionStartTime(Instant.now());
            session.setLastActivity(Instant.now());
            System.out.println("🆕 Created new session for room: " + roomName);
            return session;
        });
    }
    
    /**
     * Get existing session (if any)
     */
    public Optional<RoomSession> getSession(UUID roomId) {
        return Optional.ofNullable(activeSessions.get(roomId));
    }
    
    /**
     * Add player to session (idempotent)
     */
    public PlayerInfo addPlayer(UUID roomId, String webSocketSessionId, UUID playerId, String username) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalStateException("Session does not exist for room: " + roomId);
        }
        
        // Check if player already in session
        PlayerInfo existing = session.getPlayers().get(webSocketSessionId);
        if (existing != null) {
            System.out.println("⚠️ Player already in session: " + username);
            existing.setLastHeartbeat(Instant.now());
            return existing;
        }
        
        // Create new player info
        PlayerInfo player = new PlayerInfo(
            webSocketSessionId,
            playerId,
            username,
            null, // Role assigned during game start
            PlayerStatus.ALIVE,
            Instant.now(),
            Instant.now()
        );
        
        session.getPlayers().put(webSocketSessionId, player);
        session.setLastActivity(Instant.now());
        
        // Assign host if first player
        if (session.getHostSessionId() == null) {
            session.setHostSessionId(webSocketSessionId);
            System.out.println("👑 " + username + " is now host (first to join)");
        }
        
        System.out.println("✅ Added player to session: " + username + " (total: " + session.getPlayers().size() + ")");
        return player;
    }
    
    /**
     * Remove player from session
     */
    public void removePlayer(UUID roomId, String webSocketSessionId) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) {
            return;
        }
        
        PlayerInfo removed = session.getPlayers().remove(webSocketSessionId);
        if (removed == null) {
            return;
        }
        
        System.out.println("🚪 Player left: " + removed.getUsername());
        
        // Handle host departure
        if (webSocketSessionId.equals(session.getHostSessionId())) {
            reassignHost(session);
        }
        
        // Clean up empty sessions
        if (session.getPlayers().isEmpty()) {
            activeSessions.remove(roomId);
            System.out.println("🗑️ Destroyed empty session for room: " + session.getRoomName());
        }
    }
    
    /**
     * Reassign host to oldest remaining player
     */
    private void reassignHost(RoomSession session) {
        if (session.getPlayers().isEmpty()) {
            session.setHostSessionId(null);
            System.out.println("❌ No players left, no host");
            return;
        }
        
        // Find player who joined earliest
        PlayerInfo newHost = session.getPlayers().values().stream()
            .min(Comparator.comparing(PlayerInfo::getJoinedAt))
            .orElse(null);
        
        if (newHost != null) {
            session.setHostSessionId(newHost.getWebSocketSessionId());
            System.out.println("👑 NEW HOST: " + newHost.getUsername());
        }
    }
    
    /**
     * Update player heartbeat
     */
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
    
    /**
     * Get player info by session ID
     */
    public Optional<PlayerInfo> getPlayer(UUID roomId, String webSocketSessionId) {
        return getSession(roomId)
            .map(session -> session.getPlayers().get(webSocketSessionId));
    }
    
    /**
     * Check if player is host
     */
    public boolean isHost(UUID roomId, String webSocketSessionId) {
        return getSession(roomId)
            .map(session -> webSocketSessionId.equals(session.getHostSessionId()))
            .orElse(false);
    }
    
    /**
     * Get all players as list
     */
    public List<PlayerInfo> getPlayers(UUID roomId) {
        return getSession(roomId)
            .map(session -> new ArrayList<>(session.getPlayers().values()))
            .orElse(Collections.emptyList());
    }
    
    /**
     * Get player count
     */
    public int getPlayerCount(UUID roomId) {
        return getSession(roomId)
            .map(session -> session.getPlayers().size())
            .orElse(0);
    }
    
    /**
     * Assign roles to all players (for game start)
     */
    public void assignRoles(UUID roomId, Map<UUID, Role> roleAssignments) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalStateException("Session not found");
        }
        
        session.getPlayers().values().forEach(player -> {
            Role role = roleAssignments.get(player.getPlayerId());
            if (role != null) {
                player.setRole(role);
                System.out.println("🎭 Assigned role " + role + " to " + player.getUsername());
            }
        });
    }
    
    /**
     * Mark player as dead
     */
    public void killPlayer(UUID roomId, UUID playerId) {
        RoomSession session = activeSessions.get(roomId);
        if (session == null) return;
        
        session.getPlayers().values().stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .ifPresent(player -> {
                player.setStatus(PlayerStatus.DEAD);
                System.out.println("☠️ " + player.getUsername() + " was killed");
            });
    }
    
    /**
     * Get all active sessions (for monitoring)
     */
    public Map<UUID, RoomSession> getAllSessions() {
        return new HashMap<>(activeSessions);
    }
    
    /**
     * Destroy session manually
     */
    public void destroySession(UUID roomId) {
        RoomSession removed = activeSessions.remove(roomId);
        if (removed != null) {
            System.out.println("🗑️ Manually destroyed session: " + removed.getRoomName());
        }
    }
}