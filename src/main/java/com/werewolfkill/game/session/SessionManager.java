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
        private int dayNumber = 0;
        private Map<UUID, UUID> nightActions = new ConcurrentHashMap<>(); // actorId -> targetId
        private Map<UUID, UUID> dayVotes = new ConcurrentHashMap<>(); // voterId -> targetId
        private UUID doctorProtectionTarget = null;
        private UUID werewolfKillTarget = null;
        private UUID seerCheckTarget = null;
        private Set<UUID> playersWhoActedTonight = ConcurrentHashMap.newKeySet();
        private boolean allNightActionsComplete = false;
        private boolean allVotesComplete = false;
        private UUID hunterRevengeHunterId = null;
        private Long hunterRevengeDeadline = null; // Timestamp when hunter decision expires
        private boolean hunterRevengeComplete = false;

        public RoomSession() {
            this.players = new ConcurrentHashMap<>();
            this.currentPhase = "WAITING";
        }

        // Getters and setters
        public UUID getRoomId() {
            return roomId;
        }

        public String getRoomName() {
            return roomName;
        }

        public Map<String, PlayerInfo> getPlayers() {
            return players;
        }

        public String getHostSessionId() {
            return hostSessionId;
        }

        public Instant getSessionStartTime() {
            return sessionStartTime;
        }

        public Instant getLastActivity() {
            return lastActivity;
        }

        public String getCurrentPhase() {
            return currentPhase;
        }

        public void setRoomId(UUID roomId) {
            this.roomId = roomId;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public void setPlayers(Map<String, PlayerInfo> players) {
            this.players = players;
        }

        public void setHostSessionId(String hostSessionId) {
            this.hostSessionId = hostSessionId;
        }

        public void setSessionStartTime(Instant sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
        }

        public void setLastActivity(Instant lastActivity) {
            this.lastActivity = lastActivity;
        }

        public void setCurrentPhase(String currentPhase) {
            this.currentPhase = currentPhase;
        }

        public int getDayNumber() {
            return dayNumber;
        }

        public Map<UUID, UUID> getNightActions() {
            return nightActions;
        }

        public Map<UUID, UUID> getDayVotes() {
            return dayVotes;
        }

        public UUID getDoctorProtectionTarget() {
            return doctorProtectionTarget;
        }

        public UUID getWerewolfKillTarget() {
            return werewolfKillTarget;
        }

        public UUID getSeerCheckTarget() {
            return seerCheckTarget;
        }

        public Set<UUID> getPlayersWhoActedTonight() {
            return playersWhoActedTonight;
        }

        public boolean isAllNightActionsComplete() {
            return allNightActionsComplete;
        }

        public boolean isAllVotesComplete() {
            return allVotesComplete;
        }

        // Setters
        public void setDayNumber(int dayNumber) {
            this.dayNumber = dayNumber;
        }

        public void setNightActions(Map<UUID, UUID> nightActions) {
            this.nightActions = nightActions;
        }

        public void setDayVotes(Map<UUID, UUID> dayVotes) {
            this.dayVotes = dayVotes;
        }

        public void setDoctorProtectionTarget(UUID doctorProtectionTarget) {
            this.doctorProtectionTarget = doctorProtectionTarget;
        }

        public void setWerewolfKillTarget(UUID werewolfKillTarget) {
            this.werewolfKillTarget = werewolfKillTarget;
        }

        public void setSeerCheckTarget(UUID seerCheckTarget) {
            this.seerCheckTarget = seerCheckTarget;
        }

        public void setPlayersWhoActedTonight(Set<UUID> playersWhoActedTonight) {
            this.playersWhoActedTonight = playersWhoActedTonight;
        }

        public void setAllNightActionsComplete(boolean allNightActionsComplete) {
            this.allNightActionsComplete = allNightActionsComplete;
        }

        public void setAllVotesComplete(boolean allVotesComplete) {
            this.allVotesComplete = allVotesComplete;
        }

        public UUID getHunterRevengeHunterId() {
            return hunterRevengeHunterId;
        }

        public void setHunterRevengeHunterId(UUID hunterId) {
            this.hunterRevengeHunterId = hunterId;
        }

        public Long getHunterRevengeDeadline() {
            return hunterRevengeDeadline;
        }

        public void setHunterRevengeDeadline(Long deadline) {
            this.hunterRevengeDeadline = deadline;
        }

        public boolean isHunterRevengeComplete() {
            return hunterRevengeComplete;
        }

        public void setHunterRevengeComplete(boolean complete) {
            this.hunterRevengeComplete = complete;
        }

        public void clearHunterRevenge() {
            this.hunterRevengeHunterId = null;
            this.hunterRevengeDeadline = null;
            this.hunterRevengeComplete = false;
        }

        // Helper methods
        public void clearNightActions() {
            nightActions.clear();
            doctorProtectionTarget = null;
            werewolfKillTarget = null;
            seerCheckTarget = null;
            playersWhoActedTonight.clear();
            allNightActionsComplete = false;
        }

        public void clearDayVotes() {
            dayVotes.clear();
            allVotesComplete = false;
        }

        public void incrementDay() {
            this.dayNumber++;
        }
    }

    public static class PlayerInfo {
        private String webSocketSessionId;
        private UUID playerId;
        private String username;
        private Role role;
        private PlayerStatus status;
        private Instant joinedAt;
        private Instant lastHeartbeat;

        public PlayerInfo() {
        }

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
        public String getWebSocketSessionId() {
            return webSocketSessionId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getUsername() {
            return username;
        }

        public Role getRole() {
            return role;
        }

        public PlayerStatus getStatus() {
            return status;
        }

        public Instant getJoinedAt() {
            return joinedAt;
        }

        public Instant getLastHeartbeat() {
            return lastHeartbeat;
        }

        // Setters
        public void setWebSocketSessionId(String webSocketSessionId) {
            this.webSocketSessionId = webSocketSessionId;
        }

        public void setPlayerId(UUID playerId) {
            this.playerId = playerId;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public void setStatus(PlayerStatus status) {
            this.status = status;
        }

        public void setJoinedAt(Instant joinedAt) {
            this.joinedAt = joinedAt;
        }

        public void setLastHeartbeat(Instant lastHeartbeat) {
            this.lastHeartbeat = lastHeartbeat;
        }
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
                Instant.now());

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
        if (session == null)
            return;

        PlayerInfo removed = session.getPlayers().remove(webSocketSessionId);
        if (removed == null)
            return;

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

    /**
     * Check if a room's game is currently active (not in WAITING phase)
     */
    public boolean isGameActive(UUID roomId) {
        return getSession(roomId)
                .map(session -> {
                    String phase = session.getCurrentPhase();
                    return phase != null && !phase.equals("WAITING");
                })
                .orElse(false);
    }

    /**
     * Check if a room can accept new players
     */
    public boolean canAcceptPlayers(UUID roomId, int maxPlayers) {
        return getSession(roomId)
                .map(session -> {
                    // Game must be in WAITING phase
                    if (isGameActive(roomId)) {
                        return false;
                    }
                    // Room must not be full
                    return session.getPlayers().size() < maxPlayers;
                })
                .orElse(true); // If no session exists, can accept players
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